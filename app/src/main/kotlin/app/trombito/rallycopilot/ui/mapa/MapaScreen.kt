package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.ui.comun.BotonVolver
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_CARGANDO = "mapa-cargando"
const val ETIQUETA_SIN_DATOS = "mapa-sin-datos"
const val ETIQUETA_SIN_DATOS_BOTON = "mapa-sin-datos-boton"
const val ETIQUETA_AVISO = "mapa-aviso"
const val ETIQUETA_AVISO_CERRAR = "mapa-aviso-cerrar"
const val ETIQUETA_ATRIBUCION = "mapa-atribucion"
const val ETIQUETA_CAPA_MAPA = "mapa-capa-mapa"
const val ETIQUETA_CAPA_SATELITE = "mapa-capa-satelite"
const val ETIQUETA_RECENTRAR = "mapa-recentrar"
const val ETIQUETA_PERMISO = "mapa-permiso"
const val ETIQUETA_PERMISO_PEDIR = "mapa-permiso-pedir"
const val ETIQUETA_PERMISO_AJUSTES = "mapa-permiso-ajustes"

private const val OPACIDAD_SUPERFICIE_SOBRE_MAPA = 0.88f
private val ANCHO_PANEL_LATERAL = 230.dp
private val ALTO_MINIMO_TACTIL = 48.dp

// Lados de los insets del sistema que respeta cada zona.
private val LADOS_PANEL_LATERAL = WindowInsetsSides.End + WindowInsetsSides.Vertical
private val LADOS_PANEL_INFERIOR = WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
private val LADOS_MAPA_HORIZONTAL = WindowInsetsSides.Start + WindowInsetsSides.Vertical
private val LADOS_MAPA_VERTICAL = WindowInsetsSides.Top + WindowInsetsSides.Horizontal

/** Estado de la vista del mapa que no sale del ViewModel: permiso de ubicación y si la cámara sigue al coche. */
data class EstadoVistaMapa(
    val permiso: EstadoPermisoUbicacion = EstadoPermisoUbicacion.CONCEDIDO,
    val siguiendo: Boolean = true,
    /** Pantalla apaisada: el panel va a la derecha. Lo fija [MapaScreen] según el espacio disponible. */
    val horizontal: Boolean = false,
)

/** Callbacks de la pantalla de mapa. */
data class AccionesMapa(
    val onVolver: () -> Unit = {},
    val onCapa: (CapaMapa) -> Unit = {},
    val onRecentrar: () -> Unit = {},
    val onPedirPermiso: () -> Unit = {},
    val onAbrirAjustes: () -> Unit = {},
    val onDescartarAviso: () -> Unit = {},
    val onAbrirMapasOffline: () -> Unit = {},
)

/**
 * Mapa a pantalla completa con el panel de velocidad: abajo en vertical, a la derecha en horizontal. El mapa en sí lo
 * pone [mapa] (en producción [VistaMapa]) para poder probar la pantalla sin MapLibre.
 */
@Composable
fun MapaScreen(
    estado: MapaUiState,
    vista: EstadoVistaMapa,
    acciones: AccionesMapa,
    mapa: @Composable (estiloJson: String, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val horizontal = maxWidth > maxHeight
        if (horizontal) {
            Row(Modifier.fillMaxSize()) {
                ZonaMapa(estado, vista.copy(horizontal = true), acciones, mapa, Modifier.weight(1f).fillMaxHeight())
                PanelVelocidad(
                    velocidadKmh = estado.velocidadKmh,
                    limiteKmh = estado.limiteKmh,
                    via = estado.via?.ref ?: estado.via?.nombre,
                    enColumna = true,
                    modifier =
                        Modifier
                            .width(ANCHO_PANEL_LATERAL)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(LADOS_PANEL_LATERAL)),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ZonaMapa(estado, vista.copy(horizontal = false), acciones, mapa, Modifier.weight(1f).fillMaxWidth())
                PanelVelocidad(
                    velocidadKmh = estado.velocidadKmh,
                    limiteKmh = estado.limiteKmh,
                    via = estado.via?.ref ?: estado.via?.nombre,
                    enColumna = false,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(LADOS_PANEL_INFERIOR)),
                )
            }
        }
    }
}

@Composable
private fun ZonaMapa(
    estado: MapaUiState,
    vista: EstadoVistaMapa,
    acciones: AccionesMapa,
    mapa: @Composable (String, Modifier) -> Unit,
    modifier: Modifier,
) {
    val lados = if (vista.horizontal) LADOS_MAPA_HORIZONTAL else LADOS_MAPA_VERTICAL
    Box(modifier) {
        val json = estado.estiloJson
        when {
            estado.cargando -> Cargando()
            json == null -> TarjetaSinDatos(acciones.onAbrirMapasOffline, Modifier.align(Alignment.Center))
            else -> mapa(json, Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(lados)).padding(Espacio.s)) {
            CabeceraMapa(estado, acciones, Modifier.align(Alignment.TopStart))
            if (estado.hayDatos) {
                PieMapa(estado, vista, acciones, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

@Composable
private fun Cargando() {
    Column(
        modifier = Modifier.fillMaxSize().testTag(ETIQUETA_CARGANDO),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.m, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.mapa_cargando),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TarjetaSinDatos(
    onAbrirMapasOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.padding(Espacio.xl).testTag(ETIQUETA_SIN_DATOS),
    ) {
        Column(Modifier.padding(Espacio.xl), verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
            Text(
                text = stringResource(R.string.mapa_sin_datos_titulo),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.mapa_sin_datos_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = onAbrirMapasOffline,
                modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_SIN_DATOS_BOTON),
            ) { Text(stringResource(R.string.mapa_sin_datos_boton)) }
        }
    }
}

/** Arriba: volver, conmutador Mapa/Satélite y aviso de actualización. */
@Composable
private fun CabeceraMapa(
    estado: MapaUiState,
    acciones: AccionesMapa,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(OPACIDAD_SUPERFICIE_SOBRE_MAPA),
            ) {
                BotonVolver(acciones.onVolver)
            }
            if (estado.hayDatos) SelectorCapa(estado, acciones.onCapa)
        }
        estado.datos.avisoActualizado?.let { AvisoActualizado(it, acciones.onDescartarAviso) }
    }
}

@Composable
private fun SelectorCapa(
    estado: MapaUiState,
    onCapa: (CapaMapa) -> Unit,
) {
    val descripcion = stringResource(R.string.mapa_capa_selector)
    val noInstalado = stringResource(R.string.mapa_capa_satelite_no_instalado)
    SingleChoiceSegmentedButtonRow(Modifier.semantics { contentDescription = descripcion }) {
        SegmentedButton(
            selected = estado.capa == CapaMapa.VECTORIAL,
            onClick = { onCapa(CapaMapa.VECTORIAL) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_CAPA_MAPA),
        ) { Text(stringResource(R.string.mapa_capa_mapa)) }
        SegmentedButton(
            selected = estado.capa == CapaMapa.SATELITE,
            onClick = { onCapa(CapaMapa.SATELITE) },
            enabled = estado.puedeSatelite,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier =
                Modifier
                    .heightIn(min = ALTO_MINIMO_TACTIL)
                    .testTag(ETIQUETA_CAPA_SATELITE)
                    .semantics { if (!estado.puedeSatelite) stateDescription = noInstalado },
        ) { Text(stringResource(R.string.mapa_capa_satelite)) }
    }
}

@Composable
private fun AvisoActualizado(
    version: String,
    onCerrar: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_AVISO),
    ) {
        Row(Modifier.padding(start = Espacio.l), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.mapa_aviso_actualizado, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCerrar, modifier = Modifier.testTag(ETIQUETA_AVISO_CERRAR)) {
                Icon(
                    imageVector = Iconos.Cerrar,
                    contentDescription = stringResource(R.string.mapa_aviso_cerrar),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** Abajo: permiso de ubicación (si falta), botón de recentrar y la atribución, que no se puede ocultar. */
@Composable
private fun PieMapa(
    estado: MapaUiState,
    vista: EstadoVistaMapa,
    acciones: AccionesMapa,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        if (vista.permiso != EstadoPermisoUbicacion.CONCEDIDO) {
            TarjetaPermiso(vista.permiso, acciones.onPedirPermiso, acciones.onAbrirAjustes)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Atribucion(satelite = estado.capa == CapaMapa.SATELITE, modifier = Modifier.weight(1f, fill = false))
            if (!vista.siguiendo && estado.posicion != null) {
                SmallFloatingActionButton(
                    onClick = acciones.onRecentrar,
                    modifier = Modifier.testTag(ETIQUETA_RECENTRAR),
                ) {
                    Icon(Iconos.MiUbicacion, contentDescription = stringResource(R.string.mapa_recentrar))
                }
            }
        }
    }
}

/** «© OpenStreetMap contributors» siempre; en satélite, además «PNOA CC-BY 4.0 scne.es». */
@Composable
fun Atribucion(
    satelite: Boolean,
    modifier: Modifier = Modifier,
) {
    val osm = stringResource(R.string.mapa_atribucion_osm)
    val pnoa = stringResource(R.string.mapa_atribucion_pnoa)
    val texto = if (satelite) stringResource(R.string.mapa_atribucion_doble, osm, pnoa) else osm
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(OPACIDAD_SUPERFICIE_SOBRE_MAPA),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.testTag(ETIQUETA_ATRIBUCION),
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Espacio.s, vertical = Espacio.xs),
        )
    }
}

@Composable
private fun TarjetaPermiso(
    permiso: EstadoPermisoUbicacion,
    onPedir: () -> Unit,
    onAbrirAjustes: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(OPACIDAD_SUPERFICIE_SOBRE_MAPA),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_PERMISO),
    ) {
        Column(Modifier.padding(Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Text(
                text = stringResource(R.string.permiso_ubicacion_titulo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text =
                    stringResource(
                        when (permiso) {
                            EstadoPermisoUbicacion.DENEGADO_PERMANENTE -> R.string.permiso_ubicacion_bloqueado
                            EstadoPermisoUbicacion.SOLO_APROXIMADA -> R.string.permiso_ubicacion_aproximada
                            else -> R.string.permiso_ubicacion_texto
                        },
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (permiso == EstadoPermisoUbicacion.DENEGADO_PERMANENTE) {
                Button(
                    onClick = onAbrirAjustes,
                    modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_PERMISO_AJUSTES),
                ) { Text(stringResource(R.string.permiso_ubicacion_ajustes)) }
            } else {
                Button(
                    onClick = onPedir,
                    modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_PERMISO_PEDIR),
                ) { Text(stringResource(R.string.permiso_ubicacion_permitir)) }
            }
        }
    }
}
