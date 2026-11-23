package app.trombito.rallycopilot.ui.alertas

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import java.time.Instant

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

/**
 * Los datos de la DGT que enriquecen el bocadillo de una incidencia; todos `null` si el feed no los trae. [pkDesde] y
 * [pkHasta] son los puntos kilométricos (el segundo solo si es un tramo), [sentido] el destino o sentido de la marcha,
 * [carriles] un resumen de lo afectado, [limiteKmh] el límite temporal de velocidad, [fin] cuándo acaba y [comentario]
 * el comentario público.
 */
@Immutable
data class DetalleIncidencia(
    val pkDesde: Double? = null,
    val pkHasta: Double? = null,
    val sentido: String? = null,
    val municipio: String? = null,
    val carriles: String? = null,
    val limiteKmh: Int? = null,
    val fin: Instant? = null,
    val comentario: String? = null,
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
    val detalle: DetalleIncidencia = DetalleIncidencia(),
)

/**
 * Un radar (fijo, móvil o entrada de tramo) pintado siempre en el mapa. [rumboGrados] es el sentido de marcha que
 * vigila (null = ambos); [tramoId] solo lo llevan los de tramo.
 */
@Immutable
data class MarcadorRadar(
    val id: String,
    val clase: ClaseRadar,
    val lat: Double,
    val lon: Double,
    val limiteKmh: Int?,
    val rumboGrados: Double? = null,
    val tramoId: String? = null,
)

/** El segmento de un radar de tramo sobre el mapa: [puntos] de la entrada a la salida, de [longitudM] metros. */
@Immutable
data class TramoRadarMapa(
    val id: String,
    val limiteKmh: Int?,
    val puntos: List<PuntoGeo>,
    val longitudM: Double = 0.0,
)

/**
 * Bandera de salida o de meta de un tramo cronometrado que pasa por el camino, con lo que dice su bocadillo: [nombre]
 * del tramo y [mejorMs] (su mejor tiempo), null si no se conocen.
 */
@Immutable
data class BanderaMapa(
    val tramoId: String,
    val esMeta: Boolean,
    val lat: Double,
    val lon: Double,
    val nombre: String?,
    val mejorMs: Long?,
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

    /**
     * Incidencia de la ruta o tocada en el mapa. Con [completo] (la tocada) lleva toda la línea de detalle, el
     * comentario de la DGT y, si se sabe, cuánto hace que se descargaron los datos ([antiguedadMs], al minuto); sin él
     * (la que se acerca) solo «carretera km».
     */
    data class Incidencia(
        val tipo: TipoIncidencia,
        val carretera: String? = null,
        val detalle: DetalleIncidencia = DetalleIncidencia(),
        val completo: Boolean = false,
        val antiguedadMs: Long? = null,
    ) : ContenidoBocadillo

    /** Radar fijo o zona de radar móvil tocados: su [clase], el límite y el sentido que vigila ([rumboGrados]). */
    data class RadarTocado(
        val clase: ClaseRadar,
        val limiteKmh: Int?,
        val rumboGrados: Double?,
    ) : ContenidoBocadillo

    /** Radar de tramo tocado: su límite y su longitud (con ellas, el tiempo mínimo para no pasarse de la media). */
    data class TramoRadarTocado(
        val limiteKmh: Int?,
        val longitudM: Double,
    ) : ContenidoBocadillo

    /** Curva o aviso de la hoja tocados: la [frase] de la nota y su [icono] de roadbook. */
    data class NotaDelMapa(
        val frase: String,
        @DrawableRes val icono: Int,
    ) : ContenidoBocadillo

    /** Bandera de salida o meta de un tramo tocada: [nombre] del tramo y su mejor tiempo. */
    data class Bandera(
        val esMeta: Boolean,
        val nombre: String?,
        val mejorMs: Long?,
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
    /** Bocadillo de lo tocado en el mapa o, si no, de lo que se acerca o se acaba de pasar; null si no hay. */
    val bocadillo: BocadilloMapa? = null,
    /** Lo tocado en el mapa cuyo bocadillo está abierto (ya comprobado que existe); null si nada. */
    val seleccionada: SeleccionMapa? = null,
)
