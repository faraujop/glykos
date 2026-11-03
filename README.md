# Glykos

App Android personal que comunica la glucosa de un sensor CGM **mientras corres**, sin mirar ni
desbloquear el móvil:

- **Voz** en los cascos (TTS, bajando el volumen de la música mientras habla).
- **Muñeca**: notificaciones reenviadas al reloj Garmin a través de Garmin Connect.
- **Ambas**, según la configuración.

Ejemplos: voz "Ciento doce, bajando, menos ocho"; notificación `🩸 112 ↘ −8 (hace 1 min)`.

> ⚠️ **Aviso**: proyecto personal de uso propio. **No es un producto sanitario** y no sustituye a los
> sistemas de alarma oficiales del sensor ni al criterio médico. No recomienda tratamientos ni dosis.

## Nombres

| Nombre | Qué es |
| --- | --- |
| **Glykos** | La plataforma (app, web, futura suscripción). Del griego *glykós*, "dulce". |
| **Zonnia** | El asistente de voz y texto de Glykos (fase 3+). |
| **Glykos Run** | Modo para actividad física (el MVP actual). |

## Cómo funciona

```
Sensor CGM ──BLE──► App oficial del sensor ──► Nube LibreLinkUp
                                                   │ (polling, cuenta seguidora)
                                                   ▼
                               App Glykos (foreground service)
                                ├─ VoiceOutput             → TTS por cascos
                                ├─ NotificationWatchOutput → notificación → Garmin
                                ├─ AlarmEngine             → umbrales, tendencia, dato obsoleto
                                └─ Sync (fase 2)           → Backend .NET 8 → Panel Angular
```

- La fuente de datos es intercambiable (`GlucoseSource`). La primera implementación usa la API
  **no oficial** de LibreLinkUp con una **cuenta seguidora**, nunca la cuenta principal.
- Las salidas son intercambiables (`GlucoseOutput`).
- Los fallos son ruidosos: si no hay datos, la app lo dice en lugar de callar.

## Módulos

| Módulo | Contenido |
| --- | --- |
| `:app` | UI de configuración (Jetpack Compose), arranque y parada del servicio. |
| `:core:domain` | Modelos y reglas (`GlucoseReading`, `Trend`, `GlucoseResult`). Kotlin puro, sin Android, compatible con KMP. |
| `:core:data` | `GlucoseSource` y sus implementaciones; almacenamiento cifrado de credenciales. |
| `:feature:monitor` | Foreground service y planificación de lecturas. |
| `:feature:output` | `GlucoseOutput`: voz (TTS) y notificación al reloj. |
| `:feature:assistant` | Zonnia (fase 3+). |

## Stack

Kotlin, coroutines + Flow, Jetpack Compose, Hilt, Ktor client, kotlinx.serialization, kotlinx-datetime,
DataStore. Tests con JUnit 5, MockK y Turbine. Dependencias en `gradle/libs.versions.toml`.

## Requisitos

- JDK 17
- Android SDK (compileSdk 35, minSdk 26)
- Android Studio (recomendado)

## Puesta en marcha

1. Clonar el repositorio y abrirlo en Android Studio.
2. Si no existe el wrapper de Gradle (`gradlew`), generarlo:
   ```bash
   gradle wrapper --gradle-version 8.11.1
   ```
3. Compilar y probar:
   ```bash
   ./gradlew assembleDebug   # compilar
   ./gradlew test            # tests unitarios
   ./gradlew lint            # lint
   ./gradlew installDebug    # instalar en dispositivo conectado
   ```

Credenciales y datos de prueba van en `local.properties` o variables de entorno; **nunca** en el
código ni en el repositorio.

## Hoja de ruta

- **Fase 0 (spike)**: login LibreLinkUp, lectura, TTS con pantalla bloqueada y notificación al reloj.
- **Fase 1 (MVP)**: configuración completa, alarmas (bajo, muy bajo, alto, dato obsoleto), robustez.
- **Fase 2**: histórico local (Room), sync a backend .NET 8 en AWS, resumen de glucosa en Strava.
- **Fase 3**: panel Angular (tiempo en rango, comparativa por actividad, informe PDF) y Zonnia v1
  (push-to-talk con los cascos).
- **Fase 3.5+**: Zonnia v2 (preguntas sobre el histórico), predicción según esfuerzo.

## Limitaciones conocidas

- La API de LibreLinkUp **no es oficial** y puede cambiar sin aviso.
- El Garmin Forerunner 35 no admite Connect IQ: la única vía a la muñeca son las notificaciones.
- Algunos fabricantes de Android matan servicios en segundo plano aunque se excluya la app de la
  optimización de batería.

## Marcas

FreeStyle Libre y LibreLinkUp son marcas de Abbott. Este proyecto no está afiliado, respaldado ni
patrocinado por Abbott ni por Garmin.
