package com.lesmangeursdurouleau.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lesmangeursdurouleau.app.MainActivity
import com.lesmangeursdurouleau.app.R
// MODIFIÉ: Import de UserProfileRepository et suppression de UserRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service pour la réception des messages Firebase Cloud Messaging (FCM).
 * Gère la réception des nouveaux jetons d'enregistrement FCM et l'affichage des notifications.
 */
@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    // MODIFIÉ: Injection de UserProfileRepository
    lateinit var userProfileRepository: UserProfileRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    companion object {
        private const val TAG = "MyFCMService"

        const val CHANNEL_ID_GENERAL = "general_notifications_channel"
        const val CHANNEL_NAME_GENERAL = "Notifications Générales"
        const val CHANNEL_DESC_GENERAL = "Notifications générales du club de lecture."

        const val NOTIFICATION_TYPE_KEY = "notificationType"
        const val MONTHLY_READING_ID_KEY = "monthlyReadingId"
        const val TITLE_KEY = "title"
        const val BODY_KEY = "body"

        const val TYPE_NEW_MONTHLY_READING = "new_monthly_reading"
        const val TYPE_PHASE_REMINDER = "phase_reminder"
        const val TYPE_PHASE_STATUS_CHANGE = "phase_status_change"
        const val TYPE_MEETING_LINK_UPDATE = "meeting_link_update"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Nouveau jeton FCM : $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String?) {
        token?.let {
            val userId = firebaseAuth.currentUser?.uid
            if (userId != null) {
                serviceScope.launch {
                    try {
                        // MODIFIÉ: Appel sur userProfileRepository
                        userProfileRepository.updateUserFCMToken(userId, it)
                        Log.d(TAG, "Jeton FCM mis à jour avec succès pour l'utilisateur $userId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Échec de la mise à jour du jeton FCM pour l'utilisateur $userId: ${e.message}", e)
                    }
                }
            } else {
                Log.w(TAG, "Aucun utilisateur connecté, impossible de sauvegarder le jeton FCM.")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Message reçu de : ${remoteMessage.from}")

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Payload de données du message : ${remoteMessage.data}")

            val notificationType = remoteMessage.data[NOTIFICATION_TYPE_KEY] ?: ""
            val monthlyReadingId = remoteMessage.data[MONTHLY_READING_ID_KEY]
            val title = remoteMessage.data[TITLE_KEY] ?: remoteMessage.notification?.title ?: getString(R.string.app_name)
            val body = remoteMessage.data[BODY_KEY] ?: remoteMessage.notification?.body ?: ""

            when (notificationType) {
                TYPE_NEW_MONTHLY_READING,
                TYPE_PHASE_REMINDER,
                TYPE_PHASE_STATUS_CHANGE,
                TYPE_MEETING_LINK_UPDATE -> {
                    sendNotification(title, body, monthlyReadingId, notificationType)
                }
                else -> {
                    sendNotification(title, body, null, null)
                }
            }
        }

        remoteMessage.notification?.let {
            Log.d(TAG, "Corps de la notification : ${it.body}")
            if (remoteMessage.data.isEmpty()) {
                sendNotification(it.title ?: getString(R.string.app_name), it.body ?: "", null, null)
            }
        }
    }

    private fun sendNotification(title: String, messageBody: String, monthlyReadingId: String?, notificationType: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MONTHLY_READING_ID_KEY, monthlyReadingId)
            putExtra(NOTIFICATION_TYPE_KEY, notificationType)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                CHANNEL_NAME_GENERAL,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_GENERAL
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}