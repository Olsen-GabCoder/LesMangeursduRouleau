import * as functions from "firebase-functions";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";

// Initialise l'Admin SDK de Firebase.
admin.initializeApp();

// Référence à la base de données Firestore
const db = admin.firestore();

// Constantes pour les noms de collections Firestore (à aligner avec l'app Android)
const COLLECTIONS = {
  MONTHLY_READINGS: "monthly_readings",
  USERS: "users",
  BOOKS: "books",
};

// Constantes pour les types de notifications (à aligner avec MyFirebaseMessagingService)
const NOTIFICATION_TYPES = {
  NEW_MONTHLY_READING: "new_monthly_reading",
  PHASE_REMINDER: "phase_reminder", // Sera utilisée plus tard pour les rappels planifiés
  PHASE_STATUS_CHANGE: "phase_status_change",
  MEETING_LINK_UPDATE: "meeting_link_update",
};

// Constantes pour les statuts de phase
const PHASE_STATUS = {
  PLANIFIED: "planified",
  IN_PROGRESS: "in_progress",
  COMPLETED: "completed",
};

/**
 * Fonction utilitaire pour normaliser les statuts (gère minuscules et majuscules)
 */
function normalizeStatus(status: string | undefined | null): string | null {
  if (!status || typeof status !== "string") return null;

  const normalizedStatus = status.toLowerCase().trim();

  // Mapping des différentes variations possibles
  switch (normalizedStatus) {
    case "planified":
    case "planned":
      return PHASE_STATUS.PLANIFIED;
    case "in_progress":
    case "in progress":
    case "inprogress":
      return PHASE_STATUS.IN_PROGRESS;
    case "completed":
    case "complete":
    case "finished":
      return PHASE_STATUS.COMPLETED;
    default:
      return normalizedStatus;
  }
}

/**
 * Fonction utilitaire pour récupérer tous les jetons FCM des utilisateurs,
 * y compris leur ID utilisateur.
 */
async function getAllUserFcmTokens(): Promise<Array<{ uid: string; token: string }>> {
  const usersSnapshot = await db.collection(COLLECTIONS.USERS).get();
  const tokensWithUids: Array<{ uid: string; token: string }> = [];

  functions.logger.info("Début de la collecte des jetons FCM des utilisateurs.");
  usersSnapshot.forEach((doc) => {
    const userData = doc.data();
    const fcmToken = userData.fcmToken;

    if (fcmToken && typeof fcmToken === "string" && fcmToken.trim().length > 0) {
      tokensWithUids.push({ uid: doc.id, token: fcmToken.trim() });
      functions.logger.info(`  Collecté jeton pour utilisateur ${doc.id}: ${fcmToken.trim().substring(0, 10)}...`);
    } else {
      functions.logger.warn(`  Jeton FCM invalide ou absent pour l'utilisateur ${doc.id}: ${fcmToken}`);
    }
  });
  functions.logger.info(`Fin de la collecte. ${tokensWithUids.length} jetons valides trouvés.`);
  return tokensWithUids;
}

/**
 * Fonction utilitaire pour envoyer un message FCM multicast.
 */
async function sendFCMNotification(
  recipients: Array<{ uid: string; token: string }>,
  title: string,
  body: string,
  data: { [key: string]: string }
): Promise<boolean> { // Retourne maintenant un boolean pour indiquer si l'envoi a réussi
  functions.logger.info(`[sendFCMNotification] DÉBUT - Destinataires: ${recipients.length}`);

  if (recipients.length === 0) {
    functions.logger.warn("[sendFCMNotification] Aucun destinataire FCM valide fourni pour l'envoi.");
    return false; // Indique que l'envoi n'a pas eu lieu
  }

  const tokensToSend = recipients.map(r => r.token);
  functions.logger.info(`[sendFCMNotification] Jetons à envoyer: ${tokensToSend.length} jetons`);
  functions.logger.info(`[sendFCMNotification] Premier jeton (extrait): ${tokensToSend[0]?.substring(0, 20)}...`);

  const message: admin.messaging.MulticastMessage = {
    tokens: tokensToSend,
    notification: {
      title: title,
      body: body,
    },
    data: {
      ...data,
      title: title,
      body: body,
    },
    android: {
      priority: "high",
      notification: {
        channelId: "general_notifications_channel", // Correspond à votre service Android
        priority: "high",
      },
    },
    apns: {
      headers: { "apns-priority": "10" },
      payload: {
        aps: {
          sound: "default",
          badge: 1,
        }
      },
    },
  };

  try {
    functions.logger.info("[sendFCMNotification] *** TENTATIVE D'ENVOI FCM ***");
    functions.logger.info(`[sendFCMNotification] Message: Titre="${title}", Corps="${body}"`);
    functions.logger.info(`[sendFCMNotification] Data: ${JSON.stringify(data)}`);

    const response = await admin.messaging().sendEachForMulticast(message);

    functions.logger.info(`[sendFCMNotification] RÉSULTAT: ${response.successCount} succès, ${response.failureCount} échecs`);

    if (response.successCount > 0) {
      functions.logger.info(`[sendFCMNotification] ✅ ${response.successCount} notifications envoyées avec SUCCÈS !`);
    }

    if (response.failureCount > 0) {
      functions.logger.warn(`[sendFCMNotification] ⚠️ ${response.failureCount} échecs détectés`);
      const tokensToRemove: string[] = [];
      const batch = db.batch();

      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          const failedRecipient = recipients[idx];
          const failedToken = failedRecipient?.token;
          const failedUid = failedRecipient?.uid;

          functions.logger.error(`[sendFCMNotification] Échec pour ${failedUid}: ${resp.error?.message} (Code: ${resp.error?.code})`);

          const isInvalidTokenError = resp.error?.code === "messaging/invalid-registration-token" ||
                                      resp.error?.code === "messaging/registration-token-not-registered" ||
                                      resp.error?.code === "messaging/not-found";

          if (failedToken && failedUid && isInvalidTokenError) {
            tokensToRemove.push(failedToken);
            functions.logger.warn(`[sendFCMNotification] Suppression du jeton invalide pour ${failedUid}`);
            const userRef = db.collection(COLLECTIONS.USERS).doc(failedUid);
            batch.update(userRef, { fcmToken: admin.firestore.FieldValue.delete() });
          }
        }
      });

      if (tokensToRemove.length > 0) {
        await batch.commit();
        functions.logger.info(`[sendFCMNotification] Supprimé ${tokensToRemove.length} jetons invalides de Firestore.`);
      }
      return response.successCount > 0; // Si des succès, on considère que l'envoi a eu lieu
    }
    return response.successCount > 0; // Retourne true si au moins un succès
  } catch (error) {
    functions.logger.error("[sendFCMNotification] ❌ ERREUR lors de l'envoi FCM:", error);
    // throw error; // Ne pas re-throw ici pour ne pas faire échouer toute la fonction Cloud Function
    return false; // Indique que l'envoi a échoué
  } finally {
    functions.logger.info("[sendFCMNotification] FIN de la fonction");
  }
}

/**
 * Cloud Function déclenchée lors de la création d'une nouvelle lecture mensuelle.
 */
export const onNewMonthlyReadingCreated = onDocumentCreated(
  `${COLLECTIONS.MONTHLY_READINGS}/{monthlyReadingId}`,
  async (event) => {
    const newMonthlyReading = event.data?.data();
    const monthlyReadingId = event.params.monthlyReadingId as string;

    if (!newMonthlyReading) {
      functions.logger.warn(`Aucune donnée pour la lecture mensuelle ${monthlyReadingId}. Annulation.`);
      return null;
    }

    functions.logger.info(`Nouvelle lecture mensuelle détectée : ${monthlyReadingId}`);

    let bookTitle = "Nouvelle lecture";
    if (newMonthlyReading.bookId) {
      try {
        const bookDoc = await db.collection(COLLECTIONS.BOOKS).doc(newMonthlyReading.bookId).get();
        if (bookDoc.exists) {
          bookTitle = bookDoc.data()?.title || bookTitle;
          functions.logger.info(`Titre du livre récupéré : ${bookTitle}`);
        } else {
          functions.logger.warn(`Livre avec ID ${newMonthlyReading.bookId} non trouvé.`);
        }
      } catch (error) {
        functions.logger.error("Erreur lors de la récupération du titre du livre :", error);
      }
    }

    const notificationTitle = `📖 Nouvelle lecture mensuelle !`;
    const notificationBody = `Le livre du mois est "${bookTitle}" ! Découvrez-le vite.`;

    const recipients = await getAllUserFcmTokens();
    await sendFCMNotification(recipients, notificationTitle, notificationBody, {
      notificationType: NOTIFICATION_TYPES.NEW_MONTHLY_READING,
      monthlyReadingId: monthlyReadingId,
    });

    return null;
  }
);

/**
 * Cloud Function déclenchée lors de la mise à jour d'une lecture mensuelle.
 */
export const onMonthlyReadingUpdated = onDocumentUpdated(
  `${COLLECTIONS.MONTHLY_READINGS}/{monthlyReadingId}`,
  async (event) => {
    const beforeData = event.data?.before.data();
    const afterData = event.data?.after.data();
    const monthlyReadingId = event.params.monthlyReadingId as string;

    if (!beforeData || !afterData) {
      functions.logger.warn(`Données avant ou après manquantes pour la lecture mensuelle ${monthlyReadingId}. Annulation.`);
      return null;
    }

    functions.logger.info(`[onMonthlyReadingUpdated] DÉBUT - Lecture: ${monthlyReadingId}`);

    // Logs de diagnostic détaillés
    functions.logger.info(`[onMonthlyReadingUpdated] Raw beforeData.analysisPhase: ${JSON.stringify(beforeData.analysisPhase)}`);
    functions.logger.info(`[onMonthlyReadingUpdated] Raw afterData.analysisPhase: ${JSON.stringify(afterData.analysisPhase)}`);
    functions.logger.info(`[onMonthlyReadingUpdated] Raw beforeData.debatePhase: ${JSON.stringify(beforeData.debatePhase)}`);
    functions.logger.info(`[onMonthlyReadingUpdated] Raw afterData.debatePhase: ${JSON.stringify(afterData.debatePhase)}`);

    const recipients = await getAllUserFcmTokens();
    if (recipients.length === 0) {
      functions.logger.warn("Aucun destinataire FCM valide trouvé pour les notifications de mise à jour.");
      return null;
    }

    let notificationsSent = false; // Flag pour suivre si au moins une notification a été envoyée

    // --- Utilisation de la fonction de normalisation ---
    const oldAnalysisStatus = normalizeStatus(beforeData.analysisPhase?.status);
    const newAnalysisStatus = normalizeStatus(afterData.analysisPhase?.status);
    const oldDebateStatus = normalizeStatus(beforeData.debatePhase?.status);
    const newDebateStatus = normalizeStatus(afterData.debatePhase?.status);

    functions.logger.info(`[onMonthlyReadingUpdated] Statuts Analyse NORMALISÉS: Old="${oldAnalysisStatus}", New="${newAnalysisStatus}"`);
    functions.logger.info(`[onMonthlyReadingUpdated] Statuts Débat NORMALISÉS: Old="${oldDebateStatus}", New="${newDebateStatus}"`);

    let bookTitle = "La lecture du mois";
    if (afterData.bookId) {
      try {
        const bookDoc = await db.collection(COLLECTIONS.BOOKS).doc(afterData.bookId).get();
        if (bookDoc.exists) {
          bookTitle = bookDoc.data()?.title || bookTitle;
        }
      } catch (error) {
        functions.logger.error("Erreur lors de la récupération du titre du livre pour la mise à jour :", error);
      }
    }

    // --- Vérification du changement de statut de phase ---
    if (newAnalysisStatus && newAnalysisStatus !== oldAnalysisStatus) {
      functions.logger.info(`[onMonthlyReadingUpdated] ✅ CHANGEMENT DE STATUT D'ANALYSE: ${oldAnalysisStatus} -> ${newAnalysisStatus}`);

      if (newAnalysisStatus === PHASE_STATUS.IN_PROGRESS) {
        functions.logger.info(`[onMonthlyReadingUpdated] 🚀 Envoi notification: Analyse EN COURS`);
        const title = `🔍 Début de l'analyse !`;
        const body = `La phase d'analyse de "${bookTitle}" est maintenant en cours. Participez !`;
        notificationsSent = await sendFCMNotification(recipients, title, body, {
          notificationType: NOTIFICATION_TYPES.PHASE_STATUS_CHANGE,
          monthlyReadingId: monthlyReadingId,
          phase: "analysis",
          status: PHASE_STATUS.IN_PROGRESS,
        }) || notificationsSent;
      } else if (newAnalysisStatus === PHASE_STATUS.COMPLETED) {
        functions.logger.info(`[onMonthlyReadingUpdated] ✅ Envoi notification: Analyse TERMINÉE`);
        const title = `✅ Analyse terminée !`;
        const body = `La phase d'analyse de "${bookTitle}" est à présent terminée.`;
        notificationsSent = await sendFCMNotification(recipients, title, body, {
          notificationType: NOTIFICATION_TYPES.PHASE_STATUS_CHANGE,
          monthlyReadingId: monthlyReadingId,
          phase: "analysis",
          status: PHASE_STATUS.COMPLETED,
        }) || notificationsSent;
      }
    } else {
      functions.logger.info(`[onMonthlyReadingUpdated] ℹ️ Pas de changement de statut d'analyse détecté (${oldAnalysisStatus || 'null'} -> ${newAnalysisStatus || 'null'}).`);
    }

    if (newDebateStatus && newDebateStatus !== oldDebateStatus) {
      functions.logger.info(`[onMonthlyReadingUpdated] ✅ CHANGEMENT DE STATUT DE DÉBAT: ${oldDebateStatus} -> ${newDebateStatus}`);

      if (newDebateStatus === PHASE_STATUS.IN_PROGRESS) {
        functions.logger.info(`[onMonthlyReadingUpdated] 🚀 Envoi notification: Débat EN COURS`);
        const title = `💬 Début du débat !`;
        const body = `La phase de débat de "${bookTitle}" est maintenant en cours. Rejoignez-nous !`;
        notificationsSent = await sendFCMNotification(recipients, title, body, {
          notificationType: NOTIFICATION_TYPES.PHASE_STATUS_CHANGE,
          monthlyReadingId: monthlyReadingId,
          phase: "debate",
          status: PHASE_STATUS.IN_PROGRESS,
        }) || notificationsSent;
      } else if (newDebateStatus === PHASE_STATUS.COMPLETED) {
        functions.logger.info(`[onMonthlyReadingUpdated] ✅ Envoi notification: Débat TERMINÉ`);
        const title = `✅ Débat terminé !`;
        const body = `La phase de débat de "${bookTitle}" est à présent terminée.`;
        notificationsSent = await sendFCMNotification(recipients, title, body, {
          notificationType: NOTIFICATION_TYPES.PHASE_STATUS_CHANGE,
          monthlyReadingId: monthlyReadingId,
          phase: "debate",
          status: PHASE_STATUS.COMPLETED,
        }) || notificationsSent;
      }
    } else {
      functions.logger.info(`[onMonthlyReadingUpdated] ℹ️ Pas de changement de statut de débat détecté (${oldDebateStatus || 'null'} -> ${newDebateStatus || 'null'}).`);
    }

    // --- Vérification du changement de lien de réunion ---
    // Normalisation des liens pour une comparaison robuste (traite null/undefined comme vide)
    const oldAnalysisLink = beforeData.analysisPhase?.meetingLink || "";
    const newAnalysisLink = afterData.analysisPhase?.meetingLink || "";
    const oldDebateLink = beforeData.debatePhase?.meetingLink || "";
    const newDebateLink = afterData.debatePhase?.meetingLink || "";

    functions.logger.info(`[onMonthlyReadingUpdated] Liens Analyse: Old="${oldAnalysisLink}", New="${newAnalysisLink}"`);
    functions.logger.info(`[onMonthlyReadingUpdated] Liens Débat: Old="${oldDebateLink}", New="${newDebateLink}"`);

    if (newAnalysisLink && newAnalysisLink !== oldAnalysisLink) {
      functions.logger.info(`[onMonthlyReadingUpdated] ✅ CHANGEMENT DE LIEN D'ANALYSE: ${oldAnalysisLink} -> ${newAnalysisLink}`);
      functions.logger.info(`[onMonthlyReadingUpdated] 🔗 Envoi notification: Lien d'analyse mis à jour`);
      const title = `🔗 Lien de réunion d'analyse mis à jour !`;
      const body = `Le lien pour la réunion d'analyse de "${bookTitle}" a été mis à jour.`;
      notificationsSent = await sendFCMNotification(recipients, title, body, {
        notificationType: NOTIFICATION_TYPES.MEETING_LINK_UPDATE,
        monthlyReadingId: monthlyReadingId,
        phase: "analysis",
        newLink: newAnalysisLink,
      }) || notificationsSent;
    } else {
      functions.logger.info(`[onMonthlyReadingUpdated] ℹ️ Pas de changement de lien d'analyse détecté (${oldAnalysisLink} -> ${newAnalysisLink}).`);
    }

    if (newDebateLink && newDebateLink !== oldDebateLink) {
      functions.logger.info(`[onMonthlyReadingUpdated] ✅ CHANGEMENT DE LIEN DE DÉBAT: ${oldDebateLink} -> ${newDebateLink}`);
      functions.logger.info(`[onMonthlyReadingUpdated] 🔗 Envoi notification: Lien de débat mis à jour`);
      const title = `🔗 Lien de réunion de débat mis à jour !`;
      const body = `Le lien pour la réunion de débat de "${bookTitle}" a été mis à jour.`;
      notificationsSent = await sendFCMNotification(recipients, title, body, {
        notificationType: NOTIFICATION_TYPES.MEETING_LINK_UPDATE,
        monthlyReadingId: monthlyReadingId,
        phase: "debate",
        newLink: newDebateLink,
      }) || notificationsSent;
    } else {
      functions.logger.info(`[onMonthlyReadingUpdated] ℹ️ Pas de changement de lien de débat détecté (${oldDebateLink} -> ${newDebateLink}).`);
    }

    functions.logger.info(`[onMonthlyReadingUpdated] FIN - Lecture: ${monthlyReadingId}. Notifications envoyées: ${notificationsSent}`);
    return null;
  }
);