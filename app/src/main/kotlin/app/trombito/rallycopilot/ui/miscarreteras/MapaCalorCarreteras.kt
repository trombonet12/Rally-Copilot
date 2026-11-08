package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.RojoAcento
import app.trombito.rallycopilot.ui.theme.TextoSecundarioSobreCarbon
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

const val ETIQUETA_CARRETERAS_MAPA = "carreteras-mapa"
const val ETIQUETA_CARRETERAS_MAPA_AVISO = "carreteras-mapa-aviso"
const val ETIQUETA_CARRETERAS_LEYENDA = "carreteras-leyenda"

private val ALTO_MAPA = 300.dp
private val ALTO_MUESTRA = 4.dp
private val ANCHO_MUESTRA = 20.dp
private const val MARGEN_MAPA_PX = 24f
private const val SEPARACION_MIN_PX = 1.2f
private const val GRADOS_A_RADIANES = Math.PI / 180.0

/** Tramos de la escala de calor: de una pasada (ámbar apagado) a diez o más (rojo). */
enum class NivelUso(
    val desde: Int,
    val grosorPx: Float,
    val color: Color,
) {
    UNA(1, 4f, Color(0xFF9C7430)),
    POCAS(2, 5f, Color(0xFFE5AB46)),
    VARIAS(5, 6f, Color(0xFFF0742C)),
    MUCHAS(10, 7f, RojoAcento),
    ;

    companion object {
        /** El tramo de la escala que corresponde a [pasadas] (al menos una). */
        fun de(pasadas: Int): NivelUso = entries.last { pasadas >= it.desde }
    }
}

/**
 * Caja que encierra los trazos, para ponerlos en un lienzo: proyección equirectangular con la longitud corregida por
 * la latitud media (no se deforma), escala común, centrado y con norte arriba.
 */
internal class Encuadre private constructor(
    private val minX: Double,
    private val minY: Double,
    private val ancho: Double,
    private val alto: Double,
    private val factorLon: Double,
) {
    /** Convierte puntos en píxeles de un lienzo de [anchoPx] x [altoPx] con [margenPx] alrededor. */
    inner class Proyector(
        anchoPx: Float,
        altoPx: Float,
        private val margenPx: Float,
    ) {
        private val utilX = (anchoPx - 2 * margenPx).coerceAtLeast(1f)
        private val utilY = (altoPx - 2 * margenPx).coerceAtLeast(1f)
        private val escala: Float =
            min(
                if (ancho > 0.0) utilX / ancho.toFloat() else Float.MAX_VALUE,
                if (alto > 0.0) utilY / alto.toFloat() else Float.MAX_VALUE,
            ).takeIf { it != Float.MAX_VALUE } ?: 1f
        private val sobraX = (utilX - ancho.toFloat() * escala) / 2f
        private val sobraY = (utilY - alto.toFloat() * escala) / 2f

        fun offset(punto: PuntoGeo): Offset =
            Offset(
                margenPx + sobraX + ((punto.lon * factorLon - minX).toFloat() * escala),
                margenPx + sobraY + ((-punto.lat - minY).toFloat() * escala),
            )
    }

    fun proyector(
        anchoPx: Float,
        altoPx: Float,
        margenPx: Float,
    ) = Proyector(anchoPx, altoPx, margenPx)

    companion object {
        /** El encuadre de [trazos], o null si no hay ningún punto. */
        fun de(trazos: List<TrazoCarretera>): Encuadre? {
            val puntos = trazos.flatMap { it.puntos }
            if (puntos.isEmpty()) return null
            val factorLon = cos(puntos.sumOf { it.lat } / puntos.size * GRADOS_A_RADIANES)
            val xs = puntos.map { it.lon * factorLon }
            val ys = puntos.map { -it.lat }
            val minX = xs.min()
            val minY = ys.min()
            return Encuadre(minX, minY, xs.max() - minX, ys.max() - minY, factorLon)
        }
    }
}

/**
 * Mapa de calor sin mapa base (robusto y sin red): cada vía conducida se dibuja con el color de su [NivelUso]; las más
 * repetidas encima. Sobre carbón, también en tema claro. Sin trazos enseña [aviso].
 */
@Composable
internal fun MapaCalorCarreteras(
    trazos: List<TrazoCarretera>,
    aviso: String,
    modifier: Modifier = Modifier,
) {
    val encuadre = remember(trazos) { Encuadre.de(trazos) }
    val descripcion = stringResource(R.string.carreteras_mapa_descripcion)
    val fondo =
        Modifier
            .fillMaxWidth()
            .height(ALTO_MAPA)
            .clip(RoundedCornerShape(Radios.tarjeta))
            .background(CarbonOpaco)
    Box(modifier.then(fondo).testTag(ETIQUETA_CARRETERAS_MAPA), contentAlignment = Alignment.Center) {
        if (encuadre == null) {
            Text(
                text = aviso,
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSecundarioSobreCarbon,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Espacio.xl).testTag(ETIQUETA_CARRETERAS_MAPA_AVISO),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .semantics { contentDescription = descripcion }
                    .drawWithCache {
                        val proyector = encuadre.proyector(size.width, size.height, MARGEN_MAPA_PX)
                        val caminos = NivelUso.entries.map { it to caminoDe(trazos, it, proyector) }
                        onDrawBehind {
                            for ((nivel, camino) in caminos) {
                                drawPath(
                                    camino,
                                    nivel.color,
                                    style = Stroke(nivel.grosorPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    },
            )
        }
    }
}

/** Un solo camino con todas las vías de [nivel], sin los puntos a menos de un píxel del anterior. */
private fun caminoDe(
    trazos: List<TrazoCarretera>,
    nivel: NivelUso,
    proyector: Encuadre.Proyector,
): Path {
    val camino = Path()
    for (trazo in trazos) {
        if (NivelUso.de(trazo.pasadas) != nivel) continue
        var ultimo: Offset? = null
        for (punto in trazo.puntos) {
            val p = proyector.offset(punto)
            val previo = ultimo
            when {
                previo == null -> {
                    camino.moveTo(p.x, p.y)
                    ultimo = p
                }

                hypot(p.x - previo.x, p.y - previo.y) >= SEPARACION_MIN_PX -> {
                    camino.lineTo(p.x, p.y)
                    ultimo = p
                }
            }
        }
    }
    return camino
}

/** Leyenda de la escala: una muestra del color de cada [NivelUso] con su rango de veces. */
@Composable
internal fun LeyendaCalor(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().testTag(ETIQUETA_CARRETERAS_LEYENDA),
        horizontalArrangement = Arrangement.spacedBy(Espacio.l, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.carreteras_leyenda_titulo),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NivelUso.entries.forEach { nivel ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) {
                Box(
                    Modifier
                        .width(ANCHO_MUESTRA)
                        .height(ALTO_MUESTRA)
                        .clip(RoundedCornerShape(ALTO_MUESTRA / 2))
                        .background(nivel.color),
                )
                Text(
                    stringResource(textoNivel(nivel)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun textoNivel(nivel: NivelUso): Int =
    when (nivel) {
        NivelUso.UNA -> R.string.carreteras_leyenda_una
        NivelUso.POCAS -> R.string.carreteras_leyenda_pocas
        NivelUso.VARIAS -> R.string.carreteras_leyenda_varias
        NivelUso.MUCHAS -> R.string.carreteras_leyenda_muchas
    }
