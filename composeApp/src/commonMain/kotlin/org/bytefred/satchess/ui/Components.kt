package org.bytefred.satchess.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** A titled card; with [expanded] / [onToggle] it collapses to its header. */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        val header = if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier
        Row(
            header.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onToggle != null) {
                ChevronGlyph(expanded, MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            trailing()
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** A button showing the current choice; clicking opens the [options] menu. */
@Composable
fun <T> ChoiceDropdown(
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }) {
            Text(label(value))
            Spacer(Modifier.width(6.dp))
            ChevronGlyph(expanded = true, color = MaterialTheme.colorScheme.primary, size = 12.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** A small label + a row of single-choice filter chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    title: String?,
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        title?.let { FieldLabel(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (option in options) {
                FilterChip(selected = option == value, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A text input as low as the buttons (Material's OutlinedTextField is 56 dp, far taller than the text
 * in it): an optional [label] above, the optional [supporting] line below.
 */
@Composable
fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    isError: Boolean = false,
    supporting: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        label?.let { FieldLabel(it) }
        CompactInput(value, onValueChange, modifier, isError)
        supporting?.let { Supporting(it, isError) }
    }
}

/**
 * A whole-number input like [CompactField], as wide as the longest allowed value (the digits of
 * [range]'s maximum) instead of a fixed width, with "−"/"+" buttons that step by 1 within [range]
 * (ktoy's steps/sec field). Typing works as before: [onValueChange] gets the text, the caller
 * validates. Test tags: [tag] on the field, "<tag>-minus" / "<tag>-plus" on the buttons.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    range: IntRange,
    tag: String,
    label: String? = null,
    isError: Boolean = false,
    supporting: String? = null,
) {
    val current = value.trim().toIntOrNull()
    fun step(by: Int) {
        current?.let { onValueChange((it + by).coerceIn(range).toString()) }
    }
    val style = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val digits = range.last.toString().length
    val textPx = remember(digits, style) { measurer.measure("0".repeat(digits), style).size.width }
    // + 2 dp for the cursor after the last digit
    val width = with(LocalDensity.current) { textPx.toDp() } + INPUT_PADDING * 2 + 2.dp
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        label?.let { FieldLabel(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { step(-1) },
                enabled = current != null && current > range.first,
                modifier = Modifier.testTag("$tag-minus"),
            ) { Text("−") }
            CompactInput(value, onValueChange, Modifier.width(width).testTag(tag), isError, KeyboardType.Number)
            OutlinedButton(
                onClick = { step(1) },
                enabled = current != null && current < range.last,
                modifier = Modifier.testTag("$tag-plus"),
            ) { Text("+") }
        }
        supporting?.let { Supporting(it, isError) }
    }
}

private val INPUT_PADDING = 8.dp

/** The low bordered text field of [CompactField] and [NumberField]; red border when [isError]. */
@Composable
private fun CompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    isError: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.onSurface),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.height(ButtonDefaults.MinHeight),
        // centre the text vertically instead of fixed padding, so a larger font scale doesn't clip it
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize()
                    .border(1.dp, if (isError) colors.error else colors.outline, RoundedCornerShape(4.dp))
                    .padding(horizontal = INPUT_PADDING),
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        },
    )
}

@Composable
private fun Supporting(text: String, isError: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
