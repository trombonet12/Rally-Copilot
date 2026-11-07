package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.analisis.MapaTramo
import app.trombito.rallycopilot.ui.comun.BotonVolver
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.ListaEsqueletoWrc
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.Rotulo
import app.trombito.rallycopilot.ui.theme.Titulo
import app.trombito.rallycopilot.ui.tramos.colorDelta
import app.trombito.rallycopilot.ui.tramos.formatearDelta
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_DETALLE_CARGANDO = "detalle-cargando"
const val ETIQUETA_DETALLE_NO_ENCONTRADO = "detalle-no-encontrado"
const val ETIQUETA_DETALLE_EDITAR = "detalle-editar-notas"
const val ETIQUETA_DETALLE_RECONOCER = "detalle-reconocer"
const val ETIQUETA_DETALLE_AYUDA = "detalle-ayuda-reconocer"
const val ETIQUETA_DETALLE_TITULO = "detalle-titulo"
const val ETIQUETA_DETALLE_FAVORITO = "detalle-favorito"
const val ETIQUETA_DETALLE_RENOMBRAR = "detalle-renombrar"
const val ETIQUETA_DETALLE_COMPARTIR = "detalle-compartir"
const val ETIQUETA_DETALLE_SUBTITULO = "detalle-subtitulo"
const val ETIQUETA_PASADA_PREFIJO = "pasada-"
const val ETIQUETA_ANALISIS_PREFIJO = "pasada-analisis-"
const val ETIQUETA_PASADAS_VACIAS = "detalle-sin-pasadas"
const val ETIQUETA_MAPA_TRAMO = "detalle-mapa"

private val ALTO_CABECERA = 56.dp
private val ALTO_MAPA = 240.dp
private val ALTO_FILA_PASADA = 56.dp
private val TAMANO_TITULO = 20.sp
private val ALTO_LINEA_TITULO = 26.sp

/** Cifra de un tile o de una pasada: 22 sp, cursiva Black, sin el apretado de las cifras grandes. */
private val CifraTile = Rotulo.copy(fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = 0.sp)

/** El mapa del detalle: recibe el estilo, la traza del tramo y el modificador (tamaño y forma). */
typealias MapaDelTramo = @Composable (estiloJson: String, traza: List<PuntoGeo>, modifier: Modifier) -> Unit

/** El mapa real, MapLibre con la traza del tramo en el color de la marca y sin la etiqueta de la isla. */
val MapaTramoReal: MapaDelTramo = { estiloJson, traza, modificador ->
    val estilo = remember(estiloJson) { sinEtiquetaDeIslas(estiloJson) }
    MapaTramo(estilo, traza, MaterialTheme.colorScheme.primary.toArgb(), modificador)
}

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
    val onCompartir: () -> Unit = {},
    val onCompartirVisto: () -> Unit = {},
)

/**
 * Detalle del tramo: la traza sobre el mapa ([mapa]; si no hay mapa o aún no hay datos de mapa instalados, el trazo
 * dibujado en un lienzo), medidas y las pasadas, cada una pulsable para abrir su análisis.
 */
@Composable
fun DetalleTramoScreen(
    estado: DetalleTramoUiState,
    acciones: AccionesDetalleTramo,
    modifier: Modifier = Modifier,
    mapa: MapaDelTramo? = null,
) {
    val avisos = remember { SnackbarHostState() }
    AvisoCompartir(estado.compartir, avisos, acciones.onCompartirVisto)
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        CuerpoDetalle(estado, acciones, mapa)
        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).padding(Espacio.l))
    }
    val editando = estado.tramo
    if (estado.renombrando && editando != null) {
        DialogoRenombrarTramo(editando, acciones.onRenombrar, acciones.onCancelarRenombrar)
    }
}

@Composable
private fun CuerpoDetalle(
    estado: DetalleTramoUiState,
    acciones: AccionesDetalleTramo,
    mapa: MapaDelTramo?,
) {
    Column(Modifier.fillMaxSize()) {
        CabeceraDetalle(estado.tramo, estado.compartir == EstadoCompartir.PREPARANDO, acciones)
        val tramo = estado.tramo
        when {
            estado.cargando -> {
                CargandoDetalle()
            }

            tramo == null -> {
                EstadoVacioWrc(
                    icono = Iconos.Bandera,
                    titulo = stringResource(R.string.detalle_no_existe_titulo),
                    texto = stringResource(R.string.detalle_no_existe_texto),
                    etiqueta = ETIQUETA_DETALLE_NO_ENCONTRADO,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                ContenidoDetalle(tramo, estado, acciones, mapa)
            }
        }
    }
}

@Composable
private fun CargandoDetalle() {
    Box(Modifier.fillMaxSize().testTag(ETIQUETA_DETALLE_CARGANDO)) { ListaEsqueletoWrc(filas = 3) }
}

@Composable
private fun CabeceraDetalle(
    tramo: ResumenTramo?,
    compartiendo: Boolean,
    acciones: AccionesDetalleTramo,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_CABECERA).padding(horizontal = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonVolver(acciones.onVolver)
        Column(Modifier.weight(1f).padding(horizontal = Espacio.xs)) {
            Text(
                text = tramo?.nombre.orEmpty(),
                style = Titulo.copy(fontSize = TAMANO_TITULO, lineHeight = ALTO_LINEA_TITULO),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(ETIQUETA_DETALLE_TITULO).semantics { heading() },
            )
            tramo?.subtitulo?.let {
                Text(
                    text = it,
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(ETIQUETA_DETALLE_SUBTITULO),
                )
            }
        }
        if (tramo != null) {
            BotonFavorito(tramo.favorito, acciones.onFavorito, Modifier.testTag(ETIQUETA_DETALLE_FAVORITO))
            IconButton(
                onClick = acciones.onCompartir,
                enabled = !compartiendo,
                modifier = Modifier.testTag(ETIQUETA_DETALLE_COMPARTIR),
            ) {
                Icon(
                    imageVector = IconoCompartir,
                    contentDescription = stringResource(R.string.detalle_compartir),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    mapa: MapaDelTramo?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item { TrazaOMapa(estado, mapa) }
        item { MedidasTramo(tramo) }
        item { AccionesNotasTramo(acciones) }
        item {
            Text(
                text = stringResource(R.string.detalle_pasadas_titulo),
                style = Titulo,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Espacio.s).semantics { heading() },
            )
        }
        if (estado.pasadas.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.detalle_sin_pasadas),
                    style = Ayuda,
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

/** La traza sobre el mapa real; sin mapa o sin datos de mapa, el trazo en un lienzo. */
@Composable
private fun TrazaOMapa(
    estado: DetalleTramoUiState,
    mapa: MapaDelTramo?,
) {
    val estilo = estado.estiloMapaJson
    if (mapa == null || estilo == null || estado.traza.size < 2) {
        TrazaTramo(estado.traza)
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ALTO_MAPA)
                .clip(Radios.formaTarjeta)
                .testTag(ETIQUETA_MAPA_TRAMO),
        ) {
            mapa(estilo, estado.traza, Modifier.fillMaxSize())
        }
    }
}

/** Tres tiles con icono: longitud, número de pasadas (sin repetir la palabra) y mejor tiempo. */
@Composable
private fun MedidasTramo(tramo: ResumenTramo) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Medida(
            icono = Iconos.Curvas,
            etiqueta = stringResource(R.string.detalle_longitud),
            valor = formatearDistanciaTramo(tramo.longitudM),
            modifier = Modifier.weight(1f),
        )
        Medida(
            icono = Iconos.Bandera,
            etiqueta = stringResource(R.string.detalle_pasadas),
            valor = tramo.pasadas.toString(),
            modifier = Modifier.weight(1f),
        )
        Medida(
            icono = Iconos.Cronometro,
            etiqueta = stringResource(R.string.mistramos_mejor),
            valor = tramo.mejorTotalMs?.let { formatearTiempo(it) } ?: stringResource(R.string.mistramos_sin_tiempo),
            modifier = Modifier.weight(1f),
            destacado = tramo.mejorTotalMs != null,
        )
    }
}

@Composable
private fun Medida(
    icono: ImageVector,
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    destacado: Boolean = false,
) {
    TarjetaWrc(modifier.semantics(mergeDescendants = true) {}, relleno = Espacio.m) {
        Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = valor,
            style = CifraTile,
            color = if (destacado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(top = Espacio.xs),
        )
        Text(text = etiqueta, style = EtiquetaIcono, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** La pasada entera es pulsable (56 dp como mínimo) y lleva al análisis; el chevron lo anuncia. */
@Composable
private fun TarjetaPasada(
    pasada: FilaPasada,
    onVerAnalisis: () -> Unit,
) {
    val verAnalisis = stringResource(R.string.detalle_ver_analisis)
    TarjetaWrc(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA_PASADA)
                .testTag(ETIQUETA_PASADA_PREFIJO + pasada.id)
                .semantics(mergeDescendants = true) { onClick(label = verAnalisis, action = null) },
        relleno = Espacio.m,
        onClick = onVerAnalisis,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatearFechaHora(pasada.inicioMs),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TiempoYDiferencia(pasada)
            }
            Icon(
                imageVector = Iconos.Siguiente,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ETIQUETA_ANALISIS_PREFIJO + pasada.id),
            )
        }
    }
}

@Composable
private fun TiempoYDiferencia(pasada: FilaPasada) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Text(
            text = formatearTiempo(pasada.totalMs),
            style = CifraTile,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (pasada.esMejor) {
            Icon(Iconos.Estrella, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.mistramos_mejor),
                style = MaterialTheme.typography.labelLarge,
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
