package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.ui.analisis.formatearKmDesdeM
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.edicion.AccionesExtraEditor
import app.trombito.rallycopilot.ui.edicion.nombreAviso
import app.trombito.rallycopilot.ui.navegacion.notas.iconoAviso
import app.trombito.rallycopilot.ui.theme.Espacio

private val ALTO_FILA_AVISO = 56.dp

/**
 * Lo que ofrece el editor además de corregir la nota: con una nota abierta, fusionarla con la siguiente (si la hay) y
 * dividirla; con un punto del trazo, dividir la curva que lo contiene (si hay una) y añadir un aviso.
 */
internal fun extrasDelEditor(
    abierto: EditorDeTramo,
    notas: List<Nota>,
    acciones: AccionesEditarTramo,
): AccionesExtraEditor {
    val nota = abierto.nota
    return if (nota != null) {
        val indice = notas.indexOfFirst { it.posicionM == nota.posicionM }
        AccionesExtraEditor(
            onFusionar = acciones.onFusionar.takeIf { indice in 0 until notas.lastIndex },
            onDividir = acciones.onDividir,
        )
    } else {
        val sobreCurva = notas.any { abierto.posicionM > it.posicionM && abierto.posicionM < it.finM }
        AccionesExtraEditor(
            onDividir = acciones.onDividir.takeIf { sobreCurva },
            dividirEnElPunto = true,
            onAnadirAviso = acciones.onAnadirAviso,
        )
    }
}

/** Un aviso suelto de la hoja (automático o añadido): su icono, su nombre, dónde está y quitarlo. */
@Composable
internal fun FilaAviso(
    aviso: Aviso,
    onQuitar: (Double) -> Unit,
) {
    val nombre = nombreAviso(aviso.tipo)
    val km = formatearKmDesdeM(aviso.posicionM)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA_AVISO)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = Espacio.m)
                .testTag(ETIQUETA_EDITAR_AVISO + aviso.posicionM.toInt()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        Icon(
            painter = painterResource(iconoAviso(aviso.tipo)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(text = nombre, style = MaterialTheme.typography.titleSmall)
            Text(
                text = km,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onQuitar(aviso.posicionM) },
            modifier = Modifier.size(48.dp).testTag(ETIQUETA_EDITAR_AVISO_QUITAR + aviso.posicionM.toInt()),
        ) {
            Icon(
                Iconos.Borrar,
                contentDescription = stringResource(R.string.edicion_quitar_aviso_descripcion, nombre, km),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
