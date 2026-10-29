package com.kowloondairy.navprint.receipt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kowloondairy.navprint.landing.DeliveryNote
import com.kowloondairy.navprint.print.SavedPrinter

/**
 * Every page of every 收據 in [deliveryNotes], one under the other.
 * 打印 prints them all, after [PrintDialog] asks which printer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPreviewScreen(
    deliveryNotes: List<DeliveryNote>,
    state: ReceiptPreviewState,
    printStatus: PrintStatus,
    printers: List<SavedPrinter>,
    /** The IP printed to last time, which the dialog picks first. */
    lastUsed: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPrint: (address: String, render: RenderPage) -> Unit,
    onRetryPrint: (render: RenderPage) -> Unit,
    onStop: () -> Unit,
    onDismissPrintStatus: () -> Unit,
    onSettings: () -> Unit,
) {
    val several = deliveryNotes.size > 1
    val pages = rememberPageLayers(deliveryNotes)
    var choosing by remember { mutableStateOf(false) }

    // While printing the dialog is up and keeps back for itself.
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (several) "預覽（${deliveryNotes.size} 張）"
                        else "預覽 ${deliveryNotes.first().displayNumber}"
                    )
                },
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            Button(
                onClick = { choosing = true },
                enabled = state is ReceiptPreviewState.Loaded,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            ) { Text(if (several) "打印（${deliveryNotes.size} 張送貨單）" else "打印（三頁）") }
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
                        pages.Record(state.receipts)
                        ReceiptList(receipts = state.receipts, several = several)
                    }
                }
            }
        }
    }

    if (choosing || printStatus != PrintStatus.Idle) {
        PrintDialog(
            printers = printers,
            lastUsed = lastUsed,
            total = deliveryNotes.size,
            receipts = state,
            printStatus = printStatus,
            onPrint = { address -> onPrint(address, pages.render) },
            onRetryLoad = onRetry,
            onRetryPrint = { onRetryPrint(pages.render) },
            onStop = onStop,
            onClose = {
                choosing = false
                onDismissPrintStatus()
            },
            onSettings = onSettings,
        )
    }
}

/** Each 收據's pages in print order; for 多張打印 each 送貨單 gets a heading. */
@Composable
private fun ReceiptList(receipts: List<Receipt>, several: Boolean) {
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        receipts.forEachIndexed { index, receipt ->
            val note = receipt.deliveryNote
            if (several) {
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
                        Text(
                            page.label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(PaperWidth).padding(vertical = 8.dp),
                        )
                        Surface(shadowElevation = 2.dp) {
                            ReceiptPaper(receipt, page)
                        }
                    }
                }
            }
        }
    }
}
