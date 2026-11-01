package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.alertas.AlertasPanel
import app.trombito.rallycopilot.ui.theme.Espacio

private val LADO_SENAL_COMPACTA = 48.dp

/** Lo que pinta el panel compacto, agrupado. */
internal class DatosCompactos(
    val velocidadKmh: Int?,
    val limiteKmh: Int?,
    val alertas: AlertasPanel,
    val izquierda: List<@Composable () -> Unit>,
    val derecha: List<@Composable () -> Unit>,
)

/**
 * Panel parado planificando: velocidad pequeña, límite en una señal chica y los botones en la misma fila (en vertical)
 * o en una fila bajo la velocidad (en la columna lateral). Mantiene las mismas etiquetas que el panel completo.
 */
@Composable
internal fun ContenidoCompacto(
    datos: DatosCompactos,
    enColumna: Boolean,
) {
    val centro: @Composable () -> Unit = {
        Velocidad(datos.velocidadKmh, datos.alertas, compacto = true)
        if (datos.limiteKmh != null) SenalLimite(datos.limiteKmh, LADO_SENAL_COMPACTA)
    }
    if (enColumna) {
        Column(
            modifier = Modifier.padding(horizontal = Espacio.s, vertical = Espacio.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espacio.xs, Alignment.CenterVertically),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.m),
            ) { centro() }
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                (datos.izquierda + datos.derecha).forEach { it() }
            }
        }
    } else {
        Row(
            modifier = Modifier.padding(horizontal = Espacio.s, vertical = Espacio.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            datos.izquierda.forEach { it() }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.m, Alignment.CenterHorizontally),
            ) { centro() }
            datos.derecha.forEach { it() }
        }
    }
}

/** La cifra y la unidad: apiladas y grandes, o en una línea y pequeñas en el panel [compacto]. */
@Composable
internal fun TextoVelocidad(
    texto: String,
    cifra: Color,
    unidad: Color,
    compacto: Boolean,
    modifier: Modifier,
) {
    if (compacto) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            Text(
                text = texto,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp, lineHeight = 44.sp),
                fontWeight = FontWeight.Black,
                color = cifra,
            )
            Text(
                text = stringResource(R.string.panel_unidad),
                style = MaterialTheme.typography.labelLarge,
                color = unidad,
                modifier = Modifier.padding(bottom = Espacio.xs),
            )
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
            Text(
                text = texto,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp, lineHeight = 92.sp),
                fontWeight = FontWeight.Black,
                color = cifra,
            )
            Text(
                text = stringResource(R.string.panel_unidad),
                style = MaterialTheme.typography.titleMedium,
                color = unidad,
            )
        }
    }
}
