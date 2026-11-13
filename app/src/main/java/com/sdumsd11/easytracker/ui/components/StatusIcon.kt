package com.sdumsd11.easytracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.ui.theme.Danger500
import com.sdumsd11.easytracker.ui.theme.Success500

/** Filled status disc with a drawn check (success) or cross (failure). No icons dep needed. */
@Composable
fun StatusIcon(success: Boolean, modifier: Modifier = Modifier) {
    val fill = if (success) Success500 else Danger500
    Canvas(
        modifier = modifier.size(72.dp).semantics {
            contentDescription = if (success) "Payment successful" else "Payment failed"
        },
    ) {
        drawCircle(color = fill)
        val w = size.width
        val h = size.height
        val mark = Path().apply {
            if (success) {
                moveTo(w * 0.28f, h * 0.53f)
                lineTo(w * 0.45f, h * 0.68f)
                lineTo(w * 0.73f, h * 0.33f)
            } else {
                moveTo(w * 0.35f, h * 0.35f)
                lineTo(w * 0.65f, h * 0.65f)
                moveTo(w * 0.65f, h * 0.35f)
                lineTo(w * 0.35f, h * 0.65f)
            }
        }
        drawPath(
            path = mark,
            color = Color.White,
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
