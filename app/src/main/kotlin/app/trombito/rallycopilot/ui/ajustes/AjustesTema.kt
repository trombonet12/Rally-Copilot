package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme

const val ETIQUETA_TEMA_PREFIJO = "ajustes-tema-"
const val ETIQUETA_TEMA_MUESTRA = "ajustes-tema-muestra"

private fun Tema.nombre(): Int =
    when (this) {
        Tema.OSCURO -> R.string.ajustes_tema_oscuro
        Tema.CLARO -> R.string.ajustes_tema_claro
        Tema.SISTEMA -> R.string.ajustes_tema_sistema
    }

private fun Tema.icono(): ImageVector =
    when (this) {
        Tema.OSCURO -> Iconos.TemaOscuro
        Tema.CLARO -> Iconos.TemaClaro
        Tema.SISTEMA -> Iconos.TemaSistema
    }

/** Tema: oscuro (por defecto), claro o el del móvil, con una muestra pintada ya con el tema elegido. */
@Composable
internal fun SeccionTema(
    tema: Tema,
    onTema: (Tema) -> Unit,
) {
    TarjetaSeccion(
        clave = "tema",
        icono = Iconos.TemaOscuro,
        titulo = R.string.ajustes_seccion_tema,
        detalle = R.string.ajustes_seccion_tema_detalle,
        vistaPrevia = { MuestraTema(tema) },
    ) {
        Text(
            text = stringResource(R.string.ajustes_tema_detalle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Tema.entries.forEach { opcion ->
                BotonIcono(
                    descripcion = stringResource(opcion.nombre()),
                    onClick = { onTema(opcion) },
                    modifier = Modifier.testTag(ETIQUETA_TEMA_PREFIJO + opcion.name.lowercase()),
                    estado = estadoSeleccion(opcion == tema),
                ) { Icon(opcion.icono(), contentDescription = null) }
            }
        }
    }
}

/** Una mini pantalla de conducción (velocidad, delta y botón) con los colores del [tema] elegido. */
@Composable
private fun MuestraTema(tema: Tema) {
    val oscuro = tema.esOscuro(isSystemInDarkTheme())
    val descripcion = stringResource(R.string.ajustes_tema_muestra_descripcion, stringResource(tema.nombre()))
    RallyCopilotTheme(darkTheme = oscuro) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.small,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(ETIQUETA_TEMA_MUESTRA)
                    .semantics { contentDescription = descripcion },
        ) {
            Row(
                modifier = Modifier.padding(Espacio.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                Text(
                    text = stringResource(R.string.ajustes_tema_muestra_velocidad),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.ajustes_tema_muestra_unidad),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.ajustes_tema_muestra_delta),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = LocalColoresRally.current.deltaMejor,
                )
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = stringResource(R.string.ajustes_tema_muestra_titulo),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = Espacio.m, vertical = Espacio.s),
                    )
                }
            }
        }
    }
}
