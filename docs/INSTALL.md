# Instalar Glykos (guía para no desarrolladores)

Glykos te dice tu glucosa **por voz en los cascos** y **en tu reloj Garmin** sin sacar el móvil:
corriendo, en la bici, en el gimnasio, paseando o conduciendo.

> ⚠️ **Antes de empezar**
> - Glykos es un **proyecto personal y gratuito, no un producto sanitario**. Informa, pero **no
>   sustituye** las alarmas de la app oficial de tu sensor ni el criterio de tu equipo médico.
>   **Mantén siempre activas las alarmas de la app oficial.** Úsalo bajo tu responsabilidad.
> - Solo funciona en **Android 8 o superior**. No hay versión para iPhone.
> - Glykos lee tus datos de la nube de **LibreLinkUp** (el servicio para compartir datos de los
>   sensores FreeStyle Libre). Usa un acceso **no oficial** que podría dejar de funcionar sin aviso.

Tardarás unos **15 minutos**. Necesitas:

- ✅ Un móvil **Android** (8.0 o superior).
- ✅ Tu sensor funcionando con su **app oficial** y con conexión a internet.
- ✅ Un **email** distinto al de tu cuenta del sensor (ver paso 1).
- ✅ Opcional: un reloj **Garmin** emparejado con la app Garmin Connect.

---

## Paso 1 · Crear tu cuenta "seguidora"

Glykos no usa tu cuenta principal. Usa una **cuenta seguidora**: la misma que usaría un familiar al
que invitas a ver tu glucosa. Así, si algo falla, tu cuenta principal no se ve afectada.

**1.1 Elige un email para la cuenta seguidora.** Tiene que ser distinto al de tu cuenta principal.
Si usas Gmail, puedes añadir `+glykos` antes de la arroba: si tu correo es `ana@gmail.com`, usa
`ana+glykos@gmail.com`. Los correos te llegarán a la misma bandeja.

**1.2 Invita a ese email desde la app oficial de tu sensor** (la que lee el sensor):

1. Abre el **menú** (☰).
2. Entra en **Aplicaciones conectadas**.
3. Pulsa **LibreLinkUp** → **Conectar** / **Añadir conexión**.
4. Escribe un nombre (por ejemplo "Glykos") y el email del paso 1.1.
5. Envía la invitación.

*(Los nombres de los menús pueden variar un poco según la versión.)*

**1.3 Crea la cuenta seguidora:**

1. Instala la app **LibreLinkUp** desde Google Play.
2. Pulsa **Registrarse** con el email del paso 1.1 y una contraseña segura. **Apúntala**: la
   necesitarás en el paso 3.
3. Confirma tu email con el correo que recibirás.
4. Acepta los **términos de uso** y la **política de privacidad**.
5. Acepta la **invitación**.

**1.4 Compruébalo:** en LibreLinkUp tienes que ver tu glucosa actual. Si la ves, ¡listo! Ya puedes
desinstalar LibreLinkUp si quieres (la cuenta sigue existiendo).

---

## Paso 2 · Instalar Glykos

Glykos no está en Google Play: se instala con un archivo **APK** (el instalador de una app Android)
que te pasa el desarrollador. Android avisa de que viene "de fuera de la tienda"; es normal.

**2.1 Descarga el APK.** Abre **en el móvil** el enlace que te han mandado y descarga el archivo
`glykos-….apk`. Si el navegador avisa de que *"este tipo de archivo puede ser dañino"*, pulsa
**Descargar de todas formas**.

> 🔒 Instala Glykos **solo desde el enlace que te ha dado el desarrollador**. Un APK de otra fuente
> podría estar modificado.

**2.2 Ábrelo.** Cuando termine la descarga, pulsa **Abrir** en el aviso del navegador. Si ya lo
cerraste, abre la app **Archivos** (o **Mis archivos**) → **Descargas** → toca `glykos-….apk`.

**2.3 Permite la instalación.** La primera vez, Android dice que no puede instalar apps de este origen:

1. Pulsa **Ajustes**.
2. Activa **Permitir de esta fuente** (o *Permitir apps desconocidas*) para el navegador o la app de
   archivos que estés usando.
3. Vuelve atrás con la flecha.

**2.4 Instala.** Pulsa **Instalar** y, cuando termine, **Abrir**.

> 💡 Si aparece **Google Play Protect** diciendo que la app es desconocida, pulsa **Más detalles** →
> **Instalar de todos modos**. Sale porque Glykos no está en la tienda, no porque haya un problema.
> Si te ofrece **enviarla para analizarla**, puedes decir que sí o que no: no cambia nada.

Cuando esté instalada, puedes volver a desactivar *Permitir de esta fuente* en los ajustes; Glykos
seguirá funcionando.

---

## Paso 3 · Configurar Glykos

En la pantalla de Glykos, de arriba abajo:

1. **Cuenta LibreLinkUp (seguidora)**: escribe el **email y la contraseña de la cuenta seguidora**
   (paso 1) y pulsa **Guardar**. Se guardan cifradas en tu móvil.
2. **Salida**: elige **Voz (cascos)**, **Reloj** o **Ambos**.
3. **Avisos** (puedes dejarlo como está):
   - **Avisar cada**: cada cuántos minutos te dice la glucosa (por defecto, 5).
   - **Hablar solo si cambia**: si lo activas, no repite el aviso si la glucosa apenas ha cambiado.
   - **Umbrales de alarma**: baja, muy baja y alta. Ajústalos con tu equipo médico.
   - Pulsa **Guardar** si cambias algo.
4. **Batería**: pulsa **Quitar optimización** y acepta. **Muy importante**: si no, el móvil puede
   apagar Glykos con la pantalla bloqueada.
5. **Ajustes extra según la marca de tu móvil** (para que no apague Glykos):
   - **Xiaomi / Redmi / POCO**: pulsa **Abrir ajustes de la app** → *Ahorro de batería* →
     **Sin restricciones**, y activa **Inicio automático**.
   - **Samsung**: Ajustes → Batería → *Límites de uso en segundo plano* → asegúrate de que Glykos
     **no** está en "Aplicaciones en suspensión" ni en "En suspensión profunda".
   - **Huawei / Honor**: Ajustes → Batería → *Inicio de aplicaciones* → Glykos → **Gestionar
     manualmente** y activa las tres opciones.
   - Otras marcas: busca tu modelo en [dontkillmyapp.com](https://dontkillmyapp.com).

---

## Paso 4 · Conectar el reloj Garmin (opcional)

1. Abre **Garmin Connect** en el móvil.
2. **Dispositivos** → tu reloj → **Notificaciones** → **Notificaciones de apps**.
3. Activa **Glykos**.
4. En el reloj, activa las **notificaciones durante la actividad** (en el menú de ajustes del reloj →
   Notificaciones).

---

## Paso 5 · Probar

1. Ponte los cascos con música.
2. En Glykos, baja hasta **Pruebas → Probar voz y reloj** y pulsa **Ambos** en el primer ejemplo.
   Deberías oír *"Ciento doce, bajando, menos ocho"* mientras la música baja, y ver
   `112 -8 baja` en el reloj. (Son datos de ejemplo, no tu glucosa.)
3. Sube y pulsa **Empezar**. Concede los permisos que pida:
   - **Notificaciones** (para el reloj y para la notificación fija).
   - **Actividad física** (Android lo exige para que funcione con la pantalla bloqueada; Glykos no
     lee tu actividad).
4. Aparece una notificación fija **"Glykos vigilando tu glucosa"** y enseguida oirás tu glucosa real.

**¡Listo!** Bloquea el móvil y sal a entrenar. Para terminar, pulsa **Parar** en la notificación o en
la app.

---

## Qué te dirá Glykos

| Situación | Cuándo |
| --- | --- |
| Glucosa en rango | Cada X minutos (lo que configures) |
| Baja, muy baja o alta | **Al momento**, y se repite cada 5 min mientras dure |
| Vuelves a rango | Al momento |
| Sin datos | Al momento, y se repite cada 5 min |

Ejemplo por voz: *"Ciento doce, bajando, menos ocho"* → 112 mg/dl, tendencia bajando, 8 menos que el
aviso anterior.

---

## Si algo no funciona

| Glykos dice… | Qué pasa | Qué hacer |
| --- | --- | --- |
| "Sin datos de glucosa, **revisa la cuenta**" | Email o contraseña incorrectos, o LibreLinkUp tiene términos nuevos pendientes | Revisa el email y la contraseña. Abre LibreLinkUp con la cuenta seguidora y acepta lo que pida. Glykos espera unos minutos antes de reintentar |
| "Sin datos de glucosa, **el sensor no envía datos**" | Sensor nuevo calentándose (~1 h), sensor caducado o la app oficial no sube datos | Comprueba que la app oficial muestra tu glucosa y tiene internet |
| "Sin datos de glucosa, **sin conexión**" | Tu móvil no tiene internet | Revisa datos móviles / Wi-Fi |
| "**Sin datos recientes**. Último dato hace…" | El último dato tiene más de 10 minutos | El móvil con la app oficial puede estar lejos del sensor o sin internet |
| No habla, pero el reloj sí recibe | Falta la voz en español | Ajustes del móvil → busca **"Texto a voz"** → instala *Español (España)* |
| No llega nada al reloj | Glykos no está activado en Garmin Connect | Paso 4 |
| Se para con la pantalla apagada | El móvil está ahorrando batería | Paso 3, puntos 4 y 5 |

---

## Actualizar y desinstalar

- **Actualizar**: Glykos **no se actualiza sola**. Cuando el desarrollador te pase una versión nueva,
  descarga el APK e instálalo encima (paso 2): conservas tu cuenta y tu configuración. Si Android dice
  que la app **no es compatible** con la instalada, avisa al desarrollador antes de desinstalar nada.
- **Desinstalar**: como cualquier app. Tus credenciales se borran del móvil. Si quieres, puedes
  quitar también el acceso de la cuenta seguidora desde la app oficial del sensor →
  *Aplicaciones conectadas*.

---

*FreeStyle Libre, LibreLink y LibreLinkUp son marcas de Abbott. Garmin es marca de Garmin. Glykos no
está afiliado, respaldado ni patrocinado por Abbott ni por Garmin.*
