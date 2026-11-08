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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.importar.MotivoFalloImportar
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.ChipIconoWrc
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.ListaEsqueletoWrc
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_TRAMOS_LISTA = "tramos-lista"
const val ETIQUETA_TRAMOS_CARGANDO = "tramos-cargando"
const val ETIQUETA_TRAMOS_VACIO = "tramos-vacio"
const val ETIQUETA_TRAMOS_IMPORTAR = "tramos-importar"
const val ETIQUETA_TRAMOS_DIBUJAR = "tramos-dibujar"
const val ETIQUETA_TRAMOS_CARRETERAS = "tramos-carreteras"
const val ETIQUETA_TRAMOS_IMPORTANDO = "tramos-importando"
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
    /** Abre el selector de archivos para importar un GPX/KML; null = sin botón (p. ej. al elegir tramo a reconocer). */
    val onImportar: (() -> Unit)? = null,
    /** Abre el mapa en modo dibujo; null = sin botón. */
    val onDibujar: (() -> Unit)? = null,
    /** Abre «Mis carreteras»; null = sin icono (p. ej. al elegir tramo a reconocer). */
    val onMisCarreteras: (() -> Unit)? = null,
    val onImportacionVista: () -> Unit = {},
    /** El usuario confirma importar el archivo que llegó de otra app. */
    val onConfirmarImportacion: () -> Unit = {},
    /** El usuario descarta el archivo que llegó de otra app. */
    val onCancelarImportacion: () -> Unit = {},
)

/** «Mis tramos»: filtros, lista por último uso, borrar deslizando con deshacer y renombrar. */
@Composable
fun MisTramosScreen(
    estado: MisTramosUiState,
    acciones: AccionesMisTramos,
    modifier: Modifier = Modifier,
    @StringRes titulo: Int = R.string.inicio_mis_tramos,
    importacion: EstadoImportacion = EstadoImportacion.Inactivo,
) {
    val avisos = remember { SnackbarHostState() }
    AvisoDeshacer(estado.borradoPendiente, avisos, acciones)
    AvisoFallo(estado.falloBorrado, avisos, acciones.onFalloVisto)
    AvisoImportacion(importacion, avisos, acciones.onImportacionVista)
    // La ayuda de la estrella no sale con un diálogo abierto: quedaría detrás, en su propia ventana.
    val pistaLibre = estado.renombrando == null && importacion !is EstadoImportacion.Pendiente
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            BarraSuperior(titulo = stringResource(titulo), onVolver = acciones.onVolver) {
                BotonMisCarreteras(acciones.onMisCarreteras)
                BotonesCrear(acciones)
            }
            if (importacion == EstadoImportacion.Importando) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag(ETIQUETA_TRAMOS_IMPORTANDO))
            }
            FiltrosTramos(estado.filtro, acciones.onFiltro)
            when {
                estado.cargando -> Cargando()
                estado.tramos.isEmpty() -> VacioTramos(estado.filtro)
                else -> ListaTramos(estado.tramos, estado.trazas, acciones, pistaLibre)
            }
        }
        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).padding(Espacio.l))
    }
    estado.renombrando?.let { DialogoRenombrarTramo(it, acciones.onRenombrar, acciones.onCancelarRenombrar) }
    if (importacion is EstadoImportacion.Pendiente) {
        DialogoConfirmarImportacion(importacion, acciones.onConfirmarImportacion, acciones.onCancelarImportacion)
    }
}

/** Icono de la barra que abre «Mis carreteras» (solo si la pantalla lo ofrece). */
@Composable
private fun BotonMisCarreteras(onClick: (() -> Unit)?) {
    if (onClick == null) return
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag(ETIQUETA_TRAMOS_CARRETERAS)) {
        Icon(Iconos.Curvas, stringResource(R.string.carreteras_acceso), tint = MaterialTheme.colorScheme.onSurface)
    }
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
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Espacio.l, vertical = Espacio.xs),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        FiltroTramos.entries.forEach { filtro ->
            ChipIconoWrc(
                icono = iconoFiltro(filtro),
                texto = stringResource(textoFiltro(filtro)),
                seleccionado = filtro == actual,
                onClick = { onFiltro(filtro) },
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

private fun iconoFiltro(filtro: FiltroTramos): ImageVector =
    when (filtro) {
        FiltroTramos.TODOS -> Iconos.Capas
        FiltroTramos.REPETIDOS -> Iconos.Grafico
        FiltroTramos.FAVORITOS -> Iconos.Estrella
    }

/** Carga: esqueletos con la silueta de las filas. */
@Composable
private fun Cargando() {
    Box(Modifier.fillMaxSize().testTag(ETIQUETA_TRAMOS_CARGANDO)) { ListaEsqueletoWrc() }
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
    EstadoVacioWrc(
        icono = if (filtro == FiltroTramos.FAVORITOS) Iconos.EstrellaVacia else Iconos.Bandera,
        titulo = stringResource(titulo),
        texto = stringResource(texto),
        etiqueta = ETIQUETA_TRAMOS_VACIO,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ListaTramos(
    tramos: List<ResumenTramo>,
    trazas: Map<String, List<PuntoGeo>>,
    acciones: AccionesMisTramos,
    pistaLibre: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ETIQUETA_TRAMOS_LISTA),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        items(tramos, key = { it.id }) { tramo ->
            // La pista de la estrella solo señala la primera fila.
            val conPista = pistaLibre && tramo.id == tramos.first().id
            FilaTramoDeslizable(tramo, acciones, Modifier.animateItem(), trazas[tramo.id], conPista)
        }
    }
}
