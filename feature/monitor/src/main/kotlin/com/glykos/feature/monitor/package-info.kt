/**
 * Foreground service (type `health`) and reading scheduling.
 * Fase 0: polling of [com.glykos.core.data.source.GlucoseSource] and fan-out to
 * [com.glykos.feature.output.GlucoseOutput]s.
 */
package com.glykos.feature.monitor
