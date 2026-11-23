package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.PantallaWrc
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios

const val ETIQUETA_AYUDA_VOZ_GRUPO_PREFIJO = "ayuda-voz-grupo-"
const val ETIQUETA_AYUDA_VOZ_LIMITES = "ayuda-voz-limites"

/**
 * «Qué le puedes decir»: la chuleta de voz agrupada (preguntas de ruta, control del copiloto, navegación, mapa y
 * crono, edición de notas) como chips en dos columnas con el icono de lo que hace cada orden (las parejas sin/con
 * comparten fila), y al final lo que el copiloto no puede hacer.
 */
@Composable
fun AyudaVozScreen(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PantallaWrc(titulo = stringResource(R.string.ayuda_voz_titulo), onVolver = onVolver, modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Espacio.l)
                    .padding(bottom = Espacio.l),
            verticalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            Text(
                stringResource(R.string.ayuda_voz_intro),
                style = Cuerpo,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GrupoVoz.entries.forEach { grupo ->
                TarjetaAyuda(
                    titulo = stringResource(grupo.titulo),
                    etiqueta = ETIQUETA_AYUDA_VOZ_GRUPO_PREFIJO + grupo.name.lowercase(),
                ) {
                    val sueltas =
                        when (grupo) {
                            GrupoVoz.CONTROL -> stringArrayResource(R.array.ayuda_voz_control_sueltas).toList()
                            GrupoVoz.EDICION -> stringArrayResource(R.array.ayuda_voz_edicion).toList()
                            else -> emptyList()
                        }
                    RejillaChipsVoz(
                        chipsDeGrupo(
                            grupo,
                            sueltas.map { it.trim('«', '»') },
                            stringResource(R.string.ayuda_voz_ejemplo_lugar),
                            stringResource(R.string.ayuda_voz_ejemplo_parada),
                        ),
                    )
                }
            }
            TarjetaAyuda(
                titulo = stringResource(R.string.ayuda_voz_grupo_limites),
                etiqueta = ETIQUETA_AYUDA_VOZ_LIMITES,
                acentoRojo = true,
            ) {
                LIMITES_VOZ.forEach {
                    Text(stringResource(it), style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Tarjeta de ayuda con su título de sección (encabezado para TalkBack) y el contenido debajo. */
@Composable
internal fun TarjetaAyuda(
    titulo: String,
    etiqueta: String,
    modifier: Modifier = Modifier,
    acentoRojo: Boolean = false,
    contenido: @Composable () -> Unit,
) {
    TarjetaWrc(modifier = modifier.testTag(etiqueta), acentoRojo = acentoRojo, relleno = Espacio.m) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Text(
                text = titulo.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            contenido()
        }
    }
}

/** Las [filas] de chips en dos columnas iguales; una fila de un solo chip deja el hueco de al lado vacío. */
@Composable
internal fun RejillaChipsVoz(chips: List<ChipVoz>) {
    filasDeChips(chips).forEach { fila ->
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            fila.forEach { ChipFraseVoz(it, Modifier.weight(1f).fillMaxHeight()) }
            if (fila.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Chip informativo de una frase: icono a la izquierda y el texto, que puede ocupar dos líneas. */
@Composable
private fun ChipFraseVoz(
    chip: ChipVoz,
    modifier: Modifier = Modifier,
) {
    val colores = MaterialTheme.colorScheme
    Surface(
        shape = Radios.formaPildora,
        color = colores.surfaceContainerHigh,
        contentColor = colores.onSurface,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            modifier =
                Modifier
                    .defaultMinSize(minHeight = Medidas.zonaTactil - Espacio.s)
                    .padding(horizontal = Espacio.m, vertical = Espacio.xs),
        ) {
            Icon(
                painter = painterDeIcono(chip.icono),
                contentDescription = null,
                tint = colores.primary,
                modifier = Modifier.size(Medidas.iconoLista),
            )
            Text(text = chip.texto, style = MaterialTheme.typography.labelLarge, maxLines = 3)
        }
    }
}

@Composable
private fun painterDeIcono(icono: IconoVoz): Painter =
    icono.vector?.let { rememberVectorPainter(it) } ?: painterResource(icono.dibujo)
