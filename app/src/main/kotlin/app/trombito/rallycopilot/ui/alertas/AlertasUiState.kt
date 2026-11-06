package app.trombito.rallycopilot.ui.alertas

import androidx.compose.runtime.Immutable
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Clase de radar tal como la ve el conductor (el tramo cuenta una sola vez, entrada o salida). */
enum class ClaseRadar { FIJO, MOVIL, TRAMO }

/** El aviso que sale en el banner: un radar o una incidencia de la ruta. [distanciaM] ya va redondeada a 10 m. */
sealed interface AvisoAlerta {
    val distanciaM: Int

    data class Radar(
        val clase: ClaseRadar,
        override val distanciaM: Int,
        val limiteKmh: Int?,
        /** Fijo que vigila el otro sentido: se avisa «por si acaso» como «Radar · sentido contrario». */
        val sentidoContrario: Boolean = false,
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

/** Un radar (fijo, móvil o entrada de tramo) pintado siempre en el mapa. */
@Immutable
data class MarcadorRadar(
    val id: String,
    val clase: ClaseRadar,
    val lat: Double,
    val lon: Double,
    val limiteKmh: Int?,
)

/** El segmento de un radar de tramo sobre el mapa: [puntos] de la entrada a la salida. */
@Immutable
data class TramoRadarMapa(
    val id: String,
    val limiteKmh: Int?,
    val puntos: List<PuntoGeo>,
)

/** Radares y tramos que se pintan siempre en el mapa (con o sin conducción en curso). */
@Immutable
data class RadaresUi(
    val marcadores: List<MarcadorRadar> = emptyList(),
    val tramos: List<TramoRadarMapa> = emptyList(),
)

/** Lo que dice el bocadillo anclado a un marcador del mapa. */
sealed interface ContenidoBocadillo {
    /** Radar que se acerca; [sentidoContrario] lo avisa como «Radar · sentido contrario». */
    data class Radar(
        val clase: ClaseRadar,
        val limiteKmh: Int?,
        val sentidoContrario: Boolean,
    ) : ContenidoBocadillo

    /** Radar de tramo en el que se está: límite y media actual (null hasta recorrer unos 100 m). */
    data class Tramo(
        val limiteKmh: Int?,
        val mediaKmh: Int?,
        val supera: Boolean,
    ) : ContenidoBocadillo

    /** Se acaba de pasar un radar fijo a [velocidadKmh]; [limiteKmh] null si no se conoce. */
    data class Paso(
        val velocidadKmh: Int,
        val limiteKmh: Int?,
    ) : ContenidoBocadillo {
        /** Verde si va dentro del límite, rojo si lo pasa, `null` (neutro) sin límite. */
        val supera: Boolean? get() = limiteKmh?.let { velocidadKmh > it }
    }

    data class Incidencia(
        val tipo: TipoIncidencia,
    ) : ContenidoBocadillo
}

/** Bocadillo anclado a la coordenada [lat], [lon] (el marcador al que se refiere). [id] cambia con el contenido. */
@Immutable
data class BocadilloMapa(
    val id: String,
    val lat: Double,
    val lon: Double,
    val contenido: ContenidoBocadillo,
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
    /** Todos los radares y los tramos, siempre en el mapa. */
    val radares: RadaresUi = RadaresUi(),
    /** Bocadillo de lo que se acerca o se acaba de pasar (radar, tramo o incidencia); null si no hay. */
    val bocadillo: BocadilloMapa? = null,
    /** La incidencia tocada en el mapa (tarjeta abierta); null si ninguna. */
    val seleccionada: MarcadorIncidencia? = null,
)
