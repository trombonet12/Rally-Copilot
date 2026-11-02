package app.trombito.rallycopilot.ui.mistramos

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.EstadoVacio
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_TRAMOS_LISTA = "tramos-lista"
const val ETIQUETA_TRAMOS_CARGANDO = "tramos-cargando"
const val ETIQUETA_TRAMOS_VACIO = "tramos-vacio"
const val ETIQUETA_FILTRO_PREFIJO = "tramos-filtro-"

/** Callbacks de «Mis tramos». */
data class AccionesMisTramos(
    val onVolver: () -> Unit = {},
    val onAbrir: (ResumenTramo) -> Unit = {},
    val onFiltro: (FiltroTramos) -> Unit = {},
    val onFavorito: (ResumenTramo) -> Unit = {},
    val onBorrar: (ResumenTramo) -> Unit = {},
    val onDeshacer: () -> Unit = {},
    val onBorradoConfirmado: () -> Unit = {},
    val onPedirRenombrar: (ResumenTramo) -> Unit = {},
    val onRenombrar: (String) -> Unit = {},
    val onCancelarRenombrar: () -> Unit = {},
    val onFalloVisto: () -> Unit = {},
)

/** «Mis tramos»: filtros, lista por último uso, borrar deslizando con deshacer y renombrar. */
@Composable
fun MisTramosScreen(
    estado: MisTramosUiState,
    acciones: AccionesMisTramos,
    modifier: Modifier = Modifier,
    @StringRes titulo: Int = R.string.inicio_mis_tramos,
) {
    val avisos = remember { SnackbarHostState() }
    AvisoDeshacer(estado.borradoPendiente, avisos, acciones)
    AvisoFallo(estado.falloBorrado, avisos, acciones.onFalloVisto)
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            BarraSuperior(titulo = stringResource(titulo), onVolver = acciones.onVolver)
            FiltrosTramos(estado.filtro, acciones.onFiltro)
            when {
                estado.cargando -> Cargando()
                estado.tramos.isEmpty() -> VacioTramos(estado.filtro)
                else -> ListaTramos(estado.tramos, acciones)
            }
        }
        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).padding(Espacio.l))
    }
    estado.renombrando?.let { DialogoRenombrarTramo(it, acciones.onRenombrar, acciones.onCancelarRenombrar) }
}

/** Muestra el aviso de borrado y, según cómo acabe, deshace o confirma. */
@Composable
private fun AvisoDeshacer(
    borrado: BorradoPendiente?,
    avisos: SnackbarHostState,
    acciones: AccionesMisTramos,
) {
    val mensaje = borrado?.let { stringResource(R.string.mistramos_borrado_aviso, it.nombre) }
    val deshacer = stringResource(R.string.mistramos_deshacer)
    LaunchedEffect(borrado) {
        if (mensaje != null) {
            val resultado = avisos.showSnackbar(mensaje, deshacer, duration = SnackbarDuration.Long)
            if (resultado == SnackbarResult.ActionPerformed) acciones.onDeshacer() else acciones.onBorradoConfirmado()
        }
    }
}

/** Avisa de que la base no pudo borrar el tramo (que sigue en la lista) sin cerrar la app. */
@Composable
private fun AvisoFallo(
    nombre: String?,
    avisos: SnackbarHostState,
    onVisto: () -> Unit,
) {
    val mensaje = nombre?.let { stringResource(R.string.mistramos_borrado_error, it) }
    LaunchedEffect(nombre) {
        if (mensaje != null) {
            avisos.showSnackbar(mensaje, duration = SnackbarDuration.Long)
            onVisto()
        }
    }
}

@Composable
private fun FiltrosTramos(
    actual: FiltroTramos,
    onFiltro: (FiltroTramos) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Espacio.l),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        FiltroTramos.entries.forEach { filtro ->
            FilterChip(
                selected = filtro == actual,
                onClick = { onFiltro(filtro) },
                label = { Text(stringResource(textoFiltro(filtro)).uppercase()) },
                modifier = Modifier.testTag(ETIQUETA_FILTRO_PREFIJO + filtro.name.lowercase()),
            )
        }
    }
}

private fun textoFiltro(filtro: FiltroTramos): Int =
    when (filtro) {
        FiltroTramos.TODOS -> R.string.mistramos_filtro_todos
        FiltroTramos.REPETIDOS -> R.string.mistramos_filtro_repetidos
        FiltroTramos.FAVORITOS -> R.string.mistramos_filtro_favoritos
    }

@Composable
private fun Cargando() {
    Box(Modifier.fillMaxSize().testTag(ETIQUETA_TRAMOS_CARGANDO), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun VacioTramos(filtro: FiltroTramos) {
    val (titulo, texto) =
        when (filtro) {
            FiltroTramos.TODOS -> {
                R.string.mistramos_vacio_titulo to R.string.mistramos_vacio_texto
            }

            FiltroTramos.REPETIDOS -> {
                R.string.mistramos_vacio_repetidos_titulo to R.string.mistramos_vacio_repetidos_texto
            }

            FiltroTramos.FAVORITOS -> {
                R.string.mistramos_vacio_favoritos_titulo to R.string.mistramos_vacio_favoritos_texto
            }
        }
    EstadoVacio(
        icono = if (filtro == FiltroTramos.FAVORITOS) Iconos.EstrellaVacia else Iconos.Bandera,
        titulo = stringResource(titulo),
        texto = stringResource(texto),
        etiqueta = ETIQUETA_TRAMOS_VACIO,
    )
}

@Composable
private fun ListaTramos(
    tramos: List<ResumenTramo>,
    acciones: AccionesMisTramos,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ETIQUETA_TRAMOS_LISTA),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        items(tramos, key = { it.id }) { tramo ->
            FilaTramoDeslizable(tramo, acciones, Modifier.animateItem())
        }
    }
}
