package com.lesmangeursdurouleau.app.utils.auth

import android.content.Context
import com.lesmangeursdurouleau.app.R // Assurez-vous que cet import est correct

object AuthErrorConverter {
    fun getFirebaseAuthErrorMessage(context: Context, exception: Exception?, errorCode: String?): String {
        return when (errorCode) {
            "ERROR_INVALID_CUSTOM_TOKEN" -> context.getString(R.string.firebase_error_invalid_custom_token)
            "ERROR_CUSTOM_TOKEN_MISMATCH" -> context.getString(R.string.firebase_error_custom_token_mismatch)
            "ERROR_INVALID_CREDENTIAL" -> context.getString(R.string.firebase_error_invalid_credential)
            "ERROR_USER_DISABLED" -> context.getString(R.string.firebase_error_user_disabled)
            "ERROR_USER_TOKEN_EXPIRED" -> context.getString(R.string.firebase_error_user_token_expired)
            "ERROR_USER_NOT_FOUND" -> context.getString(R.string.firebase_error_user_not_found)
            "ERROR_INVALID_USER_TOKEN" -> context.getString(R.string.firebase_error_invalid_user_token)
            "ERROR_OPERATION_NOT_ALLOWED" -> context.getString(R.string.firebase_error_operation_not_allowed)
            "ERROR_EMAIL_ALREADY_IN_USE" -> context.getString(R.string.firebase_error_email_already_in_use)
            "ERROR_WEAK_PASSWORD" -> context.getString(R.string.firebase_error_weak_password)
            "ERROR_WRONG_PASSWORD" -> context.getString(R.string.firebase_error_wrong_password)
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> context.getString(R.string.firebase_error_account_exists_with_different_credential)
            "ERROR_REQUIRES_RECENT_LOGIN" -> context.getString(R.string.firebase_error_requires_recent_login)
            "ERROR_NETWORK_REQUEST_FAILED" -> context.getString(R.string.firebase_error_network_request_failed)
            else -> exception?.message ?: context.getString(R.string.unknown_error)
        }
    }
}