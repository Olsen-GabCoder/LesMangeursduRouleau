package com.lesmangeursdurouleau.app.data.remote

import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStorageService @Inject constructor(
    private val storage: FirebaseStorage
) {
    companion object {
        private const val TAG = "FirebaseStorageService"
    }

    suspend fun uploadProfilePicture(userId: String, imageData: ByteArray): Resource<String> {
        // --- AJOUT DU LOG POUR LA TAILLE DE L'IMAGE ---
        Log.d(TAG, "uploadProfilePicture: Reçu pour UserID: $userId, Taille des imageData: ${imageData.size} bytes.")
        // --- FIN DE L'AJOUT ---

        if (imageData.isEmpty()) {
            Log.e(TAG, "uploadProfilePicture: imageData est vide. Annulation de l'upload.")
            return Resource.Error("Impossible d'uploader une image vide.")
        }

        val randomFileName = "${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child("profile_pictures/$userId/$randomFileName")
        Log.i(TAG, "uploadProfilePicture: Tentative d'upload vers Storage Path: ${storageRef.path}")

        return try {
            Log.d(TAG, "uploadProfilePicture: Exécution de storageRef.putBytes() pour ${storageRef.path}")
            storageRef.putBytes(imageData).await()
            Log.i(TAG, "uploadProfilePicture: putBytes SUCCÈS pour ${storageRef.path}")

            Log.d(TAG, "uploadProfilePicture: Exécution de storageRef.downloadUrl.await() pour ${storageRef.path}")
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.i(TAG, "uploadProfilePicture: getDownloadUrl SUCCÈS pour ${storageRef.path}. URL: $downloadUrl")

            Resource.Success(downloadUrl)

        } catch (e: Exception) {
            Log.e(TAG, "uploadProfilePicture: EXCEPTION lors de l'opération sur ${storageRef.path}. Type: ${e.javaClass.simpleName}, Message: ${e.message}", e)

            val errorMessage = if (e is StorageException) {
                "Erreur Firebase Storage (code ${e.errorCode}): ${e.localizedMessage}"
            } else {
                "Erreur lors de l'upload de la photo: ${e.localizedMessage}"
            }
            Log.e(TAG, "uploadProfilePicture: Message d'erreur final retourné au Repository: $errorMessage")
            Resource.Error(errorMessage)
        }
    }
}