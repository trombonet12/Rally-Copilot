package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_ANTICIPACION_PREFIJO = "ajustes-anticipacion-"
const val ETIQUETA_NOTAS_SIMPLIFICADAS = "ajustes-notas-simplificadas"

/** Anticipación (5 niveles, solo iconos) e interruptor de notas simplificadas, con la vista previa de la nota. */
@Composable
internal fun SeccionNotas(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    TarjetaSeccion(
        clave = "notas",
        icono = Iconos.Notas,
        titulo = R.string.ajustes_seccion_notas,
        detalle = R.string.ajustes_seccion_notas_detalle,
        vistaPrevia = {
            VistaPreviaNotas(ajustes.anticipacion, ajustes.notasSimplificadas, acciones.onEscucharEjemplo)
        },
    ) {
        Opcion(R.string.ajustes_anticipacion_titulo, R.string.ajustes_anticipacion_detalle) {
            OpcionesAnticipacion(ajustes.anticipacion, acciones.onAnticipacion)
        }
        FilaInterruptor(
            titulo = stringResource(R.string.ajustes_notas_simplificadas),
            marcado = ajustes.notasSimplificadas,
            onCambio = acciones.onNotasSimplificadas,
            etiqueta = ETIQUETA_NOTAS_SIMPLIFICADAS,
            detalle = stringResource(R.string.ajustes_simplificadas_detalle),
        )
    }
}

/** Los cinco niveles de anticipación como botones de icono (barras de señal). */
@Composable
internal fun OpcionesAnticipacion(
    elegido: NivelAnticipacion,
    onAnticipacion: (NivelAnticipacion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NivelAnticipacion.entries.forEach { nivel ->
            BotonIcono(
                descripcion = stringResource(nivel.nombre()),
                onClick = { onAnticipacion(nivel) },
                modifier = Modifier.testTag(ETIQUETA_ANTICIPACION_PREFIJO + nivel.name.lowercase()),
                estado = estadoSeleccion(nivel == elegido),
            ) { Icon(painterResource(nivel.icono()), contentDescription = null, modifier = Modifier.size(32.dp)) }
        }
    }
}

/** Volumen propio del copiloto (50–150 %), con ▶ para oírlo con el volumen elegido. */
@Composable
internal fun SeccionVoz(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    TarjetaSeccion(
        clave = "voz",
        icono = Iconos.Volumen,
        titulo = R.string.ajustes_seccion_voz,
        detalle = R.string.ajustes_seccion_voz_detalle,
        vistaPrevia = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                ControlVolumen(ajustes.volumenVoz, acciones.onVolumenVoz)
                BotonEscucharEjemplo(acciones.onEscucharEjemplo)
            }
        },
    ) {
        Text(
            text = stringResource(R.string.ajustes_volumen_aviso),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun FilaInterruptor(
    titulo: String,
    marcado: Boolean,
    onCambio: (Boolean) -> Unit,
    etiqueta: String,
    detalle: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(etiqueta),
    ) {
        Row(
            modifier =
                Modifier
                    .toggleable(value = marcado, role = Role.Switch, onValueChange = onCambio)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = Espacio.l, vertical = Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (detalle != null) {
                    Text(
                        text = detalle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = marcado, onCheckedChange = null)
        }
    }
}
