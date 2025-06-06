package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

// Interface du nouveau dépôt pour la configuration de l'application
interface AppConfigRepository {
    /**
     * Récupère le code secret permettant d'accorder la permission d'édition des lectures.
     * Ce code est attendu dans le document 'permissions' de la collection 'app_config'.
     *
     * @return Un Flow de Resource contenant le code secret (String) ou une erreur.
     */
    fun getEditReadingsCode(): Flow<Resource<String>>

    /**
     * NOUVEAU : Récupère le timestamp de la dernière mise à jour du code secret d'édition.
     * Ce timestamp est attendu dans le document 'permissions' de la collection 'app_config'.
     * Il est utilisé pour invalider les permissions des utilisateurs si le code a été changé.
     *
     * @return Un Flow de Resource contenant le timestamp (Long) ou une erreur. Peut être null si le champ n'existe pas.
     */
    fun getSecretCodeLastUpdatedTimestamp(): Flow<Resource<Long?>>
}