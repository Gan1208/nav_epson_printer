package com.kowloondairy.navprint.receipt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import com.kowloondairy.navprint.landing.DeliveryNote

private val allPages = ReceiptPage.entries.toList()

/**
 * Every page of every 收據 in [deliveryNotes], one under the other.
 * More than one 送貨單 is a 全日打印: they print one at a time, with no single-page printing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPreviewScreen(
    deliveryNotes: List<DeliveryNote>,
    state: ReceiptPreviewState,
    printStatus: PrintStatus,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPrint: (jobs: List<PrintJob>, render: RenderPage) -> Unit,
    onRetryPrint: (render: RenderPage) -> Unit,
    onStop: () -> Unit,
    onDismissPrintStatus: () -> Unit,
) {
    val wholeDay = deliveryNotes.size > 1
    val snackbar = remember { SnackbarHostState() }
    val printing = printStatus is PrintStatus.Printing

    // One recording per page of each 送貨單, filled by PrintPages and read when printing.
    val layers = deliveryNotes.associate { note ->
        key(note.number) { note.number to ReceiptPage.entries.associateWith { rememberGraphicsLayer() } }
    }
    val render: RenderPage = { note, page -> layers.getValue(note.number).getValue(page).printImage() }
    val print = { jobs: List<PrintJob> -> onPrint(jobs, render) }
    val printAll = { print(deliveryNotes.map { PrintJob(it, allPages) }) }

    // Leaving while printing asks first, and waits for the 送貨單 printing now to finish.
    var askingToLeave by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val back = {
        if (printing) {
            askingToLeave = true
        } else {
            onBack()
        }
    }
    BackHandler(onBack = back)
    LaunchedEffect(leaving, printing) {
        if (!printing) askingToLeave = false
        if (leaving && !printing) onBack()
    }

    LaunchedEffect(printStatus) {
        when (printStatus) {
            PrintStatus.Printed -> {
                onDismissPrintStatus()
                snackbar.showSnackbar("打印完成")
            }
            is PrintStatus.Stopped -> {
                onDismissPrintStatus()
                snackbar.showSnackbar("已停止，已印 ${printStatus.done}/${printStatus.total}")
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (wholeDay) "預覽 全日（${deliveryNotes.size} 張）"
                        else "預覽 ${deliveryNotes.first().displayNumber}"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = back) {
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val canPrint = state is ReceiptPreviewState.Loaded && !printing
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Button(
                    onClick = printAll,
                    enabled = canPrint,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when {
                            printStatus is PrintStatus.Printing && wholeDay ->
                                "打印中 ${minOf(printStatus.done + 1, printStatus.total)}/${printStatus.total}…"
                            printing -> "打印中…"
                            wholeDay -> "打印全日（${deliveryNotes.size} 張送貨單）"
                            else -> "打印（三頁）"
                        }
                    )
                }
                if (printStatus is PrintStatus.Printing && wholeDay) {
                    OutlinedButton(onClick = onStop, enabled = !printStatus.stopping) {
                        Text(if (printStatus.stopping) "停止中…" else "停止")
                    }
                }
            }
            if (printing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                when (state) {
                    ReceiptPreviewState.Loading -> CircularProgressIndicator()
                    ReceiptPreviewState.Failed -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("讀取收據失敗")
                        Button(onClick = onRetry) { Text("重試") }
                    }
                    is ReceiptPreviewState.Loaded -> {
                        state.receipts.forEach { PrintPages(it, layers.getValue(it.deliveryNote.number)) }
                        ReceiptList(
                            receipts = state.receipts,
                            wholeDay = wholeDay,
                            canPrint = canPrint,
                            onPrintPage = { note, page -> print(listOf(PrintJob(note, listOf(page)))) },
                        )
                    }
                }
            }
        }
    }

    if (printStatus is PrintStatus.Failed) {
        val failed = printStatus.remaining.first()
        AlertDialog(
            onDismissRequest = onDismissPrintStatus,
            title = {
                Text(
                    if (printStatus.total > 1) {
                        "第 ${printStatus.done + 1}/${printStatus.total} 張（${failed.deliveryNote.displayNumber}）打印失敗"
                    } else {
                        "打印失敗"
                    }
                )
            },
            text = { Text(printStatus.problem.message) },
            confirmButton = {
                TextButton(onClick = { onRetryPrint(render) }) {
                    Text(
                        when {
                            printStatus.total > 1 -> "重試"
                            failed.pages.size > 1 -> "重印全部三頁"
                            else -> "重印此頁"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPrintStatus) { Text("取消") }
            },
        )
    }

    if (askingToLeave && printing) {
        AlertDialog(
            onDismissRequest = { askingToLeave = false },
            title = { Text("打印中，要停止嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    onStop()
                    leaving = true
                    askingToLeave = false
                }) { Text("停止並返回") }
            },
            dismissButton = {
                TextButton(onClick = { askingToLeave = false }) { Text("繼續打印") }
            },
        )
    }
}

/** Each 收據's pages in print order; for 全日 each 送貨單 gets a heading. */
@Composable
private fun ReceiptList(
    receipts: List<Receipt>,
    wholeDay: Boolean,
    canPrint: Boolean,
    onPrintPage: (DeliveryNote, ReceiptPage) -> Unit,
) {
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        receipts.forEachIndexed { index, receipt ->
            val note = receipt.deliveryNote
            if (wholeDay) {
                item(key = note.number) {
                    Text(
                        "${index + 1}. 送貨單 ${note.displayNumber}（${note.category.label}）",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 24.dp),
                    )
                }
            }
            ReceiptPage.entries.forEach { page ->
                item(key = note.number to page) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.width(PaperWidth),
                        ) {
                            Text(page.label, style = MaterialTheme.typography.labelLarge)
                            if (!wholeDay) {
                                TextButton(onClick = { onPrintPage(note, page) }, enabled = canPrint) {
                                    Text("只打印此頁")
                                }
                            }
                        }
                        Surface(shadowElevation = 2.dp) {
                            ReceiptPaper(receipt, page)
                        }
                    }
                }
            }
        }
    }
}
