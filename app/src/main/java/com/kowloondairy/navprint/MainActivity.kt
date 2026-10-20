package com.kowloondairy.navprint

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kowloondairy.navprint.landing.LandingScreen
import com.kowloondairy.navprint.landing.LandingViewModel
import com.kowloondairy.navprint.landing.SqlConnectionSettings
import com.kowloondairy.navprint.landing.SqlDeliveryNoteSource
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

                LandingScreen(
                    state = state,
                    onDeliveryDateChosen = viewModel::chooseDeliveryDate,
                    onCategoryChosen = viewModel::chooseCategory,
                    onDeliveryNoteChosen = viewModel::chooseDeliveryNote,
                    onRetry = viewModel::retry,
                    onPrint = { toast("列印：${state.selectedDeliveryNote?.number}（未做）") },
                    onPreview = { toast("預覽：${state.selectedDeliveryNote?.number}（未做）") },
                )
            }
        }
    }

    private val navSqlSettings = SqlConnectionSettings(
        host = BuildConfig.NAV_SQL_HOST,
        port = BuildConfig.NAV_SQL_PORT,
        instance = BuildConfig.NAV_SQL_INSTANCE,
        database = BuildConfig.NAV_SQL_DATABASE,
        user = BuildConfig.NAV_SQL_USER,
        password = BuildConfig.NAV_SQL_PASSWORD,
    )

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
