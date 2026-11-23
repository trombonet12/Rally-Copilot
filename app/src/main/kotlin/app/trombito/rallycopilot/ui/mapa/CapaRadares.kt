package app.trombito.rallycopilot.ui.mapa

import android.content.Context
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.alertas.ClaseRadar
import app.trombito.rallycopilot.ui.alertas.MarcadorRadar
import app.trombito.rallycopilot.ui.alertas.RadaresUi
import app.trombito.rallycopilot.ui.alertas.TramoRadarMapa
import app.trombito.rallycopilot.ui.alertas.iconoRadar
import app.trombito.rallycopilot.ui.alertas.imagenRadar
import app.trombito.rallycopilot.ui.theme.RallyOscuro
import org.maplibre.android.geometry.LatLng
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

private const val ID_FUENTE_RADARES = "radares-fuente"
private const val ID_CAPA_RADARES = "radares-capa"
private const val ID_FUENTE_TRAMOS = "tramos-radar-fuente"
private const val ID_CAPA_TRAMOS = "tramos-radar-capa"

/** Los marcadores de radar se ven desde este zoom (la isla entera cabe en ~8–9). */
internal const val ZOOM_MIN_RADARES = 10f

/** El segmento de un tramo de radar se ve desde este zoom. */
internal const val ZOOM_MIN_TRAMOS_RADAR = 11f
private const val ZOOM_CERCA = 15
private const val TAMANO_LEJOS = 0.6f
private const val TAMANO_CERCA = 1f
private const val ANCHO_TRAMO_LEJOS = 2.5f
private const val ANCHO_TRAMO_CERCA = 5f
private const val GUION_TRAMO = 2f
private const val HUECO_TRAMO = 1.5f
internal const val PROPIEDAD_RADAR_ICONO = "icono"

/**
 * Radares siempre visibles en el mapa: insignia redonda (carbón, borde ámbar) con el icono de la clase (fijo, móvil,
 * tramo) y, para cada radar de tramo, el segmento entrada→salida en ámbar discontinuo (sobre la vía si hay geometría).
 * Las dos fuentes siempre existen (vacías sin datos) y cambiar los radares solo cambia sus datos; se vuelve a instalar
 * al cargar cada estilo. Los marcadores quedan sobre el segmento y debajo de las incidencias (que se instalan después).
 */
internal object CapaRadares {
    fun instalar(
        estilo: Style,
        contexto: Context,
        radares: RadaresUi,
    ) {
        val ambar = RallyOscuro.aviso.toArgb()
        ClaseRadar.entries.forEach { estilo.addImage(imagenRadar(it), insignia(contexto, iconoRadar(it), ambar)) }
        estilo.addSource(GeoJsonSource(ID_FUENTE_TRAMOS, featuresTramos(radares.tramos)))
        val tramos =
            LineLayer(ID_CAPA_TRAMOS, ID_FUENTE_TRAMOS).withProperties(
                PropertyFactory.lineColor(ambar),
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineDasharray(arrayOf(GUION_TRAMO, HUECO_TRAMO)),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(ZOOM_MIN_TRAMOS_RADAR.toInt(), ANCHO_TRAMO_LEJOS),
                        Expression.stop(ZOOM_CERCA, ANCHO_TRAMO_CERCA),
                    ),
                ),
            )
        tramos.minZoom = ZOOM_MIN_TRAMOS_RADAR
        estilo.addLayer(tramos)
        estilo.addSource(GeoJsonSource(ID_FUENTE_RADARES, featuresRadares(radares.marcadores)))
        val marcadores =
            SymbolLayer(ID_CAPA_RADARES, ID_FUENTE_RADARES).withProperties(
                PropertyFactory.iconImage(Expression.get(PROPIEDAD_RADAR_ICONO)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(ZOOM_MIN_RADARES.toInt(), TAMANO_LEJOS),
                        Expression.stop(ZOOM_CERCA, TAMANO_CERCA),
                    ),
                ),
            )
        marcadores.minZoom = ZOOM_MIN_RADARES
        estilo.addLayer(marcadores)
    }

    fun actualizar(
        estilo: Style,
        radares: RadaresUi,
    ) {
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_TRAMOS)?.setGeoJson(featuresTramos(radares.tramos))
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_RADARES)?.setGeoJson(featuresRadares(radares.marcadores))
    }

    /** El id del marcador de radar pintado bajo el toque en [x], [y] (píxeles), con un margen cómodo para el dedo. */
    fun radarEn(
        mapa: MapLibreMap,
        x: Float,
        y: Float,
        densidad: Float,
    ): String? =
        mapa
            .queryRenderedFeatures(zonaDeToque(x, y, densidad), ID_CAPA_RADARES)
            .firstOrNull()
            ?.getStringProperty(PROPIEDAD_ID)

    /** El id del tramo de radar cuya línea está pintada bajo el toque. */
    fun tramoEn(
        mapa: MapLibreMap,
        x: Float,
        y: Float,
        densidad: Float,
    ): String? =
        mapa
            .queryRenderedFeatures(zonaDeToque(x, y, densidad), ID_CAPA_TRAMOS)
            .firstOrNull()
            ?.getStringProperty(PROPIEDAD_ID)

    /**
     * Como [radarEn] pero midiendo en pantalla la distancia a los [marcadores] (no depende de lo que la capa tenga
     * colocado en ese instante); solo si el zoom ya muestra los marcadores.
     */
    fun radarCercano(
        mapa: MapLibreMap,
        marcadores: List<MarcadorRadar>,
        x: Float,
        y: Float,
        densidad: Float,
    ): String? =
        if (mapa.cameraPosition.zoom < ZOOM_MIN_RADARES) {
            null
        } else {
            masCercano(marcadores, x, y, MARGEN_TOQUE_DP * densidad) {
                mapa.projection.toScreenLocation(LatLng(it.lat, it.lon)).let { p -> p.x to p.y }
            }?.id
        }

    /** Como [tramoEn] pero midiendo la distancia en pantalla a la línea de cada tramo; el más cercano al dedo. */
    fun tramoCercano(
        mapa: MapLibreMap,
        tramos: List<TramoRadarMapa>,
        x: Float,
        y: Float,
        densidad: Float,
    ): String? =
        if (mapa.cameraPosition.zoom < ZOOM_MIN_TRAMOS_RADAR) {
            null
        } else {
            tramos
                .map { t -> t.id to distanciaALinea(t.puntos.map { pantalla(mapa, it) }, x, y) }
                .filter { it.second <= MARGEN_TOQUE_DP * densidad }
                .minByOrNull { it.second }
                ?.first
        }

    private fun pantalla(
        mapa: MapLibreMap,
        punto: PuntoGeo,
    ): Pair<Float, Float> = mapa.projection.toScreenLocation(LatLng(punto.lat, punto.lon)).let { it.x to it.y }
}

/** El cuadrado de [MARGEN_TOQUE_DP] alrededor del dedo en el que se buscan elementos pintados. */
internal fun zonaDeToque(
    x: Float,
    y: Float,
    densidad: Float,
): RectF {
    val margen = MARGEN_TOQUE_DP * densidad
    return RectF(x - margen, y - margen, x + margen, y + margen)
}

/** Un punto por radar con el nombre de la imagen de su clase. */
internal fun featuresRadares(marcadores: List<MarcadorRadar>): FeatureCollection =
    FeatureCollection.fromFeatures(
        marcadores.map { m ->
            Feature.fromGeometry(Point.fromLngLat(m.lon, m.lat)).also {
                it.addStringProperty(PROPIEDAD_ID, m.id)
                it.addStringProperty(PROPIEDAD_RADAR_ICONO, imagenRadar(m.clase))
            }
        },
    )

/** Una línea por tramo con al menos dos puntos. */
internal fun featuresTramos(tramos: List<TramoRadarMapa>): FeatureCollection =
    FeatureCollection.fromFeatures(
        tramos
            .filter { it.puntos.size >= 2 }
            .map { t ->
                Feature.fromGeometry(LineString.fromLngLats(t.puntos.map { Point.fromLngLat(it.lon, it.lat) })).also {
                    it.addStringProperty(PROPIEDAD_ID, t.id)
                }
            },
    )
