package ru.raikmann.doorbell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

const val CHANNEL_ID = "doorbell_ring4"

// Максимум 30 секунд звона (каждый ДИН-ДОН ~2.35 сек → ~13 повторов)
private const val MAX_REPEATS = 13

class FCMService : FirebaseMessagingService() {

    companion object {
        @Volatile private var activePlayer: MediaPlayer? = null

        fun stopRinging() {
            activePlayer?.let {
                try { if (it.isPlaying) it.stop(); it.release() } catch (_: Exception) {}
            }
            activePlayer = null
        }
    }

    override fun onNewToken(token: String) {
        val phone = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PHONE, "") ?: ""
        if (phone.isNotBlank()) registerToken(phone, token)
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.data["title"] ?: msg.notification?.title ?: "Домофон"
        val body  = msg.data["body"]  ?: msg.notification?.body  ?: "Звонок в дверь"
        val url   = msg.data["url"] ?: ""
        stopRinging()
        playDoorbellSound(MAX_REPEATS)
        showNotification(title, body, url)
    }

    private fun playDoorbellSound(repeat: Int) {
        if (repeat <= 0) { activePlayer = null; return }
        try {
            val mp = MediaPlayer()
            activePlayer = mp
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
                if (activePlayer == mp) playDoorbellSound(repeat - 1)
            }
        } catch (e: Exception) { activePlayer = null }
    }

    private fun showNotification(title: String, body: String, url: String = "") {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (url.isNotBlank()) putExtra("door_url", url)
        }
        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
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
