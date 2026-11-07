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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bytefred.satchess.resources.Res
import org.bytefred.satchess.resources.dejavu_sans_mono
import org.jetbrains.compose.resources.Font

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
    mono: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        label?.let { FieldLabel(it) }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = (if (mono) monoStyle() else MaterialTheme.typography.bodyMedium).copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.onSurface),
            modifier = modifier.height(ButtonDefaults.MinHeight),
            // centre the text vertically instead of fixed padding, so a larger font scale doesn't clip it
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxSize()
                        .border(1.dp, if (isError) colors.error else colors.outline, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
        supporting?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colors.error else colors.onSurfaceVariant,
            )
        }
    }
}

private val LocalMonoStyle = staticCompositionLocalOf { TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp) }

/** The bundled monospace style (DejaVu Sans Mono; FontFamily.Monospace is not monospaced on the web). */
@Composable
fun monoStyle(): TextStyle = LocalMonoStyle.current

/** Bundled font: DejaVu Sans Mono, license in doc/licenses/LICENSE_DEJAVU.txt. */
@Composable
fun ProvideMonoStyle(content: @Composable () -> Unit) {
    val family = FontFamily(Font(Res.font.dejavu_sans_mono))
    val style = remember(family) { TextStyle(fontFamily = family, fontSize = 13.sp) }
    CompositionLocalProvider(LocalMonoStyle provides style, content = content)
}
