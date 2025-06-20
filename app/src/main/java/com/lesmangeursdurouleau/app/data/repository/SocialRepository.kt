package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository dédié à la gestion des interactions sociales entre utilisateurs (graphe social).
 */
interface SocialRepository {

    /**
     * Permet à un utilisateur d'en suivre un autre.
     * @param currentUserId L'ID de l'utilisateur qui effectue l'action.
     * @param targetUserId L'ID de l'utilisateur à suivre.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun followUser(currentUserId: String, targetUserId: String): Resource<Unit>

    /**
     * Permet à un utilisateur de ne plus en suivre un autre.
     * @param currentUserId L'ID de l'utilisateur qui effectue l'action.
     * @param targetUserId L'ID de l'utilisateur à ne plus suivre.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun unfollowUser(currentUserId: String, targetUserId: String): Resource<Unit>

    /**
     * Vérifie en temps réel si un utilisateur en suit un autre.
     * @param currentUserId L'ID de l'utilisateur qui suit potentiellement.
     * @param targetUserId L'ID de l'utilisateur qui est potentiellement suivi.
     * @return Un Flow de Resource contenant un booléen (true si suivi, false sinon).
     */
    fun isFollowing(currentUserId: String, targetUserId: String): Flow<Resource<Boolean>>

    /**
     * Récupère en temps réel la liste des utilisateurs qu'un utilisateur donné suit (ses abonnements).
     * @param userId L'ID de l'utilisateur dont on veut la liste des abonnements.
     * @return Un Flow de Resource contenant la liste des utilisateurs suivis.
     */
    fun getFollowingUsers(userId: String): Flow<Resource<List<User>>>

    /**
     * Récupère en temps réel la liste des utilisateurs qui suivent un utilisateur donné (ses abonnés).
     * @param userId L'ID de l'utilisateur dont on veut la liste des abonnés.
     * @return Un Flow de Resource contenant la liste des utilisateurs abonnés.
     */
    fun getFollowersUsers(userId: String): Flow<Resource<List<User>>>
}