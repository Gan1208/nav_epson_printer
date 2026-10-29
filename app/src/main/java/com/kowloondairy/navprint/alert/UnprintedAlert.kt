package com.kowloondairy.navprint.alert

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kowloondairy.navprint.MainActivity
import com.kowloondairy.navprint.R
import com.kowloondairy.navprint.landing.DeliveryNote
import java.time.LocalDateTime
import java.time.ZoneId

/** Whether this phone has the 未打印提醒 on. Off until staff turn it on in 設定. */
class UnprintedAlertSettings(context: Context) {
    private val prefs = context.getSharedPreferences("unprinted_alert", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Checks in a row that could not reach the database. */
    var failures: Int
        get() = prefs.getInt(KEY_FAILURES, 0)
        set(value) = prefs.edit().putInt(KEY_FAILURES, value).apply()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_FAILURES = "failures"
    }
}

/** What the phone lets the 未打印提醒 do; each false one is shown in 設定 with a way to fix it. */
data class AlertPermissions(val notifications: Boolean, val exactAlarms: Boolean, val batteryUnrestricted: Boolean) {
    companion object {
        fun of(context: Context) = AlertPermissions(
            notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            exactAlarms = canScheduleExactAlarms(context),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
        )
    }
}

private fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

/**
 * Keeps one alarm set for the next check while the 未打印提醒 is on.
 * An alarm clock, not a plain alarm: it fires on time even when the phone is idle, and lets the check start in the foreground.
 */
object UnprintedAlertSchedule {

    fun update(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val check = PendingIntent.getForegroundService(
            context,
            0,
            Intent(context, UnprintedAlertService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!UnprintedAlertSettings(context).enabled || !canScheduleExactAlarms(context)) {
            alarms.cancel(check)
            return
        }
        val at = nextCheck(LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp(context, showUnprinted = false)), check)
    }
}

/** Sets the alarm again after anything that clears or moves it. */
class UnprintedAlertRescheduler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = UnprintedAlertSchedule.update(context)
}

/** Opens the app; with [showUnprinted], on tomorrow's list with the 未打印 送貨單 ticked. */
fun openApp(context: Context, showUnprinted: Boolean): PendingIntent =
    PendingIntent.getActivity(
        context,
        if (showUnprinted) 1 else 0,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SHOW_UNPRINTED, showUnprinted)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

/** The 未打印提醒's notifications. */
class UnprintedAlertNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RUNNING, "未打印提醒運行中", NotificationManager.IMPORTANCE_LOW),
                // Silent: the service rings on the alarm stream itself, so silent mode does not hush it.
                NotificationChannel(CHANNEL_ALERT, "未打印提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_PROBLEM, "未打印提醒問題", NotificationManager.IMPORTANCE_LOW),
            )
        )
    }

    /** Shown while the service is up, from 4 to 6. */
    fun running() = NotificationCompat.Builder(context, CHANNEL_RUNNING)
        .setSmallIcon(R.drawable.ic_unprinted_alert)
        .setContentTitle("未打印提醒運行中")
        .setContentText("下午 4 至 6 點，每 5 分鐘檢查明日送貨單")
        .setOngoing(true)
        .build()

    fun showUnprinted(notes: List<DeliveryNote>) {
        val numbers = notes.joinToString("、") { it.displayNumber }
        manager.notify(
            ID_ALERT,
            NotificationCompat.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_unprinted_alert)
                .setContentTitle("明日有 ${notes.size} 張送貨單未打印")
                .setContentText(numbers)
                .setStyle(NotificationCompat.BigTextStyle().bigText(numbers))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openApp(context, showUnprinted = true))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancelUnprinted() = manager.cancel(ID_ALERT)

    fun showCannotConnect() = manager.notify(
        ID_PROBLEM,
        NotificationCompat.Builder(context, CHANNEL_PROBLEM)
            .setSmallIcon(R.drawable.ic_unprinted_alert)
            .setContentTitle("未打印提醒：連不上數據庫")
            .setContentText("請檢查手機是否已連接公司 wifi")
            .setContentIntent(openApp(context, showUnprinted = false))
            .setAutoCancel(true)
            .build(),
    )

    fun cancelCannotConnect() = manager.cancel(ID_PROBLEM)

    companion object {
        const val ID_RUNNING = 1
        private const val ID_ALERT = 2
        private const val ID_PROBLEM = 3
        private const val CHANNEL_RUNNING = "unprinted_alert_running"
        private const val CHANNEL_ALERT = "unprinted_alert"
        private const val CHANNEL_PROBLEM = "unprinted_alert_problem"
    }
}
