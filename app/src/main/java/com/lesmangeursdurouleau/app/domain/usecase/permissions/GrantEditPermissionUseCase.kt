package com.lesmangeursdurouleau.app.domain.usecase.permissions

import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.repository.AppConfigRepository
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.first // Importez .first() pour collecter la première valeur d'un Flow
import javax.inject.Inject

/**
 * Ce Use Case gère la logique de vérification d'un code secret
 * pour accorder à l'utilisateur la permission d'édition des lectures mensuelles.
 */
class GrantEditPermissionUseCase @Inject constructor(
    private val appConfigRepository: AppConfigRepository, // Pour récupérer le code secret de l'application
    private val userRepository: UserRepository,       // Pour mettre à jour la permission de l'utilisateur
    private val firebaseAuth: FirebaseAuth            // Pour obtenir l'ID de l'utilisateur actuellement connecté
) {
    /**
     * Exécute le cas d'utilisation pour valider le code et accorder la permission.
     * @param enteredCode Le code secret saisi par l'utilisateur.
     * @return Un Resource<Unit> indiquant le succès ou l'échec de l'opération.
     */
    suspend operator fun invoke(enteredCode: String): Resource<Unit> {
        val currentUser = firebaseAuth.currentUser
        // Vérifie si un utilisateur est connecté
        if (currentUser == null) {
            return Resource.Error("Vous devez être connecté pour effectuer cette action.")
        }

        // 1. Récupérer le code secret attendu depuis Firestore via AppConfigRepository
        // CORRECTION MAJEURE ICI: Collecter le Flow et attendre un état de succès/erreur.
        val appCodeResource = appConfigRepository.getEditReadingsCode().first { resource ->
            // Le predicat ici permet à .first() de ne pas collecter Resource.Loading()
            // Il attend que la ressource soit soit un Success, soit un Error.
            resource is Resource.Success || resource is Resource.Error
        }

        val expectedCode: String? = when (appCodeResource) {
            is Resource.Success -> appCodeResource.data
            // REVERTED: Revenir à la version sans 'throwable'
            is Resource.Error -> return Resource.Error("Erreur lors de la récupération du code secret de l'application: ${appCodeResource.message}")
            is Resource.Loading -> { // Cette branche ne devrait pas être atteinte grâce au prédicat ci-dessus, mais c'est une bonne pratique
                return Resource.Error("La configuration de l'application est en cours de chargement, veuillez réessayer.")
            }
        }

        // 2. Valider que le code secret est configuré
        if (expectedCode.isNullOrBlank()) {
            return Resource.Error("Code secret non configuré par l'administrateur de l'application.")
        }

        // 3. Comparer le code saisi par l'utilisateur avec le code attendu
        if (enteredCode != expectedCode) {
            return Resource.Error("Code secret incorrect. Veuillez réessayer.")
        }

        // 4. Si le code est correct, mettre à jour la permission de l'utilisateur dans Firestore
        val updatePermissionResult = userRepository.updateUserEditPermission(currentUser.uid, true)
        if (updatePermissionResult is Resource.Success) {
            // AJOUT NOUVELLE LOGIQUE : Mettre à jour le timestamp de la dernière permission accordée
            return userRepository.updateUserLastPermissionTimestamp(currentUser.uid, System.currentTimeMillis())
        }
        return updatePermissionResult
    }
}