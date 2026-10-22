package com.kowloondairy.navprint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kowloondairy.navprint.landing.DeliveryNote
import com.kowloondairy.navprint.landing.LandingScreen
import com.kowloondairy.navprint.landing.LandingViewModel
import com.kowloondairy.navprint.landing.SqlConnectionSettings
import com.kowloondairy.navprint.landing.SqlDeliveryNoteSource
import com.kowloondairy.navprint.print.EpsonPrinterFinder
import com.kowloondairy.navprint.print.EpsonReceiptPrinter
import com.kowloondairy.navprint.print.SharedPrefsPrinterAddressStore
import com.kowloondairy.navprint.receipt.ReceiptPreviewScreen
import com.kowloondairy.navprint.receipt.ReceiptPreviewViewModel
import com.kowloondairy.navprint.receipt.SqlReceiptSource
import com.kowloondairy.navprint.settings.PrinterSettingsScreen
import com.kowloondairy.navprint.settings.PrinterSettingsViewModel
import com.kowloondairy.navprint.ui.NavPrintTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NavPrintTheme {
                val viewModel: LandingViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            LandingViewModel(LocalDate.now(), SqlDeliveryNoteSource(navSqlSettings))
                        }
                    }
                )
                val state by viewModel.state.collectAsStateWithLifecycle()
                var previewing by remember { mutableStateOf<List<DeliveryNote>?>(null) }
                var inSettings by remember { mutableStateOf(false) }

                val notes = previewing
                when {
                    inSettings -> {
                        BackHandler { inSettings = false }
                        PrinterSettings(onBack = { inSettings = false })
                    }
                    // The preview handles back itself, to ask first while printing.
                    notes != null -> ReceiptPreview(notes, onBack = { previewing = null })
                    else -> LandingScreen(
                        state = state,
                        onDeliveryDateChosen = viewModel::chooseDeliveryDate,
                        onCategoryChosen = viewModel::chooseCategory,
                        onQuery = viewModel::query,
                        onToggle = viewModel::toggle,
                        onToggleAll = viewModel::toggleAll,
                        onRetry = viewModel::retry,
                        onPreview = { previewing = state.toPreview },
                        onSettings = { inSettings = true },
                    )
                }
            }
        }
    }

    @Composable
    private fun ReceiptPreview(notes: List<DeliveryNote>, onBack: () -> Unit) {
        val viewModel: ReceiptPreviewViewModel = viewModel(
            key = "receipt-${notes.joinToString(",") { it.number }}",
            factory = viewModelFactory {
                initializer { ReceiptPreviewViewModel(notes, SqlReceiptSource(navSqlSettings), printer) }
            },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val printStatus by viewModel.printStatus.collectAsStateWithLifecycle()
        ReceiptPreviewScreen(
            notes,
            state,
            printStatus,
            onBack = onBack,
            onRetry = viewModel::retry,
            onPrint = viewModel::print,
            onRetryPrint = viewModel::retryPrint,
            onStop = viewModel::stop,
            onDismissPrintStatus = viewModel::dismissPrintStatus,
        )
    }

    @Composable
    private fun PrinterSettings(onBack: () -> Unit) {
        val viewModel: PrinterSettingsViewModel = viewModel(
            factory = viewModelFactory {
                initializer { PrinterSettingsViewModel(printerAddresses, printer, printerFinder) }
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
        )
    }

    private val printerAddresses by lazy { SharedPrefsPrinterAddressStore(applicationContext) }
    private val printer by lazy { EpsonReceiptPrinter(applicationContext, printerAddresses) }
    private val printerFinder by lazy { EpsonPrinterFinder(applicationContext) }

    private val navSqlSettings = SqlConnectionSettings(
        host = BuildConfig.NAV_SQL_HOST,
        port = BuildConfig.NAV_SQL_PORT,
        instance = BuildConfig.NAV_SQL_INSTANCE,
        database = BuildConfig.NAV_SQL_DATABASE,
        user = BuildConfig.NAV_SQL_USER,
        password = BuildConfig.NAV_SQL_PASSWORD,
    )
}
