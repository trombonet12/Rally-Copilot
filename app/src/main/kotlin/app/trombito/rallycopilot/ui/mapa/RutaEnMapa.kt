package app.trombito.rallycopilot.ui.mapa

import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.theme.DarkColors
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** La ruta que se dibuja en el mapa (vista previa o guiado). [encuadrar]: la cámara abarca la ruta entera. */
data class RutaEnMapa(
    val puntos: List<PuntoGeo>,
    val encuadrar: Boolean = false,
    /** Marcador del destino (círculo rojo de la marca con borde); null = sin marcador. */
    val destino: PuntoGeo? = null,
)

private const val ID_FUENTE = "ruta-fuente"
private const val ID_BORDE = "ruta-borde"
private const val ID_LINEA = "ruta-linea"
private const val ID_FUENTE_DESTINO = "ruta-destino-fuente"
private const val ID_DESTINO_HALO = "ruta-destino-halo"
private const val ID_DESTINO = "ruta-destino"
private const val RADIO_HALO = 13f
private const val RADIO_DESTINO = 8f
private const val GROSOR_HALO = 3f
private const val EXPONENTE_ANCHO = 1.5f
private const val ZOOM_ANCHO_MINIMO = 8
private const val ZOOM_ANCHO_MEDIO = 14
private const val ZOOM_ANCHO_MAXIMO = 18
private const val ANCHO_LINEA_MINIMO = 3f
private const val ANCHO_LINEA_MEDIO = 6f
private const val ANCHO_LINEA_MAXIMO = 12f
private const val EXTRA_BORDE = 4f
private const val DURACION_ENCUADRE_MS = 800

// Margen del encuadre: deja sitio a la barra de arriba y al panel de abajo (dp, con tope relativo al alto del mapa).
private const val ENCUADRE_ARRIBA_DP = 128f
private const val ENCUADRE_ABAJO_DP = 220f
private const val ENCUADRE_LATERAL_DP = 40f
private const val ENCUADRE_MAXIMO_ARRIBA = 0.25f
private const val ENCUADRE_MAXIMO_ABAJO = 0.4f

/**
 * Línea de la ruta: un `GeoJsonSource` con dos `LineLayer` (borde oscuro debajo, color de marca encima). Se coloca
 * por encima de las vías y por debajo de las etiquetas (justo antes de la primera capa de símbolos del estilo), y
 * antes del componente de ubicación, que se activa después y queda encima. Se vuelve a instalar al cargar cada
 * estilo; la fuente siempre existe (vacía sin ruta), así que cambiar la ruta solo cambia sus datos.
 */
internal object CapaRuta {
    fun instalar(
        estilo: Style,
        ruta: RutaEnMapa?,
    ) {
        estilo.addSource(GeoJsonSource(ID_FUENTE, lineaGeoJson(ruta?.puntos.orEmpty())))
        estilo.addSource(GeoJsonSource(ID_FUENTE_DESTINO, puntoGeoJson(ruta?.destino)))
        val borde = capa(ID_BORDE, DarkColors.surfaceContainerLowest.toArgb(), EXTRA_BORDE)
        val linea = capa(ID_LINEA, DarkColors.primary.toArgb(), 0f)
        val etiquetas = estilo.layers.firstOrNull { it is SymbolLayer }?.id
        val halo = capaDestino(ID_DESTINO_HALO, Color.WHITE, RADIO_HALO)
        val punto = capaDestino(ID_DESTINO, DarkColors.tertiary.toArgb(), RADIO_DESTINO)
        listOf(borde, linea, halo, punto).forEach { capa ->
            if (etiquetas != null) estilo.addLayerBelow(capa, etiquetas) else estilo.addLayer(capa)
        }
    }

    private fun capaDestino(
        id: String,
        color: Int,
        radio: Float,
    ): CircleLayer =
        CircleLayer(id, ID_FUENTE_DESTINO).withProperties(
            PropertyFactory.circleColor(color),
            PropertyFactory.circleRadius(radio),
            PropertyFactory.circleStrokeColor(DarkColors.surfaceContainerLowest.toArgb()),
            PropertyFactory.circleStrokeWidth(GROSOR_HALO),
        )

    fun actualizar(
        estilo: Style,
        ruta: RutaEnMapa?,
    ) {
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE)?.setGeoJson(lineaGeoJson(ruta?.puntos.orEmpty()))
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_DESTINO)?.setGeoJson(puntoGeoJson(ruta?.destino))
    }

    private fun capa(
        id: String,
        color: Int,
        extra: Float,
    ): LineLayer =
        LineLayer(id, ID_FUENTE).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineWidth(
                Expression.interpolate(
                    Expression.exponential(EXPONENTE_ANCHO),
                    Expression.zoom(),
                    Expression.stop(ZOOM_ANCHO_MINIMO, ANCHO_LINEA_MINIMO + extra),
                    Expression.stop(ZOOM_ANCHO_MEDIO, ANCHO_LINEA_MEDIO + extra),
                    Expression.stop(ZOOM_ANCHO_MAXIMO, ANCHO_LINEA_MAXIMO + extra),
                ),
            ),
        )
}

/** Una línea (o nada con menos de dos puntos) lista para un `GeoJsonSource`. */
internal fun lineaGeoJson(puntos: List<PuntoGeo>): FeatureCollection =
    if (puntos.size < 2) {
        FeatureCollection.fromFeatures(emptyList<Feature>())
    } else {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(LineString.fromLngLats(puntos.map { Point.fromLngLat(it.lon, it.lat) })),
        )
    }

/** Un punto (o nada) listo para un `GeoJsonSource`. */
internal fun puntoGeoJson(punto: PuntoGeo?): FeatureCollection =
    if (punto == null) {
        FeatureCollection.fromFeatures(emptyList<Feature>())
    } else {
        FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(punto.lon, punto.lat)))
    }

/** Rectángulo que abarca todos los puntos; null con menos de dos. */
internal fun limitesDe(puntos: List<PuntoGeo>): LatLngBounds? {
    if (puntos.size < 2) return null
    return LatLngBounds.from(
        puntos.maxOf { it.lat },
        puntos.maxOf { it.lon },
        puntos.minOf { it.lat },
        puntos.minOf { it.lon },
    )
}

/** Anima la cámara hasta abarcar la ruta entera; [altoPx] es el alto de la vista (0 si aún no se midió). */
internal fun MapLibreMap.encuadrarRuta(
    puntos: List<PuntoGeo>,
    densidad: Float,
    altoPx: Int,
) {
    val limites = limitesDe(puntos) ?: return
    val alto = altoPx.toFloat()
    val arriba = minOf(ENCUADRE_ARRIBA_DP * densidad, alto * ENCUADRE_MAXIMO_ARRIBA).toInt()
    val abajo = minOf(ENCUADRE_ABAJO_DP * densidad, alto * ENCUADRE_MAXIMO_ABAJO).toInt()
    val lado = (ENCUADRE_LATERAL_DP * densidad).toInt()
    easeCamera(CameraUpdateFactory.newLatLngBounds(limites, lado, arriba, lado, abajo), DURACION_ENCUADRE_MS)
}
