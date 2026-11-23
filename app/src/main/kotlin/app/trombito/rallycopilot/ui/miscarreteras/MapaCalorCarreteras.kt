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
import androidx.compose.ui.graphics.Brush
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
import app.trombito.rallycopilot.ui.analisis.PaletaVelocidad
import app.trombito.rallycopilot.ui.mapa.ANCHO_CALOR_SIN_DATO
import app.trombito.rallycopilot.ui.mapa.ANCHO_CALOR_VELOCIDAD
import app.trombito.rallycopilot.ui.mapa.ModoCalor
import app.trombito.rallycopilot.ui.mapa.fraccionVelocidadCalor
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.RojoAcento
import app.trombito.rallycopilot.ui.theme.TextoSecundarioSobreCarbon
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

const val ETIQUETA_CARRETERAS_MAPA = "carreteras-mapa"
const val ETIQUETA_CARRETERAS_MAPA_AVISO = "carreteras-mapa-aviso"
const val ETIQUETA_CARRETERAS_LEYENDA = "carreteras-leyenda"
const val ETIQUETA_CARRETERAS_LEYENDA_VELOCIDAD = "carreteras-leyenda-velocidad"

/** Alto del mapa de calor, el mismo con mapa base y sin él. */
internal val ALTO_MAPA_CALOR = 300.dp
private val ALTO_MUESTRA = 4.dp
private val ANCHO_MUESTRA = 20.dp
private val ANCHO_DEGRADADO = 72.dp
private const val MARGEN_MAPA_PX = 24f
private const val SEPARACION_MIN_PX = 1.2f
private const val GRADOS_A_RADIANES = Math.PI / 180.0
private const val CUBOS_VELOCIDAD = 8
private val COLOR_SIN_DATO = Color(0xFF8A8F98)

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

/** Cómo se dibuja una vía en el mapa sin base: color y grosor según el modo; [orden] decide qué va encima. */
internal data class EstiloTrazo(
    val color: Color,
    val grosorPx: Float,
    val orden: Int,
)

/** El estilo de [trazo] en el [modo]: el nivel de uso o, por velocidad, un color de la paleta en ocho tramos. */
internal fun estiloDeTrazo(
    trazo: TrazoCarretera,
    modo: ModoCalor,
): EstiloTrazo =
    when (modo) {
        ModoCalor.USO -> {
            val nivel = NivelUso.de(trazo.pasadas)
            EstiloTrazo(nivel.color, nivel.grosorPx, nivel.ordinal + 1)
        }

        ModoCalor.VELOCIDAD -> {
            val kmh = trazo.velMediaKmh
            if (kmh == null) {
                EstiloTrazo(COLOR_SIN_DATO, ANCHO_CALOR_SIN_DATO, 0)
            } else {
                val cubo = (fraccionVelocidadCalor(kmh) * CUBOS_VELOCIDAD).roundToInt()
                EstiloTrazo(PaletaVelocidad.color(cubo.toFloat() / CUBOS_VELOCIDAD), ANCHO_CALOR_VELOCIDAD, cubo + 1)
            }
        }
    }

/**
 * Mapa de calor sin mapa base (robusto y sin red; es el que se ve mientras no haya estilo de mapa instalado): cada vía
 * conducida se dibuja con el color de su [NivelUso] o, en el [modo] velocidad, el de su velocidad media; las más
 * repetidas encima. Sobre carbón, también en tema claro. Sin trazos enseña [aviso].
 */
@Composable
internal fun MapaCalorCarreteras(
    trazos: List<TrazoCarretera>,
    aviso: String,
    modifier: Modifier = Modifier,
    modo: ModoCalor = ModoCalor.USO,
) {
    val encuadre = remember(trazos) { Encuadre.de(trazos) }
    val descripcion =
        stringResource(
            if (modo == ModoCalor.USO) {
                R.string.carreteras_mapa_descripcion
            } else {
                R.string.carreteras_mapa_descripcion_velocidad
            },
        )
    val fondo =
        Modifier
            .fillMaxWidth()
            .height(ALTO_MAPA_CALOR)
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
                        val caminos = caminosPorEstilo(trazos, modo, proyector)
                        onDrawBehind {
                            for ((estilo, camino) in caminos) {
                                drawPath(
                                    camino,
                                    estilo.color,
                                    style = Stroke(estilo.grosorPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    },
            )
        }
    }
}

/** Un camino por estilo, de lo que va debajo a lo que va encima, con todas las vías de ese estilo. */
private fun caminosPorEstilo(
    trazos: List<TrazoCarretera>,
    modo: ModoCalor,
    proyector: Encuadre.Proyector,
): List<Pair<EstiloTrazo, Path>> =
    trazos
        .groupBy { estiloDeTrazo(it, modo) }
        .toList()
        .sortedBy { it.first.orden }
        .map { (estilo, grupo) -> estilo to caminoDe(grupo, proyector) }

/** Un solo camino con todas las vías de [trazos], sin los puntos a menos de un píxel del anterior. */
private fun caminoDe(
    trazos: List<TrazoCarretera>,
    proyector: Encuadre.Proyector,
): Path {
    val camino = Path()
    for (trazo in trazos) {
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

/** Leyenda del [modo]: veces por color (uso) o la barra de velocidad con su «sin dato» (velocidad). */
@Composable
internal fun LeyendaCalor(
    modo: ModoCalor,
    modifier: Modifier = Modifier,
) {
    when (modo) {
        ModoCalor.USO -> LeyendaUso(modifier)
        ModoCalor.VELOCIDAD -> LeyendaVelocidad(modifier)
    }
}

/** Leyenda de la escala de uso: una muestra del color de cada [NivelUso] con su rango de veces. */
@Composable
private fun LeyendaUso(modifier: Modifier = Modifier) {
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
                Muestra(nivel.color)
                Text(
                    stringResource(textoNivel(nivel)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Leyenda de la velocidad: degradado de la paleta entre 20 y 110 km/h y una muestra gris para «sin dato». */
@Composable
@Suppress("SpreadOperator") // una sola vez al componer la leyenda, con las paradas de la paleta
private fun LeyendaVelocidad(modifier: Modifier = Modifier) {
    val degradado =
        Brush.horizontalGradient(
            *PaletaVelocidad.paradas.map { (posicion, color) -> posicion to Color(color) }.toTypedArray(),
        )
    Row(
        modifier.fillMaxWidth().testTag(ETIQUETA_CARRETERAS_LEYENDA_VELOCIDAD),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextoLeyenda(stringResource(R.string.carreteras_leyenda_velocidad_titulo), apagado = true)
        TextoLeyenda(stringResource(R.string.carreteras_leyenda_lenta))
        Box(
            Modifier
                .width(ANCHO_DEGRADADO)
                .height(ALTO_MUESTRA)
                .clip(RoundedCornerShape(ALTO_MUESTRA / 2))
                .background(degradado),
        )
        TextoLeyenda(stringResource(R.string.carreteras_leyenda_rapida))
        Muestra(COLOR_SIN_DATO)
        TextoLeyenda(stringResource(R.string.carreteras_leyenda_sin_dato))
    }
}

@Composable
private fun TextoLeyenda(
    texto: String,
    apagado: Boolean = false,
) {
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        color = if (apagado) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Muestra(color: Color) {
    Box(
        Modifier
            .width(ANCHO_MUESTRA)
            .height(ALTO_MUESTRA)
            .clip(RoundedCornerShape(ALTO_MUESTRA / 2))
            .background(color),
    )
}

private fun textoNivel(nivel: NivelUso): Int =
    when (nivel) {
        NivelUso.UNA -> R.string.carreteras_leyenda_una
        NivelUso.POCAS -> R.string.carreteras_leyenda_pocas
        NivelUso.VARIAS -> R.string.carreteras_leyenda_varias
        NivelUso.MUCHAS -> R.string.carreteras_leyenda_muchas
    }
