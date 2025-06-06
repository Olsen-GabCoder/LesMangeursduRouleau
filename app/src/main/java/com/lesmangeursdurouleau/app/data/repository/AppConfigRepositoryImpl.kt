package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Indique que Hilt doit créer une seule instance de ce dépôt
class AppConfigRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : AppConfigRepository {

    companion object {
        private const val TAG = "AppConfigRepositoryImpl"
    }

    // Référence au document spécifique où le code secret et le timestamp de mise à jour seront stockés
    private val configDocRef = firestore
        .collection(FirebaseConstants.COLLECTION_APP_CONFIG)
        .document(FirebaseConstants.DOCUMENT_PERMISSIONS)

    override fun getEditReadingsCode(): Flow<Resource<String>> = callbackFlow {
        trySend(Resource.Loading()) // Émet un état de chargement initial

        val listenerRegistration = configDocRef
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // En cas d'erreur Firestore, loguer et émettre l'erreur
                    Log.e(TAG, "Error listening for edit readings code: ${error.message}", error)
                    // REVERTED: Revenir à la version sans 'throwable'
                    trySend(Resource.Error("Erreur lors du chargement du code secret: ${error.localizedMessage}"))
                    close(error) // Ferme le flux pour signaler une terminaison anormale
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    // Le document existe, tenter de lire le champ du code secret
                    val code = snapshot.getString(FirebaseConstants.FIELD_EDIT_READINGS_CODE)
                    if (code != null) {
                        // Si le code est trouvé, émettre le succès
                        Log.d(TAG, "Edit readings code fetched successfully.")
                        trySend(Resource.Success(code))
                    } else {
                        // Si le champ n'existe pas, émettre une erreur spécifique
                        Log.w(TAG, "Edit readings code field not found in app config document.")
                        trySend(Resource.Error("Code secret d'édition introuvable dans la configuration de l'application."))
                    }
                } else {
                    // Si le document n'existe pas, émettre une erreur spécifique
                    Log.w(TAG, "App config document for permissions does not exist.")
                    trySend(Resource.Error("Document de configuration des permissions introuvable."))
                }
            }

        // Le bloc awaitClose est appelé lorsque le Flow est annulé ou collecté
        awaitClose {
            Log.d(TAG, "Closing edit readings code listener.")
            listenerRegistration.remove() // Supprime le listener Firestore pour éviter les fuites de mémoire
        }
    }

    // NOUVELLE MÉTHODE IMPLÉMENTÉE : Récupère le timestamp de la dernière mise à jour du code secret
    override fun getSecretCodeLastUpdatedTimestamp(): Flow<Resource<Long?>> = callbackFlow {
        trySend(Resource.Loading()) // Émet un état de chargement initial

        val listenerRegistration = configDocRef
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for secret code last updated timestamp: ${error.message}", error)
                    trySend(Resource.Error("Erreur lors du chargement du timestamp du code secret: ${error.localizedMessage}"))
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    // Récupérer le timestamp. Il peut être null si le champ n'existe pas encore.
                    val timestamp = snapshot.getTimestamp(FirebaseConstants.FIELD_SECRET_CODE_LAST_UPDATED_TIMESTAMP)
                    val longTimestamp = timestamp?.toDate()?.time // Convertir en Long (millisecondes depuis l'époque)
                    Log.d(TAG, "Secret code last updated timestamp fetched: $longTimestamp")
                    trySend(Resource.Success(longTimestamp))
                } else {
                    Log.w(TAG, "App config document for permissions does not exist (for timestamp).")
                    // Si le document n'existe pas, le timestamp est considéré comme non disponible.
                    trySend(Resource.Success(null))
                }
            }

        awaitClose {
            Log.d(TAG, "Closing secret code last updated timestamp listener.")
            listenerRegistration.remove()
        }
    }
}