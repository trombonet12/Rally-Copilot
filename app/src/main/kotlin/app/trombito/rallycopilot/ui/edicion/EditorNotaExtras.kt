package app.trombito.rallycopilot.ui.edicion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.notas.LONGITUD_MAX_TEXTO_LIBRE
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.ui.navegacion.notas.iconoAviso
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.RallyOscuro

const val ETIQUETA_EDITOR_TEXTO = "editor-nota-texto"
const val ETIQUETA_EDITOR_FUSIONAR = "editor-nota-fusionar"
const val ETIQUETA_EDITOR_DIVIDIR = "editor-nota-dividir"
const val ETIQUETA_EDITOR_MODO_NOTA = "editor-modo-nota"
const val ETIQUETA_EDITOR_MODO_AVISO = "editor-modo-aviso"
const val ETIQUETA_EDITOR_AVISO_PREFIJO = "editor-aviso-"

private val ALTO_AVISO = 80.dp
private val ICONO_AVISO = 28.dp
private val ALTO_BOTON = 48.dp
private const val AVISOS_POR_FILA = 4

/**
 * Acciones del editor que solo ofrece el detalle del tramo; las que sean `null` no se muestran. [onFusionar] une la
 * nota con la siguiente; [onDividir] la parte en dos (por el punto tocado si [dividirEnElPunto], si no por la mitad);
 * [onAnadirAviso] añade un aviso del tipo elegido en el punto (solo con una nota nueva).
 */
data class AccionesExtraEditor(
    val onFusionar: (() -> Unit)? = null,
    val onDividir: (() -> Unit)? = null,
    val dividirEnElPunto: Boolean = false,
    val onAnadirAviso: ((TipoAviso) -> Unit)? = null,
)

/** Nombre del aviso para quien lo elige o lo lee: su frase con la primera en mayúscula. */
internal fun nombreAviso(tipo: TipoAviso): String = tipo.frase.replaceFirstChar { it.uppercase() }

/** Nota o aviso: qué se añade en el punto tocado. */
@Composable
internal fun SelectorModo(
    modoAviso: Boolean,
    alCambiar: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        FilterChip(
            selected = !modoAviso,
            onClick = { alCambiar(false) },
            label = { Text(stringResource(R.string.edicion_modo_nota)) },
            modifier = Modifier.testTag(ETIQUETA_EDITOR_MODO_NOTA),
        )
        FilterChip(
            selected = modoAviso,
            onClick = { alCambiar(true) },
            label = { Text(stringResource(R.string.edicion_modo_aviso)) },
            modifier = Modifier.testTag(ETIQUETA_EDITOR_MODO_AVISO),
        )
    }
}

/** Un icono por tipo de aviso, en filas de cuatro; tocar uno lo añade en el punto. */
@Composable
internal fun SelectorAvisos(alElegir: (TipoAviso) -> Unit) {
    Text(
        text = stringResource(R.string.edicion_aviso_ayuda),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TipoAviso.entries.chunked(AVISOS_POR_FILA).forEach { fila ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            fila.forEach { tipo -> CasillaAviso(tipo, alElegir, Modifier.weight(1f)) }
            repeat(AVISOS_POR_FILA - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun CasillaAviso(
    tipo: TipoAviso,
    alElegir: (TipoAviso) -> Unit,
    modifier: Modifier,
) {
    val nombre = nombreAviso(tipo)
    val descripcion = stringResource(R.string.edicion_aviso_descripcion, nombre)
    Surface(
        onClick = { alElegir(tipo) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            modifier
                .height(ALTO_AVISO)
                .testTag(ETIQUETA_EDITOR_AVISO_PREFIJO + tipo.name)
                .semantics { contentDescription = descripcion },
    ) {
        Column(
            modifier = Modifier.padding(Espacio.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconoAviso(tipo)),
                contentDescription = null,
                tint = if (tipo.esCuidado) RallyOscuro.aviso else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(ICONO_AVISO),
            )
            Text(
                text = nombre,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Campo del texto libre con su contador; vacío, el copiloto canta la frase generada. */
@Composable
internal fun CampoTexto(
    borrador: BorradorNota,
    alCambiar: (BorradorNota) -> Unit,
) {
    OutlinedTextField(
        value = borrador.texto,
        onValueChange = { alCambiar(borrador.conTexto(it)) },
        label = { Text(stringResource(R.string.edicion_texto_libre)) },
        supportingText = {
            Text(stringResource(R.string.edicion_texto_libre_ayuda, borrador.texto.length, LONGITUD_MAX_TEXTO_LIBRE))
        },
        maxLines = 2,
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_EDITOR_TEXTO),
    )
}

/** Fusionar con la siguiente y dividir en dos, para la nota que se corrige o la curva bajo el punto tocado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccionesEstructura(
    extras: AccionesExtraEditor,
    nueva: Boolean,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        extras.onFusionar?.let {
            OutlinedButton(
                onClick = it,
                modifier = Modifier.heightIn(min = ALTO_BOTON).testTag(ETIQUETA_EDITOR_FUSIONAR),
            ) { Text(stringResource(R.string.edicion_fusionar)) }
        }
        extras.onDividir?.let {
            val enElPunto = nueva || extras.dividirEnElPunto
            OutlinedButton(
                onClick = it,
                modifier = Modifier.heightIn(min = ALTO_BOTON).testTag(ETIQUETA_EDITOR_DIVIDIR),
            ) { Text(stringResource(if (enElPunto) R.string.edicion_dividir_aqui else R.string.edicion_dividir)) }
        }
    }
}
