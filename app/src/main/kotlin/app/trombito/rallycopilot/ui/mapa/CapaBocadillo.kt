package app.trombito.rallycopilot.ui.mapa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.ui.alertas.BocadilloMapa
import app.trombito.rallycopilot.ui.alertas.ContenidoBocadillo
import app.trombito.rallycopilot.ui.alertas.iconoBocadillo
import app.trombito.rallycopilot.ui.alertas.lineasDetalleBocadillo
import app.trombito.rallycopilot.ui.alertas.textoBocadillo
import app.trombito.rallycopilot.ui.theme.RallyOscuro
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

private const val ID_FUENTE = "bocadillo-fuente"
private const val ID_CAPA = "bocadillo-capa"
private const val PROPIEDAD_IMAGEN = "imagen"

/** Sube el bocadillo sobre la insignia del marcador (que mide 40 dp). */
private const val SUBIDA_PX = 26f
private const val ALTO_DP = 38f
private const val RELLENO_DP = 10f
private const val RELLENO_TITULO_DP = 8f
private const val ICONO_DP = 22f
private const val HUECO_DP = 8f
private const val PICO_DP = 9f
private const val BORDE_DP = 2f
private const val RADIO_DP = 8f
private const val TEXTO_SP = 16f
private const val DETALLE_SP = 13f
private const val ANCHO_MAXIMO_DP = 290f
private const val HUECO_LINEAS_DP = 2f
private const val HUECO_TITULO_DP = 4f
private const val BASE_DETALLE_DP = 8f
private const val LINEAS_TITULO = 2
private const val LINEAS_DETALLE = 3
private const val COLOR_DETALLE = 0xFFE6E6E6.toInt()
private const val FONDO = 0xEB101114.toInt()

/** Lo que el bocadillo no puede pisar: el borde izquierdo, la columna de botones (derecha) y la franja de arriba. */
private const val MARGEN_IZQUIERDO_DP = 8f
private const val MARGEN_DERECHO_DP = 76f
private const val MARGEN_SUPERIOR_DP = 56f
private const val MARGEN_PICO_DP = 18f
private const val PASO_PICO_DP = 8f

/**
 * Dónde está el marcador del bocadillo en la pantalla del mapa ([xPx], [yPx]) y cuánto mide la pantalla: con ello el
 * bocadillo se limita al ancho libre (sin salirse ni quedar bajo los botones) y se desplaza o se pone debajo.
 */
internal data class EntornoBocadillo(
    val anchoPantallaPx: Int,
    val xPx: Float,
    val yPx: Float,
)

/**
 * Bocadillo anclado al marcador de un radar, incidencia, bandera o nota: un único punto en una `SymbolLayer` cuya
 * imagen (rectángulo carbón translúcido con borde ámbar, icono, título, detalle y un piquito hacia el marcador) se
 * dibuja con Canvas al cambiar el contenido. Así queda pegado a la coordenada al mover el mapa sin proyectar nada. Va
 * sobre todo lo demás del estilo. El texto hace salto de línea dentro del ancho libre de la pantalla (hasta
 * [LINEAS_DETALLE] líneas por párrafo de detalle, con «…»); el [EntornoBocadillo] decide dónde cae el piquito y si va
 * encima o debajo del marcador, y se recalcula al parar la cámara. La velocidad de paso lleva el texto en verde o
 * rojo.
 */
internal object CapaBocadillo {
    private var imagenActual: String? = null

    fun instalar(
        estilo: Style,
        contexto: Context,
        bocadillo: BocadilloMapa?,
        entorno: EntornoBocadillo?,
    ) {
        imagenActual = null
        estilo.addSource(GeoJsonSource(ID_FUENTE, FeatureCollection.fromFeatures(emptyList<Feature>())))
        estilo.addLayer(
            SymbolLayer(ID_CAPA, ID_FUENTE).withProperties(
                PropertyFactory.iconImage(Expression.get(PROPIEDAD_IMAGEN)),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconOffset(arrayOf(0f, -SUBIDA_PX)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        actualizar(estilo, contexto, bocadillo, entorno)
    }

    fun actualizar(
        estilo: Style,
        contexto: Context,
        bocadillo: BocadilloMapa?,
        entorno: EntornoBocadillo?,
    ) {
        val fuente = estilo.getSourceAs<GeoJsonSource>(ID_FUENTE) ?: return
        val anterior = imagenActual
        if (bocadillo == null) {
            fuente.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
            imagenActual = null
        } else {
            val medidas = medirGlobo(contexto, bocadillo.contenido, entorno)
            val colocacion = colocar(contexto, medidas, entorno)
            val nombre = "bocadillo-${bocadillo.hashCode()}-${colocacion.picoXPx.roundToInt()}-${colocacion.debajo}"
            if (nombre != anterior) {
                estilo.addImage(nombre, imagenDe(contexto, bocadillo.contenido, medidas, colocacion))
            }
            imagenActual = nombre
            colocarCapa(estilo, contexto, medidas, colocacion)
            fuente.setGeoJson(
                Feature.fromGeometry(Point.fromLngLat(bocadillo.lon, bocadillo.lat)).also {
                    it.addStringProperty(PROPIEDAD_IMAGEN, nombre)
                },
            )
        }
        if (anterior != null && anterior != imagenActual) estilo.removeImage(anterior)
    }

    /** Ancla y desplazamiento de la capa: el piquito del bocadillo cae sobre el marcador, encima o debajo de él. */
    private fun colocarCapa(
        estilo: Style,
        contexto: Context,
        medidas: MedidasGlobo,
        colocacion: ColocacionGlobo,
    ) {
        val dp = contexto.resources.displayMetrics.density
        val dx = colocacion.desplazamientoPx(medidas.ancho) / dp
        val ancla = if (colocacion.debajo) Property.ICON_ANCHOR_TOP else Property.ICON_ANCHOR_BOTTOM
        val dy = if (colocacion.debajo) SUBIDA_PX else -SUBIDA_PX
        estilo.getLayer(ID_CAPA)?.setProperties(
            PropertyFactory.iconAnchor(ancla),
            PropertyFactory.iconOffset(arrayOf(dx, dy)),
        )
    }
}

private fun colocar(
    contexto: Context,
    medidas: MedidasGlobo,
    entorno: EntornoBocadillo?,
): ColocacionGlobo {
    val dp = contexto.resources.displayMetrics.density
    if (entorno == null) return ColocacionGlobo(medidas.ancho / 2, debajo = false)
    val limites =
        LimitesGlobo(
            anchoPantallaPx = entorno.anchoPantallaPx.toFloat(),
            margenIzquierdoPx = MARGEN_IZQUIERDO_DP * dp,
            margenDerechoPx = MARGEN_DERECHO_DP * dp,
            margenSuperiorPx = MARGEN_SUPERIOR_DP * dp,
        )
    return colocarGlobo(
        xPx = entorno.xPx,
        yPx = entorno.yPx,
        anchoPx = medidas.ancho,
        altoPx = medidas.alto + PICO_DP * dp,
        subidaPx = SUBIDA_PX * dp,
        limites = limites,
        margenPicoPx = MARGEN_PICO_DP * dp,
        pasoPx = PASO_PICO_DP * dp,
    )
}

private fun colorTexto(contenido: ContenidoBocadillo): Int =
    when {
        contenido is ContenidoBocadillo.Paso && contenido.supera == true -> RallyOscuro.deltaPeor.toArgb()
        contenido is ContenidoBocadillo.Paso && contenido.supera == false -> RallyOscuro.deltaMejor.toArgb()
        contenido is ContenidoBocadillo.Tramo && contenido.supera -> RallyOscuro.deltaPeor.toArgb()
        else -> Color.WHITE
    }

/**
 * Lo que mide el bocadillo: el título (hasta [LINEAS_TITULO] líneas) y cada párrafo de detalle ya partidos en líneas
 * dentro del ancho libre, el [ancho] del cuerpo (sin el piquito), su [alto] y el alto de la franja del título.
 */
private class MedidasGlobo(
    val titulo: StaticLayout,
    val detalle: List<StaticLayout>,
    val ancho: Float,
    val alto: Float,
    val altoTitulo: Float,
)

/** El ancho máximo del bocadillo: [ANCHO_MAXIMO_DP] o, si la pantalla es más estrecha, lo que queda libre. */
private fun anchoMaximoPx(
    dp: Float,
    entorno: EntornoBocadillo?,
): Float {
    val maximo = ANCHO_MAXIMO_DP * dp
    val libre = entorno?.let { it.anchoPantallaPx - (MARGEN_IZQUIERDO_DP + MARGEN_DERECHO_DP) * dp }
    return if (libre == null) maximo else minOf(maximo, libre)
}

private fun medirGlobo(
    contexto: Context,
    contenido: ContenidoBocadillo,
    entorno: EntornoBocadillo?,
): MedidasGlobo {
    val metricas = contexto.resources.displayMetrics
    val dp = metricas.density
    val anchoTexto = (anchoMaximoPx(dp, entorno) - RELLENO_DP * 2 * dp).toInt()
    val anchoTitulo = anchoTexto - ((ICONO_DP + HUECO_DP) * dp).toInt()
    val pincelTitulo =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTexto(contenido)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXTO_SP, metricas)
        }
    val texto = textoBocadillo(contexto.resources, contenido)
    val titulo = capaDeTexto(texto, pincelTitulo, anchoTitulo, LINEAS_TITULO)
    val detalle = capasDeDetalle(contexto, contenido, anchoTexto)
    val anchoDeTitulo = (RELLENO_DP * 2 + ICONO_DP + HUECO_DP) * dp + titulo.anchoUtil()
    val anchoDeDetalle = RELLENO_DP * 2 * dp + (detalle.maxOfOrNull { it.anchoUtil() } ?: 0f)
    val altoTitulo = maxOf(ALTO_DP * dp, titulo.height + RELLENO_TITULO_DP * 2 * dp)
    val alto = if (detalle.isEmpty()) altoTitulo else altoTitulo - HUECO_TITULO_DP * dp + altoDetalle(detalle, dp)
    return MedidasGlobo(titulo, detalle, maxOf(anchoDeTitulo, anchoDeDetalle), alto, altoTitulo)
}

/** Dibuja el bocadillo: fondo, borde ámbar, icono ámbar, título, párrafos de detalle (si hay) y piquito al marcador. */
private fun imagenDe(
    contexto: Context,
    contenido: ContenidoBocadillo,
    medidas: MedidasGlobo,
    colocacion: ColocacionGlobo,
): Bitmap {
    val dp = contexto.resources.displayMetrics.density
    val pico = PICO_DP * dp
    // Con el piquito arriba (bocadillo bajo el marcador) el cuerpo baja lo que mide el piquito.
    val cuerpoY = if (colocacion.debajo) pico else 0f
    val bitmap =
        Bitmap.createBitmap(medidas.ancho.toInt() + 1, (medidas.alto + pico).toInt() + 1, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(bitmap)
    dibujarGlobo(lienzo, medidas.ancho, medidas.alto, dp, colocacion)
    // Fila del título: icono y texto centrados en su franja (la de arriba si hay detalle debajo).
    val centro = cuerpoY + medidas.altoTitulo / 2
    ContextCompat.getDrawable(contexto, iconoBocadillo(contenido))?.mutate()?.let { icono ->
        val lado = (ICONO_DP * dp).toInt()
        val x = (RELLENO_DP * dp).toInt()
        val y = (centro - lado / 2).toInt()
        icono.setBounds(x, y, x + lado, y + lado)
        icono.setTint(RallyOscuro.aviso.toArgb())
        icono.draw(lienzo)
    }
    lienzo.save()
    lienzo.translate((RELLENO_DP + ICONO_DP + HUECO_DP) * dp, centro - medidas.titulo.height / 2f)
    medidas.titulo.draw(lienzo)
    lienzo.restore()
    var arriba = cuerpoY + medidas.altoTitulo - HUECO_TITULO_DP * dp
    medidas.detalle.forEach { capa ->
        lienzo.save()
        lienzo.translate(RELLENO_DP * dp, arriba)
        capa.draw(lienzo)
        lienzo.restore()
        arriba += capa.height + HUECO_LINEAS_DP * dp
    }
    return bitmap
}

/** Alto de las líneas de detalle con sus huecos y el relleno de abajo. */
private fun altoDetalle(
    detalle: List<StaticLayout>,
    dp: Float,
): Float = detalle.sumOf { it.height } + (detalle.size - 1) * HUECO_LINEAS_DP * dp + BASE_DETALLE_DP * dp

/**
 * Cuerpo redondeado carbón con borde ámbar y el piquito (hacia el marcador) en [colocacion]: abajo si el bocadillo va
 * encima del marcador y arriba si va debajo, a [ColocacionGlobo.picoXPx] del borde izquierdo.
 */
private fun dibujarGlobo(
    lienzo: Canvas,
    ancho: Float,
    alto: Float,
    dp: Float,
    colocacion: ColocacionGlobo,
) {
    val pico = PICO_DP * dp
    val borde = BORDE_DP * dp
    val y0 = if (colocacion.debajo) pico else 0f
    val cuerpo = RectF(borde / 2, y0 + borde / 2, ancho - borde / 2, y0 + alto - borde / 2)
    val x = colocacion.picoXPx
    val forma =
        Path().apply {
            addRoundRect(cuerpo, RADIO_DP * dp, RADIO_DP * dp, Path.Direction.CW)
            if (colocacion.debajo) {
                moveTo(x - pico, y0 + borde / 2)
                lineTo(x, 0f)
                lineTo(x + pico, y0 + borde / 2)
            } else {
                moveTo(x - pico, alto - borde / 2)
                lineTo(x, alto + pico)
                lineTo(x + pico, alto - borde / 2)
            }
            close()
        }
    val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    pincel.color = FONDO
    lienzo.drawPath(forma, pincel)
    pincel.style = Paint.Style.STROKE
    pincel.strokeWidth = borde
    pincel.strokeJoin = Paint.Join.ROUND
    pincel.color = RallyOscuro.aviso.toArgb()
    lienzo.drawPath(forma, pincel)
}

/** Una capa de texto por párrafo de detalle (recortada a [LINEAS_DETALLE] líneas con «…»), de [ancho] px como mucho. */
private fun capasDeDetalle(
    contexto: Context,
    contenido: ContenidoBocadillo,
    ancho: Int,
): List<StaticLayout> {
    val metricas = contexto.resources.displayMetrics
    val pincel =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_DETALLE
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DETALLE_SP, metricas)
        }
    return lineasDetalleBocadillo(contexto.resources, contenido).map { capaDeTexto(it, pincel, ancho, LINEAS_DETALLE) }
}

/** El [texto] partido en líneas de [ancho] px como mucho, con [lineas] como máximo y «…» si no cabe. */
private fun capaDeTexto(
    texto: String,
    pincel: TextPaint,
    ancho: Int,
    lineas: Int,
): StaticLayout =
    StaticLayout.Builder
        .obtain(texto, 0, texto.length, pincel, ancho.coerceAtLeast(1))
        .setMaxLines(lineas)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

private fun StaticLayout.anchoUtil(): Float = (0 until lineCount).maxOfOrNull { getLineWidth(it) } ?: 0f
