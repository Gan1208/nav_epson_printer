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
import com.kowloondairy.navprint.ui.ProvideListText
import com.kowloondairy.navprint.ui.ProvidePageScale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** Every control uses the same rounded rectangle as the date field. */
private val fieldShape = RoundedCornerShape(12.dp)
private val fieldHeight = 56.dp

/** Shortcut chips under the date field, as days after today. */
private val quickDates = listOf(0L to "今日", 1L to "明日")

/** e.g. 2026-10-17（六） */
private fun LocalDate.display(): String =
    "${format(dateFormat)}（${dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.TRADITIONAL_CHINESE)}）"

/** 全部 first, then each [Category]. */
private val categoryChoices: List<Category?> = listOf(null) + Category.entries

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
    onPrint: () -> Unit,
    onSettings: () -> Unit,
) {
    ProvidePageScale {
        LandingPage(
            state, onDeliveryDateChosen, onCategoryChosen, onQuery, onToggle, onToggleAll, onRetry, onPreview, onPrint,
            onSettings,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LandingPage(
    state: LandingUiState,
    onDeliveryDateChosen: (LocalDate) -> Unit,
    onCategoryChosen: (Category?) -> Unit,
    onQuery: () -> Unit,
    onToggle: (DeliveryNote) -> Unit,
    onToggleAll: () -> Unit,
    onRetry: () -> Unit,
    onPreview: () -> Unit,
    onPrint: () -> Unit,
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    enabled = state.canPrint,
                    shape = fieldShape,
                    modifier = Modifier.weight(1f).height(fieldHeight),
                ) { Text("預覽") }
                // Prints straight away, after asking which printer.
                Button(
                    onClick = onPrint,
                    enabled = state.canPrint,
                    shape = fieldShape,
                    modifier = Modifier.weight(1f).height(fieldHeight),
                ) { Text(if (state.canPrint) "打印（${state.ticked.size} 張）" else "打印") }
            }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

    // The shortcut chips sit beside the field, level with its box rather than its floating label.
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = date.display(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("日期") },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "選擇日期") },
            shape = fieldShape,
            interactionSource = interactions,
            modifier = Modifier.weight(1f),
        )
        quickDates.forEach { (daysAhead, label) ->
            val quickDate = today.plusDays(daysAhead)
            FilterChip(
                selected = date == quickDate,
                onClick = { onChosen(quickDate) },
                label = { Text(label) },
                shape = fieldShape,
                modifier = Modifier.height(fieldHeight),
            )
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

/** Starts on the 本裝置預設類別; a change here is for this search only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(category: Category?, onChosen: (Category?) -> Unit) {
    // No 類別 heading: 全部 / 牛奶 / 雪糕 already say what this is.
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
            ) { Text(choice.choiceLabel) }
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
                LoadStatus.NOT_QUERIED -> Hint("揀好日期，撳「查詢」")
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

    ProvideListText {
        Column(modifier) {
            // Its tick is 全選; the 預覽 button already shows how many are picked.
            TickRow(checked = state.allSelected, onToggle = onToggleAll) {
                ThreeColumns("送貨單號", "名字", "訂單號", FontWeight.Bold, Modifier.weight(1f))
                PrintedColumn("打印", FontWeight.Bold)
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxWidth()) {
                items(notes, key = { it.number }) { note ->
                    TickRow(checked = note.number in state.selected, onToggle = { onToggle(note) }) {
                        DeliveryNoteRow(note, Modifier.weight(1f))
                        PrintedColumn(if (note.printed) "✓" else "")
                    }
                    HorizontalDivider()
                }
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

/** e.g. `1799** | 王小明 | SO123 +2`, in three equal columns so they line up down the list. */
@Composable
private fun DeliveryNoteRow(note: DeliveryNote, modifier: Modifier = Modifier) {
    val orders = note.orderNumbers
    val orderText = when (orders.size) {
        0 -> ""
        1 -> orders.first()
        else -> "${orders.first()} +${orders.size - 1}"
    }
    ThreeColumns(note.displayNumber, note.customerName, orderText, modifier = modifier)
}

/** The 打印 column: its heading, or ✓ for a 已打印 送貨單. Fixed width, so it lines up down the list. */
@Composable
private fun PrintedColumn(text: String, fontWeight: FontWeight? = null) {
    Text(
        text,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(48.dp),
    )
}

/** Equal, centred columns, shared by the heading and every row so they line up. */
@Composable
private fun ThreeColumns(
    first: String,
    second: String,
    third: String,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(first, second, third).forEach {
            Text(
                it,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
