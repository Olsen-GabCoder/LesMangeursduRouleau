package com.lesmangeursdurouleau.app.domain.usecase.permissions

import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.repository.AppConfigRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Ce Use Case gère la logique de vérification d'un code secret
 * pour accorder à l'utilisateur la permission d'édition des lectures mensuelles.
 */
class GrantEditPermissionUseCase @Inject constructor(
    private val appConfigRepository: AppConfigRepository,
    private val userProfileRepository: UserProfileRepository,
    private val firebaseAuth: FirebaseAuth
) {
    /**
     * Exécute le cas d'utilisation pour valider le code et accorder la permission.
     * @param enteredCode Le code secret saisi par l'utilisateur.
     * @return Un Resource<Unit> indiquant le succès ou l'échec de l'opération.
     */
    suspend operator fun invoke(enteredCode: String): Resource<Unit> {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            return Resource.Error("Vous devez être connecté pour effectuer cette action.")
        }

        val appCodeResource = appConfigRepository.getEditReadingsCode().first { resource ->
            resource is Resource.Success || resource is Resource.Error
        }

        val expectedCode: String? = when (appCodeResource) {
            // CORRECTION: L'accès à .data est correct, car il est de type T? (String?)
            is Resource.Success -> appCodeResource.data
            is Resource.Error -> return Resource.Error("Erreur lors de la récupération du code secret: ${appCodeResource.message}")
            is Resource.Loading -> return Resource.Error("La configuration est en cours de chargement, veuillez réessayer.")
        }

        if (expectedCode.isNullOrBlank()) {
            return Resource.Error("Code secret non configuré par l'administrateur.")
        }

        if (enteredCode != expectedCode) {
            return Resource.Error("Code secret incorrect. Veuillez réessayer.")
        }

        val updatePermissionResult = userProfileRepository.updateUserEditPermission(currentUser.uid, true)
        if (updatePermissionResult is Resource.Success) {
            return userProfileRepository.updateUserLastPermissionTimestamp(currentUser.uid, System.currentTimeMillis())
        }
        return updatePermissionResult
    }
}