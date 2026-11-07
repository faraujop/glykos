package com.glykos.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glykos.app.ui.theme.GlykosTheme
import com.glykos.core.domain.announce.Announcement
import com.glykos.core.domain.announce.Announcement.Kind
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.Trend
import com.glykos.feature.output.GlucoseOutput
import com.glykos.feature.output.NotificationWatchOutput
import com.glykos.feature.output.VoiceOutput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var voice: VoiceOutput
    @Inject lateinit var watch: NotificationWatchOutput

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlykosTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    OutputTestScreen(voice, watch, Modifier.padding(padding))
                }
            }
        }
    }
}

/** Phase 0 spike screen: try voice and watch outputs with sample announcements. */
@Composable
private fun OutputTestScreen(voice: GlucoseOutput, watch: GlucoseOutput, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Listo") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Notificaciones permitidas" else "⚠️ Notificaciones denegadas: el reloj no recibirá nada"
    }

    fun send(label: String, sample: Announcement, vararg outputs: GlucoseOutput) {
        scope.launch {
            status = "Enviando: $label…"
            val errors = outputs.mapNotNull { output ->
                try {
                    output.deliver(sample)
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "${output::class.simpleName}: ${e.message}"
                }
            }
            status = if (errors.isEmpty()) "✓ $label" else "⚠️ " + errors.joinToString("\n")
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Glykos · prueba de salidas", style = MaterialTheme.typography.headlineSmall)
        Text(status, style = MaterialTheme.typography.bodyMedium)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(
                onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Permitir notificaciones") }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Samples.all().forEach { (label, sample) ->
            Text(label, style = MaterialTheme.typography.titleSmall)
            Button(onClick = { send(label, sample(), voice) }, modifier = Modifier.fillMaxWidth()) { Text("Voz") }
            Button(onClick = { send(label, sample(), watch) }, modifier = Modifier.fillMaxWidth()) { Text("Reloj") }
            OutlinedButton(onClick = { send(label, sample(), voice, watch) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ambos")
            }
        }
    }
}

private object Samples {
    private fun reading(value: Int, trend: Trend, ago: kotlin.time.Duration, kind: Kind, delta: Int?): Announcement {
        val now = Clock.System.now()
        return Announcement(kind, GlucoseResult.Available(GlucoseReading(value, trend, now - ago)), delta, now)
    }

    fun all(): List<Pair<String, () -> Announcement>> = listOf(
        "Rutina: 112 ↘ −8" to { reading(112, Trend.FALLING, 70.seconds, Kind.ROUTINE, -8) },
        "Alarma: baja 65" to { reading(65, Trend.FALLING, 10.seconds, Kind.LOW, -10) },
        "Alarma: muy baja 50" to { reading(50, Trend.FALLING_FAST, 10.seconds, Kind.VERY_LOW, -12) },
        "Sin datos (obsoleto)" to { reading(112, Trend.STABLE, 12.minutes, Kind.NO_DATA, null) },
        "Sin datos (sin conexión)" to {
            Announcement(
                Kind.NO_DATA,
                GlucoseResult.Unavailable(GlucoseResult.Reason.NETWORK),
                null,
                Clock.System.now(),
            )
        },
    )
}
