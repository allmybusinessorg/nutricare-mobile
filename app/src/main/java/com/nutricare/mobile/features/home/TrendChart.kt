package com.nutricare.mobile.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Slate

/**
 * Lightweight biomarker trend chart (no chart library): a smooth polyline
 * with dots, min/max labels and date ticks, drawn on a Canvas.
 */
@Composable
fun TrendChart(
    points: List<Pair<String, Double>>, // (date label, value)
    modifier: Modifier = Modifier,
    lineColor: Color = EmeraldDark,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Slate)

    if (points.size < 2) {
        Text(
            "Not enough data yet — upload at least two reports to see trends.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        return
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val w = size.width
            val h = size.height
            val padLeft = 8.dp.toPx()
            val padRight = 8.dp.toPx()
            val padTop = 18.dp.toPx()
            val padBottom = 26.dp.toPx()

            val values = points.map { it.second }
            val minV = values.min()
            val maxV = values.max()
            val range = (maxV - minV).takeIf { it > 0 } ?: 1.0

            fun x(i: Int) = padLeft + (w - padLeft - padRight) * i / (points.size - 1)
            fun y(v: Double) = padTop + (h - padTop - padBottom) * (1.0 - (v - minV) / range).toFloat()

            // baseline grid (min & max)
            val gridEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            for (v in listOf(minV, maxV)) {
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(padLeft, y(v)),
                    end = Offset(w - padRight, y(v)),
                    pathEffect = gridEffect,
                    strokeWidth = 2f,
                )
                drawText(
                    textMeasurer,
                    text = if (v == maxV) "max ${format(v)}" else "min ${format(v)}",
                    style = labelStyle,
                    topLeft = Offset(padLeft, (y(v) - 34f).coerceAtLeast(0f)),
                )
            }

            // line
            val path = Path()
            points.forEachIndexed { i, (_, v) ->
                if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
            }
            drawPath(path, color = lineColor, style = Stroke(width = 5f))

            // dots
            points.forEachIndexed { i, (_, v) ->
                drawCircle(color = lineColor, radius = 7f, center = Offset(x(i), y(v)))
                drawCircle(color = Color.White, radius = 3.5f, center = Offset(x(i), y(v)))
            }

            // first/last date labels
            drawText(textMeasurer, text = points.first().first, style = labelStyle, topLeft = Offset(padLeft, h - 22.dp.toPx()))
            val lastLabel = points.last().first
            val measured = textMeasurer.measure(lastLabel, labelStyle)
            drawText(textMeasurer, text = lastLabel, style = labelStyle, topLeft = Offset(w - padRight - measured.size.width, h - 22.dp.toPx()))
        }
    }
}

private fun format(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
