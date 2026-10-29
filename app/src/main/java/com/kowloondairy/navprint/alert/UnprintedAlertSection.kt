package com.kowloondairy.navprint.alert

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/** The 未打印提醒 switch in 設定, and below it anything the phone still has to allow for it to work. */
@Composable
fun UnprintedAlertSection() {
    val context = LocalContext.current
    val settings = remember { UnprintedAlertSettings(context) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var permissions by remember { mutableStateOf(AlertPermissions.of(context)) }

    // Coming back from a system settings page is how most of these get fixed.
    LifecycleResumeEffect(Unit) {
        permissions = AlertPermissions.of(context)
        UnprintedAlertSchedule.update(context)
        onPauseOrDispose {}
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions = AlertPermissions.of(context)
    }
    fun open(action: String, withPackage: Boolean = false) {
        val intent = Intent(action)
        if (withPackage) intent.data = Uri.parse("package:${context.packageName}")
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        context.startActivity(intent)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("未打印提醒", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "下午 4 至 6 點每 5 分鐘檢查明日送貨單，有未打印就響鬧鐘",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                // As big as the printer buttons above it.
                modifier = Modifier.scale(1.3f),
                checked = enabled,
                onCheckedChange = { on ->
                    enabled = on
                    settings.enabled = on
                    UnprintedAlertSchedule.update(context)
                    if (on && !permissions.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
        if (!enabled) return@Column
        if (!permissions.exactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Fix("未允許準時鬧鐘，提醒不會運行", "允許") {
                open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, withPackage = true)
            }
        }
        if (!permissions.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Fix("未允許通知，只會響，不會顯示哪張未打印", "允許") { open(Settings.ACTION_APP_NOTIFICATION_SETTINGS) }
        }
        if (!permissions.batteryUnrestricted) {
            Fix("電池優化可能會停止提醒，請將本 app 設為「不優化」或「不限制」", "設定") {
                open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
        }
    }
}

@Composable
private fun Fix(problem: String, action: String, onFix: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            problem,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onFix, modifier = Modifier.height(64.dp)) {
            Text(action, style = MaterialTheme.typography.titleMedium)
        }
    }
}
