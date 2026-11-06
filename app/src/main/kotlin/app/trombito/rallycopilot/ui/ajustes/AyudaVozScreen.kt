package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.FondoWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_AYUDA_VOZ_GRUPO_PREFIJO = "ayuda-voz-grupo-"
const val ETIQUETA_AYUDA_VOZ_LIMITES = "ayuda-voz-limites"

private val ICONO_FRASE = 20.dp

/**
 * «Qué le puedes decir»: la chuleta de voz agrupada (preguntas de ruta, control del copiloto, navegación, mapa y
 * crono, edición de notas) con ejemplos, y al final lo que el copiloto no puede hacer.
 */
@Composable
fun AyudaVozScreen(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FondoWrc(modifier) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BarraSuperior(stringResource(R.string.ayuda_voz_titulo), onVolver)
            Column(
                modifier =
                    Modifier
                        .verticalScroll(
                            rememberScrollState(),
                        ).padding(horizontal = Espacio.l)
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
                        ChuletaVoz
                            .frases(
                                grupo,
                                sueltas.map { it.trim('«', '»') },
                                stringResource(R.string.ayuda_voz_ejemplo_lugar),
                                stringResource(R.string.ayuda_voz_ejemplo_parada),
                            ).forEach { (frase, pregunta) -> FraseDeVoz(ChuletaVoz.formato(frase, pregunta)) }
                    }
                }
                TarjetaAyuda(
                    titulo = stringResource(R.string.ayuda_voz_grupo_limites),
                    etiqueta = ETIQUETA_AYUDA_VOZ_LIMITES,
                    acentoRojo = true,
                ) {
                    LIMITES_VOZ.forEach {
                        Text(
                            stringResource(it),
                            style = Ayuda,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Una frase de ejemplo con el icono del micrófono. */
@Composable
private fun FraseDeVoz(frase: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Icon(
            imageVector = Iconos.Microfono,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICONO_FRASE),
        )
        Text(frase, style = Cuerpo, color = MaterialTheme.colorScheme.onSurface)
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
