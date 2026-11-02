package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_DIBUJAR_DESHACER = "dibujar-deshacer"
const val ETIQUETA_DIBUJAR_BORRAR = "dibujar-borrar"
const val ETIQUETA_DIBUJAR_GUARDAR = "dibujar-guardar"
const val ETIQUETA_DIBUJAR_CALCULANDO = "dibujar-calculando"
const val ETIQUETA_DIBUJAR_ERROR = "dibujar-error"

/** Callbacks del modo dibujo. */
data class AccionesDibujar(
    val onVolver: () -> Unit = {},
    val onDeshacer: () -> Unit = {},
    val onBorrarTodo: () -> Unit = {},
    val onGuardar: () -> Unit = {},
)

/**
 * Modo dibujo: el [mapa] a pantalla completa (recibe los toques) con la barra de arriba y, abajo, el resumen de la
 * ruta y los iconos de deshacer el último punto, borrar todo y guardar. Sin lógica: el estado lo pone el ViewModel.
 */
@Composable
fun DibujarScreen(
    estado: DibujarUiState,
    acciones: AccionesDibujar,
    mapa: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        mapa(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = FONDO_PANEL),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BarraSuperior(titulo = stringResource(R.string.dibujar_titulo), onVolver = acciones.onVolver)
            }
            Box(Modifier.weight(1f))
            PanelDibujo(estado, acciones)
        }
    }
}

@Composable
private fun PanelDibujo(
    estado: DibujarUiState,
    acciones: AccionesDibujar,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = FONDO_PANEL),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(Espacio.s),
    ) {
        Column(Modifier.padding(Espacio.m), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                if (estado.calculando) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp).testTag(ETIQUETA_DIBUJAR_CALCULANDO),
                        strokeWidth = 2.dp,
                    )
                }
                Text(textoResumen(estado), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            estado.error?.let {
                Text(
                    stringResource(textoError(it)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(ETIQUETA_DIBUJAR_ERROR),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = acciones.onDeshacer,
                    enabled = estado.puntos.isNotEmpty(),
                    modifier = Modifier.size(48.dp).testTag(ETIQUETA_DIBUJAR_DESHACER),
                ) {
                    Icon(Iconos.Deshacer, stringResource(R.string.dibujar_deshacer))
                }
                IconButton(
                    onClick = acciones.onBorrarTodo,
                    enabled = estado.puntos.isNotEmpty(),
                    modifier = Modifier.size(48.dp).testTag(ETIQUETA_DIBUJAR_BORRAR),
                ) {
                    Icon(Iconos.Borrar, stringResource(R.string.dibujar_borrar_todo))
                }
                Box(Modifier.weight(1f))
                FilledIconButton(
                    onClick = acciones.onGuardar,
                    enabled = estado.puedeGuardar,
                    modifier = Modifier.size(56.dp).testTag(ETIQUETA_DIBUJAR_GUARDAR),
                ) {
                    Icon(Iconos.Hecho, stringResource(R.string.dibujar_guardar))
                }
            }
        }
    }
}

@Composable
private fun textoResumen(estado: DibujarUiState): String =
    when {
        estado.calculando -> {
            stringResource(R.string.dibujar_calculando)
        }

        estado.ruta.size >= 2 -> {
            stringResource(R.string.dibujar_longitud, formatearDistanciaTramo(estado.longitudM), estado.puntos.size)
        }

        else -> {
            stringResource(R.string.dibujar_ayuda)
        }
    }

private fun textoError(error: ErrorDibujo): Int =
    when (error) {
        ErrorDibujo.SIN_DATOS -> R.string.dibujar_sin_datos
        ErrorDibujo.RUTA -> R.string.dibujar_error_ruta
        ErrorDibujo.CORTA -> R.string.dibujar_corto
        ErrorDibujo.NO_GUARDADA -> R.string.dibujar_no_guardado
    }

private const val FONDO_PANEL = 0.92f
