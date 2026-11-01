package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.motor.notas.NotaVisible

const val ETIQUETA_FRANJA_NOTAS = "notas-franja"
const val ETIQUETA_NOTA_PREFIJO = "notas-fila-"
const val ETIQUETA_NOTA_CUIDADO_PREFIJO = "notas-cuidado-"

/** Distancia hasta la nota («120 m»); solo la lleva la tarjeta grande: `notas-distancia-0`. */
const val ETIQUETA_NOTA_DISTANCIA_PREFIJO = "notas-distancia-"

/** Icono de un aviso unido (o del puente de la curva) de la nota: `notas-aviso-<fila>-<TIPO>`. */
const val ETIQUETA_NOTA_AVISO_PREFIJO = "notas-aviso-"

private const val PESO_GRANDE = 1.5f

/**
 * Franja de notas bajo el banner de maniobra (o arriba del todo en modo libre): la próxima nota grande, con su icono de
 * roadbook, el texto en negrita cursiva y la distancia a la siguiente («DERECHA 4 LARGA · 50»), la distancia hasta ella
 * a la derecha («120 m») y las dos siguientes en pequeño, sin distancia. La que ya toca cantar ([NotaVisible.activa])
 * va resaltada en latón y las notas de «cuidado» en ámbar. El texto es solo la curva: los avisos unidos y el cuidado
 * van como iconos. Con [compacta] (pantalla apaisada) todo va en una sola fila, la nota grande y la siguiente.
 * No pinta nada si no hay notas.
 */
@Composable
fun FranjaNotas(
    estado: NotasUiState,
    modifier: Modifier = Modifier,
    compacta: Boolean = false,
) {
    val primera = estado.visibles.firstOrNull() ?: return
    val siguientes = estado.visibles.drop(1)
    if (compacta) {
        FranjaCompacta(primera, siguientes.firstOrNull(), estado.simplificadas, modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_FRANJA_NOTAS),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilaNota(primera, 0, estado.simplificadas, MEDIDAS_GRANDE, Modifier.fillMaxWidth())
        if (siguientes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                siguientes.forEachIndexed { i, visible ->
                    FilaNota(visible, i + 1, estado.simplificadas, MEDIDAS_PEQUENA, Modifier.weight(1f))
                }
                // Con una sola nota pequeña no se estira a todo el ancho: ocupa la misma mitad que si hubiera dos.
                if (siguientes.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Una sola fila: la nota grande (más ancha) y, a su derecha, la siguiente en pequeño. */
@Composable
private fun FranjaCompacta(
    primera: NotaVisible,
    siguiente: NotaVisible?,
    simplificadas: Boolean,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_FRANJA_NOTAS),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilaNota(primera, 0, simplificadas, MEDIDAS_COMPACTA, Modifier.weight(PESO_GRANDE))
        if (siguiente != null) {
            FilaNota(siguiente, 1, simplificadas, MEDIDAS_PEQUENA, Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}
