package app.trombito.rallycopilot.ui.mapa

import android.graphics.RectF
import androidx.compose.runtime.Immutable
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val ID_FUENTE = "notas-fuente"
private const val ID_CAPA = "notas-capa"
private const val PROPIEDAD_POSICION = "posicionM"
private const val PROPIEDAD_COLOR = "color"

// MapLibre mide radios y bordes en píxeles lógicos: ya escalan con la densidad de la pantalla.
private const val RADIO_NOTA_DP = 11f
private const val BORDE_NOTA_DP = 3f
private const val TOLERANCIA_TOQUE_DP = 24f
private const val COLOR_CUIDADO = "#FFB300"
private const val COLOR_NOTA = "#F2EDE0"
private const val COLOR_BORDE = "#1A1B1F"

private const val PREFIJO_CAPA_UBICACION = "mapbox-location-"

/**
 * La capa de las [capas] (de abajo arriba) bajo la que hay que insertar una capa de marcadores para que quede por
 * debajo de la flecha de posición: la más baja del componente de ubicación; null si aún no está puesto.
 */
internal fun capaBajoLaPosicion(capas: List<String>): String? =
    capas.firstOrNull { it.startsWith(PREFIJO_CAPA_UBICACION) }

/** Una nota de la hoja puesta en el mapa para poder tocarla (solo con el coche parado). */
@Immutable
data class MarcadorNota(
    /** Metros de la nota sobre la hoja vigente: lo que identifica la nota al editarla. */
    val posicionM: Double,
    val lat: Double,
    val lon: Double,
    val cuidado: Boolean,
)

/**
 * Las notas de la hoja como círculos tocables (ámbar si piden cuidado), una `CircleLayer` encima de las incidencias.
 * Como en las incidencias, la fuente siempre existe (vacía sin notas) y cambiar los marcadores solo cambia sus datos.
 */
internal object CapaNotas {
    fun instalar(
        estilo: Style,
        marcadores: List<MarcadorNota>,
    ) {
        estilo.addSource(GeoJsonSource(ID_FUENTE, features(marcadores)))
        val capa =
            CircleLayer(ID_CAPA, ID_FUENTE).withProperties(
                PropertyFactory.circleRadius(RADIO_NOTA_DP),
                PropertyFactory.circleColor(Expression.toColor(Expression.get(PROPIEDAD_COLOR))),
                PropertyFactory.circleStrokeColor(COLOR_BORDE),
                PropertyFactory.circleStrokeWidth(BORDE_NOTA_DP),
            )
        // La posición del coche va siempre por encima de las notas, aunque el componente de ubicación ya esté puesto
        // (al recargar el estilo, p. ej. con el relieve, se añade antes que esta capa).
        val bajoLaPosicion = capaBajoLaPosicion(estilo.layers.map { it.id })
        if (bajoLaPosicion != null) estilo.addLayerBelow(capa, bajoLaPosicion) else estilo.addLayer(capa)
    }

    fun actualizar(
        estilo: Style,
        marcadores: List<MarcadorNota>,
    ) {
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE)?.setGeoJson(features(marcadores))
    }

    /** La posición de la nota pintada bajo el toque en [x], [y] (píxeles), con un margen cómodo para el dedo. */
    fun posicionEn(
        mapa: MapLibreMap,
        x: Float,
        y: Float,
        densidad: Float,
    ): Double? {
        val margen = TOLERANCIA_TOQUE_DP * densidad
        val zona = RectF(x - margen, y - margen, x + margen, y + margen)
        return mapa
            .queryRenderedFeatures(zona, ID_CAPA)
            .firstOrNull()
            ?.getNumberProperty(PROPIEDAD_POSICION)
            ?.toDouble()
    }

    private fun features(marcadores: List<MarcadorNota>): FeatureCollection =
        FeatureCollection.fromFeatures(
            marcadores.map { m ->
                Feature.fromGeometry(Point.fromLngLat(m.lon, m.lat)).also {
                    it.addNumberProperty(PROPIEDAD_POSICION, m.posicionM)
                    it.addStringProperty(PROPIEDAD_COLOR, if (m.cuidado) COLOR_CUIDADO else COLOR_NOTA)
                }
            },
        )
}
