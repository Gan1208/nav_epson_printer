package com.kowloondairy.navprint.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kowloondairy.navprint.print.SavedPrinter
import com.kowloondairy.navprint.ui.ProvideListText
import com.kowloondairy.navprint.ui.ProvidePageScale

/**
 * Asks which 已儲存打印機 to print [total] 送貨單 on, then shows how the printing goes.
 * [lastUsed] is the IP printed to last time, picked to start with.
 * While printing it cannot be closed; 停止 is the way out.
 * Text is as big as the main page's 送貨單 list.
 */
@Composable
fun PrintDialog(
    printers: List<SavedPrinter>,
    lastUsed: String,
    total: Int,
    receipts: ReceiptPreviewState,
    printStatus: PrintStatus,
    onPrint: (address: String) -> Unit,
    onRetryLoad: () -> Unit,
    onRetryPrint: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit,
) {
    val printing = printStatus is PrintStatus.Printing
    Dialog(
        onDismissRequest = { if (!printing) onClose() },
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        ProvidePageScale {
            ProvideListText {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(24.dp)) {
                        when (printStatus) {
                            PrintStatus.Idle -> Choosing(
                                printers, lastUsed, total, receipts, onPrint, onRetryLoad, onClose, onSettings,
                            )
                            is PrintStatus.Printing -> {
                                Text("打印中 ${minOf(printStatus.done + 1, printStatus.total)}/${printStatus.total}…")
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                if (printStatus.total > 1) {
                                    Buttons {
                                        DialogButton(
                                            if (printStatus.stopping) "停止中…" else "停止",
                                            onStop,
                                            enabled = !printStatus.stopping,
                                        )
                                    }
                                }
                            }
                            PrintStatus.Printed -> {
                                Text("打印完成")
                                Buttons { DialogButton("關閉", onClose) }
                            }
                            is PrintStatus.Stopped -> {
                                Text("已停止，已印 ${printStatus.done}/${printStatus.total}")
                                Buttons { DialogButton("關閉", onClose) }
                            }
                            is PrintStatus.Failed -> {
                                Text(
                                    if (printStatus.total > 1) {
                                        val failed = printStatus.remaining.first()
                                        "第 ${printStatus.done + 1}/${printStatus.total} 張（${failed.displayNumber}）打印失敗"
                                    } else {
                                        "打印失敗"
                                    }
                                )
                                Text(printStatus.problem.message, color = MaterialTheme.colorScheme.error)
                                Buttons {
                                    DialogButton("取消", onClose)
                                    DialogButton("重試", onRetryPrint, filled = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Before printing: the 已儲存打印機 to pick from, and whether the 收據 are ready. */
@Composable
private fun Choosing(
    printers: List<SavedPrinter>,
    lastUsed: String,
    total: Int,
    receipts: ReceiptPreviewState,
    onPrint: (address: String) -> Unit,
    onRetryLoad: () -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit,
) {
    Text("打印（$total 張）")
    if (printers.isEmpty()) {
        Text("未有打印機")
        Buttons {
            DialogButton("取消", onClose)
            DialogButton("去設定", onSettings, filled = true)
        }
        return
    }

    var chosen by remember {
        mutableStateOf(printers.firstOrNull { it.address == lastUsed }?.address ?: printers.first().address)
    }
    Column(
        Modifier
            .selectableGroup()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        printers.forEach { printer ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = printer.address == chosen,
                        role = Role.RadioButton,
                        onClick = { chosen = printer.address },
                    )
                    .padding(vertical = 8.dp),
            ) {
                RadioButton(selected = printer.address == chosen, onClick = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(printer.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // A printer with no name already shows its IP as its label.
                    if (printer.name.isNotBlank()) {
                        Text(printer.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    when (receipts) {
        ReceiptPreviewState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.padding(end = 12.dp))
            Text("讀取收據中…")
        }
        ReceiptPreviewState.Failed -> Text("讀取收據失敗", color = MaterialTheme.colorScheme.error)
        is ReceiptPreviewState.Loaded -> Unit
    }
    Buttons {
        DialogButton("取消", onClose)
        if (receipts == ReceiptPreviewState.Failed) {
            DialogButton("重試", onRetryLoad, filled = true)
        } else {
            DialogButton(
                "打印",
                { onPrint(chosen) },
                enabled = receipts is ReceiptPreviewState.Loaded,
                filled = true,
            )
        }
    }
}

@Composable
private fun Buttons(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

/** Buttons set their own small text; this keeps the dialog's big text on them too. */
@Composable
private fun DialogButton(text: String, onClick: () -> Unit, enabled: Boolean = true, filled: Boolean = false) {
    val style = LocalTextStyle.current
    if (filled) {
        Button(onClick = onClick, enabled = enabled) { Text(text, style = style) }
    } else {
        TextButton(onClick = onClick, enabled = enabled) { Text(text, style = style) }
    }
}
