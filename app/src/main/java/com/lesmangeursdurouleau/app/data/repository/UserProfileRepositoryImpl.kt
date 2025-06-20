package com.lesmangeursdurouleau.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Implémentation de UserProfileRepository pour la gestion du profil utilisateur avec Firebase.
 */
class UserProfileRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorageService: FirebaseStorageService
) : UserProfileRepository {

    override suspend fun updateUserProfile(userId: String, username: String): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override fun getAllUsers(): Flow<Resource<List<User>>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override fun getUserById(userId: String): Flow<Resource<User>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun updateUserTypingStatus(userId: String, isTyping: Boolean): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun updateUserBio(userId: String, bio: String): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun updateUserCity(userId: String, city: String): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun updateUserEditPermission(userId: String, canEdit: Boolean): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun updateUserLastPermissionTimestamp(userId: String, timestamp: Long?): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }

    override suspend fun updateUserFCMToken(userId: String, token: String): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 4.")
    }
}