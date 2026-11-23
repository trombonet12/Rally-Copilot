package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoUiState
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono

private val LADO_ICONO = 28.dp

/** Interruptores con icono y su nombre debajo: cruces, puentes, salidas y entradas de autopista y rotondas. */
@Composable
internal fun InterruptoresIndicaciones(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.l)) {
        InterruptorIndicacion(
            icono = R.drawable.ic_nota_cruce,
            descripcion = stringResource(R.string.indicaciones_cruces),
            etiqueta = stringResource(R.string.rapidos_indicacion_cruces),
            activado = estado.avisarCruces,
            onCambio = acciones.onAvisarCruces,
            prueba = ETIQUETA_AVISAR_CRUCES,
        )
        InterruptorIndicacion(
            icono = R.drawable.ic_nota_puente,
            descripcion = stringResource(R.string.indicaciones_puentes),
            etiqueta = stringResource(R.string.rapidos_indicacion_puentes),
            activado = estado.avisarPuentes,
            onCambio = acciones.onAvisarPuentes,
            prueba = ETIQUETA_AVISAR_PUENTES,
        )
        InterruptorIndicacion(
            icono = R.drawable.ic_nota_salida_autopista,
            descripcion = stringResource(R.string.indicaciones_salidas_autopista),
            etiqueta = stringResource(R.string.rapidos_indicacion_autopista),
            activado = estado.avisarSalidasAutopista,
            onCambio = acciones.onAvisarSalidasAutopista,
            prueba = ETIQUETA_AVISAR_SALIDAS_AUTOPISTA,
        )
        InterruptorIndicacion(
            icono = R.drawable.ic_nota_rotonda,
            descripcion = stringResource(R.string.indicaciones_rotondas),
            etiqueta = stringResource(R.string.rapidos_indicacion_rotondas),
            activado = estado.avisarRotondas,
            onCambio = acciones.onAvisarRotondas,
            prueba = ETIQUETA_AVISAR_ROTONDAS,
        )
    }
}

/** Salida del cronómetro: automática (al moverse) o manual (botón SALIDA y cuenta atrás); manual = seleccionado. */
@Composable
internal fun InterruptorSalida(
    manual: Boolean,
    onCambio: (Boolean) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BotonIcono(
            descripcion = stringResource(R.string.salida_modo_titulo),
            onClick = { onCambio(!manual) },
            modifier = Modifier.testTag(ETIQUETA_SALIDA_MANUAL),
            estado =
                EstadoBotonIcono(
                    seleccionado = manual,
                    descripcionEstado =
                        stringResource(if (manual) R.string.salida_modo_manual else R.string.salida_modo_automatica),
                    rol = Role.Switch,
                ),
        ) { Icon(Iconos.Semaforo, contentDescription = null, modifier = Modifier.size(LADO_ICONO)) }
        Text(
            text = stringResource(if (manual) R.string.salida_modo_manual else R.string.salida_modo_automatica),
            style = EtiquetaIcono,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
@Suppress("LongParameterList") // icono, textos, estado y etiqueta de prueba del interruptor
private fun InterruptorIndicacion(
    icono: Int,
    descripcion: String,
    etiqueta: String,
    activado: Boolean,
    onCambio: (Boolean) -> Unit,
    prueba: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BotonIcono(
            descripcion = descripcion,
            onClick = { onCambio(!activado) },
            modifier = Modifier.testTag(prueba),
            estado =
                EstadoBotonIcono(
                    seleccionado = activado,
                    descripcionEstado = stringResource(estadoIndicacion(activado)),
                    rol = Role.Switch,
                ),
        ) { Icon(painterResource(icono), contentDescription = null, modifier = Modifier.size(LADO_ICONO)) }
        Text(
            text = etiqueta,
            style = EtiquetaIcono,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

private fun estadoIndicacion(activado: Boolean): Int =
    if (activado) R.string.indicaciones_estado_activo else R.string.indicaciones_estado_inactivo
