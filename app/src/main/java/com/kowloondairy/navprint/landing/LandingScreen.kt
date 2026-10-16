package com.kowloondairy.navprint.landing

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** Every control uses the same rounded rectangle as the 送貨單 field. */
private val fieldShape = RoundedCornerShape(4.dp)
private val fieldHeight = 56.dp

/** 全部 first, then each [Category]. */
private val categoryChoices: List<Category?> = listOf(null) + Category.entries

/** Shortcut chips under the date field, as days after today. */
private val quickDates = listOf(0L to "今日", 1L to "聽日")

/** e.g. 2026-10-17（六） */
private fun LocalDate.display(): String =
    "${format(dateFormat)}（${dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.TRADITIONAL_CHINESE)}）"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    state: LandingUiState,
    onDeliveryDateChosen: (LocalDate) -> Unit,
    onCategoryChosen: (Category?) -> Unit,
    onDeliveryNoteChosen: (DeliveryNote) -> Unit,
    onPrint: () -> Unit,
    onPreview: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("送貨單打印") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    enabled = state.canPrint,
                    shape = fieldShape,
                    modifier = Modifier.weight(1f).height(fieldHeight),
                ) { Text("預覽") }
                Button(
                    onClick = onPrint,
                    enabled = state.canPrint,
                    shape = fieldShape,
                    modifier = Modifier.weight(1f).height(fieldHeight),
                ) { Text("列印") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            // Sits a quarter of the way down the free space: above centre, off the top.
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                DeliveryDateField(state.today, state.deliveryDate, onDeliveryDateChosen)
                CategoryField(state.category, onCategoryChosen)
                DeliveryNoteField(state.selectedDeliveryNote, state.deliveryNoteOptions, onDeliveryNoteChosen)
            }
            Spacer(Modifier.weight(3f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeliveryDateField(today: LocalDate, date: LocalDate, onChosen: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    // A read-only text field swallows clicks, so open the picker on press release instead.
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(interactions) {
        interactions.interactions.collect {
            if (it is PressInteraction.Release) showPicker = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = date.display(),
            onValueChange = {},
            readOnly = true,
            label = { Text("日期") },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "揀日期") },
            shape = fieldShape,
            interactionSource = interactions,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickDates.forEach { (daysAhead, label) ->
                val quickDate = today.plusDays(daysAhead)
                FilterChip(
                    selected = date == quickDate,
                    onClick = { onChosen(quickDate) },
                    label = {
                        Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    },
                    shape = fieldShape,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showPicker) {
        // DatePicker works in UTC millis.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onChosen(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(category: Category?, onChosen: (Category?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("類別")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            categoryChoices.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = choice == category,
                    onClick = { onChosen(choice) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = categoryChoices.size,
                        baseShape = fieldShape,
                    ),
                    modifier = Modifier.height(fieldHeight),
                ) { Text(choice?.label ?: "全部") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeliveryNoteField(
    selected: DeliveryNote?,
    options: List<DeliveryNote>,
    onChosen: (DeliveryNote) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasOptions = options.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded && hasOptions,
        onExpandedChange = { expanded = it && hasOptions },
    ) {
        OutlinedTextField(
            // The placeholder only shows when focused, so say "none" in the value itself.
            value = selected?.number ?: if (hasOptions) "" else "冇送貨單",
            onValueChange = {},
            readOnly = true,
            enabled = hasOptions,
            label = { Text("送貨單") },
            placeholder = { Text("請選擇") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = fieldShape,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = hasOptions)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && hasOptions, onDismissRequest = { expanded = false }) {
            options.forEach { note ->
                DropdownMenuItem(
                    text = { Text("${note.number}（${note.category.label}）") },
                    onClick = {
                        onChosen(note)
                        expanded = false
                    },
                )
            }
        }
    }
}
