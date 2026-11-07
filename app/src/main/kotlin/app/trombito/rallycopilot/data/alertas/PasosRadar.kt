package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.domain.alertas.PasoRadarEnCurso
import app.trombito.rallycopilot.motor.alertas.DetectorPasoRadar
import app.trombito.rallycopilot.motor.alertas.PasoRadar
import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.velocidad.msAKmh

/**
 * Paso por los radares fijos de una conducción: envuelve a [DetectorPasoRadar] (que existe cuando se cargan los
 * radares) y mantiene [RETENCION_MS] el último paso para que la UI enseñe la velocidad de paso. No es concurrente.
 */
internal class PasosRadar {
    private var detector: DetectorPasoRadar? = null
    private var retenido: Pair<PasoRadar, Long>? = null

    /** Radares ya leídos: a partir de aquí se detectan pasos. */
    fun cargar(radares: List<Radar>) {
        detector = DetectorPasoRadar(radares)
    }

    /**
     * Una fijación con la velocidad estimada [velocidadMs] (null = no se sabe, no se mira). Devuelve el último paso
     * si aún está vigente a la hora de la fijación, o null.
     */
    fun actualizar(
        fijacion: Fijacion,
        velocidadMs: Double?,
    ): PasoRadarEnCurso? {
        val nuevo = velocidadMs?.let { detector?.actualizar(fijacion.punto, fijacion.rumboGrados, it.msAKmh()) }
        if (nuevo != null) retenido = nuevo to fijacion.tiempoMs + RETENCION_MS
        return retenido
            ?.takeIf { fijacion.tiempoMs < it.second }
            ?.first
            ?.let { PasoRadarEnCurso(it.radarId, it.velocidadKmh, it.limiteKmh) }
    }

    companion object {
        /** Cuánto se sigue enseñando la velocidad de paso por un radar fijo. */
        const val RETENCION_MS = 4_000L
    }
}
