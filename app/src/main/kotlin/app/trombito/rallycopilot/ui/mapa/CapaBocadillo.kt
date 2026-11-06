package app.trombito.rallycopilot.ui.mapa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.ui.alertas.BocadilloMapa
import app.trombito.rallycopilot.ui.alertas.ContenidoBocadillo
import app.trombito.rallycopilot.ui.alertas.iconoBocadillo
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

private const val ID_FUENTE = "bocadillo-fuente"
private const val ID_CAPA = "bocadillo-capa"
private const val PROPIEDAD_IMAGEN = "imagen"

/** Sube el bocadillo sobre la insignia del marcador (que mide 40 dp). */
private const val SUBIDA_PX = -26f
private const val ALTO_DP = 38f
private const val RELLENO_DP = 10f
private const val ICONO_DP = 22f
private const val HUECO_DP = 8f
private const val PICO_DP = 9f
private const val BORDE_DP = 2f
private const val RADIO_DP = 8f
private const val TEXTO_SP = 16f
private const val FONDO = 0xEB101114.toInt()

/**
 * Bocadillo anclado al marcador de un radar o incidencia: un único punto en una `SymbolLayer` cuya imagen (rectángulo
 * carbón translúcido con borde ámbar, icono, texto y un piquito hacia el marcador) se dibuja con Canvas al cambiar el
 * contenido. Así queda pegado a la coordenada al mover el mapa sin proyectar nada. Va sobre todo lo demás del estilo.
 * La velocidad de paso lleva el texto en verde (dentro del límite) o rojo (por encima).
 */
internal object CapaBocadillo {
    private var imagenActual: String? = null

    fun instalar(
        estilo: Style,
        contexto: Context,
        bocadillo: BocadilloMapa?,
    ) {
        imagenActual = null
        estilo.addSource(GeoJsonSource(ID_FUENTE, FeatureCollection.fromFeatures(emptyList<Feature>())))
        estilo.addLayer(
            SymbolLayer(ID_CAPA, ID_FUENTE).withProperties(
                PropertyFactory.iconImage(Expression.get(PROPIEDAD_IMAGEN)),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconOffset(arrayOf(0f, SUBIDA_PX)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        actualizar(estilo, contexto, bocadillo)
    }

    fun actualizar(
        estilo: Style,
        contexto: Context,
        bocadillo: BocadilloMapa?,
    ) {
        val fuente = estilo.getSourceAs<GeoJsonSource>(ID_FUENTE) ?: return
        val anterior = imagenActual
        if (bocadillo == null) {
            fuente.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
            imagenActual = null
        } else {
            val nombre = "bocadillo-${bocadillo.hashCode()}"
            if (nombre != anterior) estilo.addImage(nombre, imagenDe(contexto, bocadillo.contenido))
            imagenActual = nombre
            fuente.setGeoJson(
                Feature.fromGeometry(Point.fromLngLat(bocadillo.lon, bocadillo.lat)).also {
                    it.addStringProperty(PROPIEDAD_IMAGEN, nombre)
                },
            )
        }
        if (anterior != null && anterior != imagenActual) estilo.removeImage(anterior)
    }
}

private fun colorTexto(contenido: ContenidoBocadillo): Int =
    when {
        contenido is ContenidoBocadillo.Paso && contenido.supera == true -> RallyOscuro.deltaPeor.toArgb()
        contenido is ContenidoBocadillo.Paso && contenido.supera == false -> RallyOscuro.deltaMejor.toArgb()
        contenido is ContenidoBocadillo.Tramo && contenido.supera -> RallyOscuro.deltaPeor.toArgb()
        else -> Color.WHITE
    }

/** Dibuja el bocadillo: fondo, borde ámbar, icono ámbar, texto y piquito hacia abajo. */
private fun imagenDe(
    contexto: Context,
    contenido: ContenidoBocadillo,
): Bitmap {
    val metricas = contexto.resources.displayMetrics
    val dp = metricas.density
    val texto = textoBocadillo(contexto.resources, contenido)
    val ambar = RallyOscuro.aviso.toArgb()
    val pincelTexto =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTexto(contenido)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXTO_SP, metricas)
        }
    val ancho = (RELLENO_DP * 2 + ICONO_DP + HUECO_DP) * dp + pincelTexto.measureText(texto)
    val alto = ALTO_DP * dp
    val pico = PICO_DP * dp
    val borde = BORDE_DP * dp
    val bitmap = Bitmap.createBitmap(ancho.toInt() + 1, (alto + pico).toInt() + 1, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(bitmap)
    val cuerpo = RectF(borde / 2, borde / 2, ancho - borde / 2, alto - borde / 2)
    val forma =
        Path().apply {
            addRoundRect(cuerpo, RADIO_DP * dp, RADIO_DP * dp, Path.Direction.CW)
            moveTo(ancho / 2 - pico, alto - borde / 2)
            lineTo(ancho / 2, alto + pico)
            lineTo(ancho / 2 + pico, alto - borde / 2)
            close()
        }
    val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    pincel.color = FONDO
    lienzo.drawPath(forma, pincel)
    pincel.style = Paint.Style.STROKE
    pincel.strokeWidth = borde
    pincel.strokeJoin = Paint.Join.ROUND
    pincel.color = ambar
    lienzo.drawPath(forma, pincel)
    ContextCompat.getDrawable(contexto, iconoBocadillo(contenido))?.mutate()?.let { icono ->
        val lado = (ICONO_DP * dp).toInt()
        val x = (RELLENO_DP * dp).toInt()
        val y = ((alto - lado) / 2).toInt()
        icono.setBounds(x, y, x + lado, y + lado)
        icono.setTint(ambar)
        icono.draw(lienzo)
    }
    val x = (RELLENO_DP + ICONO_DP + HUECO_DP) * dp
    val y = alto / 2 - (pincelTexto.descent() + pincelTexto.ascent()) / 2
    lienzo.drawText(texto, x, y, pincelTexto)
    return bitmap
}
