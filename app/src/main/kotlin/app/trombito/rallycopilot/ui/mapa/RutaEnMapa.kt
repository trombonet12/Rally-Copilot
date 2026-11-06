package app.trombito.rallycopilot.ui.mapa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import app.trombito.rallycopilot.domain.navegacion.EdicionParadas
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.navegacion.MarcadorParada
import app.trombito.rallycopilot.ui.theme.DarkColors
import app.trombito.rallycopilot.ui.theme.RojoRuta
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
    /** Paradas intermedias con su número (círculo carbón con borde y cifra latón); vacío = ninguna. */
    val paradas: List<MarcadorParada> = emptyList(),
    /** Punto elegido manteniendo pulsado el mapa (tarjeta abierta); null = ninguno. */
    val elegido: PuntoGeo? = null,
    /** Metros ya recorridos de la ruta guiada: esa parte no se pinta en rojo (la cubre la estela); 0 = ruta entera. */
    val recorridoM: Double = 0.0,
)

private const val ID_FUENTE = "ruta-fuente"
private const val ID_BORDE = "ruta-borde"
private const val ID_LINEA = "ruta-linea"
private const val ID_FUENTE_DESTINO = "ruta-destino-fuente"
private const val ID_DESTINO_HALO = "ruta-destino-halo"
private const val ID_DESTINO = "ruta-destino"
private const val ID_FUENTE_PARADAS = "ruta-paradas-fuente"
private const val ID_PARADAS = "ruta-paradas"
private const val ID_FUENTE_ELEGIDO = "punto-elegido-fuente"
private const val ID_ELEGIDO_HALO = "punto-elegido-halo"
private const val ID_ELEGIDO = "punto-elegido"
private const val PREFIJO_IMAGEN_PARADA = "parada-"
private const val PROPIEDAD_IMAGEN = "icono"
private const val RADIO_ELEGIDO = 9f
private const val RADIO_HALO_ELEGIDO = 15f
private const val LADO_PARADA_DP = 32f
private const val BORDE_PARADA_DP = 3f
private const val TEXTO_PARADA_DP = 16f
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
internal const val DURACION_ENCUADRE_MS = 800

// Margen del encuadre: deja sitio a la barra de arriba y al panel de abajo (dp, con tope relativo al alto del mapa).
private const val ENCUADRE_ARRIBA_DP = 128f
private const val ENCUADRE_ABAJO_DP = 260f
private const val ENCUADRE_LATERAL_DP = 40f
private const val ENCUADRE_MAXIMO_ARRIBA = 0.25f
private const val ENCUADRE_MAXIMO_ABAJO = 0.4f
private const val ENCUADRE_MAXIMO_ABAJO_PARADAS = 0.55f
private const val ENCUADRE_FILA_PARADA_DP = 48f
private const val ENCUADRE_FILAS_MAXIMAS = 3

/**
 * Línea de la ruta: un `GeoJsonSource` con dos `LineLayer` (borde oscuro debajo, color de marca encima). Se coloca
 * por encima de las vías y por debajo de las etiquetas (justo antes de la primera capa de símbolos del estilo), y
 * antes del componente de ubicación, que se activa después y queda encima. Se vuelve a instalar al cargar cada
 * estilo; la fuente siempre existe (vacía sin ruta), así que cambiar la ruta solo cambia sus datos.
 */
internal object CapaRuta {
    fun instalar(
        estilo: Style,
        contexto: Context,
        ruta: RutaEnMapa?,
    ) {
        estilo.addSource(GeoJsonSource(ID_FUENTE, lineaGeoJson(puntosPorDelanteDe(ruta))))
        estilo.addSource(GeoJsonSource(ID_FUENTE_DESTINO, puntoGeoJson(ruta?.destino)))
        estilo.addSource(GeoJsonSource(ID_FUENTE_PARADAS, paradasGeoJson(ruta?.paradas.orEmpty())))
        estilo.addSource(GeoJsonSource(ID_FUENTE_ELEGIDO, puntoGeoJson(ruta?.elegido)))
        for (numero in 1..EdicionParadas.MAX_PARADAS) {
            estilo.addImage(
                imagenParada(numero),
                insigniaParada(contexto, numero),
            )
        }
        val borde = capa(ID_BORDE, DarkColors.surfaceContainerLowest.toArgb(), EXTRA_BORDE)
        val linea = capa(ID_LINEA, COLOR_RUTA, 0f)
        val etiquetas = estilo.layers.firstOrNull { it is SymbolLayer }?.id
        val halo = capaDestino(ID_DESTINO_HALO, Color.WHITE, RADIO_HALO)
        val punto = capaDestino(ID_DESTINO, DarkColors.tertiary.toArgb(), RADIO_DESTINO)
        listOf(borde, linea, halo, punto).forEach { capa ->
            if (etiquetas != null) estilo.addLayerBelow(capa, etiquetas) else estilo.addLayer(capa)
        }
        // Paradas numeradas y punto elegido: encima de las etiquetas, debajo de las incidencias y de la posición.
        estilo.addLayer(
            SymbolLayer(ID_PARADAS, ID_FUENTE_PARADAS).withProperties(
                PropertyFactory.iconImage(Expression.get(PROPIEDAD_IMAGEN)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        estilo.addLayer(capaElegido(ID_ELEGIDO_HALO, Color.WHITE, RADIO_HALO_ELEGIDO, 0f))
        estilo.addLayer(capaElegido(ID_ELEGIDO, DarkColors.primary.toArgb(), RADIO_ELEGIDO, GROSOR_HALO))
    }

    private fun capaElegido(
        id: String,
        color: Int,
        radio: Float,
        grosorBorde: Float,
    ): CircleLayer =
        CircleLayer(id, ID_FUENTE_ELEGIDO).withProperties(
            PropertyFactory.circleColor(color),
            PropertyFactory.circleRadius(radio),
            PropertyFactory.circleStrokeColor(DarkColors.surfaceContainerLowest.toArgb()),
            PropertyFactory.circleStrokeWidth(grosorBorde),
        )

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
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE)?.setGeoJson(lineaGeoJson(puntosPorDelanteDe(ruta)))
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_DESTINO)?.setGeoJson(puntoGeoJson(ruta?.destino))
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_PARADAS)?.setGeoJson(paradasGeoJson(ruta?.paradas.orEmpty()))
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_ELEGIDO)?.setGeoJson(puntoGeoJson(ruta?.elegido))
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

/** Rojo de la marca de la línea de la ruta (distinto del latón de la flecha de posición). */
internal val COLOR_RUTA: Int = RojoRuta.toArgb()

private fun puntosPorDelanteDe(ruta: RutaEnMapa?): List<PuntoGeo> =
    ruta?.let { puntosPorDelante(it.puntos, it.recorridoM) }.orEmpty()

/**
 * La ruta desde [recorridoM] metros del inicio hasta el final (el primer punto es el de corte, interpolado). Con 0 o
 * menos, la ruta entera; pasado el final, un solo punto (nada que pintar).
 */
internal fun puntosPorDelante(
    puntos: List<PuntoGeo>,
    recorridoM: Double,
): List<PuntoGeo> =
    if (recorridoM <= 0.0 || puntos.size < 2) {
        puntos
    } else {
        cortarEn(puntos, recorridoM) ?: puntos.takeLast(1) // pasado el final no queda nada que pintar
    }

/** La ruta desde [recorridoM] metros (punto de corte interpolado primero); null si la ruta es más corta. */
private fun cortarEn(
    puntos: List<PuntoGeo>,
    recorridoM: Double,
): List<PuntoGeo>? {
    var acumulado = 0.0
    for (i in 1 until puntos.size) {
        val a = puntos[i - 1]
        val b = puntos[i]
        val tramo = a.distanciaA(b)
        if (acumulado + tramo > recorridoM) {
            val fraccion = if (tramo > 0.0) (recorridoM - acumulado) / tramo else 0.0
            val corte = PuntoGeo(a.lat + (b.lat - a.lat) * fraccion, a.lon + (b.lon - a.lon) * fraccion)
            return listOf(corte) + puntos.subList(i, puntos.size)
        }
        acumulado += tramo
    }
    return null
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

private fun imagenParada(numero: Int): String = PREFIJO_IMAGEN_PARADA + numero

/** Un punto por parada con el nombre de la imagen de su número: lo que se pinta en el mapa. */
internal fun paradasGeoJson(paradas: List<MarcadorParada>): FeatureCollection =
    FeatureCollection.fromFeatures(
        paradas.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.punto.lon, p.punto.lat)).also {
                it.addStringProperty(PROPIEDAD_IMAGEN, imagenParada(p.numero.coerceIn(1, EdicionParadas.MAX_PARADAS)))
            }
        },
    )

/** Insignia redonda de parada: fondo carbón, borde latón y el número en latón. */
private fun insigniaParada(
    contexto: Context,
    numero: Int,
): Bitmap {
    val densidad = contexto.resources.displayMetrics.density
    val lado = (LADO_PARADA_DP * densidad).toInt()
    val borde = BORDE_PARADA_DP * densidad
    val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(bitmap)
    val radio = lado / 2f
    val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    pincel.color = FONDO_PARADA
    lienzo.drawCircle(radio, radio, radio - borde / 2, pincel)
    pincel.style = Paint.Style.STROKE
    pincel.strokeWidth = borde
    pincel.color = LATON_PARADA
    lienzo.drawCircle(radio, radio, radio - borde / 2, pincel)
    pincel.style = Paint.Style.FILL
    pincel.textAlign = Paint.Align.CENTER
    pincel.isFakeBoldText = true
    pincel.textSize = TEXTO_PARADA_DP * densidad
    val centroVertical = radio - (pincel.descent() + pincel.ascent()) / 2
    lienzo.drawText(numero.toString(), radio, centroVertical, pincel)
    return bitmap
}

private const val FONDO_PARADA = 0xF00E0F12.toInt()
private const val LATON_PARADA = 0xFFE5AB46.toInt()

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
    paradas: Int = 0,
) {
    val limites = limitesDe(puntos) ?: return
    val alto = altoPx.toFloat()
    val arriba = minOf(ENCUADRE_ARRIBA_DP * densidad, alto * ENCUADRE_MAXIMO_ARRIBA).toInt()
    // Cada parada (hasta tres filas visibles) añade su fila a la lista del panel de la vista previa.
    val dpAbajo = ENCUADRE_ABAJO_DP + ENCUADRE_FILA_PARADA_DP * minOf(paradas, ENCUADRE_FILAS_MAXIMAS)
    val tope = if (paradas == 0) ENCUADRE_MAXIMO_ABAJO else ENCUADRE_MAXIMO_ABAJO_PARADAS
    val abajo = minOf(dpAbajo * densidad, alto * tope).toInt()
    val lado = (ENCUADRE_LATERAL_DP * densidad).toInt()
    easeCamera(CameraUpdateFactory.newLatLngBounds(limites, 0.0, 0.0, lado, arriba, lado, abajo), DURACION_ENCUADRE_MS)
}
