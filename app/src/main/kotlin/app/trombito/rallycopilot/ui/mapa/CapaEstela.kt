package app.trombito.rallycopilot.ui.mapa

import androidx.compose.ui.graphics.toArgb
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.theme.DarkColors
import app.trombito.rallycopilot.ui.theme.GrisEstela
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private const val ID_FUENTE_ESTELA = "estela-fuente"
private const val ID_ESTELA_BORDE = "estela-borde"
private const val ID_ESTELA = "estela-linea"
private const val EXPONENTE_ESTELA = 1.5f
private const val ZOOM_ESTELA_MINIMO = 8
private const val ZOOM_ESTELA_BASE = 15
private const val ZOOM_ESTELA_MAXIMO = 18
private const val ANCHO_ESTELA_MINIMO = 1.5f
private const val ANCHO_ESTELA_BASE = 4f
private const val ANCHO_ESTELA_MAXIMO = 6f
private const val EXTRA_BORDE_ESTELA = 2f
internal const val OPACIDAD_ESTELA = 0.7f
private const val OPACIDAD_BORDE_ESTELA = 0.35f

/** Gris apagado de la estela (sin opacidad); la capa lo pinta al [OPACIDAD_ESTELA]. */
internal val COLOR_ESTELA: Int = GrisEstela.toArgb()

/**
 * Estela de por dónde se ha pasado: línea fina gris con un contorno carbón muy tenue (para leerse sobre el mapa claro y
 * el satélite). Va por debajo de la línea de la ruta, del destino y de la flecha de posición, así que se instala ANTES
 * que [CapaRuta]; como ella, justo antes de la primera capa de símbolos. La fuente siempre existe (vacía sin estela).
 */
internal object CapaEstela {
    fun instalar(
        estilo: Style,
        puntos: List<PuntoGeo>,
    ) {
        estilo.addSource(GeoJsonSource(ID_FUENTE_ESTELA, lineaGeoJson(puntos)))
        val etiquetas = estilo.layers.firstOrNull { it is SymbolLayer }?.id
        listOf(
            capa(
                ID_ESTELA_BORDE,
                DarkColors.surfaceContainerLowest.toArgb(),
                OPACIDAD_BORDE_ESTELA,
                EXTRA_BORDE_ESTELA,
            ),
            capa(ID_ESTELA, COLOR_ESTELA, OPACIDAD_ESTELA, 0f),
        ).forEach { capa ->
            if (etiquetas != null) estilo.addLayerBelow(capa, etiquetas) else estilo.addLayer(capa)
        }
    }

    fun actualizar(
        estilo: Style,
        puntos: List<PuntoGeo>,
    ) {
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_ESTELA)?.setGeoJson(lineaGeoJson(puntos))
    }

    private fun capa(
        id: String,
        color: Int,
        opacidad: Float,
        extra: Float,
    ): LineLayer =
        LineLayer(id, ID_FUENTE_ESTELA).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineOpacity(opacidad),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineWidth(
                Expression.interpolate(
                    Expression.exponential(EXPONENTE_ESTELA),
                    Expression.zoom(),
                    Expression.stop(ZOOM_ESTELA_MINIMO, ANCHO_ESTELA_MINIMO + extra),
                    Expression.stop(ZOOM_ESTELA_BASE, ANCHO_ESTELA_BASE + extra),
                    Expression.stop(ZOOM_ESTELA_MAXIMO, ANCHO_ESTELA_MAXIMO + extra),
                ),
            ),
        )
}
