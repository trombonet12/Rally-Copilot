package app.trombito.rallycopilot.ui.edicion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.LONGITUD_MAX_TEXTO_LIBRE
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.limpiarTextoLibre
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_EDITOR_NOTA = "editor-nota"
const val ETIQUETA_EDITOR_GUARDAR = "editor-nota-guardar"
const val ETIQUETA_EDITOR_QUITAR = "editor-nota-quitar"
const val ETIQUETA_EDITOR_GRADO_PREFIJO = "editor-nota-grado-"

/** Grado elegido en el editor: del 1 al 6 o horquilla (`null`). */
private val GRADOS = listOf(1, 2, 3, 4, 5, 6)

/** Grado de una escuadra. */
private const val GRADO_ESCUADRA = 3

/**
 * Lo que se está editando de una nota: [direccion], [grado] (`null` = horquilla), [modificadores], [cuidado] y el
 * [texto] libre (vacío = se canta la frase generada). La nota resultante ([aNota]) no lleva posición: la pone quien la
 * guarda (el tramo conserva la de la automática).
 */
data class BorradorNota(
    val direccion: Direccion = Direccion.DERECHA,
    val grado: Int? = 4,
    val modificadores: Set<Modificador> = emptySet(),
    val cuidado: Boolean = false,
    val texto: String = "",
) {
    fun aNota(): Nota =
        Nota(0.0, 0.0, direccion, grado, modificadores, cuidado = cuidado, textoLibre = limpiarTextoLibre(texto))

    /** Pone el [texto] que se está escribiendo: sin saltos de línea y con el largo máximo. */
    fun conTexto(texto: String): BorradorNota =
        copy(texto = texto.replace('\n', ' ').replace('\r', ' ').take(LONGITUD_MAX_TEXTO_LIBRE))

    /**
     * Pone o quita [modificador]; larga y muy larga se excluyen entre sí, y cierra y abre también. La escuadra es
     * grado 3 y excluye a los demás (se canta sola, sin número).
     */
    fun conModificador(modificador: Modificador): BorradorNota {
        val opuestos =
            when (modificador) {
                Modificador.ESCUADRA -> Modificador.entries.toSet() - Modificador.ESCUADRA
                Modificador.LARGA -> setOf(Modificador.MUY_LARGA, Modificador.ESCUADRA)
                Modificador.MUY_LARGA -> setOf(Modificador.LARGA, Modificador.ESCUADRA)
                Modificador.CIERRA -> setOf(Modificador.ABRE, Modificador.ESCUADRA)
                Modificador.ABRE -> setOf(Modificador.CIERRA, Modificador.ESCUADRA)
            }
        val nuevos =
            if (modificador in
                modificadores
            ) {
                modificadores - modificador
            } else {
                modificadores - opuestos + modificador
            }
        return copy(modificadores = nuevos, grado = if (Modificador.ESCUADRA in nuevos) GRADO_ESCUADRA else grado)
    }

    companion object {
        fun de(nota: Nota?): BorradorNota =
            nota?.let { BorradorNota(it.direccion, it.grado, it.modificadores, it.cuidado, it.textoLibre.orEmpty()) }
                ?: BorradorNota()
    }
}

/**
 * Hoja inferior para corregir o añadir una nota: dirección, grado (1 a 6 o horquilla), modificadores, cuidado y texto
 * libre, con la frase que dirá el copiloto al instante. [inicial] es la nota que se corrige (null: nota nueva);
 * [onQuitar] solo se ofrece al corregir una existente. [extras] suma fusionar, dividir y, en un punto nuevo, añadir un
 * aviso en vez de una nota.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList") // la nota, tres acciones y el aspecto: cada una la pone quien edita
fun EditorNota(
    inicial: Nota?,
    simplificadas: Boolean,
    onAplicar: (Nota) -> Unit,
    onQuitar: (() -> Unit)?,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    extras: AccionesExtraEditor = AccionesExtraEditor(),
) {
    var borrador by remember(inicial) { mutableStateOf(BorradorNota.de(inicial)) }
    var modoAviso by rememberSaveable { mutableStateOf(false) }
    val ofreceAviso = inicial == null && extras.onAnadirAviso != null
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier.testTag(ETIQUETA_EDITOR_NOTA),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(
                        rememberScrollState(),
                    ).padding(horizontal = Espacio.l, vertical = Espacio.s),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            val conAviso = ofreceAviso && modoAviso
            Text(
                text =
                    stringResource(
                        when {
                            conAviso -> R.string.edicion_titulo_aviso
                            inicial == null -> R.string.edicion_titulo_nueva
                            else -> R.string.edicion_titulo
                        },
                    ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (ofreceAviso) SelectorModo(modoAviso) { modoAviso = it }
            if (conAviso) {
                SelectorAvisos(checkNotNull(extras.onAnadirAviso))
            } else {
                ContenidoNota(borrador, simplificadas, extras, inicial == null) { borrador = it }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (onQuitar != null) {
                        TextButton(onClick = onQuitar, modifier = Modifier.testTag(ETIQUETA_EDITOR_QUITAR)) {
                            Text(stringResource(R.string.edicion_quitar))
                        }
                    }
                    TextButton(
                        onClick = { onAplicar(borrador.aNota()) },
                        modifier = Modifier.testTag(ETIQUETA_EDITOR_GUARDAR),
                    ) { Text(stringResource(R.string.edicion_guardar)) }
                }
            }
        }
    }
}

@Composable
private fun ContenidoNota(
    borrador: BorradorNota,
    simplificadas: Boolean,
    extras: AccionesExtraEditor,
    nueva: Boolean,
    alCambiar: (BorradorNota) -> Unit,
) {
    Text(
        text = stringResource(R.string.edicion_dira, FrasesNota.frase(borrador.aNota(), simplificadas)),
        style = MaterialTheme.typography.titleMedium,
    )
    SelectorDireccion(borrador, alCambiar)
    SelectorGrado(borrador, alCambiar)
    SelectorModificadores(borrador, alCambiar)
    CampoTexto(borrador, alCambiar)
    if (extras.onFusionar != null || extras.onDividir != null) AccionesEstructura(extras, nueva)
}

@Composable
private fun SelectorDireccion(
    borrador: BorradorNota,
    alCambiar: (BorradorNota) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Direccion.entries.forEach { direccion ->
            FilterChip(
                selected = borrador.direccion == direccion,
                onClick = { alCambiar(borrador.copy(direccion = direccion)) },
                label = {
                    Text(
                        stringResource(
                            if (direccion ==
                                Direccion.IZQUIERDA
                            ) {
                                R.string.edicion_izquierda
                            } else {
                                R.string.edicion_derecha
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun SelectorGrado(
    borrador: BorradorNota,
    alCambiar: (BorradorNota) -> Unit,
) {
    Text(stringResource(R.string.edicion_grado), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        GRADOS.forEach { grado ->
            FilterChip(
                selected = borrador.grado == grado,
                onClick = { alCambiar(borrador.copy(grado = grado)) },
                label = { Text(grado.toString()) },
                modifier = Modifier.testTag(ETIQUETA_EDITOR_GRADO_PREFIJO + grado),
            )
        }
        FilterChip(
            selected = borrador.grado == null,
            onClick = { alCambiar(borrador.copy(grado = null)) },
            label = { Text(stringResource(R.string.edicion_horquilla)) },
            modifier = Modifier.testTag(ETIQUETA_EDITOR_GRADO_PREFIJO + "horquilla"),
        )
    }
}

@Composable
private fun SelectorModificadores(
    borrador: BorradorNota,
    alCambiar: (BorradorNota) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Modificador.entries.forEach { modificador ->
            FilterChip(
                selected = modificador in borrador.modificadores,
                onClick = { alCambiar(borrador.conModificador(modificador)) },
                label = { Text(stringResource(textoModificador(modificador))) },
            )
        }
        FilterChip(
            selected = borrador.cuidado,
            onClick = { alCambiar(borrador.copy(cuidado = !borrador.cuidado)) },
            label = { Text(stringResource(R.string.edicion_cuidado)) },
        )
    }
}

private fun textoModificador(modificador: Modificador): Int =
    when (modificador) {
        Modificador.ESCUADRA -> R.string.edicion_escuadra
        Modificador.LARGA -> R.string.edicion_larga
        Modificador.MUY_LARGA -> R.string.edicion_muy_larga
        Modificador.CIERRA -> R.string.edicion_cierra
        Modificador.ABRE -> R.string.edicion_abre
    }
