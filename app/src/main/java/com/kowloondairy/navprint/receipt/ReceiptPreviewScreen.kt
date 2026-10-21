package com.kowloondairy.navprint.receipt

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import com.kowloondairy.navprint.landing.DeliveryNote

private val allPages = ReceiptPage.entries.toList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPreviewScreen(
    deliveryNote: DeliveryNote,
    state: ReceiptPreviewState,
    printStatus: PrintStatus,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPrint: (pages: List<ReceiptPage>, render: suspend (ReceiptPage) -> Bitmap) -> Unit,
    onDismissPrintStatus: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(ReceiptPage.RETURN) }
    val snackbar = remember { SnackbarHostState() }

    // One recording per page, filled by PrintPages and read when printing.
    val layers = ReceiptPage.entries.associateWith { rememberGraphicsLayer() }
    val print = { pages: List<ReceiptPage> -> onPrint(pages) { layers.getValue(it).printImage() } }

    LaunchedEffect(printStatus) {
        if (printStatus == PrintStatus.Printed) {
            onDismissPrintStatus()
            snackbar.showSnackbar("印好喇")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("預覽 ${deliveryNote.displayNumber}") },
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val printing = printStatus is PrintStatus.Printing
            val canPrint = state is ReceiptPreviewState.Loaded && !printing
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Button(
                    onClick = { print(allPages) },
                    enabled = canPrint,
                    modifier = Modifier.weight(1f),
                ) { Text(if (printing) "打印緊…" else "打印（三頁）") }
                OutlinedButton(
                    onClick = { print(listOf(page)) },
                    enabled = canPrint,
                    modifier = Modifier.weight(1f),
                ) { Text("只印呢頁") }
            }
            if (printing) LinearProgressIndicator(Modifier.fillMaxWidth())
            TabRow(selectedTabIndex = page.ordinal) {
                ReceiptPage.entries.forEach { choice ->
                    Tab(
                        selected = choice == page,
                        onClick = { page = choice },
                        text = { Text(choice.label) },
                    )
                }
            }
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
                        PrintPages(state.receipt, layers)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp),
                        ) {
                            Surface(shadowElevation = 2.dp) {
                                ReceiptPaper(state.receipt, page)
                            }
                        }
                    }
                }
            }
        }
    }

    if (printStatus is PrintStatus.Failed) {
        AlertDialog(
            onDismissRequest = onDismissPrintStatus,
            title = { Text("打印失敗") },
            text = { Text(printStatus.problem.message) },
            confirmButton = {
                TextButton(onClick = { print(printStatus.pages) }) {
                    Text(if (printStatus.pages.size > 1) "重印全部三頁" else "重印呢頁")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPrintStatus) { Text("取消") }
            },
        )
    }
}
