package com.glykos.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.session.SessionSummary
import kotlinx.datetime.Instant
import java.text.DateFormat
import java.util.Date
import kotlin.time.Duration

private val InRange = Color(0xFF2E7D32)
private val Below = Color(0xFFC62828)
private val Above = Color(0xFFEF6C00)
private val NoData = Color(0xFF9E9E9E)

/**
 * Shown after "Parar": glucose chart of the session and time in range. The readings are only in
 * memory and are forgotten when this screen is closed (privacy promise).
 */
@Composable
fun SessionSummaryScreen(summary: SessionSummary, onClose: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onClose)
    val data = summary.data
    val t = summary.thresholds

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Resumen de la sesión", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${time(data.start)} – ${time(data.end)} (${minutes(data.duration)} min)",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (summary.segments.isEmpty()) {
                    Text("⚠️ No hubo lecturas durante la sesión.", color = MaterialTheme.colorScheme.error)
                } else {
                    GlucoseChart(summary, Modifier.fillMaxWidth().height(200.dp))
                }
                RangeBar(summary)
                Legend("🔴 Bajo (<${t.low})", summary.timeBelow)
                Legend("🟢 En rango (${t.low}–${t.high})", summary.timeInRange)
                Legend("🟠 Alto (>${t.high})", summary.timeAbove)
                if (summary.timeNoData > Duration.ZERO) {
                    Legend("⚪ Sin datos", summary.timeNoData, MaterialTheme.colorScheme.error)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Stat("Inicio", summary.first?.valueMgDl)
            Stat("Fin", summary.last?.valueMgDl)
            Stat("Mín", summary.min)
            Stat("Máx", summary.max)
        }

        Text(
            "🔒 Estos datos no se guardan: se borrarán al cerrar esta pantalla.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
    }
}

@Composable
private fun RangeBar(summary: SessionSummary) {
    val parts = listOf(
        summary.timeBelow to Below,
        summary.timeInRange to InRange,
        summary.timeAbove to Above,
        summary.timeNoData to NoData,
    ).filter { it.first > Duration.ZERO }
    if (parts.isEmpty()) return

    Row(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(6.dp))) {
        parts.forEach { (duration, color) -> BarPart(summary.percent(duration), duration, color) }
    }
}

@Composable
private fun RowScope.BarPart(percent: Int, duration: Duration, color: Color) {
    Box(
        Modifier.weight(duration.inWholeSeconds.toFloat().coerceAtLeast(1f)).fillMaxSize().background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (percent >= 10) Text("$percent %", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Legend(label: String, duration: Duration, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = color)
        Text("${minutes(duration)} min", color = color)
    }
}

@Composable
private fun Stat(label: String, value: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value?.toString() ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** Line per run of readings (gaps stay empty), coloured by range, over the target band. */
@Composable
private fun GlucoseChart(summary: SessionSummary, modifier: Modifier) {
    val t = summary.thresholds
    val start = summary.data.start
    val totalMs = summary.data.duration.inWholeMilliseconds.coerceAtLeast(1)
    val yMin = minOf(40, (summary.min ?: 40) - 10).toFloat()
    val yMax = maxOf(t.high + 40, (summary.max ?: 0) + 10).toFloat()
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)

    Canvas(modifier) {
        val left = 36.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val width = size.width - left
        fun x(at: Instant) = left + ((at - start).inWholeMilliseconds.toFloat() / totalMs).coerceIn(0f, 1f) * width
        fun y(v: Int) = bottom - (v - yMin) / (yMax - yMin) * bottom

        drawRect(InRange.copy(alpha = 0.15f), Offset(left, y(t.high)), androidx.compose.ui.geometry.Size(width, y(t.low) - y(t.high)))
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        drawLine(InRange, Offset(left, y(t.high)), Offset(size.width, y(t.high)), pathEffect = dash)
        drawLine(Below, Offset(left, y(t.low)), Offset(size.width, y(t.low)), pathEffect = dash)
        listOf(t.high, t.low).forEach { v ->
            drawText(measurer, "$v", Offset(0f, y(v) - 8.dp.toPx()), labelStyle)
        }
        drawText(measurer, "0 min", Offset(left, bottom + 2.dp.toPx()), labelStyle)
        val endLabel = measurer.measure("${minutes(summary.data.duration)} min", labelStyle)
        drawText(endLabel, topLeft = Offset(size.width - endLabel.size.width, bottom + 2.dp.toPx()))

        val stroke = 3.dp.toPx()
        summary.segments.forEach { segment ->
            if (segment.size == 1) {
                val r = segment.single()
                drawCircle(colorOf(r, t.low, t.high), stroke, Offset(x(r.timestamp), y(r.valueMgDl)))
            }
            segment.zipWithNext { a, b ->
                drawLine(
                    colorOf(a, t.low, t.high),
                    Offset(x(a.timestamp), y(a.valueMgDl)),
                    Offset(x(b.timestamp), y(b.valueMgDl)),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun colorOf(r: GlucoseReading, low: Int, high: Int) = when {
    r.valueMgDl < low -> Below
    r.valueMgDl > high -> Above
    else -> InRange
}

private fun time(at: Instant): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at.toEpochMilliseconds()))

private fun minutes(d: Duration): Long = (d.inWholeSeconds + 30) / 60
