package com.kowloondairy.navprint.landing

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** Every control uses the same rounded rectangle as the date field. */
private val fieldShape = RoundedCornerShape(4.dp)
private val fieldHeight = 56.dp

/** 全部 first, then each [Category]. */
private val categoryChoices: List<Category?> = listOf(null) + Category.entries

/** Shortcut chips under the date field, as days after today. */
private val quickDates = listOf(0L to "今日", 1L to "明日")

/** e.g. 2026-10-17（六） */
private fun LocalDate.display(): String =
    "${format(dateFormat)}（${dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.TRADITIONAL_CHINESE)}）"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    state: LandingUiState,
    onDeliveryDateChosen: (LocalDate) -> Unit,
    onCategoryChosen: (Category?) -> Unit,
    onQuery: () -> Unit,
    onToggle: (DeliveryNote) -> Unit,
    onToggleAll: () -> Unit,
    onRetry: () -> Unit,
    onPreview: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("送貨單打印") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "設定")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            // Printing happens from the preview, so every 收據 is seen before it is printed.
            Button(
                onClick = onPreview,
                enabled = state.canPrint,
                shape = fieldShape,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(fieldHeight),
            ) { Text(if (state.canPrint) "預覽（${state.toPreview.size} 張）" else "預覽") }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                .fillMaxSize(),
        ) {
            DeliveryDateField(state.today, state.deliveryDate, onDeliveryDateChosen)
            CategoryField(state.category, onCategoryChosen)
            OutlinedButton(
                onClick = onQuery,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth().height(fieldHeight),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("查詢")
            }
            DeliveryNoteList(
                state = state,
                onToggle = onToggle,
                onToggleAll = onToggleAll,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )
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
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "選擇日期") },
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

/** The 送貨單 found by 查詢, each with a tick; or why there are none. */
@Composable
private fun DeliveryNoteList(
    state: LandingUiState,
    onToggle: (DeliveryNote) -> Unit,
    onToggleAll: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notes = state.deliveryNoteOptions
    if (notes.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
            when (state.loadStatus) {
                LoadStatus.NOT_QUERIED -> Hint("揀好日期同類別，撳「查詢」")
                LoadStatus.LOADING -> CircularProgressIndicator()
                LoadStatus.LOADED -> Hint("沒有送貨單")
                LoadStatus.FAILED -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("連線失敗", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry, shape = fieldShape) { Text("重試") }
                }
            }
        }
        return
    }

    Column(modifier) {
        TickRow(checked = state.allSelected, onToggle = onToggleAll) {
            Text("全選", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Hint("已選 ${state.toPreview.size}/${notes.size}")
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxWidth()) {
            items(notes, key = { it.number }) { note ->
                TickRow(checked = note.number in state.selected, onToggle = { onToggle(note) }) {
                    DeliveryNoteRow(note, Modifier.weight(1f))
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The whole row ticks and unticks, not just its box. */
@Composable
private fun TickRow(checked: Boolean, onToggle: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        content()
    }
}

/** e.g. `1799**   王小明   牛奶`, with its 訂單號 underneath. */
@Composable
private fun DeliveryNoteRow(note: DeliveryNote, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(note.displayNumber, fontWeight = FontWeight.Bold)
            Text(note.customerName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Hint(note.category.label)
        }
        if (note.orderNumbers.isNotEmpty()) Hint(note.orderNumbers.joinToString(", "))
    }
}
