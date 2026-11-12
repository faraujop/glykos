package com.glykos.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.glykos.app.ui.theme.GlykosTheme
import com.glykos.core.data.settings.OutputMode
import com.glykos.core.domain.announce.AnnouncementSettings
import com.glykos.core.domain.announce.Announcement
import com.glykos.core.domain.announce.Announcement.Kind
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.Trend
import com.glykos.feature.monitor.ServiceStatus
import com.glykos.feature.output.GlucoseOutput
import com.glykos.feature.output.NotificationWatchOutput
import com.glykos.feature.output.VoiceOutput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.time.Duration
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
                    HomeScreen(voice, watch, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    voice: GlucoseOutput,
    watch: GlucoseOutput,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = hiltViewModel(),
) {
    val status by vm.status.collectAsState()
    val email by vm.email.collectAsState()
    val mode by vm.outputMode.collectAsState()
    val announce by vm.announcementSettings.collectAsState()
    val summary by vm.sessionSummary.collectAsState()

    summary?.let {
        SessionSummaryScreen(it, onClose = vm::dismissSession, modifier = modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Glykos", style = MaterialTheme.typography.headlineMedium)
        MonitorCard(status, hasCredentials = email != null, onStart = vm::start, onStop = vm::stop)
        AccountCard(email, onSave = vm::saveCredentials, onClear = vm::clearCredentials)
        OutputCard(mode, running = status.running, onSelect = vm::setOutputMode)
        announce?.let { AnnouncementCard(it, running = status.running, onSave = vm::saveAnnouncementSettings) }
        BatteryCard()
        TestCard(voice, watch)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun MonitorCard(status: ServiceStatus, hasCredentials: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.missingPermissions().isEmpty()) {
            permissionMessage = null
            onStart()
        } else {
            permissionMessage = "⚠️ Sin los permisos de notificaciones y actividad física no puedo vigilar en segundo plano."
        }
    }

    Section("Monitorización") {
        val m = status.monitor
        Text(if (status.running) "🟢 Activa" else "⚪ Parada")
        when (val r = m.lastResult) {
            is GlucoseResult.Available -> Text("Última lectura: ${r.reading.valueMgDl} mg/dl")
            is GlucoseResult.Unavailable -> Text("⚠️ Sin datos: ${r.reason.name.lowercase()}")
            null -> if (status.running) Text("Esperando la primera lectura…")
        }
        m.outputErrors.forEach { Text("⚠️ ${it.sink}: ${it.message}", color = MaterialTheme.colorScheme.error) }
        (status.error ?: permissionMessage)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!hasCredentials) Text("Configura la cuenta de LibreLinkUp para empezar.")

        if (status.running) {
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Parar") }
        } else {
            Button(
                onClick = {
                    val missing = context.missingPermissions()
                    if (missing.isEmpty()) onStart() else permissions.launch(missing.toTypedArray())
                },
                enabled = hasCredentials,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Empezar") }
        }
    }
}

private fun Context.missingPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
}.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

@Composable
private fun AccountCard(email: String?, onSave: (String, String) -> Unit, onClear: () -> Unit) {
    var editing by remember(email) { mutableStateOf(email == null) }
    var newEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Section("Cuenta LibreLinkUp (seguidora)") {
        if (!editing && email != null) {
            Text(email)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = true }) { Text("Cambiar") }
                TextButton(onClick = onClear) { Text("Borrar") }
            }
        } else {
            Text(
                "Usa la cuenta seguidora invitada desde LibreLinkUp, nunca la principal. " +
                    "Se guarda cifrada solo en este móvil.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = newEmail,
                onValueChange = { newEmail = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSave(newEmail, password)
                    password = ""
                    editing = false
                },
                enabled = newEmail.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
        }
    }
}

@Composable
private fun OutputCard(mode: OutputMode, running: Boolean, onSelect: (OutputMode) -> Unit) {
    Section("Salida") {
        listOf(OutputMode.VOICE to "Voz (cascos)", OutputMode.WATCH to "Reloj", OutputMode.BOTH to "Ambos").forEach { (m, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = mode == m, onClick = { onSelect(m) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == m, onClick = { onSelect(m) })
                Text(label)
            }
        }
        if (running) Text("Se aplica al momento.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AnnouncementCard(saved: AnnouncementSettings, running: Boolean, onSave: (AnnouncementSettings) -> Unit) {
    var draft by remember(saved) { mutableStateOf(saved) }
    val r = AnnouncementSettings

    Section("Avisos") {
        Stepper("Avisar cada", draft.intervalMinutes, r.INTERVAL_RANGE, step = 1, unit = "min") {
            draft = draft.copy(intervalMinutes = it)
        }
        Text(
            "Las alarmas (baja, muy baja, alta, sin datos) avisan al momento y se repiten cada 5 min.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hablar solo si cambia")
                Text("Silencia el aviso rutinario si la glucosa apenas cambia", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = draft.minChangeMgDl != null,
                onCheckedChange = { on -> draft = draft.copy(minChangeMgDl = if (on) 10 else null) },
            )
        }
        draft.minChangeMgDl?.let { value ->
            Stepper("Cambio mínimo", value, r.MIN_CHANGE_RANGE, step = 1, unit = "mg/dl") {
                draft = draft.copy(minChangeMgDl = it)
            }
        }

        HorizontalDivider()
        Text("Umbrales de alarma", style = MaterialTheme.typography.titleSmall)
        Stepper("Muy baja por debajo de", draft.veryLow, r.VERY_LOW_RANGE, step = 1, unit = "mg/dl") {
            draft = draft.copy(veryLow = it)
        }
        Stepper("Baja por debajo de", draft.low, r.LOW_RANGE, step = 5, unit = "mg/dl") { draft = draft.copy(low = it) }
        Stepper("Alta por encima de", draft.high, r.HIGH_RANGE, step = 10, unit = "mg/dl") { draft = draft.copy(high = it) }
        Text(
            "Por defecto: consenso internacional de tiempo en rango (54 / 70 / 180). " +
                "Ajusta los tuyos con tu equipo médico.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(draft) }, enabled = draft != saved) { Text("Guardar") }
            TextButton(onClick = { draft = AnnouncementSettings() }, enabled = draft != AnnouncementSettings()) {
                Text("Valores por defecto")
            }
        }
        if (running && draft != saved) {
            Text("Al guardar se reinicia la vigilancia y se anuncia la lectura actual.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int, unit: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        Text("$value $unit", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
    }
}

@SuppressLint("BatteryLife") // Personal, non-Play app: direct exemption request is intended.
@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    fun ignoring() = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    var exempt by remember { mutableStateOf(ignoring()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { exempt = ignoring() }

    Section("Batería") {
        Text(if (exempt) "✓ Sin optimización de batería" else "⚠️ Android puede pausar Glykos con la pantalla apagada")
        if (!exempt) {
            Button(
                onClick = {
                    launcher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Quitar optimización") }
        }
        if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
            Text(
                "Xiaomi: en Información de la app → Ahorro de batería elige «Sin restricciones» y activa «Inicio automático».",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = {
                    launcher.launch(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Abrir ajustes de la app") }
        }
    }
}

/** Phase 0: try outputs with sample announcements, without the service. */
@Composable
private fun TestCard(voice: GlucoseOutput, watch: GlucoseOutput) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    fun send(label: String, sample: Announcement, vararg outputs: GlucoseOutput) {
        scope.launch {
            result = "Enviando: $label…"
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
            result = if (errors.isEmpty()) "✓ $label" else "⚠️ " + errors.joinToString("\n")
        }
    }

    Section("Pruebas") {
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar" else "Probar voz y reloj") }
        if (expanded) {
            result?.let { Text(it) }
            Samples.all().forEach { (label, sample) ->
                HorizontalDivider()
                Text(label, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { send(label, sample(), voice) }) { Text("Voz") }
                    OutlinedButton(onClick = { send(label, sample(), watch) }) { Text("Reloj") }
                    OutlinedButton(onClick = { send(label, sample(), voice, watch) }) { Text("Ambos") }
                }
            }
        }
    }
}

private object Samples {
    private fun reading(value: Int, trend: Trend, ago: Duration, kind: Kind, delta: Int?): Announcement {
        val now = Clock.System.now()
        return Announcement(kind, GlucoseResult.Available(GlucoseReading(value, trend, now - ago)), delta, now)
    }

    fun all(): List<Pair<String, () -> Announcement>> = listOf(
        "Rutina: 112 ↘ −8" to { reading(112, Trend.FALLING, 70.seconds, Kind.ROUTINE, -8) },
        "Alarma: baja 65" to { reading(65, Trend.FALLING, 10.seconds, Kind.LOW, -10) },
        "Alarma: muy baja 50" to { reading(50, Trend.FALLING_FAST, 10.seconds, Kind.VERY_LOW, -12) },
        "Sin datos (obsoleto)" to { reading(112, Trend.STABLE, 12.minutes, Kind.NO_DATA, null) },
        "Sin datos (sin conexión)" to {
            Announcement(Kind.NO_DATA, GlucoseResult.Unavailable(GlucoseResult.Reason.NETWORK), null, Clock.System.now())
        },
    )
}
