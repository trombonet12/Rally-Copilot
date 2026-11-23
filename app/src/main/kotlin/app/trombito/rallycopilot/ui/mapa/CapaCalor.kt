package app.trombito.rallycopilot.ui.mapa

import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.analisis.PaletaVelocidad
import app.trombito.rallycopilot.ui.miscarreteras.NivelUso
import app.trombito.rallycopilot.ui.theme.DarkColors
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale

private const val ID_FUENTE = "calor-fuente"
private const val ID_BORDE = "calor-borde"
private const val ID_LINEA = "calor-linea"

internal const val PROPIEDAD_CALOR_COLOR = "color"
internal const val PROPIEDAD_CALOR_ANCHO = "ancho"
internal const val PROPIEDAD_CALOR_ORDEN = "orden"
internal const val PROPIEDAD_CALOR_REF = "ref"
internal const val PROPIEDAD_CALOR_NOMBRE = "nombre"
internal const val PROPIEDAD_CALOR_PASADAS = "pasadas"
internal const val PROPIEDAD_CALOR_KM = "km"
internal const val PROPIEDAD_CALOR_VELOCIDAD = "vel"

private const val EXPONENTE = 1.5f
private const val ZOOM_MINIMO = 8
private const val ZOOM_BASE = 14
private const val ZOOM_MAXIMO = 18
private const val ESCALA_ZOOM_MINIMO = 0.4f
private const val ESCALA_ZOOM_MAXIMO = 1.6f
private const val EXTRA_BORDE = 2f
private const val OPACIDAD_LINEA = 0.9f
private const val OPACIDAD_BORDE = 0.35f
private const val TOLERANCIA_TOQUE_DP = 20f
private const val M_POR_KM = 1_000.0

/** Anchura de las vías en el modo velocidad (la escala de color ya las distingue) y de las que no tienen dato. */
internal const val ANCHO_CALOR_VELOCIDAD = 5f
internal const val ANCHO_CALOR_SIN_DATO = 3f

/** Velocidad media (km/h) que se pinta como lenta y como rápida: la escala de las carreteras de Mallorca. */
internal const val VELOCIDAD_CALOR_LENTA_KMH = 20.0
internal const val VELOCIDAD_CALOR_RAPIDA_KMH = 110.0

/** Vías sin velocidad media (sin tiempos en el recorrido): gris neutro, más fino. */
internal const val COLOR_CALOR_SIN_DATO = "#8A8F98"

/** Qué enseña la capa de calor: por dónde se pasa más ([USO]) o a qué ritmo ([VELOCIDAD] media en marcha). */
enum class ModoCalor { USO, VELOCIDAD }

/** Una vía conducida con sus números: lo que la capa de calor pinta y la ficha enseña. */
@Immutable
data class TramoCalor(
    val viaId: Long,
    val nombre: String?,
    val ref: String?,
    val puntos: List<PuntoGeo>,
    val pasadas: Int,
    val km: Double,
    /** Velocidad media en marcha (km/h); null si los recorridos no traían tiempos. */
    val velMediaKmh: Double?,
) {
    fun ficha() = FichaCalor(ref, nombre, pasadas, km, velMediaKmh)
}

/** Lo que enseña la ficha al tocar una carretera de la capa. */
@Immutable
data class FichaCalor(
    val ref: String?,
    val nombre: String?,
    val pasadas: Int,
    val km: Double,
    val velMediaKmh: Double?,
)

/**
 * Lo que el mapa pinta cuando la capa es visible; null en [PreferenciasVistaMapa.calor] = oculta. [geoJson] es el
 * FeatureCollection ya serializado (lo construye [CalorViewModel] fuera del hilo principal; ver [calorGeoJsonTexto]).
 */
@Immutable
data class DatosCalor(
    val geoJson: String,
)

/** Estado de la capa de calor que ve la pantalla: interruptor (por defecto encendida), modo y ficha abierta. */
@Immutable
data class CalorUi(
    val activo: Boolean = true,
    val modo: ModoCalor = ModoCalor.USO,
    val ficha: FichaCalor? = null,
)

/**
 * La capa se ve solo en reposo y parado: encendida, sin guiado (ni llegada ni error) y sin ir [enMarcha] (el criterio
 * de [rememberMarchaRapida]: se esconde por encima de 20 km/h y vuelve tras 3 s por debajo de 10).
 */
internal fun calorVisible(
    activo: Boolean,
    control: EstadoNavegacion,
    enMarcha: Boolean,
): Boolean = activo && !enMarcha && (control is EstadoNavegacion.Inactiva || control is EstadoNavegacion.Libre)

/** Fracción 0..1 de la escala de velocidad (20 km/h lento, 110 km/h rápido) para colorear [kmh]. */
internal fun fraccionVelocidadCalor(kmh: Double): Float =
    ((kmh - VELOCIDAD_CALOR_LENTA_KMH) / (VELOCIDAD_CALOR_RAPIDA_KMH - VELOCIDAD_CALOR_LENTA_KMH))
        .coerceIn(0.0, 1.0)
        .toFloat()

/** Color `#RRGGBB` de una velocidad media, con la paleta del mapa del análisis (violeta lento, amarillo rápido). */
internal fun colorVelocidadCalor(kmh: Double): String = hex(PaletaVelocidad.argb(fraccionVelocidadCalor(kmh)))

private fun hex(argb: Int): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

/**
 * Las vías como líneas, con color y ancho ya resueltos por modo (propiedades `color` y `ancho`), el orden de dibujo
 * (`orden` = pasadas: las más repetidas encima) y los datos de la ficha (`ref`, `nombre`, `pasadas`, `km`, `vel`).
 * Las vías de menos de dos puntos no se pintan.
 */
internal fun calorGeoJson(
    tramos: List<TramoCalor>,
    modo: ModoCalor,
): FeatureCollection =
    FeatureCollection.fromFeatures(
        tramos
            .filter { it.puntos.size >= 2 }
            .sortedWith(compareBy<TramoCalor> { it.pasadas }.thenBy { it.km })
            .map { tramo ->
                val linea = LineString.fromLngLats(tramo.puntos.map { Point.fromLngLat(it.lon, it.lat) })
                Feature.fromGeometry(linea).also { f ->
                    val (color, ancho) = estiloDe(tramo, modo)
                    f.addStringProperty(PROPIEDAD_CALOR_COLOR, color)
                    f.addNumberProperty(PROPIEDAD_CALOR_ANCHO, ancho)
                    f.addNumberProperty(PROPIEDAD_CALOR_ORDEN, tramo.pasadas)
                    f.addNumberProperty(PROPIEDAD_CALOR_PASADAS, tramo.pasadas)
                    f.addNumberProperty(PROPIEDAD_CALOR_KM, tramo.km)
                    tramo.ref?.let { f.addStringProperty(PROPIEDAD_CALOR_REF, it) }
                    tramo.nombre?.let { f.addStringProperty(PROPIEDAD_CALOR_NOMBRE, it) }
                    tramo.velMediaKmh?.let { f.addNumberProperty(PROPIEDAD_CALOR_VELOCIDAD, it) }
                }
            },
    )

/** [calorGeoJson] ya serializado: `GeoJsonSource.setGeoJson(String)` lo consume sin trabajo extra en el main. */
internal fun calorGeoJsonTexto(
    tramos: List<TramoCalor>,
    modo: ModoCalor,
): String = calorGeoJson(tramos, modo).toJson()

/** GeoJSON de una capa sin vías. */
internal const val CALOR_GEOJSON_VACIO = """{"type":"FeatureCollection","features":[]}"""

/** Color `#RRGGBB` y ancho de una vía según el [modo]. */
private fun estiloDe(
    tramo: TramoCalor,
    modo: ModoCalor,
): Pair<String, Float> =
    when (modo) {
        ModoCalor.USO -> {
            val nivel = NivelUso.de(tramo.pasadas)
            hex(nivel.color.toArgb()) to nivel.grosorPx
        }

        ModoCalor.VELOCIDAD -> {
            val kmh = tramo.velMediaKmh
            if (kmh == null) {
                COLOR_CALOR_SIN_DATO to ANCHO_CALOR_SIN_DATO
            } else {
                colorVelocidadCalor(kmh) to ANCHO_CALOR_VELOCIDAD
            }
        }
    }

/** La ficha de la vía pintada en [feature] (lo que dejó [calorGeoJson]); null si no trae los datos. */
internal fun fichaDeFeature(feature: Feature): FichaCalor? {
    val pasadas = feature.getNumberProperty(PROPIEDAD_CALOR_PASADAS)?.toInt() ?: return null
    return FichaCalor(
        ref = feature.getStringProperty(PROPIEDAD_CALOR_REF),
        nombre = feature.getStringProperty(PROPIEDAD_CALOR_NOMBRE),
        pasadas = pasadas,
        km = feature.getNumberProperty(PROPIEDAD_CALOR_KM)?.toDouble() ?: 0.0,
        velMediaKmh = feature.getNumberProperty(PROPIEDAD_CALOR_VELOCIDAD)?.toDouble(),
    )
}

/**
 * Capa de calor de las carreteras conducidas: una fuente GeoJSON con una `LineLayer` (contorno carbón tenue y línea)
 * cuyo color y ancho salen de las propiedades de cada vía. Se instala ANTES que [CapaEstela] y la ruta, justo antes
 * de la primera capa de símbolos: queda por debajo de la estela, la ruta y el coche. Oculta (`visibility: none`) cuando
 * los datos son null; los datos solo se reenvían a MapLibre si cambian. Una instancia por mapa.
 */
internal class CapaCalor {
    private var enviado: DatosCalor? = null

    fun instalar(
        estilo: Style,
        datos: DatosCalor?,
    ) {
        enviado = datos
        estilo.addSource(GeoJsonSource(ID_FUENTE, datos?.geoJson ?: CALOR_GEOJSON_VACIO))
        val etiquetas = estilo.layers.firstOrNull { it is SymbolLayer }?.id
        val visibilidad = PropertyFactory.visibility(if (datos != null) Property.VISIBLE else Property.NONE)
        listOf(
            capaBorde().withProperties(visibilidad),
            capaLinea().withProperties(visibilidad),
        ).forEach { capa ->
            if (etiquetas != null) estilo.addLayerBelow(capa, etiquetas) else estilo.addLayer(capa)
        }
    }

    fun actualizar(
        estilo: Style,
        datos: DatosCalor?,
    ) {
        if (datos != null && datos.geoJson != enviado?.geoJson) {
            enviado = datos
            estilo.getSourceAs<GeoJsonSource>(ID_FUENTE)?.setGeoJson(datos.geoJson)
        }
        val visibilidad = PropertyFactory.visibility(if (datos != null) Property.VISIBLE else Property.NONE)
        listOf(ID_BORDE, ID_LINEA).forEach { estilo.getLayer(it)?.setProperties(visibilidad) }
    }

    /** La ficha de la vía pintada bajo el toque en [x], [y] (píxeles), con un margen cómodo para el dedo. */
    fun fichaEn(
        mapa: MapLibreMap,
        x: Float,
        y: Float,
        densidad: Float,
    ): FichaCalor? {
        val margen = TOLERANCIA_TOQUE_DP * densidad
        val zona = RectF(x - margen, y - margen, x + margen, y + margen)
        return mapa.queryRenderedFeatures(zona, ID_LINEA).firstNotNullOfOrNull(::fichaDeFeature)
    }

    private fun capaBorde() =
        LineLayer(ID_BORDE, ID_FUENTE).withProperties(
            PropertyFactory.lineColor(DarkColors.surfaceContainerLowest.toArgb()),
            PropertyFactory.lineOpacity(OPACIDAD_BORDE),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineSortKey(Expression.toNumber(Expression.get(PROPIEDAD_CALOR_ORDEN))),
            PropertyFactory.lineWidth(anchoPorZoom(EXTRA_BORDE)),
        )

    private fun capaLinea() =
        LineLayer(ID_LINEA, ID_FUENTE).withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get(PROPIEDAD_CALOR_COLOR))),
            PropertyFactory.lineOpacity(OPACIDAD_LINEA),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineSortKey(Expression.toNumber(Expression.get(PROPIEDAD_CALOR_ORDEN))),
            PropertyFactory.lineWidth(anchoPorZoom(0f)),
        )

    /** El ancho de cada vía (propiedad `ancho`) escalado con el zoom: fino de lejos, ancho de cerca. */
    private fun anchoPorZoom(extra: Float): Expression {
        val base = Expression.toNumber(Expression.get(PROPIEDAD_CALOR_ANCHO))

        fun escalado(factor: Float) =
            Expression.sum(Expression.product(base, Expression.literal(factor)), Expression.literal(extra))
        return Expression.interpolate(
            Expression.exponential(EXPONENTE),
            Expression.zoom(),
            Expression.stop(ZOOM_MINIMO, escalado(ESCALA_ZOOM_MINIMO)),
            Expression.stop(ZOOM_BASE, escalado(1f)),
            Expression.stop(ZOOM_MAXIMO, escalado(ESCALA_ZOOM_MAXIMO)),
        )
    }
}

/** Kilómetros de un tramo en metros (para construirlo desde el agregado). */
internal fun metrosAKm(metros: Double): Double = metros / M_POR_KM

/**
 * Lo que el mapa pinta de la capa de calor: las vías y el modo si la capa se ve (encendida, en reposo y parado; ver
 * [calorVisible]) y null si no. Al esconderse la capa se cierra también la ficha de la carretera tocada.
 */
@Composable
internal fun rememberDatosCalor(
    viewModel: CalorViewModel,
    calor: CalorUi,
    control: EstadoNavegacion,
    enMarcha: Boolean,
): DatosCalor? {
    val visible = calorVisible(calor.activo, control, enMarcha)
    val geoJson by viewModel.geoJson.collectAsStateWithLifecycle()
    LaunchedEffect(visible) { if (!visible) viewModel.onCerrarFicha() }
    return remember(visible, geoJson) { if (visible) DatosCalor(geoJson) else null }
}
