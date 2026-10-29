package com.kowloondairy.navprint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kowloondairy.navprint.alert.UnprintedAlertSchedule
import com.kowloondairy.navprint.alert.UnprintedAlertSection
import com.kowloondairy.navprint.landing.Category
import com.kowloondairy.navprint.landing.DeliveryNote
import com.kowloondairy.navprint.landing.LandingScreen
import com.kowloondairy.navprint.landing.LandingViewModel
import com.kowloondairy.navprint.landing.SharedPrefsDefaultCategoryStore
import com.kowloondairy.navprint.landing.SqlDeliveryNoteSource
import com.kowloondairy.navprint.print.EpsonPrinterFinder
import com.kowloondairy.navprint.print.EpsonReceiptPrinter
import com.kowloondairy.navprint.print.SharedPrefsPrinterAddressStore
import com.kowloondairy.navprint.print.SharedPrefsSavedPrinterStore
import com.kowloondairy.navprint.receipt.PrintDialog
import com.kowloondairy.navprint.receipt.PrintStatus
import com.kowloondairy.navprint.receipt.ReceiptPreviewScreen
import com.kowloondairy.navprint.receipt.ReceiptPreviewState
import com.kowloondairy.navprint.receipt.ReceiptPreviewViewModel
import com.kowloondairy.navprint.receipt.SqlPrintedRecord
import com.kowloondairy.navprint.receipt.SqlReceiptSource
import com.kowloondairy.navprint.receipt.rememberPageLayers
import com.kowloondairy.navprint.settings.PrinterSettingsScreen
import com.kowloondairy.navprint.settings.PrinterSettingsViewModel
import com.kowloondairy.navprint.ui.NavPrintTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Also sets the alarm again after the app is installed or updated.
        UnprintedAlertSchedule.update(this)
        // Only on a fresh start, so turning the screen does not load the list again.
        val showUnprinted = savedInstanceState == null && intent.getBooleanExtra(EXTRA_SHOW_UNPRINTED, false)
        setContent {
            NavPrintTheme {
                val viewModel: LandingViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            LandingViewModel(
                                LocalDate.now(),
                                SqlDeliveryNoteSource(navSqlSettings),
                                defaultCategory.category,
                            )
                        }
                    }
                )
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { if (showUnprinted) viewModel.showUnprinted() }
                var previewing by remember { mutableStateOf<List<DeliveryNote>?>(null) }
                // Each 打印 from the main page counts up, so it gets its own 收據, loaded fresh.
                var printing by remember { mutableStateOf<Pair<Int, List<DeliveryNote>>?>(null) }
                var printRuns by remember { mutableStateOf(0) }
                var inSettings by remember { mutableStateOf(false) }
                // A 本裝置預設類別 changed in 設定 is used straight away; one left alone keeps the 類別 picked here.
                var defaultBefore by remember { mutableStateOf<Category?>(null) }
                val openSettings = {
                    defaultBefore = defaultCategory.category
                    inSettings = true
                }
                val leaveSettings = {
                    inSettings = false
                    val now = defaultCategory.category
                    if (now != defaultBefore) viewModel.chooseCategory(now)
                }

                val notes = previewing
                when {
                    inSettings -> {
                        BackHandler(onBack = leaveSettings)
                        PrinterSettings(onBack = leaveSettings)
                    }
                    notes != null -> ReceiptPreview(
                        notes,
                        onBack = {
                            viewModel.reload()
                            previewing = null
                        },
                        onSettings = openSettings,
                    )
                    else -> {
                        LandingScreen(
                            state = state,
                            onDeliveryDateChosen = viewModel::chooseDeliveryDate,
                            onCategoryChosen = viewModel::chooseCategory,
                            onQuery = viewModel::query,
                            onToggle = viewModel::toggle,
                            onToggleAll = viewModel::toggleAll,
                            onRetry = viewModel::retry,
                            onPreview = { previewing = state.ticked },
                            onPrint = { printing = ++printRuns to state.ticked },
                            onSettings = openSettings,
                        )
                        printing?.let { (run, ticked) ->
                            PrintWithoutPreview(
                                run,
                                ticked,
                                onClose = { printed ->
                                    printing = null
                                    if (printed) viewModel.reload()
                                },
                                onSettings = {
                                    printing = null
                                    openSettings()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun receiptViewModel(key: String, notes: List<DeliveryNote>): ReceiptPreviewViewModel = viewModel(
        key = key,
        factory = viewModelFactory {
            initializer {
                ReceiptPreviewViewModel(notes, SqlReceiptSource(navSqlSettings), printer, printedRecord, printerAddresses)
            }
        },
    )

    @Composable
    private fun ReceiptPreview(notes: List<DeliveryNote>, onBack: () -> Unit, onSettings: () -> Unit) {
        val viewModel = receiptViewModel("receipt-${notes.joinToString(",") { it.number }}", notes)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val printStatus by viewModel.printStatus.collectAsStateWithLifecycle()
        // Read each time the preview opens, so a printer saved in 設定 shows up.
        val printers = remember { savedPrinters.printers }
        ReceiptPreviewScreen(
            notes,
            state,
            printStatus,
            printers = printers,
            lastUsed = printerAddresses.address,
            onBack = onBack,
            onRetry = viewModel::retry,
            onPrint = viewModel::print,
            onRetryPrint = viewModel::retryPrint,
            onStop = viewModel::stop,
            onDismissPrintStatus = viewModel::dismissPrintStatus,
            onSettings = onSettings,
        )
    }

    /**
     * 打印 on the main page: the print dialog over the list, with the pages drawn out of sight.
     * [onClose] says whether anything was sent to the printer, so the list can show the new ✓.
     */
    @Composable
    private fun PrintWithoutPreview(
        run: Int,
        notes: List<DeliveryNote>,
        onClose: (printed: Boolean) -> Unit,
        onSettings: () -> Unit,
    ) {
        val viewModel = receiptViewModel("print-$run", notes)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val printStatus by viewModel.printStatus.collectAsStateWithLifecycle()
        val printers = remember { savedPrinters.printers }
        val pages = rememberPageLayers(notes)
        (state as? ReceiptPreviewState.Loaded)?.let { pages.Record(it.receipts) }
        PrintDialog(
            printers = printers,
            lastUsed = printerAddresses.address,
            total = notes.size,
            receipts = state,
            printStatus = printStatus,
            onPrint = { address -> viewModel.print(address, pages.render) },
            onRetryLoad = viewModel::retry,
            onRetryPrint = { viewModel.retryPrint(pages.render) },
            onStop = viewModel::stop,
            onClose = {
                val printed = printStatus != PrintStatus.Idle
                viewModel.dismissPrintStatus()
                onClose(printed)
            },
            onSettings = onSettings,
        )
    }

    @Composable
    private fun PrinterSettings(onBack: () -> Unit) {
        val viewModel: PrinterSettingsViewModel = viewModel(
            factory = viewModelFactory {
                initializer {
                    PrinterSettingsViewModel(printerAddresses, printer, printerFinder, savedPrinters, defaultCategory)
                }
            },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        PrinterSettingsScreen(
            state,
            onBack = onBack,
            onAddressChanged = viewModel::editAddress,
            onSave = viewModel::save,
            onTestPrint = viewModel::testPrint,
            onConfirmTestPrint = viewModel::confirmTestPrint,
            onCancelTestPrint = viewModel::cancelTestPrint,
            onChoose = viewModel::choose,
            onStartEditing = viewModel::startEditing,
            onEdit = viewModel::edit,
            onCancelEditing = viewModel::cancelEditing,
            onRemove = viewModel::remove,
            onDefaultCategoryChosen = viewModel::chooseDefaultCategory,
            moreSettings = { UnprintedAlertSection() },
        )
    }

    private val printerAddresses by lazy { SharedPrefsPrinterAddressStore(applicationContext) }
    private val printer by lazy { EpsonReceiptPrinter(applicationContext, printerAddresses) }
    private val printerFinder by lazy { EpsonPrinterFinder(applicationContext) }
    private val savedPrinters by lazy { SharedPrefsSavedPrinterStore(applicationContext) }
    private val defaultCategory by lazy { SharedPrefsDefaultCategoryStore(applicationContext) }
    companion object {
        /** Set by the 未打印提醒 notification: open on tomorrow's list with the 未打印 ones ticked. */
        const val EXTRA_SHOW_UNPRINTED = "show_unprinted"
    }

    private val printedRecord by lazy { SqlPrintedRecord(navSqlSettings) { printerAddresses.address } }
}
