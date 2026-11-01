package app.trombito.rallycopilot.ui.alertas

import androidx.compose.runtime.Immutable
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia

/** Clase de radar tal como la ve el conductor (el tramo cuenta una sola vez, entrada o salida). */
enum class ClaseRadar { FIJO, MOVIL, TRAMO }

/** El aviso que sale en el banner: un radar o una incidencia de la ruta. [distanciaM] ya va redondeada a 10 m. */
sealed interface AvisoAlerta {
    val distanciaM: Int

    data class Radar(
        val clase: ClaseRadar,
        override val distanciaM: Int,
        val limiteKmh: Int?,
    ) : AvisoAlerta

    /** [distanciaM] es 0 si el coche ya está dentro de la incidencia. */
    data class Incidencia(
        val tipo: TipoIncidencia,
        val carretera: String?,
        override val distanciaM: Int,
    ) : AvisoAlerta
}

/** Velocidad media de un tramo de radar. [mediaKmh] es null hasta recorrer unos 100 m; [terminado]: media final. */
@Immutable
data class MediaTramoUi(
    val mediaKmh: Int?,
    val supera: Boolean,
    val terminado: Boolean,
)

/**
 * Lo que cambia en el panel de velocidad: [rojo] la velocidad pasa del límite, [exceso] pasa del límite más un 10 %
 * (parpadeo) y [tramo] la media del radar de tramo en curso (o recién terminado).
 */
@Immutable
data class AlertasPanel(
    val rojo: Boolean = false,
    val exceso: Boolean = false,
    val tramo: MediaTramoUi? = null,
)

/** Un marcador de incidencia en el mapa. [lat]/[lon] es el punto (el inicio si es un tramo). */
@Immutable
data class MarcadorIncidencia(
    val id: String,
    val tipo: TipoIncidencia,
    val lat: Double,
    val lon: Double,
    val descripcion: String,
    val carretera: String?,
    val enRuta: Boolean,
)

/**
 * Cuánto hace que se obtuvieron los datos de incidencias. Solo existe si merece decirlo: pasan de 30 minutos
 * ([conError] = false) o el último refresco falló ([conError] = true) y se usa la copia.
 */
@Immutable
data class FrescuraIncidencias(
    val antiguedadMs: Long,
    val conError: Boolean,
)

/** Estado de las alertas de la conducción para la pantalla de mapa. */
@Immutable
data class AlertasUiState(
    val aviso: AvisoAlerta? = null,
    val panel: AlertasPanel = AlertasPanel(),
    val marcadores: List<MarcadorIncidencia> = emptyList(),
    val frescura: FrescuraIncidencias? = null,
    /** La incidencia tocada en el mapa (tarjeta abierta); null si ninguna. */
    val seleccionada: MarcadorIncidencia? = null,
)
