package app.trombito.rallycopilot.ui.mapa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.motor.tramos.PosicionFantasma
import app.trombito.rallycopilot.ui.comun.recurso
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val ID_FUENTE = "fantasma-fuente"
private const val ID_CAPA = "fantasma-capa"
private const val ID_IMAGEN = "fantasma-icono"
internal const val PROPIEDAD_RUMBO_FANTASMA = "rumbo"
private const val LADO_ICONO_DP = 40

/** Opacidad del coche fantasma: translúcido para que se lea el mapa por debajo y no se confunda con el coche real. */
internal const val OPACIDAD_FANTASMA = 0.45f

/** Gris casi blanco de la silueta del fantasma (sin opacidad; la capa lo pinta al [OPACIDAD_FANTASMA]). */
private const val COLOR_FANTASMA = 0xFFE8EAED.toInt()

/**
 * Coche fantasma: dónde iba el coche de la mejor pasada a este mismo instante del tramo. Un solo punto en una
 * `SymbolLayer` con la silueta del icono de posición elegido (gris claro al 45 %), girada con el rumbo de la traza.
 * Se instala justo antes de activar la ubicación, así queda por debajo del coche real. Sin [PosicionFantasma] (sin
 * tramo vivo o sin mejor pasada) la fuente queda vacía y no se ve nada. Una instancia por mapa: recuerda el icono
 * dibujado.
 */
internal class CapaFantasma {
    private var iconoDibujado: IconoPosicion? = null

    fun instalar(
        estilo: Style,
        contexto: Context,
        icono: IconoPosicion,
        fantasma: PosicionFantasma?,
    ) {
        iconoDibujado = null
        estilo.addSource(GeoJsonSource(ID_FUENTE, fantasmaGeoJson(fantasma)))
        estilo.addLayer(
            SymbolLayer(ID_CAPA, ID_FUENTE).withProperties(
                PropertyFactory.iconImage(ID_IMAGEN),
                PropertyFactory.iconRotate(Expression.get(PROPIEDAD_RUMBO_FANTASMA)),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconOpacity(OPACIDAD_FANTASMA),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        dibujarIcono(estilo, contexto, icono)
    }

    fun actualizar(
        estilo: Style,
        contexto: Context,
        icono: IconoPosicion,
        fantasma: PosicionFantasma?,
    ) {
        dibujarIcono(estilo, contexto, icono)
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE)?.setGeoJson(fantasmaGeoJson(fantasma))
    }

    /** La silueta se vuelve a dibujar solo si cambia el icono de posición. */
    private fun dibujarIcono(
        estilo: Style,
        contexto: Context,
        icono: IconoPosicion,
    ) {
        if (icono == iconoDibujado) return
        iconoDibujado = icono
        estilo.addImage(ID_IMAGEN, siluetaDe(contexto, icono))
    }
}

/** El fantasma como un punto con su rumbo (grados desde el norte); sin fantasma, ninguno. */
internal fun fantasmaGeoJson(fantasma: PosicionFantasma?): FeatureCollection =
    FeatureCollection.fromFeatures(
        listOfNotNull(
            fantasma?.let {
                Feature.fromGeometry(Point.fromLngLat(it.punto.lon, it.punto.lat)).also { f ->
                    f.addNumberProperty(PROPIEDAD_RUMBO_FANTASMA, it.rumboGrados)
                }
            },
        ),
    )

/** Dibuja el icono de posición como silueta gris clara (el icono apunta hacia arriba: rumbo 0 = norte). */
private fun siluetaDe(
    contexto: Context,
    icono: IconoPosicion,
): Bitmap {
    val lado = (LADO_ICONO_DP * contexto.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    ContextCompat.getDrawable(contexto, icono.recurso())?.mutate()?.let { dibujo ->
        dibujo.setBounds(0, 0, lado, lado)
        dibujo.setTint(COLOR_FANTASMA)
        dibujo.draw(Canvas(bitmap))
    }
    return bitmap
}
