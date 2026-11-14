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
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.glykos.app.ui.theme.Brand
import com.glykos.app.ui.theme.ReadingStyle
import com.glykos.core.domain.announce.AnnouncementConfig
import com.glykos.core.domain.format.NotificationMessageFormatter
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
        // Both screens open with the night header, so status bar icons are always light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            GlykosTheme {
                Scaffold(modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0)) { padding ->
                    Box(Modifier.fillMaxSize()) {
                        HomeScreen(voice, watch, Modifier.padding(padding))
                        // Edge-to-edge (mandatory from targetSdk 35) leaves the navigation bar transparent:
                        // without this strip, the 3-button bar is drawn over scrolling text and buttons.
                        Spacer(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                .background(MaterialTheme.colorScheme.background),
                        )
                    }
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

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        MonitorHero(status, hasCredentials = email != null, onStart = vm::start, onStop = vm::stop)
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (email == null) AccountCard(email, onSave = vm::saveCredentials, onClear = vm::clearCredentials)
            OutputCard(mode, running = status.running, onSelect = vm::setOutputMode)
            announce?.let { AnnouncementCard(it, running = status.running, onSave = vm::saveAnnouncementSettings) }
            if (email != null) AccountCard(email, onSave = vm::saveCredentials, onClear = vm::clearCredentials)
            BatteryCard()
            TestCard(voice, watch)
        }
    }
}

/** Quiet settings sheet: flat surface, no shadow, title in the brand indigo. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/**
 * The night header: logo + wordmark, status and the latest reading in the logo's ink.
 * Problems (no data, stale reading, output errors) are shown here, loudly, never hidden.
 */
@Composable
private fun MonitorHero(status: ServiceStatus, hasCredentials: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.missingPermissions().isEmpty()) {
            permissionMessage = null
            onStart()
        } else {
            permissionMessage = "Sin los permisos de notificaciones y actividad física no puedo vigilar en segundo plano."
        }
    }
    val m = status.monitor

    NightHeader {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_glykos_logo), contentDescription = null, Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Text("Glykos", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(status.running)
            m.lastCheckAt?.takeIf { status.running }?.let {
                Spacer(Modifier.width(8.dp))
                Text("comprobado ${shortTime(it)}", style = MaterialTheme.typography.bodySmall, color = OnNightMuted)
            }
        }

        Spacer(Modifier.height(6.dp))
        when (val r = m.lastResult) {
            is GlucoseResult.Available -> ReadingRow(r.reading, m.lastAnnouncement)
            is GlucoseResult.Unavailable -> HeroWarning("Sin datos: ${r.reason.name.lowercase()}")
            null -> Text(
                when {
                    status.running -> "Esperando la primera lectura…"
                    hasCredentials -> "Pulsa Empezar antes de iniciar la actividad."
                    else -> "Configura la cuenta de LibreLinkUp para empezar."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = OnNightMuted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        m.outputErrors.forEach { HeroWarning("${it.sink}: ${it.message}") }
        (status.error ?: permissionMessage)?.let { HeroWarning(it) }

        Spacer(Modifier.height(16.dp))
        if (status.running) {
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.08f), contentColor = Color.White),
            ) { Text("Parar") }
        } else {
            InkButton(
                text = "Empezar",
                enabled = hasCredentials,
                onClick = {
                    val missing = context.missingPermissions()
                    if (missing.isEmpty()) onStart() else permissions.launch(missing.toTypedArray())
                },
            )
        }
    }
}

@Composable
private fun ReadingRow(reading: GlucoseReading, last: Announcement?) {
    val age = Clock.System.now() - reading.timestamp
    val stale = reading.isStale(Clock.System.now(), AnnouncementConfig().staleAfter)
    val delta = last?.takeIf { (it.result as? GlucoseResult.Available)?.reading == reading }?.deltaMgDl
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            reading.valueMgDl.toString(),
            style = if (stale) ReadingStyle.merge(TextStyle(color = OnNightMuted)) else ReadingStyle,
        )
        Spacer(Modifier.width(8.dp))
        Text("mg/dl", style = MaterialTheme.typography.bodyLarge, color = OnNightMuted, modifier = Modifier.padding(bottom = 10.dp))
        Spacer(Modifier.weight(1f))
        val trend = listOfNotNull(
            NotificationMessageFormatter.arrow(reading.trend),
            delta?.let { if (it > 0) "+$it" else if (it < 0) "−${-it}" else "±0" },
        ).joinToString(" ")
        Text(trend, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
    }
    if (stale) HeroWarning("Dato de hace ${age.inWholeMinutes} min: sin lecturas recientes")
}

@Composable
private fun StatusChip(running: Boolean) {
    val dot = if (running) Color(0xFF4CD38A) else Color.White.copy(alpha = 0.5f)
    val bg = if (running) Color(0x382E9E5B) else Color.White.copy(alpha = 0.1f)
    val fg = if (running) Color(0xFF8FE3B0) else OnNightMuted
    val label = if (running) "Vigilando" else "Parada"
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun HeroWarning(text: String) {
    Text(
        "⚠️ $text",
        style = MaterialTheme.typography.bodyMedium,
        color = Brand.Cream,
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(Brand.Coral.copy(alpha = 0.22f)).padding(10.dp),
    )
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
    val options = listOf(OutputMode.VOICE to "Voz", OutputMode.WATCH to "Reloj", OutputMode.BOTH to "Ambos")
    Section("Salida") {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEach { (m, label) ->
                val selected = mode == m
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(m) })
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text("Voz por los cascos, reloj con una notificación nueva en cada aviso.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (running) Text("Se aplica al momento.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AnnouncementCard(saved: AnnouncementSettings, running: Boolean, onSave: (AnnouncementSettings) -> Unit) {
    var draft by remember(saved) { mutableStateOf(saved) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val r = AnnouncementSettings

    Section("Avisos") {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryLine("Avisar cada", "${saved.intervalMinutes} min")
            SummaryLine("Hablar solo si cambia", saved.minChangeMgDl?.let { "≥ $it mg/dl" } ?: "No")
            SummaryLine("Rango", "${saved.low} – ${saved.high} mg/dl")
            SummaryLine("Muy baja por debajo de", "${saved.veryLow} mg/dl")
            Text(
                if (expanded) "Ocultar" else "Cambiar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!expanded && draft == saved) {
            Text(
                "Las alarmas (baja, muy baja, alta, sin datos) avisan al momento y se repiten cada 5 min.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Section
        }

        HorizontalDivider()
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
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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

private val OnNightMuted = Color.White.copy(alpha = 0.68f)

/** Indigo night block under the status bar, with the coral glow of the logo. Shared with the summary. */
@Composable
fun NightHeader(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brand.NightSky)
            .background(Brush.radialGradient(listOf(Brand.Coral.copy(alpha = 0.26f), Color.Transparent), radius = 900f, center = Offset(800f, 900f)))
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp),
        content = content,
    )
}

/** Main action, painted with the logo's ink. */
@Composable
fun InkButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Brand.Ink else SolidColor(Color.White.copy(alpha = 0.12f)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Brand.Indigo else OnNightMuted)
    }
}

private fun shortTime(at: kotlinx.datetime.Instant): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at.toEpochMilliseconds()))
