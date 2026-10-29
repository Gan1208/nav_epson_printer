package com.kowloondairy.navprint.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kowloondairy.navprint.print.SavedPrinter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsScreen(
    state: PrinterSettingsState,
    onBack: () -> Unit,
    onAddressChanged: (String) -> Unit,
    onSave: () -> Unit,
    onTestPrint: () -> Unit,
    onConfirmTestPrint: () -> Unit,
    onCancelTestPrint: () -> Unit,
    onChoose: (SavedPrinter) -> Unit,
    onStartNaming: (SavedPrinter) -> Unit,
    onName: (SavedPrinter, String) -> Unit,
    onCancelNaming: () -> Unit,
    onRemove: (SavedPrinter) -> Unit,
    /** Settings that are not about printers, shown under them. */
    moreSettings: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            val busy = state.busy
            OutlinedTextField(
                value = state.address,
                enabled = !busy,
                onValueChange = onAddressChanged,
                label = { Text("打印機 IP") },
                placeholder = { Text("例如 192.168.1.50") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSave, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("儲存")
                }
                Button(onClick = onTestPrint, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(
                        when (state.test) {
                            TestPrintStatus.Searching -> "搜尋打印機中…"
                            TestPrintStatus.Printing -> "打印中…"
                            else -> "測試打印"
                        },
                    )
                }
            }
            when (val test = state.test) {
                TestPrintStatus.Idle, TestPrintStatus.Searching, TestPrintStatus.Printing -> Unit
                is TestPrintStatus.Asking -> AlertDialog(
                    onDismissRequest = onCancelTestPrint,
                    text = { Text(test.question) },
                    confirmButton = { TextButton(onClick = onConfirmTestPrint) { Text("打印") } },
                    dismissButton = { TextButton(onClick = onCancelTestPrint) { Text("取消") } },
                )
                TestPrintStatus.Passed -> Text("已送出測試打印。如打印機出紙，即表示連接成功。")
                is TestPrintStatus.Failed -> Text(test.problem.message, color = MaterialTheme.colorScheme.error)
            }
            SavedPrinterList(state, onChoose, onStartNaming, onRemove)
            moreSettings()
        }
    }

    state.naming?.let { NameDialog(it, onName, onCancelNaming) }
}

/** The 已儲存打印機, newest first; a tap puts one in use. */
@Composable
private fun SavedPrinterList(
    state: PrinterSettingsState,
    onChoose: (SavedPrinter) -> Unit,
    onStartNaming: (SavedPrinter) -> Unit,
    onRemove: (SavedPrinter) -> Unit,
) {
    if (state.saved.isEmpty()) return
    var removing by remember { mutableStateOf<SavedPrinter?>(null) }

    Column {
        Text("已儲存打印機", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        HorizontalDivider()
        state.saved.forEach { printer ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.busy) { onChoose(printer) }
                    .padding(vertical = 4.dp),
            ) {
                if (printer.address == state.inUse) {
                    Icon(Icons.Filled.Check, contentDescription = "使用中", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Spacer(Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(printer.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(printer.address, printer.model).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onStartNaming(printer) }, enabled = !state.busy) {
                    Icon(Icons.Filled.Edit, contentDescription = "改名")
                }
                IconButton(onClick = { removing = printer }, enabled = !state.busy) {
                    Icon(Icons.Filled.Close, contentDescription = "刪除")
                }
            }
            HorizontalDivider()
        }
    }

    removing?.let { printer ->
        AlertDialog(
            onDismissRequest = { removing = null },
            text = { Text("確定刪除「${printer.label}」？") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(printer)
                    removing = null
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } },
        )
    }
}

/** Asks for a 打印機名稱; left blank, the IP stands in for it. */
@Composable
private fun NameDialog(printer: SavedPrinter, onName: (SavedPrinter, String) -> Unit, onCancel: () -> Unit) {
    var name by remember(printer.address) { mutableStateOf(printer.name) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("打印機名稱") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("例如 倉庫") },
                supportingText = { Text(printer.address) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onName(printer, name) }) { Text("儲存") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("略過") } },
    )
}
