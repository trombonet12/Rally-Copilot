package app.trombito.rallycopilot.ui.reconocimiento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.HojaInferiorWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Radios

const val ETIQUETA_HOJA_RECONOCER = "reconocer-hoja"
const val ETIQUETA_HOJA_RECONOCER_EMPEZAR = "reconocer-hoja-empezar"
const val ETIQUETA_HOJA_RECONOCER_CANCELAR = "reconocer-hoja-cancelar"

private val ALTO_TACTIL = 48.dp
private val LADO_ICONO_PASO = 28.dp
private const val FRACCION_ALTO_MAXIMO = 0.9f

/** Un paso de la explicación: icono, título corto y frase. */
private data class PasoReconocer(
    val icono: ImageVector,
    val titulo: Int,
    val texto: Int,
)

private val PASOS =
    listOf(
        PasoReconocer(Iconos.Microfono, R.string.reconocer_paso1_titulo, R.string.reconocer_paso1_texto),
        PasoReconocer(Iconos.Editar, R.string.reconocer_paso2_titulo, R.string.reconocer_paso2_texto),
        PasoReconocer(Iconos.Repetir, R.string.reconocer_paso3_titulo, R.string.reconocer_paso3_texto),
        PasoReconocer(Iconos.Cronometro, R.string.reconocer_paso4_titulo, R.string.reconocer_paso4_texto),
    )

/**
 * Hoja inferior que se enseña antes de empezar el reconocimiento de un tramo: cómo son las dos pasadas, qué se graba
 * y cómo se corrigen las notas, con un icono por paso. [onEmpezar] salta al mapa; [onCancelar] (también al descartar
 * la hoja) la cierra sin hacer nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaReconocer(
    onEmpezar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val altoVentana = LocalWindowInfo.current.containerSize.height
    val altoMaximo = with(LocalDensity.current) { (altoVentana * FRACCION_ALTO_MAXIMO).toDp() }
    ModalBottomSheet(
        onDismissRequest = onCancelar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier.testTag(ETIQUETA_HOJA_RECONOCER),
        sheetMaxWidth = Dp.Unspecified,
        shape = Radios.formaHoja,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        HojaInferiorWrc(Modifier.heightIn(max = altoMaximo)) {
            Text(
                text = stringResource(R.string.reconocer_titulo),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AmbarSobreCarbon,
            )
            Spacer(Modifier.height(Espacio.m))
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                PASOS.forEach { Paso(it) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Espacio.l),
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                OutlinedButton(
                    onClick = onCancelar,
                    modifier =
                        Modifier
                            .weight(
                                1f,
                            ).heightIn(min = ALTO_TACTIL)
                            .testTag(ETIQUETA_HOJA_RECONOCER_CANCELAR),
                ) { Text(stringResource(R.string.reconocer_cancelar), maxLines = 1) }
                Button(
                    onClick = onEmpezar,
                    modifier = Modifier.weight(1f).heightIn(min = ALTO_TACTIL).testTag(ETIQUETA_HOJA_RECONOCER_EMPEZAR),
                ) {
                    Icon(Iconos.Empezar, contentDescription = null, modifier = Modifier.padding(end = Espacio.xs))
                    Text(stringResource(R.string.reconocer_empezar), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Paso(paso: PasoReconocer) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = paso.icono,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(LADO_ICONO_PASO),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(paso.titulo),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(paso.texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
