package com.lesmangeursdurouleau.app.utils

import android.content.Context
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.lesmangeursdurouleau.app.R
import dagger.hilt.android.qualifiers.ApplicationContext // NOUVEAU IMPORT
import java.io.IOException
import javax.inject.Inject // NOUVEAU IMPORT

// Cette classe sera responsable de convertir les erreurs techniques en messages conviviaux.
// Elle prend un Context via l'injection Hilt pour pouvoir accéder aux ressources strings.
class ErrorMessageConverter @Inject constructor(
    @ApplicationContext private val context: Context // Hilt injectera le Context de l'application ici
) {
    fun toFriendlyMessage(originalMessage: String?, throwable: Throwable?): String {
        return when (throwable) {
            is FirebaseAuthException -> when (throwable.errorCode) {
                // Utilise context.getString(R.string.ID) pour les messages localisés
                "ERROR_EMAIL_ALREADY_IN_USE" -> context.getString(R.string.firebase_error_email_already_in_use)
                "ERROR_WEAK_PASSWORD" -> context.getString(R.string.firebase_error_weak_password)
                "ERROR_WRONG_PASSWORD" -> context.getString(R.string.firebase_error_wrong_password)
                "ERROR_USER_NOT_FOUND" -> context.getString(R.string.firebase_error_user_not_found)
                "ERROR_INVALID_EMAIL" -> context.getString(R.string.error_invalid_email) // Déjà existant
                "ERROR_OPERATION_NOT_ALLOWED" -> context.getString(R.string.firebase_error_operation_not_allowed)
                "ERROR_NETWORK_REQUEST_FAILED" -> context.getString(R.string.firebase_error_network_request_failed)
                "ERROR_USER_DISABLED" -> context.getString(R.string.firebase_error_user_disabled)
                "ERROR_REQUIRES_RECENT_LOGIN" -> context.getString(R.string.firebase_error_requires_recent_login)
                "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> context.getString(R.string.firebase_error_account_exists_with_different_credential)
                else -> originalMessage ?: context.getString(R.string.error_auth_generic) // Message générique d'authentification si non mappé
            }
            is FirebaseFirestoreException -> when (throwable.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE -> context.getString(R.string.error_server_unavailable_generic)
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> context.getString(R.string.error_permission_denied_generic)
                FirebaseFirestoreException.Code.NOT_FOUND -> context.getString(R.string.error_data_not_found_generic)
                else -> originalMessage ?: context.getString(R.string.error_unknown_generic) // Message générique pour Firestore
            }
            is IOException -> context.getString(R.string.error_network_generic) // Erreurs réseau génériques
            else -> originalMessage ?: context.getString(R.string.error_unknown_generic) // Message générique pour les autres exceptions
        }
    }
}