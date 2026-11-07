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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val ID_FUENTE_CABEZA = "estela-fuente"
private const val ID_FUENTE_COLA = "estela-cola-fuente"
private const val ID_BORDE_CABEZA = "estela-borde"
private const val ID_LINEA_CABEZA = "estela-linea"
private const val ID_BORDE_COLA = "estela-cola-borde"
private const val ID_LINEA_COLA = "estela-cola-linea"
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
 * que [CapaRuta]; como ella, justo antes de la primera capa de símbolos. Las fuentes siempre existen (vacías sin
 * estela).
 *
 * La estela va en dos fuentes (REN-15): la *cabeza* (todo menos lo último) solo se reenvía a MapLibre cuando crece un
 * bloque entero de puntos, y la *cola* (los últimos metros, unas decenas de puntos) en cada publicación. Así no se
 * reconstruyen ni se re-teselan 5 000 puntos por segundo. Una instancia por mapa: recuerda lo último que envió.
 */
internal class CapaEstela {
    private var cabezaEnviada: ClaveCabeza? = null

    fun instalar(
        estilo: Style,
        segmentos: List<List<PuntoGeo>>,
    ) {
        val partida = partirEstela(segmentos)
        cabezaEnviada = partida.clave()
        estilo.addSource(GeoJsonSource(ID_FUENTE_CABEZA, estelaGeoJson(partida.cabeza)))
        estilo.addSource(GeoJsonSource(ID_FUENTE_COLA, estelaGeoJson(partida.cola)))
        val etiquetas = estilo.layers.firstOrNull { it is SymbolLayer }?.id
        // Primero los dos contornos y luego las dos líneas: el contorno de la cola no tapa la línea de la cabeza.
        val borde = DarkColors.surfaceContainerLowest.toArgb()
        listOf(
            capa(ID_BORDE_CABEZA, ID_FUENTE_CABEZA, borde, OPACIDAD_BORDE_ESTELA, EXTRA_BORDE_ESTELA),
            capa(ID_BORDE_COLA, ID_FUENTE_COLA, borde, OPACIDAD_BORDE_ESTELA, EXTRA_BORDE_ESTELA),
            capa(ID_LINEA_CABEZA, ID_FUENTE_CABEZA, COLOR_ESTELA, OPACIDAD_ESTELA, 0f),
            capa(ID_LINEA_COLA, ID_FUENTE_COLA, COLOR_ESTELA, OPACIDAD_ESTELA, 0f),
        ).forEach { capa ->
            if (etiquetas != null) estilo.addLayerBelow(capa, etiquetas) else estilo.addLayer(capa)
        }
    }

    fun actualizar(
        estilo: Style,
        segmentos: List<List<PuntoGeo>>,
    ) {
        val partida = partirEstela(segmentos)
        val clave = partida.clave()
        if (clave != cabezaEnviada) {
            cabezaEnviada = clave
            estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_CABEZA)?.setGeoJson(estelaGeoJson(partida.cabeza))
        }
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_COLA)?.setGeoJson(estelaGeoJson(partida.cola))
    }

    private fun capa(
        id: String,
        fuente: String,
        color: Int,
        opacidad: Float,
        extra: Float,
    ): LineLayer =
        LineLayer(id, fuente).withProperties(
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

/** Puntos de la cola de la estela: siempre al menos tantos (los últimos metros) para que el coche no saque ventaja. */
internal const val PUNTOS_COLA_MINIMOS = 12

/** La cabeza solo crece de [BLOQUE_CABEZA] en [BLOQUE_CABEZA] puntos: es lo que espacia el reenvío a MapLibre. */
internal const val BLOQUE_CABEZA = 40

/**
 * La estela partida para pintarla: la [cabeza] cambia poco y la [cola] (repite el último punto de la cabeza), mucho.
 */
internal class EstelaPartida(
    val cabeza: List<List<PuntoGeo>>,
    val cola: List<List<PuntoGeo>>,
) {
    /** Lo que identifica la cabeza: mientras sea igual no hace falta reenviarla. */
    fun clave() = ClaveCabeza(cabeza.size, cabeza.lastOrNull()?.lastOrNull())
}

internal data class ClaveCabeza(
    val segmentos: Int,
    val ultimo: PuntoGeo?,
)

/**
 * Parte el último segmento de [segmentos] en cabeza (los primeros puntos, en múltiplos de [BLOQUE_CABEZA]) y cola (el
 * resto, desde el último punto de la cabeza para que la línea no se corte). Los segmentos anteriores van enteros a la
 * cabeza. Sin segmentos o con un último segmento corto, todo va a la cola.
 */
internal fun partirEstela(segmentos: List<List<PuntoGeo>>): EstelaPartida {
    val ultimo = segmentos.lastOrNull() ?: return EstelaPartida(emptyList(), emptyList())
    val anteriores = segmentos.dropLast(1)
    val largoCabeza = (ultimo.size - PUNTOS_COLA_MINIMOS) / BLOQUE_CABEZA * BLOQUE_CABEZA
    return if (largoCabeza < 2) {
        EstelaPartida(anteriores, listOf(ultimo))
    } else {
        val cabeza = anteriores + listOf(ultimo.subList(0, largoCabeza))
        EstelaPartida(cabeza, listOf(ultimo.subList(largoCabeza - 1, ultimo.size)))
    }
}

/** La estela como líneas separadas (una por segmento con al menos dos puntos) listas para un `GeoJsonSource`. */
internal fun estelaGeoJson(segmentos: List<List<PuntoGeo>>): FeatureCollection =
    FeatureCollection.fromFeatures(
        segmentos
            .filter { it.size >= 2 }
            .map { s -> Feature.fromGeometry(LineString.fromLngLats(s.map { Point.fromLngLat(it.lon, it.lat) })) },
    )
