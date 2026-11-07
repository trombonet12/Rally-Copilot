package app.trombito.rallycopilot.data.ubicacion

import android.os.SystemClock
import android.util.Log
import app.trombito.rallycopilot.di.ApplicationScope
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.ubicacion.PosicionEstimada
import app.trombito.rallycopilot.domain.ubicacion.conEstimacion
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformWhile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La posición de la app, compartida (auditoría de noviembre de 2026, ARQ-1 y REN-9): una sola suscripción al GPS
 * ([origen]) y un solo estimador de velocidad y rumbo para el mapa, la sesión de navegación y las respuestas de voz.
 * Antes cada consumidor registraba sus propios listeners y calculaba su propia velocidad, y las del mapa y las de las
 * notas podían diferir.
 *
 * Se comparte con `shareIn(WhileSubscribed)`: los listeners se registran con el primer consumidor y se retiran al irse
 * el último (sin espera, sin conservar nada). Un consumidor que llega con el GPS ya en marcha recibe enseguida la
 * última posición (con menos de [EDAD_MAX_MS]). Si el origen termina (sin permiso, sin proveedor) o falla, todos los
 * consumidores terminan, como con el flujo frío de antes. Nunca se escriben coordenadas en el log.
 */
@Singleton
class PosicionEnVivo internal constructor(
    origen: FuenteUbicacion,
    alcance: CoroutineScope,
    private val reloj: () -> Long,
) : FuenteUbicacion {
    @Inject
    constructor(
        origen: FuenteUbicacionGps,
        @ApplicationScope alcance: CoroutineScope,
    ) : this(
        origen,
        alcance,
        SystemClock::elapsedRealtime,
    )

    private val permiso = origen::tienePermiso

    /** `null` marca el final del origen: un `SharedFlow` no termina solo. */
    private val compartido: SharedFlow<PosicionEstimada?> =
        origen
            .fijaciones()
            .conEstimacion()
            .map<PosicionEstimada, PosicionEstimada?> { it }
            .onCompletion { causa -> if (causa == null) emit(null) }
            .catch { e ->
                Log.w(ETIQUETA, "La fuente de posiciones falló: ${e.javaClass.simpleName}")
                emit(null)
            }.shareIn(alcance, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), 1)

    override fun posiciones(): Flow<PosicionEstimada> =
        compartido.transformWhile { posicion ->
            when {
                posicion == null -> {
                    false
                }

                reloj() - posicion.cruda.tiempoMs > EDAD_MAX_MS -> {
                    true
                }

                else -> {
                    emit(posicion)
                    true
                }
            }
        }

    override fun fijaciones(): Flow<Fijacion> = posiciones().map { it.cruda }

    override fun tienePermiso(): Boolean = permiso()

    private companion object {
        const val ETIQUETA = "PosicionEnVivo"

        /** Una posición más vieja (la repetida a quien llega tarde) ya no vale como «la actual». */
        const val EDAD_MAX_MS = 120_000L
    }
}
