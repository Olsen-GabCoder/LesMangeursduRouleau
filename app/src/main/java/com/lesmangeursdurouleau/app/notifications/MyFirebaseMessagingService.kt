package com.lesmangeursdurouleau.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lesmangeursdurouleau.app.MainActivity
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service pour la réception des messages Firebase Cloud Messaging (FCM).
 * Gère la réception des nouveaux jetons d'enregistrement FCM et l'affichage des notifications.
 */
@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var userRepository: UserRepository // Injecte le repository pour mettre à jour le token FCM

    @Inject
    lateinit var firebaseAuth: FirebaseAuth // Injecte FirebaseAuth pour obtenir l'UID de l'utilisateur

    private val serviceJob = SupervisorJob() // Job pour gérer le cycle de vie des coroutines du service
    private val serviceScope = CoroutineScope(serviceJob) // Scope de coroutines pour les opérations asynchrones

    companion object {
        private const val TAG = "MyFCMService"

        // Constantes pour les canaux de notification Android
        const val CHANNEL_ID_GENERAL = "general_notifications_channel"
        const val CHANNEL_NAME_GENERAL = "Notifications Générales"
        const val CHANNEL_DESC_GENERAL = "Notifications générales du club de lecture."

        // Clés de données attendues dans le payload des messages FCM (envoyées par les Cloud Functions)
        const val NOTIFICATION_TYPE_KEY = "notificationType" // Type de notification (ex: "new_reading")
        const val MONTHLY_READING_ID_KEY = "monthlyReadingId" // ID de la lecture mensuelle concernée
        const val TITLE_KEY = "title" // Titre personnalisé de la notification
        const val BODY_KEY = "body" // Corps personnalisé de la notification

        // Types de notifications pour un traitement spécifique (à correspondre avec les Cloud Functions)
        const val TYPE_NEW_MONTHLY_READING = "new_monthly_reading"
        const val TYPE_PHASE_REMINDER = "phase_reminder"
        const val TYPE_PHASE_STATUS_CHANGE = "phase_status_change"
        const val TYPE_MEETING_LINK_UPDATE = "meeting_link_update"
    }

    /**
     * Appelé lorsque le jeton d'enregistrement du client est généré ou actualisé.
     * Le jeton est nécessaire pour envoyer des messages à cet appareil.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Nouveau jeton FCM : $token")
        // Enregistrez le jeton sur votre backend (Firestore dans ce cas)
        sendRegistrationToServer(token)
    }

    /**
     * Envoie le jeton d'enregistrement FCM au serveur (Firestore) pour l'utilisateur actuellement connecté.
     * @param token Le jeton FCM à enregistrer.
     */
    private fun sendRegistrationToServer(token: String?) {
        token?.let {
            val userId = firebaseAuth.currentUser?.uid
            if (userId != null) {
                serviceScope.launch {
                    try {
                        userRepository.updateUserFCMToken(userId, it)
                        Log.d(TAG, "Jeton FCM mis à jour avec succès pour l'utilisateur $userId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Échec de la mise à jour du jeton FCM pour l'utilisateur $userId: ${e.message}", e)
                    }
                }
            } else {
                Log.w(TAG, "Aucun utilisateur connecté, impossible de sauvegarder le jeton FCM.")
            }
        }
    }

    /**
     * Appelé lorsqu'un message FCM est reçu.
     * @param remoteMessage L'objet RemoteMessage contenant les données du message.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Message reçu de : ${remoteMessage.from}")

        // Vérifie si le message contient un payload de données.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Payload de données du message : ${remoteMessage.data}")

            val notificationType = remoteMessage.data[NOTIFICATION_TYPE_KEY] ?: ""
            val monthlyReadingId = remoteMessage.data[MONTHLY_READING_ID_KEY]
            val title = remoteMessage.data[TITLE_KEY] ?: remoteMessage.notification?.title ?: getString(R.string.app_name)
            val body = remoteMessage.data[BODY_KEY] ?: remoteMessage.notification?.body ?: ""

            // Gère les différents types de notifications basés sur le payload de données
            when (notificationType) {
                TYPE_NEW_MONTHLY_READING,
                TYPE_PHASE_REMINDER,
                TYPE_PHASE_STATUS_CHANGE,
                TYPE_MEETING_LINK_UPDATE -> {
                    sendNotification(title, body, monthlyReadingId, notificationType)
                }
                else -> {
                    // Fallback pour les messages génériques ou les types inattendus
                    sendNotification(title, body, null, null)
                }
            }
        }

        // Vérifie si le message contient un payload de notification (pour les notifications envoyées directement via la console Firebase sans données personnalisées)
        remoteMessage.notification?.let {
            Log.d(TAG, "Corps de la notification : ${it.body}")
            // Si c'est un message de notification uniquement et que le payload de données est vide, affichez-le génériquement.
            if (remoteMessage.data.isEmpty()) {
                sendNotification(it.title ?: getString(R.string.app_name), it.body ?: "", null, null)
            }
        }
    }

    /**
     * Crée et affiche une notification système.
     * @param title Le titre de la notification.
     * @param messageBody Le corps du message de la notification.
     * @param monthlyReadingId L'ID de la lecture mensuelle concernée (optionnel, pour navigation spécifique).
     * @param notificationType Le type de notification (optionnel, pour navigation ou logique UI spécifique).
     */
    private fun sendNotification(title: String, messageBody: String, monthlyReadingId: String?, notificationType: String?) {
        // Crée un Intent qui ouvrira la MainActivity lors du clic sur la notification
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // Efface toutes les activités au-dessus de MainActivity
            // Ajoute des extras pour que MainActivity puisse potentiellement naviguer vers un fragment spécifique
            putExtra(MONTHLY_READING_ID_KEY, monthlyReadingId)
            putExtra(NOTIFICATION_TYPE_KEY, notificationType)
            // Vous pourriez ajouter un extra pour cibler ReadingsFragment par défaut
            // ex: putExtra("targetFragment", "ReadingsFragment")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0 /* Request code */,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE // FLAG_IMMUTABLE est requis pour API 31+
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_notification_icon) // Assurez-vous que cette icône existe et est monochrome pour Android 5+
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true) // Ferme la notification lorsqu'elle est cliquée
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Définit la priorité de la notification

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crée le canal de notification sur Android O (API 26) et versions ultérieures
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                CHANNEL_NAME_GENERAL,
                NotificationManager.IMPORTANCE_HIGH // Importance élevée pour un son et une notification pop-up
            ).apply {
                description = CHANNEL_DESC_GENERAL
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = System.currentTimeMillis().toInt() // ID unique pour chaque notification
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Annule toutes les coroutines lancées dans le serviceScope lorsque le service est détruit.
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}