package app.trombito.rallycopilot.ui.mapa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import app.trombito.rallycopilot.motor.tramos.TramoEnCamino
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
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

private const val ID_FUENTE_LINEAS = "tramos-camino-fuente"
private const val ID_CAPA_LINEAS = "tramos-camino-linea"
private const val ID_FUENTE_BANDERAS = "tramos-banderas-fuente"
private const val ID_CAPA_BANDERAS = "tramos-banderas"
internal const val IMAGEN_BANDERA_SALIDA = "tramos-bandera-salida"
internal const val IMAGEN_BANDERA_META = "tramos-bandera-meta"
internal const val PROPIEDAD_BANDERA = "bandera"

/** La línea de los tramos se ve desde este zoom y las banderas desde [ZOOM_BANDERAS]. */
private const val ZOOM_LINEAS = 10f
private const val ZOOM_BANDERAS = 12f
private const val EXPONENTE_ANCHO = 1.5f
private const val ZOOM_ANCHO_MINIMO = 8
private const val ZOOM_ANCHO_MEDIO = 14
private const val ZOOM_ANCHO_MAXIMO = 18

/** Más ancha que el borde de la ruta (7 / 10 / 16 px): bajo la ruta roja asoma un ribete ámbar a cada lado. */
private const val ANCHO_LINEA_MINIMO = 10f
private const val ANCHO_LINEA_MEDIO = 17f
private const val ANCHO_LINEA_MAXIMO = 28f
internal const val OPACIDAD_TRAMOS = 0.6f

private const val ZOOM_BANDERA_LEJOS = 12
private const val ZOOM_BANDERA_CERCA = 16
private const val TAMANO_BANDERA_LEJOS = 0.7f
private const val TAMANO_BANDERA_CERCA = 1f
private const val LADO_BANDERA_DP = 44f

/**
 * Tramos conocidos por delante en el camino (guiado o libre): una línea ámbar translúcida sobre la carretera de cada
 * tramo (más ancha que la ruta: bajo ella queda un ribete ámbar) y, solo donde el camino pasa por la salida o la meta
 * reales del tramo, la bandera verde de salida o la de cuadros de meta (con el mástil clavado en el punto). Las dos
 * fuentes siempre existen (vacías sin tramos) y cambiar los tramos solo cambia sus datos; se vuelve a instalar al
 * cargar cada estilo. Se instala antes que `CapaRuta`: la línea queda bajo la ruta y bajo las etiquetas; las banderas,
 * encima de la ruta y por debajo de radares, incidencias, notas y el coche.
 */
internal object CapaTramos {
    fun instalar(
        estilo: Style,
        contexto: Context,
        tramos: List<TramoEnCamino>,
    ) {
        val densidad = contexto.resources.displayMetrics.density
        val lado = (LADO_BANDERA_DP * densidad).toInt().coerceAtLeast(1)
        estilo.addImage(IMAGEN_BANDERA_SALIDA, dibujarBandera(lado, meta = false))
        estilo.addImage(IMAGEN_BANDERA_META, dibujarBandera(lado, meta = true))
        estilo.addSource(GeoJsonSource(ID_FUENTE_LINEAS, featuresLineasTramos(tramos)))
        val linea =
            LineLayer(ID_CAPA_LINEAS, ID_FUENTE_LINEAS).withProperties(
                PropertyFactory.lineColor(AmbarSobreCarbon.toArgb()),
                PropertyFactory.lineOpacity(OPACIDAD_TRAMOS),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.exponential(EXPONENTE_ANCHO),
                        Expression.zoom(),
                        Expression.stop(ZOOM_ANCHO_MINIMO, ANCHO_LINEA_MINIMO),
                        Expression.stop(ZOOM_ANCHO_MEDIO, ANCHO_LINEA_MEDIO),
                        Expression.stop(ZOOM_ANCHO_MAXIMO, ANCHO_LINEA_MAXIMO),
                    ),
                ),
            )
        linea.minZoom = ZOOM_LINEAS
        val etiquetas = estilo.layers.firstOrNull { it is SymbolLayer }?.id
        if (etiquetas != null) estilo.addLayerBelow(linea, etiquetas) else estilo.addLayer(linea)
        estilo.addSource(GeoJsonSource(ID_FUENTE_BANDERAS, featuresBanderasTramos(tramos)))
        val banderas =
            SymbolLayer(ID_CAPA_BANDERAS, ID_FUENTE_BANDERAS).withProperties(
                PropertyFactory.iconImage(Expression.get(PROPIEDAD_BANDERA)),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM_LEFT),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(ZOOM_BANDERA_LEJOS, TAMANO_BANDERA_LEJOS),
                        Expression.stop(ZOOM_BANDERA_CERCA, TAMANO_BANDERA_CERCA),
                    ),
                ),
            )
        banderas.minZoom = ZOOM_BANDERAS
        estilo.addLayer(banderas)
    }

    fun actualizar(
        estilo: Style,
        tramos: List<TramoEnCamino>,
    ) {
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_LINEAS)?.setGeoJson(featuresLineasTramos(tramos))
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_BANDERAS)?.setGeoJson(featuresBanderasTramos(tramos))
    }
}

/** Una línea por trozo de tramo con al menos dos puntos. */
internal fun featuresLineasTramos(tramos: List<TramoEnCamino>): FeatureCollection =
    FeatureCollection.fromFeatures(
        tramos
            .filter { it.geometria.size >= 2 }
            .map(::lineaDe),
    )

private fun lineaDe(tramo: TramoEnCamino): Feature =
    Feature.fromGeometry(LineString.fromLngLats(tramo.geometria.map { Point.fromLngLat(it.lon, it.lat) })).also {
        it.addStringProperty(PROPIEDAD_ID, tramo.tramoId)
    }

/**
 * Las banderas: la verde en la salida de los trozos que empiezan en la salida real del tramo y la de cuadros en la meta
 * de los que acaban en su meta real. Un trozo recortado por el camino (entra o sale por mitad) no lleva bandera en el
 * extremo recortado.
 */
internal fun featuresBanderasTramos(tramos: List<TramoEnCamino>): FeatureCollection =
    FeatureCollection.fromFeatures(
        tramos.flatMap(::banderasDe),
    )

private fun banderasDe(t: TramoEnCamino): List<Feature> =
    listOfNotNull(
        if (t.empiezaEnSalida) bandera(t, IMAGEN_BANDERA_SALIDA, t.salida.lon, t.salida.lat) else null,
        if (t.acabaEnMeta) bandera(t, IMAGEN_BANDERA_META, t.meta.lon, t.meta.lat) else null,
    )

private fun bandera(
    tramo: TramoEnCamino,
    imagen: String,
    lon: Double,
    lat: Double,
): Feature =
    Feature.fromGeometry(Point.fromLngLat(lon, lat)).also {
        it.addStringProperty(PROPIEDAD_ID, tramo.tramoId)
        it.addStringProperty(PROPIEDAD_BANDERA, imagen)
    }

private const val COLOR_MASTIL = 0xFF0E0F12.toInt()
private const val COLOR_CONTORNO = 0xFFFFFFFF.toInt()
private const val COLOR_VERDE = 0xFF2FB344.toInt()
private const val COLOR_CUADRO_CLARO = 0xFFFFFFFF.toInt()
private const val COLOR_CUADRO_OSCURO = 0xFF0E0F12.toInt()
private const val FILAS_CUADROS = 3
private const val COLUMNAS_CUADROS = 4

/**
 * Bandera de [lado] px (cuadrada): el mástil carbón con contorno blanco pegado al borde izquierdo y abajo (el punto del
 * mapa, con el ancla abajo-izquierda) y la tela, ondeada, en la parte alta: verde lisa (salida) o de cuadros
 * blanco y carbón (meta). El contorno blanco la hace legible sobre el mapa oscuro, el claro y el satélite.
 */
internal fun dibujarBandera(
    lado: Int,
    meta: Boolean,
): Bitmap {
    val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(bitmap)
    val l = lado.toFloat()
    val grosorMastil = l * 0.09f
    val contorno = l * 0.04f
    val relleno = Paint(Paint.ANTI_ALIAS_FLAG)
    val borde =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = contorno * 2
            color = COLOR_CONTORNO
            strokeJoin = Paint.Join.ROUND
        }
    // Tela: de la altura 0,06 a 0,56 del lado, desde el mástil hacia la derecha, con el borde libre ondulado.
    val x0 = grosorMastil
    val x1 = l * 0.96f
    val arriba = l * 0.06f
    val abajo = l * 0.56f
    val onda = l * 0.05f
    val tela =
        Path().apply {
            moveTo(x0, arriba)
            cubicTo(x0 + (x1 - x0) * 0.35f, arriba - onda, x0 + (x1 - x0) * 0.65f, arriba + onda, x1, arriba)
            lineTo(x1, abajo)
            cubicTo(x0 + (x1 - x0) * 0.65f, abajo + onda, x0 + (x1 - x0) * 0.35f, abajo - onda, x0, abajo)
            close()
        }
    val mastil = Path().apply { addRect(0f, arriba - contorno, grosorMastil, l, Path.Direction.CW) }
    lienzo.drawPath(mastil, borde)
    lienzo.drawPath(tela, borde)
    relleno.color = COLOR_MASTIL
    lienzo.drawPath(mastil, relleno)
    if (meta) {
        relleno.color = COLOR_CUADRO_CLARO
        lienzo.drawPath(tela, relleno)
        dibujarCuadros(lienzo, tela, RectF(x0, arriba - onda, x1, abajo + onda), relleno)
    } else {
        relleno.color = COLOR_VERDE
        lienzo.drawPath(tela, relleno)
    }
    return bitmap
}

/** Los cuadros oscuros de la bandera de meta, recortados a la [tela] y repartidos en [zona] como un tablero. */
private fun dibujarCuadros(
    lienzo: Canvas,
    tela: Path,
    zona: RectF,
    pincel: Paint,
) {
    lienzo.save()
    lienzo.clipPath(tela)
    pincel.color = COLOR_CUADRO_OSCURO
    val ancho = zona.width() / COLUMNAS_CUADROS
    val alto = zona.height() / FILAS_CUADROS
    val oscuros = (0 until FILAS_CUADROS).flatMap { f -> (0 until COLUMNAS_CUADROS).map { c -> f to c } }
    oscuros.filter { (f, c) -> (f + c) % 2 != 0 }.forEach { (f, c) ->
        val izq = zona.left + c * ancho
        val sup = zona.top + f * alto
        lienzo.drawRect(izq, sup, izq + ancho, sup + alto, pincel)
    }
    lienzo.restore()
}
