package com.lesmangeursdurouleau.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Implémentation de SocialRepository pour la gestion des interactions sociales avec Firebase.
 */
class SocialRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : SocialRepository {

    override suspend fun followUser(currentUserId: String, targetUserId: String): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 3.")
    }

    override suspend fun unfollowUser(currentUserId: String, targetUserId: String): Resource<Unit> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 3.")
    }

    override fun isFollowing(currentUserId: String, targetUserId: String): Flow<Resource<Boolean>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 3.")
    }

    override fun getFollowingUsers(userId: String): Flow<Resource<List<User>>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 3.")
    }

    override fun getFollowersUsers(userId: String): Flow<Resource<List<User>>> {
        TODO("La logique sera migrée depuis UserRepositoryImpl dans la Phase 3.")
    }
}