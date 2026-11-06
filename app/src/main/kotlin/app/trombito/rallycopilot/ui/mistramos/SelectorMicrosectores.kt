package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.tramos.CortesTramo
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_EDITAR_MICRO_MAS = "editar-tramo-micro-mas"
const val ETIQUETA_EDITAR_MICRO_MENOS = "editar-tramo-micro-menos"
const val ETIQUETA_EDITAR_MICRO_VALOR = "editar-tramo-micro-valor"

private val LADO_BOTON_MICRO = 56.dp

/**
 * Cuántos microsectores por kilómetro hay tras pulsar «más» ([masMicrosectores] verdadero) o «menos»: de 0 al máximo.
 * Más microsectores = parciales más cortos, así que [porKm] sube con «+» y baja con «−».
 */
internal fun microsectoresTrasPulsar(
    porKm: Int,
    masMicrosectores: Boolean,
): Int = (porKm + if (masMicrosectores) 1 else -1).coerceIn(0, CortesTramo.MICROSECTORES_POR_KM_MAX)

/** Metros entre un microsector y el siguiente con [porKm] por kilómetro; null sin microsectores. */
internal fun separacionMicrosectoresM(porKm: Int): Int? = if (porKm > 0) (Tramo.MICROSECTOR_M / porKm).toInt() else null

@Composable
internal fun SelectorMicrosectores(
    porKm: Int,
    alCambiar: (Int) -> Unit,
) {
    val separacion = separacionMicrosectoresM(porKm)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Espacio.m), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Text(
                text = stringResource(R.string.edicion_microsectores),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.edicion_microsectores_ayuda),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotonMicrosectores(Iconos.Menos, R.string.edicion_menos, porKm > 0, ETIQUETA_EDITAR_MICRO_MENOS) {
                    alCambiar(microsectoresTrasPulsar(porKm, masMicrosectores = false))
                }
                Column(
                    Modifier.weight(1f).testTag(ETIQUETA_EDITAR_MICRO_VALOR).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text =
                            if (separacion == null) {
                                stringResource(R.string.edicion_microsectores_ninguno)
                            } else {
                                stringResource(R.string.edicion_microsectores_valor, porKm)
                            },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    separacion?.let {
                        Text(
                            text = stringResource(R.string.edicion_microsectores_cada, it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                BotonMicrosectores(
                    Iconos.Mas,
                    R.string.edicion_mas,
                    porKm < CortesTramo.MICROSECTORES_POR_KM_MAX,
                    ETIQUETA_EDITAR_MICRO_MAS,
                ) { alCambiar(microsectoresTrasPulsar(porKm, masMicrosectores = true)) }
            }
        }
    }
}

@Composable
private fun BotonMicrosectores(
    icono: ImageVector,
    descripcion: Int,
    activo: Boolean,
    etiqueta: String,
    alPulsar: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = alPulsar,
        enabled = activo,
        modifier = Modifier.size(LADO_BOTON_MICRO).testTag(etiqueta),
    ) { Icon(icono, stringResource(descripcion)) }
}
