package org.bytefred.satchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.bytefred.satchess.ui.model.ConstraintLevel

/*
 * Small icons drawn on a Canvas. The web build has no icon font and its default font lacks most
 * symbol glyphs, so icons are geometry instead of characters.
 */

/** Timeline icon: filled square = fixed board, half filled = partial ('?' squares), outline = open. */
@Composable
fun ConstraintGlyph(level: ConstraintLevel, color: Color, size: Dp = 12.dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.width * 0.14f
        val inset = stroke / 2
        val box = Size(this.size.width - stroke, this.size.height - stroke)
        when (level) {
            ConstraintLevel.FIXED -> drawRect(color, Offset(inset, inset), box)
            ConstraintLevel.PARTIAL -> {
                val path = Path().apply {
                    moveTo(inset, this@Canvas.size.height - inset)
                    lineTo(this@Canvas.size.width - inset, inset)
                    lineTo(this@Canvas.size.width - inset, this@Canvas.size.height - inset)
                    close()
                }
                drawPath(path, color)
            }
            ConstraintLevel.OPEN -> Unit
        }
        drawRect(color, Offset(inset, inset), box, style = Stroke(stroke))
    }
}

/** A chevron pointing down (expanded) or right (collapsed). */
@Composable
fun ChevronGlyph(expanded: Boolean, color: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(w * 0.14f, cap = StrokeCap.Round)
        val path = Path().apply {
            if (expanded) {
                moveTo(w * 0.2f, w * 0.38f); lineTo(w * 0.5f, w * 0.68f); lineTo(w * 0.8f, w * 0.38f)
            } else {
                moveTo(w * 0.38f, w * 0.2f); lineTo(w * 0.68f, w * 0.5f); lineTo(w * 0.38f, w * 0.8f)
            }
        }
        drawPath(path, color, style = stroke)
    }
}

/** A small "x" (remove). */
@Composable
fun CloseGlyph(color: Color, size: Dp = 12.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.14f
        drawLine(color, Offset(w * 0.2f, w * 0.2f), Offset(w * 0.8f, w * 0.8f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.8f, w * 0.2f), Offset(w * 0.2f, w * 0.8f), stroke, StrokeCap.Round)
    }
}

/** Three vertical dots (overflow menu). */
@Composable
fun MoreGlyph(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        for (i in 0..2) drawCircle(color, w * 0.09f, Offset(w / 2, w * (0.2f + 0.3f * i)))
    }
}

/** A tick (verified badge). */
@Composable
fun CheckGlyph(color: Color, size: Dp = 12.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val path = Path().apply { moveTo(w * 0.15f, w * 0.55f); lineTo(w * 0.42f, w * 0.8f); lineTo(w * 0.85f, w * 0.25f) }
        drawPath(path, color, style = Stroke(w * 0.16f, cap = StrokeCap.Round))
    }
}
