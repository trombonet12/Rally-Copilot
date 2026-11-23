package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.FichaCarretera
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.PlacaCarretera
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import kotlin.math.roundToInt

/** Prefijo de la etiqueta de prueba de cada tarjeta: `carreteras-tarjeta-<id de la carretera>`. */
const val ETIQUETA_CARRETERAS_TARJETA_PREFIJO = "carreteras-tarjeta-"

private val ANCHO_PLACA = 72.dp

/**
 * La tarjeta de una carretera: su placa (o un icono si solo tiene nombre) y, debajo del nombre, lo esencial en una
 * línea: «86,0 km en 6 viajes · 58 km/h · 7 curvas/km», y cuándo fue la última vez. Toda ella es un botón que abre su
 * ficha.
 */
@Composable
internal fun TarjetaCarretera(
    ficha: FichaCarretera,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TarjetaWrc(
        modifier.testTag(ETIQUETA_CARRETERAS_TARJETA_PREFIJO + ficha.id),
        onClick = onClick,
    ) {
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            PlacaOIcono(ficha)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                // Sin nombre, la placa ya dice cuál es la carretera.
                ficha.nombre?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    resumenDeCarretera(ficha),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ficha.ultimaVezMs?.let {
                    Text(
                        stringResource(R.string.carreteras_ultima_vez, formatoFecha(it)),
                        style = Ayuda,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Iconos.Siguiente, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PlacaOIcono(ficha: FichaCarretera) {
    Box(Modifier.width(ANCHO_PLACA), contentAlignment = Alignment.CenterStart) {
        val ref = ficha.ref
        if (ref != null) {
            PlacaCarretera(ref)
        } else {
            Icon(
                Iconos.Curvas,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Medidas.iconoCabecera),
            )
        }
    }
}

/** «86,0 km en 6 viajes · 58 km/h · 7 curvas/km»: el ritmo y las curvas por km solo si se conocen. */
@Composable
internal fun resumenDeCarretera(ficha: FichaCarretera): String {
    val viajes = pluralStringResource(R.plurals.carreteras_viajes, ficha.viajes, ficha.viajes)
    val partes =
        listOfNotNull(
            stringResource(R.string.carreteras_km_en_viajes, FormatoEs.distancia(ficha.metros), viajes),
            ficha.velMediaKmh?.let { stringResource(R.string.carreteras_ritmo, it.roundToInt()) },
            curvasPorKmSiFiable(ficha)?.let {
                stringResource(R.string.carreteras_curvas_por_km, formatoCurvasPorKm(it))
            },
        )
    return partes.joinToString(" · ")
}

/** Curvas por km solo si hay curvas y la carretera tiene al menos 1 km conducido (con menos, el dato no dice nada). */
private fun curvasPorKmSiFiable(ficha: FichaCarretera): Double? =
    ficha.curvasPorKm?.takeIf { ficha.curvas.total > 0 && ficha.km >= 1.0 }
