package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.ui.comun.ChipIconoWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

/** Chip de un destino reciente: `nav-reciente-<posición>` (0 = el más reciente). */
const val ETIQUETA_RECIENTE_PREFIJO = "nav-reciente-"

private const val LARGO_MAXIMO_NOMBRE = 18

/**
 * Accesos rápidos bajo la barra «¿A dónde?»: [inicio] (el chip «Ir a casa», que sigue en el mapa) y, a su derecha, un
 * chip por cada destino reciente de [NavegacionUiState.recientes]. Tocar uno llama a [onElegir], igual que «Ir a
 * casa»: calcula la ruta. Solo se ven con la barra en reposo (nada escrito, sin búsqueda ni vista previa abiertas) y
 * se desplazan en horizontal si no caben.
 */
@Composable
fun AccesosDestino(
    estado: NavegacionUiState,
    onElegir: (Destino) -> Unit,
    modifier: Modifier = Modifier,
    inicio: @Composable () -> Unit = {},
) {
    val enReposo = estado.texto.isEmpty() && estado.busqueda is BusquedaDestino.Inactiva && estado.vistaPrevia == null
    if (!enReposo || estado.recientes.isEmpty()) {
        inicio()
        return
    }
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        inicio()
        estado.recientes.forEachIndexed { indice, destino ->
            ChipDestinoReciente(destino, { onElegir(destino) }, Modifier.testTag(ETIQUETA_RECIENTE_PREFIJO + indice))
        }
    }
}

@Composable
private fun ChipDestinoReciente(
    destino: Destino,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.navegacion_ui_reciente_descripcion, destino.nombre)
    ChipIconoWrc(
        icono = Iconos.Lugar,
        texto = nombreCorto(destino.nombre),
        modifier = modifier.semantics { contentDescription = descripcion },
        onClick = onClick,
    )
}

/** El nombre recortado con «…» para que el chip no ocupe media pantalla. */
internal fun nombreCorto(nombre: String): String =
    if (nombre.length <= LARGO_MAXIMO_NOMBRE) nombre else nombre.take(LARGO_MAXIMO_NOMBRE - 1).trimEnd() + "…"
