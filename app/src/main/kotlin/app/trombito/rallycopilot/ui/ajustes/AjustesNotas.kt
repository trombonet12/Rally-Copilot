package app.trombito.rallycopilot.ui.ajustes

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
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono

const val ETIQUETA_ANTICIPACION_PREFIJO = "ajustes-anticipacion-"
const val ETIQUETA_NOTAS_SIMPLIFICADAS = "ajustes-notas-simplificadas"
const val ETIQUETA_NOTAS_GRANDES = "ajustes-notas-grandes"

/**
 * Notas: anticipación (cinco niveles con los segundos bajo cada icono), notas simplificadas y notas grandes. La vista
 * previa de arriba enseña la nota y dónde suena el aviso.
 */
@Composable
internal fun ControlesNotas(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    Opcion(R.string.ajustes_anticipacion_titulo, R.string.ajustes_anticipacion_detalle) {
        FilaOpciones {
            NivelAnticipacion.entries.forEach { nivel ->
                BotonIconoEtiquetado(
                    etiqueta = stringResource(R.string.ajustes_segundos_etiqueta, segundosEscritos(nivel)),
                    descripcion = stringResource(nivel.nombre()),
                    seleccionado = nivel == ajustes.anticipacion,
                    onClick = { acciones.onAnticipacion(nivel) },
                    etiquetaPrueba = ETIQUETA_ANTICIPACION_PREFIJO + nivel.name.lowercase(),
                    modifier = Modifier.weight(1f),
                ) { Icon(painterResource(nivel.icono()), contentDescription = null, modifier = Modifier.size(32.dp)) }
            }
        }
    }
    FilaInterruptor(
        titulo = stringResource(R.string.ajustes_notas_simplificadas),
        marcado = ajustes.notasSimplificadas,
        onCambio = acciones.onNotasSimplificadas,
        etiqueta = ETIQUETA_NOTAS_SIMPLIFICADAS,
        detalle = stringResource(R.string.ajustes_simplificadas_corto),
        info = stringResource(R.string.ajustes_simplificadas_detalle),
    )
    FilaInterruptor(
        titulo = stringResource(R.string.ajustes_notas_grandes),
        marcado = ajustes.notasGrandes,
        onCambio = acciones.onNotasGrandes,
        etiqueta = ETIQUETA_NOTAS_GRANDES,
        detalle = stringResource(R.string.ajustes_notas_grandes_corto),
        info = stringResource(R.string.ajustes_notas_grandes_detalle),
    )
}

/** Los cinco niveles de anticipación como botones de icono (barras de señal), con su nombre debajo si se pide. */
@Composable
internal fun OpcionesAnticipacion(
    elegido: NivelAnticipacion,
    onAnticipacion: (NivelAnticipacion) -> Unit,
    modifier: Modifier = Modifier,
    conEtiquetas: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.Top,
    ) {
        NivelAnticipacion.entries.forEach { nivel ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BotonIcono(
                    descripcion = stringResource(nivel.nombre()),
                    onClick = { onAnticipacion(nivel) },
                    modifier = Modifier.testTag(ETIQUETA_ANTICIPACION_PREFIJO + nivel.name.lowercase()),
                    estado = estadoSeleccion(nivel == elegido),
                ) { Icon(painterResource(nivel.icono()), contentDescription = null, modifier = Modifier.size(32.dp)) }
                if (conEtiquetas) {
                    Text(
                        text = stringResource(nivel.nombre()),
                        style = EtiquetaIcono,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
