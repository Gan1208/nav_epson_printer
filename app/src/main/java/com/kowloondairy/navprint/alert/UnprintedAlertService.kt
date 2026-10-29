package com.kowloondairy.navprint.alert

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.kowloondairy.navprint.landing.DeliveryNote
import com.kowloondairy.navprint.landing.SqlDeliveryNoteSource
import com.kowloondairy.navprint.navSqlSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Started by each alarm from 4 to 6: checks once whether tomorrow has a 送貨單 that is 未打印, and rings if so.
 * Stays in the foreground between checks, and stops itself after the last one of the day.
 */
class UnprintedAlertService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var checking: Job? = null
    private val notifications by lazy { UnprintedAlertNotifications(this) }
    private val settings by lazy { UnprintedAlertSettings(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            UnprintedAlertNotifications.ID_RUNNING,
            notifications.running(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        UnprintedAlertSchedule.update(this)
        if (checking?.isActive != true) {
            checking = scope.launch {
                if (settings.enabled) awake { check() }
                val today = LocalDate.now()
                if (!settings.enabled || nextCheck(LocalDateTime.now()).toLocalDate() != today) finish()
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 caps how long this kind of service runs; 4 to 6 is well inside it, but stop if it is ever hit. */
    override fun onTimeout(startId: Int, fgsType: Int) = finish()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun check() {
        val unprinted = try {
            SqlDeliveryNoteSource(navSqlSettings).find(LocalDate.now().plusDays(1), null).filterNot(DeliveryNote::printed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            settings.failures += 1
            if (settings.failures >= FAILURES_BEFORE_TELLING) notifications.showCannotConnect()
            return
        }
        settings.failures = 0
        notifications.cancelCannotConnect()
        if (unprinted.isEmpty()) {
            notifications.cancelUnprinted()
        } else {
            notifications.showUnprinted(unprinted)
            ring()
        }
    }

    /** On the alarm stream, like an alarm clock: it sounds in silent mode without touching the phone's volume. */
    private suspend fun ring() {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(this, sound)
            player.isLooping = true
            player.prepare()
            player.start()
            delay(RING_MILLIS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not ring", e)
        } finally {
            player.release()
        }
    }

    /** Keeps the phone awake through the check and the ring, even with the screen off. */
    private suspend fun awake(block: suspend () -> Unit) {
        val lock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "navprint:unprinted-alert")
        lock.acquire(WAKE_MILLIS)
        try {
            block()
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private companion object {
        const val TAG = "UnprintedAlertService"
        const val FAILURES_BEFORE_TELLING = 2
        const val RING_MILLIS = 30_000L
        const val WAKE_MILLIS = 2 * 60_000L
    }
}
