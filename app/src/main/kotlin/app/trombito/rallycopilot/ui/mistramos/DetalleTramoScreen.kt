package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.ui.comun.BotonVolver
import app.trombito.rallycopilot.ui.comun.EstadoVacio
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.tramos.colorDelta
import app.trombito.rallycopilot.ui.tramos.formatearDelta
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_DETALLE_CARGANDO = "detalle-cargando"
const val ETIQUETA_DETALLE_NO_ENCONTRADO = "detalle-no-encontrado"
const val ETIQUETA_DETALLE_EDITAR = "detalle-editar-notas"
const val ETIQUETA_DETALLE_RECONOCER = "detalle-reconocer"
private val ALTO_MINIMO_TACTIL = 48.dp
const val ETIQUETA_DETALLE_TITULO = "detalle-titulo"
const val ETIQUETA_DETALLE_FAVORITO = "detalle-favorito"
const val ETIQUETA_DETALLE_RENOMBRAR = "detalle-renombrar"
const val ETIQUETA_PASADA_PREFIJO = "pasada-"
const val ETIQUETA_ANALISIS_PREFIJO = "pasada-analisis-"
const val ETIQUETA_PASADAS_VACIAS = "detalle-sin-pasadas"

private val LADO_TACTIL = 48.dp
private val ALTO_CABECERA = 56.dp

/** Callbacks del detalle de un tramo. */
data class AccionesDetalleTramo(
    val onVolver: () -> Unit = {},
    val onFavorito: () -> Unit = {},
    val onPedirRenombrar: () -> Unit = {},
    val onRenombrar: (String) -> Unit = {},
    val onCancelarRenombrar: () -> Unit = {},
    val onVerAnalisis: (pasadaId: Long) -> Unit = {},
    val onEditarNotas: () -> Unit = {},
    val onReconocer: () -> Unit = {},
)

/** Detalle del tramo: traza dibujada, medidas y las pasadas, cada una con «Ver análisis». */
@Composable
fun DetalleTramoScreen(
    estado: DetalleTramoUiState,
    acciones: AccionesDetalleTramo,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        CabeceraDetalle(estado.tramo, acciones)
        val tramo = estado.tramo
        when {
            estado.cargando -> {
                CargandoDetalle()
            }

            tramo == null -> {
                EstadoVacio(
                    icono = Iconos.Bandera,
                    titulo = stringResource(R.string.detalle_no_existe_titulo),
                    texto = stringResource(R.string.detalle_no_existe_texto),
                    etiqueta = ETIQUETA_DETALLE_NO_ENCONTRADO,
                )
            }

            else -> {
                ContenidoDetalle(tramo, estado, acciones)
            }
        }
    }
    val editando = estado.tramo
    if (estado.renombrando && editando != null) {
        DialogoRenombrarTramo(editando, acciones.onRenombrar, acciones.onCancelarRenombrar)
    }
}

@Composable
private fun CargandoDetalle() {
    Box(Modifier.fillMaxSize().testTag(ETIQUETA_DETALLE_CARGANDO), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CabeceraDetalle(
    tramo: ResumenTramo?,
    acciones: AccionesDetalleTramo,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_CABECERA).padding(horizontal = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonVolver(acciones.onVolver)
        Text(
            text = tramo?.nombre.orEmpty().uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = Espacio.xs)
                    .testTag(ETIQUETA_DETALLE_TITULO)
                    .semantics { heading() },
        )
        if (tramo != null) {
            BotonFavorito(tramo.favorito, acciones.onFavorito, Modifier.testTag(ETIQUETA_DETALLE_FAVORITO))
            IconButton(
                onClick = acciones.onPedirRenombrar,
                modifier = Modifier.testTag(ETIQUETA_DETALLE_RENOMBRAR),
            ) {
                Icon(
                    imageVector = Iconos.Editar,
                    contentDescription = stringResource(R.string.mistramos_renombrar),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ContenidoDetalle(
    tramo: ResumenTramo,
    estado: DetalleTramoUiState,
    acciones: AccionesDetalleTramo,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        item { TrazaTramo(estado.traza) }
        item { MedidasTramo(tramo) }
        item { AccionesNotasTramo(acciones) }
        item {
            Text(
                text = stringResource(R.string.detalle_pasadas_titulo).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Espacio.s).semantics { heading() },
            )
        }
        if (estado.pasadas.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.detalle_sin_pasadas),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ETIQUETA_PASADAS_VACIAS),
                )
            }
        }
        items(estado.pasadas, key = { it.id }) { pasada ->
            TarjetaPasada(pasada) { acciones.onVerAnalisis(pasada.id) }
        }
    }
}

/** «Reconocer» (pasada lenta de reconocimiento de este tramo) junto a «Editar notas». */
@Composable
private fun AccionesNotasTramo(acciones: AccionesDetalleTramo) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Button(
            onClick = acciones.onReconocer,
            modifier = Modifier.weight(1f).heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_DETALLE_RECONOCER),
        ) {
            Icon(Iconos.Empezar, contentDescription = null, modifier = Modifier.padding(end = Espacio.xs))
            Text(stringResource(R.string.detalle_reconocer))
        }
        OutlinedButton(
            onClick = acciones.onEditarNotas,
            modifier = Modifier.weight(1f).heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_DETALLE_EDITAR),
        ) {
            Icon(Iconos.Editar, contentDescription = null, modifier = Modifier.padding(end = Espacio.xs))
            Text(stringResource(R.string.edicion_editar_notas))
        }
    }
    Text(
        text = stringResource(R.string.detalle_reconocer_ayuda),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Espacio.xs),
    )
}

@Composable
private fun MedidasTramo(tramo: ResumenTramo) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Medida(stringResource(R.string.detalle_longitud), formatearDistanciaTramo(tramo.longitudM), Modifier.weight(1f))
        Medida(
            stringResource(R.string.detalle_pasadas),
            pluralStringResource(R.plurals.mistramos_pasadas, tramo.pasadas, tramo.pasadas),
            Modifier.weight(1f),
        )
        Medida(
            stringResource(R.string.mistramos_mejor),
            tramo.mejorTotalMs?.let { formatearTiempo(it) } ?: stringResource(R.string.mistramos_sin_tiempo),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun Medida(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(Espacio.m), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Text(
                text = etiqueta.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valor,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TarjetaPasada(
    pasada: FilaPasada,
    onVerAnalisis: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_PASADA_PREFIJO + pasada.id),
    ) {
        Row(
            modifier = Modifier.padding(start = Espacio.m, top = Espacio.xs, bottom = Espacio.xs, end = Espacio.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatearFechaHora(pasada.inicioMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TiempoYDiferencia(pasada)
            }
            TextButton(
                onClick = onVerAnalisis,
                modifier = Modifier.heightIn(min = LADO_TACTIL).testTag(ETIQUETA_ANALISIS_PREFIJO + pasada.id),
            ) {
                Icon(Iconos.Grafico, contentDescription = null, modifier = Modifier.padding(end = Espacio.xs))
                Text(stringResource(R.string.detalle_ver_analisis))
            }
        }
    }
}

@Composable
private fun TiempoYDiferencia(pasada: FilaPasada) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Text(
            text = formatearTiempo(pasada.totalMs),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (pasada.esMejor) {
            Icon(Iconos.Estrella, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.mistramos_mejor).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = formatearDelta(pasada.deltaMejorMs),
                style = MaterialTheme.typography.titleMedium,
                color = colorDelta(pasada.deltaMejorMs, LocalColoresRally.current, MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}
