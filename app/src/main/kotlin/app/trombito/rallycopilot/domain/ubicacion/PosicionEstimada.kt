package app.trombito.rallycopilot.domain.ubicacion

import app.trombito.rallycopilot.motor.velocidad.EstimadorVelocidad
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.velocidad.PrecisionGps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Una fijación [cruda] con lo que se deduce de las anteriores, calculado una sola vez para todos los consumidores
 * (mapa, sesión de navegación, respuestas de voz):
 * - [velocidadMs]: la velocidad estimada (m/s) o `null` si no se conoce. Solo las fijaciones precisas
 *   ([PrecisionGps.FIJACION_UTIL_M]) la alimentan; con una aproximada de interior (fused/red) se conserva la vigente.
 * - [rumboGrados]: el del receptor si lo trae; si no, el del movimiento (emulador, fused, rumbo impreciso).
 */
data class PosicionEstimada(
    val cruda: Fijacion,
    val velocidadMs: Double?,
    val rumboGrados: Double?,
) {
    /** La fijación es lo bastante precisa para velocidad, ruta y notas. */
    val precisa: Boolean get() = PrecisionGps.esUtil(cruda.precisionM)
}

/** Enriquece cada fijación con velocidad y rumbo estimados; el estimador es nuevo en cada recogida del flujo. */
fun Flow<Fijacion>.conEstimacion(): Flow<PosicionEstimada> =
    flow {
        val estimador = EstimadorVelocidad()
        collect { fijacion ->
            val velocidad =
                if (PrecisionGps.esUtil(fijacion.precisionM)) {
                    estimador.actualizar(fijacion)
                } else {
                    estimador.vigente(fijacion.tiempoMs)
                }
            emit(PosicionEstimada(fijacion, velocidad, fijacion.rumboGrados ?: estimador.rumbo()))
        }
    }
