package ru.raikmann.doorbell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

const val CHANNEL_ID = "doorbell_ring3"

class FCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val phone = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PHONE, "") ?: ""
        if (phone.isNotBlank()) registerToken(phone, token)
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.data["title"] ?: msg.notification?.title ?: "Домофон"
        val body  = msg.data["body"]  ?: msg.notification?.body  ?: "Звонок в дверь"
        playDoorbellSound()
        showNotification(title, body)
    }

    private fun playDoorbellSound(repeat: Int = 3) {
        if (repeat <= 0) return
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            val uri = Uri.parse("android.resource://$packageName/${R.raw.doorbell}")
            mp.setDataSource(applicationContext, uri)
            mp.setVolume(1f, 1f)
            mp.prepare()
            mp.start()
            mp.setOnCompletionListener {
                it.release()
                playDoorbellSound(repeat - 1)
            }
        } catch (e: Exception) { /* игнорируем */ }
    }

    private fun showNotification(title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val sound = Uri.parse("android.resource://$packageName/${R.raw.doorbell}")
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSound(sound)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(1, notif)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val sound = Uri.parse("android.resource://$packageName/${R.raw.doorbell}")
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val ch = NotificationChannel(CHANNEL_ID, "Звонок домофона",
            NotificationManager.IMPORTANCE_HIGH).apply {
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(sound, attrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }
        nm.createNotificationChannel(ch)
    }
}

fun registerToken(phone: String, token: String) {
    Thread {
        try {
            val conn = java.net.URL("https://cloud1.5855993.ru/door/api/fcm/register")
                .openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            java.io.OutputStreamWriter(conn.outputStream).use {
                it.write("{\"phone\":\"$phone\",\"token\":\"$token\"}")
            }
            conn.responseCode
        } catch (e: Exception) { /* игнорируем */ }
    }.start()
}
