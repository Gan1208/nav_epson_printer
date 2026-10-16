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
import com.kowloondairy.navprint.landing.FakeDeliveryNoteSource
import com.kowloondairy.navprint.landing.LandingScreen
import com.kowloondairy.navprint.landing.LandingViewModel
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
                            val today = LocalDate.now()
                            LandingViewModel(today, FakeDeliveryNoteSource(today))
                        }
                    }
                )
                val state by viewModel.state.collectAsStateWithLifecycle()

                LandingScreen(
                    state = state,
                    onDeliveryDateChosen = viewModel::chooseDeliveryDate,
                    onCategoryChosen = viewModel::chooseCategory,
                    onDeliveryNoteChosen = viewModel::chooseDeliveryNote,
                    onPrint = { toast("列印：${state.selectedDeliveryNote?.number}（未做）") },
                    onPreview = { toast("預覽：${state.selectedDeliveryNote?.number}（未做）") },
                )
            }
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
