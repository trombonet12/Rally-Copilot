package app.trombito.rallycopilot.ui.mistramos

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.analisis.MapaTramo
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.ListaEsqueletoWrc
import app.trombito.rallycopilot.ui.comun.PantallaWrc
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
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
const val ETIQUETA_DETALLE_FAVORITO = "detalle-favorito"
const val ETIQUETA_DETALLE_RENOMBRAR = "detalle-renombrar"
const val ETIQUETA_DETALLE_COMPARTIR = "detalle-compartir"
const val ETIQUETA_PASADA_PREFIJO = "pasada-"
const val ETIQUETA_ANALISIS_PREFIJO = "pasada-analisis-"
const val ETIQUETA_RESUMEN_PASADA_PREFIJO = "pasada-resumen-"
const val ETIQUETA_PASADAS_VACIAS = "detalle-sin-pasadas"
const val ETIQUETA_MAPA_TRAMO = "detalle-mapa"

private val ALTO_MAPA = 240.dp
private val ALTO_FILA_PASADA = 56.dp

/** Con menos pasadas, ser la «mejor» no dice nada: no hay con qué compararla. */
private const val PASADAS_PARA_MEJOR = 2

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
    val onVerResumen: (recorridoId: Long) -> Unit = {},
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
    val tramo = estado.tramo
    PantallaWrc(
        titulo = tramo?.nombre.orEmpty(),
        onVolver = acciones.onVolver,
        modifier = modifier,
        subtitulo = tramo?.subtitulo,
        acciones = {
            if (tramo != null) AccionesCabecera(tramo, estado.compartir == EstadoCompartir.PREPARANDO, acciones)
        },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CuerpoDetalle(estado, acciones, mapa)
            SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).padding(Espacio.l))
        }
    }
    if (estado.renombrando && tramo != null) {
        DialogoRenombrarTramo(tramo, acciones.onRenombrar, acciones.onCancelarRenombrar)
    }
}

@Composable
private fun CuerpoDetalle(
    estado: DetalleTramoUiState,
    acciones: AccionesDetalleTramo,
    mapa: MapaDelTramo?,
) {
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

@Composable
private fun CargandoDetalle() {
    Box(Modifier.fillMaxSize().testTag(ETIQUETA_DETALLE_CARGANDO)) { ListaEsqueletoWrc(filas = 3) }
}

/** Favorito, compartir y cambiar el nombre: las acciones de la cabecera de un tramo que existe. */
@Composable
private fun AccionesCabecera(
    tramo: ResumenTramo,
    compartiendo: Boolean,
    acciones: AccionesDetalleTramo,
) {
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
    IconButton(onClick = acciones.onPedirRenombrar, modifier = Modifier.testTag(ETIQUETA_DETALLE_RENOMBRAR)) {
        Icon(
            imageVector = Iconos.Editar,
            contentDescription = stringResource(R.string.mistramos_renombrar),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        item { MedidasTramo(tramo, estado.curvas) }
        seccionEvolucion(estado.pasadas)
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
        val variasPasadas = estado.pasadas.size >= PASADAS_PARA_MEJOR
        items(estado.pasadas, key = { it.id }) { pasada ->
            TarjetaPasada(
                pasada = pasada,
                marcarMejor = variasPasadas,
                onVerAnalisis = { acciones.onVerAnalisis(pasada.id) },
                onVerResumen = pasada.recorridoId?.let { id -> { acciones.onVerResumen(id) } },
            )
        }
        seccionSubtramos(estado.subtramos)
        seccionNotasPorRevisar(estado.notasPorRevisar)
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

/**
 * La pasada entera es pulsable (56 dp como mínimo) y lleva al análisis; el chevron lo anuncia. Si la grabó un viaje
 * que sigue guardado ([onVerResumen] no es null), un botón aparte abre el resumen de ese viaje (y TalkBack lo ofrece
 * como acción personalizada de la tarjeta).
 */
@Composable
private fun TarjetaPasada(
    pasada: FilaPasada,
    marcarMejor: Boolean,
    onVerAnalisis: () -> Unit,
    onVerResumen: (() -> Unit)?,
) {
    val verAnalisis = stringResource(R.string.detalle_ver_analisis)
    val verResumen = stringResource(R.string.detalle_ver_resumen)
    TarjetaWrc(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA_PASADA)
                .testTag(ETIQUETA_PASADA_PREFIJO + pasada.id)
                .semantics(mergeDescendants = true) {
                    onClick(label = verAnalisis, action = null)
                    if (onVerResumen != null) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(verResumen) {
                                    onVerResumen()
                                    true
                                },
                            )
                    }
                },
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
                TiempoYDiferencia(pasada, marcarMejor)
            }
            if (onVerResumen != null) {
                IconButton(
                    onClick = onVerResumen,
                    modifier = Modifier.testTag(ETIQUETA_RESUMEN_PASADA_PREFIJO + pasada.id),
                ) {
                    Icon(
                        imageVector = Iconos.Grafico,
                        contentDescription = verResumen,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
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
private fun TiempoYDiferencia(
    pasada: FilaPasada,
    marcarMejor: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Text(
            text = formatearTiempo(pasada.totalMs),
            style = CifraTile,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (pasada.esMejor) {
            if (marcarMejor) MarcaMejor()
        } else {
            Text(
                text = formatearDelta(pasada.deltaMejorMs),
                style = MaterialTheme.typography.titleMedium,
                color = colorDelta(pasada.deltaMejorMs, LocalColoresRally.current, MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

/** Estrella y «Mejor»: la pasada más rápida del tramo (no es un récord: la primera pasada tampoco lo sería). */
@Composable
private fun MarcaMejor() {
    Icon(Iconos.Estrella, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text(
        text = stringResource(R.string.mistramos_mejor),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}
