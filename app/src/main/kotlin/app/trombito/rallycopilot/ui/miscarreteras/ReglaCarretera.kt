package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.RojoAcento
import kotlin.math.max
import kotlin.math.min

/** Prefijo de la etiqueta de prueba de cada franja: `ficha-franja-<id del tramo>`. */
const val ETIQUETA_FICHA_FRANJA_PREFIJO = "ficha-franja-"
const val ETIQUETA_FICHA_REGLA = "ficha-regla"
const val ETIQUETA_FICHA_REGLA_VACIA = "ficha-regla-vacia"

private val ALTO_CARRIL = 48.dp
private val ALTO_FRANJA = 32.dp
private val ANCHO_MINIMO_FRANJA = 14.dp
private const val MARCAS = 4
private const val MIN_LARGO_KM = 0.1
private const val OPACIDAD_FRANJA = 0.85f

/** Un tramo de vía sobre la regla: de [desdeKm] a [hastaKm] (siempre de menor a mayor) en su [sentido]. */
@Immutable
data class FranjaRegla(
    val tramoId: String,
    val desdeKm: Double,
    val hastaKm: Double,
    val sentido: SentidoVia,
)

/** La regla de kilómetros de una carretera: de [minKm] a [maxKm] con las [franjas] de sus tramos. */
@Immutable
data class ReglaCarretera(
    val minKm: Double,
    val maxKm: Double,
    val franjas: List<FranjaRegla>,
) {
    val largoKm: Double get() = maxKm - minKm
}

/**
 * La regla con los tramos de vía de [tramos] que tienen kilómetro de salida y de meta, o null si ninguno lo tiene. Cada
 * franja va siempre de menor a mayor kilómetro, sea cual sea el sentido de marcha del tramo.
 */
fun reglaDe(tramos: List<ResumenTramo>): ReglaCarretera? {
    val franjas =
        tramos.mapNotNull { tramo ->
            val via = tramo.via ?: return@mapNotNull null
            val desde = via.pkDesde ?: return@mapNotNull null
            val hasta = via.pkHasta ?: return@mapNotNull null
            FranjaRegla(tramo.id, min(desde, hasta), max(desde, hasta), via.sentido)
        }
    if (franjas.isEmpty()) return null
    val minimo = franjas.minOf { it.desdeKm }
    val maximo = max(franjas.maxOf { it.hastaKm }, minimo + MIN_LARGO_KM)
    return ReglaCarretera(minimo, maximo, franjas)
}

/** El texto que nombra un sentido: «Hacia Sóller» o, sin destino, el sentido de los kilómetros. */
@Composable
internal fun etiquetaSentido(
    sentido: SentidoVia,
    destino: String?,
): String =
    when {
        destino != null -> stringResource(R.string.ficha_sentido_hacia, destino)
        sentido == SentidoVia.CRECIENTE -> stringResource(R.string.ficha_sentido_creciente)
        sentido == SentidoVia.DECRECIENTE -> stringResource(R.string.ficha_sentido_decreciente)
        else -> stringResource(R.string.ficha_sentido_sin_orientar)
    }

/**
 * Los tramos de la carretera como franjas sobre una regla de kilómetros, un carril por sentido (con su destino). Cada
 * franja es un botón de al menos 48 dp de alto; la elegida ([seleccionado]) se pinta en rojo. Debajo, cinco marcas con
 * los kilómetros de la regla.
 */
@Composable
internal fun ReglaDeTramos(
    regla: ReglaCarretera,
    destinos: Map<SentidoVia, String>,
    seleccionado: String?,
    onTramo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val carriles =
        regla.franjas
            .groupBy { it.sentido }
            .toList()
            .sortedBy { it.first.ordinal }
    Column(modifier.testTag(ETIQUETA_FICHA_REGLA), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        carriles.forEach { (sentido, franjas) ->
            Column {
                Text(
                    etiquetaSentido(sentido, destinos[sentido]),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Espacio.xs),
                )
                Carril(regla, franjas, destinos[sentido], seleccionado, onTramo)
            }
        }
        MarcasDeKm(regla)
    }
}

@Composable
private fun Carril(
    regla: ReglaCarretera,
    franjas: List<FranjaRegla>,
    destino: String?,
    seleccionado: String?,
    onTramo: (String) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(ALTO_CARRIL)
            .clip(RoundedCornerShape(ALTO_FRANJA / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        franjas.forEach { franja ->
            val inicio = (maxWidth * ((franja.desdeKm - regla.minKm) / regla.largoKm).toFloat())
            val ancho = (maxWidth * ((franja.hastaKm - franja.desdeKm) / regla.largoKm).toFloat())
            Franja(franja, GeometriaFranja(inicio, ancho, maxWidth), destino, franja.tramoId == seleccionado, onTramo)
        }
    }
}

/** Dónde cae una franja en la regla: su inicio, su ancho y el ancho total de la regla. */
private data class GeometriaFranja(
    val inicio: Dp,
    val ancho: Dp,
    val anchoTotal: Dp,
)

@Composable
private fun Franja(
    franja: FranjaRegla,
    geometria: GeometriaFranja,
    destino: String?,
    elegida: Boolean,
    onTramo: (String) -> Unit,
) {
    val anchoFranja = geometria.ancho.coerceAtLeast(ANCHO_MINIMO_FRANJA)
    val x = geometria.inicio.coerceAtMost(geometria.anchoTotal - anchoFranja).coerceAtLeast(0.dp)
    val rango = stringResource(R.string.ficha_rango_km, formatoKm(franja.desdeKm), formatoKm(franja.hastaKm))
    val descripcion =
        stringResource(R.string.ficha_franja_descripcion, rango, etiquetaSentido(franja.sentido, destino))
    val fondo = if (elegida) RojoAcento else MaterialTheme.colorScheme.primary.copy(alpha = OPACIDAD_FRANJA)
    val borde =
        if (elegida) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        }
    Box(
        Modifier
            .offset(x = x)
            .width(anchoFranja)
            .fillMaxHeight()
            .clickable(onClick = { onTramo(franja.tramoId) })
            .testTag(ETIQUETA_FICHA_FRANJA_PREFIJO + franja.tramoId)
            .clearAndSetSemantics {
                contentDescription = descripcion
                role = Role.Button
                selected = elegida
            },
    ) {
        Box(
            Modifier
                .offset(y = (ALTO_CARRIL - ALTO_FRANJA) / 2)
                .fillMaxWidth()
                .height(ALTO_FRANJA)
                .clip(RoundedCornerShape(ALTO_FRANJA / 4))
                .background(fondo)
                .border(borde, RoundedCornerShape(ALTO_FRANJA / 4)),
        )
    }
}

/** Cinco marcas repartidas por igual de un extremo al otro de la regla, con sus kilómetros redondeados. */
@Composable
private fun MarcasDeKm(regla: ReglaCarretera) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0..MARCAS) {
            Text(
                formatoMarcaKm(regla.minKm + regla.largoKm * i / MARCAS),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
