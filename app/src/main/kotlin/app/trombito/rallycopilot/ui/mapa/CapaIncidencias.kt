package app.trombito.rallycopilot.ui.mapa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.ui.alertas.MarcadorIncidencia
import app.trombito.rallycopilot.ui.alertas.colorIncidencia
import app.trombito.rallycopilot.ui.alertas.iconoIncidencia
import app.trombito.rallycopilot.ui.alertas.imagenMarcador
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val ID_FUENTE = "incidencias-fuente"
private const val ID_CAPA = "incidencias-capa"
internal const val PROPIEDAD_ID = "id"
internal const val PROPIEDAD_ICONO = "icono"

private const val LADO_MARCADOR_DP = 40f
private const val BORDE_MARCADOR_DP = 3f
private const val RELLENO_ICONO_DP = 8f
private const val TOLERANCIA_TOQUE_DP = 24f

/**
 * Marcadores de las incidencias de tráfico: un `GeoJsonSource` con una `SymbolLayer` que pinta, por tipo, una insignia
 * redonda (carbón con borde ámbar o rojo) con el icono ic_alerta_* dentro. Se coloca encima de todo lo del estilo y
 * debajo del componente de ubicación (que se activa después). Se vuelve a instalar al cargar cada estilo; la fuente
 * siempre existe (vacía sin incidencias), así que cambiar los marcadores solo cambia sus datos.
 */
internal object CapaIncidencias {
    fun instalar(
        estilo: Style,
        contexto: Context,
        marcadores: List<MarcadorIncidencia>,
    ) {
        TipoIncidencia.entries.forEach { estilo.addImage(imagenMarcador(it), insigniaDe(contexto, it)) }
        estilo.addSource(GeoJsonSource(ID_FUENTE, featuresIncidencias(marcadores)))
        estilo.addLayer(
            SymbolLayer(ID_CAPA, ID_FUENTE).withProperties(
                PropertyFactory.iconImage(Expression.get(PROPIEDAD_ICONO)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }

    fun actualizar(
        estilo: Style,
        marcadores: List<MarcadorIncidencia>,
    ) {
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE)?.setGeoJson(featuresIncidencias(marcadores))
    }

    /** El id de la incidencia pintada bajo el toque en [x], [y] (píxeles), con un margen cómodo para el dedo. */
    fun idEn(
        mapa: MapLibreMap,
        x: Float,
        y: Float,
        densidad: Float,
    ): String? {
        val margen = TOLERANCIA_TOQUE_DP * densidad
        val zona = RectF(x - margen, y - margen, x + margen, y + margen)
        return mapa
            .queryRenderedFeatures(zona, ID_CAPA)
            .firstOrNull()
            ?.getStringProperty(PROPIEDAD_ID)
    }
}

/** Un punto por incidencia con su `id` y el nombre de su imagen: lo que se pinta en el mapa. */
internal fun featuresIncidencias(marcadores: List<MarcadorIncidencia>): FeatureCollection =
    FeatureCollection.fromFeatures(
        marcadores.map { m ->
            Feature.fromGeometry(Point.fromLngLat(m.lon, m.lat)).also {
                it.addStringProperty(PROPIEDAD_ID, m.id)
                it.addStringProperty(PROPIEDAD_ICONO, imagenMarcador(m.tipo))
            }
        },
    )

private fun insigniaDe(
    contexto: Context,
    tipo: TipoIncidencia,
): Bitmap = insignia(contexto, iconoIncidencia(tipo), colorIncidencia(tipo).toArgb())

/** Insignia redonda: fondo carbón, borde de [colorBorde] e icono [icono] en blanco. */
internal fun insignia(
    contexto: Context,
    @DrawableRes icono: Int,
    colorBorde: Int,
): Bitmap {
    val densidad = contexto.resources.displayMetrics.density
    val lado = (LADO_MARCADOR_DP * densidad).toInt()
    val borde = BORDE_MARCADOR_DP * densidad
    val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(bitmap)
    val radio = lado / 2f
    val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    pincel.color = FONDO_INSIGNIA
    lienzo.drawCircle(radio, radio, radio - borde / 2, pincel)
    pincel.style = Paint.Style.STROKE
    pincel.strokeWidth = borde
    pincel.color = colorBorde
    lienzo.drawCircle(radio, radio, radio - borde / 2, pincel)
    ContextCompat.getDrawable(contexto, icono)?.mutate()?.let { dibujo ->
        val relleno = (RELLENO_ICONO_DP * densidad).toInt()
        dibujo.setBounds(relleno, relleno, lado - relleno, lado - relleno)
        dibujo.setTint(Color.WHITE)
        dibujo.draw(lienzo)
    }
    return bitmap
}

private const val FONDO_INSIGNIA = 0xF00E0F12.toInt()
