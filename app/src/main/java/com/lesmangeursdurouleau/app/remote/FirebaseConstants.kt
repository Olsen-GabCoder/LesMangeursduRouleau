package com.lesmangeursdurouleau.app.remote

object FirebaseConstants {
    const val COLLECTION_USERS = "users"
    const val COLLECTION_BOOKS = "books"
    const val COLLECTION_GENERAL_CHAT = "general_chat_messages"
    const val COLLECTION_MONTHLY_READINGS = "monthly_readings"

    const val COLLECTION_APP_CONFIG = "app_config"
    const val DOCUMENT_PERMISSIONS = "permissions"
    const val FIELD_EDIT_READINGS_CODE = "edit_readings_code"
    const val FIELD_SECRET_CODE_LAST_UPDATED_TIMESTAMP = "lastSecretCodeUpdateTimestamp"

    // NOUVELLES CONSTANTES POUR LA LECTURE EN COURS
    const val SUBCOLLECTION_USER_READINGS = "user_readings"
    const val DOCUMENT_ACTIVE_READING = "activeReading"
}