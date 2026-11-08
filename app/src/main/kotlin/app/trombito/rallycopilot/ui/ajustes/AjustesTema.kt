package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.CifraMedia
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.theme.RojoRuta
import app.trombito.rallycopilot.ui.theme.oscuroAhora

const val ETIQUETA_TEMA_PREFIJO = "ajustes-tema-"
const val ETIQUETA_TEMA_MUESTRA = "ajustes-tema-muestra"
const val ETIQUETA_TEMA_MINIATURA_PREFIJO = "ajustes-tema-miniatura-"

private fun Tema.icono(): ImageVector =
    when (this) {
        Tema.OSCURO -> Iconos.TemaOscuro
        Tema.CLARO -> Iconos.TemaClaro
        Tema.SISTEMA -> Iconos.TemaSistema
        Tema.SOL -> Iconos.TemaSol
    }

/** Tema: oscuro, claro, el del móvil o el del sol (por defecto), con la etiqueta de cada uno bajo su icono. */
@Composable
internal fun ControlesTema(
    tema: Tema,
    onTema: (Tema) -> Unit,
) {
    Opcion(R.string.ajustes_seccion_tema, R.string.ajustes_tema_detalle) {
        FilaOpciones {
            Tema.entries.forEach { opcion ->
                BotonIconoEtiquetado(
                    etiqueta = stringResource(opcion.nombre()),
                    descripcion = stringResource(opcion.nombre()),
                    seleccionado = opcion == tema,
                    onClick = { onTema(opcion) },
                    etiquetaPrueba = ETIQUETA_TEMA_PREFIJO + opcion.name.lowercase(),
                    modifier = Modifier.weight(1f),
                ) { Icon(opcion.icono(), contentDescription = null) }
            }
        }
    }
}

/**
 * Dos miniaturas de pantalla de conducción, oscura y clara; se resalta la del tema elegido (con «Automático», la
 * que usa el móvil ahora; con «Día y noche», la que toca según el sol).
 */
@Composable
internal fun PrevistaTema(tema: Tema) {
    val oscuroElegido = tema.oscuroAhora()
    val descripcion = stringResource(R.string.ajustes_tema_muestra_descripcion, stringResource(tema.nombre()))
    Row(
        modifier = Modifier.fillMaxSize().testTag(ETIQUETA_TEMA_MUESTRA).semantics { contentDescription = descripcion },
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        MiniaturaTema(oscuro = true, elegida = oscuroElegido, modifier = Modifier.weight(1f))
        MiniaturaTema(oscuro = false, elegida = !oscuroElegido, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MiniaturaTema(
    oscuro: Boolean,
    elegida: Boolean,
    modifier: Modifier = Modifier,
) {
    val nombre = stringResource(if (oscuro) R.string.ajustes_tema_oscuro else R.string.ajustes_tema_claro)
    val colorBorde = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        RallyCopilotTheme(darkTheme = oscuro) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(if (elegida) Medidas.bordeActivo * 2 else 1.dp, colorBorde),
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag(ETIQUETA_TEMA_MINIATURA_PREFIJO + if (oscuro) "oscuro" else "claro"),
            ) { PantallaMiniatura() }
        }
        Text(
            text = nombre,
            style = EtiquetaIcono,
            color = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Una mini pantalla de conducción: el mapa con la ruta y, debajo, la velocidad y el delta con los colores del tema. */
@Composable
private fun PantallaMiniatura() {
    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(
                    color = RojoRuta,
                    start = Offset(size.width * 0.3f, size.height),
                    end = Offset(size.width * 0.7f, 0f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Text(
                text = stringResource(R.string.ajustes_tema_muestra_velocidad),
                style = CifraMedia,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.ajustes_tema_muestra_delta),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = LocalColoresRally.current.deltaMejor,
            )
        }
    }
}
