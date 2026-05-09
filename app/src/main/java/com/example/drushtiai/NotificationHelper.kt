package com.example.drushtiai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.drushtiai.data.CheatingSnapshotRow

/**
 * Sound + system notification when a new cheating snapshot is detected by
 * the backend and inserted into Supabase. The polling loop in
 * SurveillanceActivity invokes [notifyNewSnapshot] for every newly arrived
 * snapshot row so the invigilator gets an immediate alert with a buzz/sound
 * even if they're looking away from the screen.
 */
object NotificationHelper {

    const val CHANNEL_ID_ALERTS = "drushti_cheating_alerts"
    private const val CHANNEL_NAME = "Cheating alerts"
    private const val CHANNEL_DESCRIPTION =
        "Plays a sound and vibration when a new cheating snapshot is detected during surveillance."

    private var nextNotificationId = 5000

    /** Idempotent — safe to call from Application.onCreate() and on every activity start. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID_ALERTS) != null) return

        val soundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID_ALERTS,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 120, 250)
            setSound(soundUri, audioAttrs)
            lightColor = 0xFFFF3B30.toInt()
            enableLights(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Show a heads-up notification for a new snapshot. The notification taps
     * through to SurveillanceActivity for the same exam.
     */
    fun notifyNewSnapshot(
        context: Context,
        examId: String,
        snapshot: CheatingSnapshotRow,
        totalNew: Int = 1
    ) {
        ensureChannel(context)

        // Always buzz + beep regardless of permission state — guarantees the
        // invigilator gets feedback even if the system notification is blocked.
        vibrateAndBeep(context)

        if (!hasPostPermission(context)) {
            android.util.Log.w(
                "Notif",
                "POST_NOTIFICATIONS not granted — fired sound+vibrate only."
            )
            return
        }

        val openIntent = Intent(context, SurveillanceActivity::class.java).apply {
            putExtra(IntentExtras.EXAM_ID, examId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, 0, openIntent, pendingFlags)

        val title = if (totalNew > 1)
            "Cheating detected ($totalNew new)"
        else
            "Cheating detected"
        val body = snapshot.label?.takeIf { it.isNotBlank() }
            ?: "A new violation was captured by the backend."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pending)

        val nm = NotificationManagerCompat.from(context)
        try {
            val id = nextNotificationId++
            nm.notify(id, builder.build())
            android.util.Log.i("Notif", "Posted notification id=$id title='$title'")
        } catch (e: SecurityException) {
            android.util.Log.w("Notif", "notify() threw SecurityException: ${e.message}")
        }
    }

    private fun vibrateAndBeep(context: Context) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 250, 120, 250), -1)
            }
        } catch (_: Exception) {
            // best-effort fallback
        }
        try {
            val ringtone = RingtoneManager.getRingtone(
                context,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )
            ringtone?.play()
        } catch (_: Exception) {
            // ignore
        }
    }
}
