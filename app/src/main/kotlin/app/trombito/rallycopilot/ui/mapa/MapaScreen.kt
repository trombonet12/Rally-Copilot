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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.MantenerPantallaEncendida
import app.trombito.rallycopilot.ui.navegacion.BotonTerminar
import app.trombito.rallycopilot.ui.navegacion.CabeceraBusqueda
import app.trombito.rallycopilot.ui.navegacion.ListaDestinos
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.PanelInferiorNavegacion
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_CARGANDO = "mapa-cargando"
const val ETIQUETA_SIN_DATOS = "mapa-sin-datos"
const val ETIQUETA_SIN_DATOS_BOTON = "mapa-sin-datos-boton"
const val ETIQUETA_DANADOS = "mapa-danados"
const val ETIQUETA_DANADOS_BOTON = "mapa-danados-boton"
const val ETIQUETA_AVISO = "mapa-aviso"
const val ETIQUETA_AVISO_CERRAR = "mapa-aviso-cerrar"
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
    val onCapa: (CapaMapa) -> Unit = {},
    val onRelieve: (Boolean) -> Unit = {},
    val onOrientacion: (Orientacion) -> Unit = {},
    val onRecentrar: () -> Unit = {},
    val onPedirPermiso: () -> Unit = {},
    val onAbrirAjustes: () -> Unit = {},
    val onDescartarAviso: () -> Unit = {},
    val onAbrirMapasOffline: () -> Unit = {},
)

/**
 * Mapa a pantalla completa con el panel negro de velocidad: abajo en vertical, a la derecha en horizontal. Los botones
 * (capas, rumbo/norte y terminar) van en ese panel, a los lados de la velocidad; no hay flecha de volver (se usa el
 * atrás del sistema). El mapa en sí lo pone [mapa] (en producción [VistaMapa]) para poder probar la pantalla sin
 * MapLibre. [navegacion] trae la barra «¿A dónde?», la vista previa de rutas, el botón de terminar y el hueco del
 * panel de guiado (banner a todo el ancho arriba).
 */
@Composable
@Suppress("LongParameterList") // estado, vista, acciones, mapa y navegación son los bloques de la pantalla
fun MapaScreen(
    estado: MapaUiState,
    vista: EstadoVistaMapa,
    acciones: AccionesMapa,
    mapa: @Composable (estiloJson: String, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
    navegacion: NavegacionEnMapa = NavegacionEnMapa(),
) {
    MantenerPantallaEncendida()
    var capasAbiertas by rememberSaveable { mutableStateOf(false) }
    val botones =
        if (estado.hayDatos) {
            botonesDelPanel(estado, acciones, capasAbiertas, navegacion) { capasAbiertas = !capasAbiertas }
        } else {
            BotonesPanel()
        }
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val horizontal = maxWidth > maxHeight
        if (horizontal) {
            Row(Modifier.fillMaxSize()) {
                ZonaMapa(
                    ZonaMapaContenido(estado, vista.copy(horizontal = true), acciones, navegacion, capasAbiertas),
                    mapa,
                    Modifier.weight(1f).fillMaxHeight(),
                )
                PanelVelocidad(
                    velocidadKmh = estado.velocidadKmh,
                    limiteKmh = estado.limiteKmh,
                    via = estado.via,
                    enColumna = true,
                    botonesIzquierda = botones.izquierda,
                    botonesDerecha = botones.derecha,
                    modifier =
                        Modifier
                            .width(ANCHO_PANEL_LATERAL)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(LADOS_PANEL_LATERAL)),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ZonaMapa(
                    ZonaMapaContenido(estado, vista.copy(horizontal = false), acciones, navegacion, capasAbiertas),
                    mapa,
                    Modifier.weight(1f).fillMaxWidth(),
                )
                PanelVelocidad(
                    velocidadKmh = estado.velocidadKmh,
                    limiteKmh = estado.limiteKmh,
                    via = estado.via,
                    enColumna = false,
                    botonesIzquierda = botones.izquierda,
                    botonesDerecha = botones.derecha,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(LADOS_PANEL_INFERIOR)),
                )
            }
        }
    }
}

/** Los botones de icono del panel inferior: a la izquierda de la velocidad y a la derecha. */
private class BotonesPanel(
    val izquierda: List<@Composable () -> Unit> = emptyList(),
    val derecha: List<@Composable () -> Unit> = emptyList(),
)

/** Izquierda: capas. Derecha: rumbo/norte y, mientras se guía, la bandera de terminar. */
private fun botonesDelPanel(
    estado: MapaUiState,
    acciones: AccionesMapa,
    capasAbiertas: Boolean,
    navegacion: NavegacionEnMapa,
    onCapas: () -> Unit,
): BotonesPanel {
    val terminar = navegacion.acciones.onTerminar.takeIf { navegacion.estado.conduciendo }
    return BotonesPanel(
        izquierda = listOf({ BotonCapas(capasAbiertas, onCapas) }),
        derecha =
            listOfNotNull(
                { BotonOrientacion(estado.orientacion, acciones.onOrientacion) },
                terminar?.let { alTerminar -> { BotonTerminar(alTerminar) } },
            ),
    )
}

/** Lo que pinta la zona del mapa, agrupado. */
private class ZonaMapaContenido(
    val estado: MapaUiState,
    val vista: EstadoVistaMapa,
    val acciones: AccionesMapa,
    val navegacion: NavegacionEnMapa,
    val capasAbiertas: Boolean,
)

@Composable
private fun ZonaMapa(
    contenido: ZonaMapaContenido,
    mapa: @Composable (String, Modifier) -> Unit,
    modifier: Modifier,
) {
    val estado = contenido.estado
    val vista = contenido.vista
    val acciones = contenido.acciones
    val navegacion = contenido.navegacion
    val lados = if (vista.horizontal) LADOS_MAPA_HORIZONTAL else LADOS_MAPA_VERTICAL
    Box(modifier) {
        val json = estado.estiloJson
        when {
            estado.cargando -> {
                Cargando()
            }

            json == null -> {
                TarjetaMapas(estado.datosDanados, acciones.onAbrirMapasOffline, Modifier.align(Alignment.Center))
            }

            else -> {
                mapa(json, Modifier.fillMaxSize())
            }
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(lados)).padding(Espacio.s)) {
            CabeceraMapa(estado, acciones, navegacion, vista.horizontal, Modifier.align(Alignment.TopStart))
            if (estado.hayDatos) {
                PieMapa(estado, vista, acciones, navegacion, Modifier.align(Alignment.BottomStart))
                (navegacion.estado.control as? EstadoNavegacion.Guiando)?.let {
                    navegacion.panelGuiado(this, it, vista.horizontal)
                }
                // Encima de todo y pegado al panel inferior, junto al botón de capas que lo abre.
                if (contenido.capasAbiertas) {
                    PanelCapas(
                        estado,
                        acciones,
                        Modifier.align(if (vista.horizontal) Alignment.BottomEnd else Alignment.BottomStart),
                    )
                }
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
private fun TarjetaMapas(
    danados: Boolean,
    onAbrirMapasOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titulo = if (danados) R.string.mapa_danados_titulo else R.string.mapa_sin_datos_titulo
    val texto = if (danados) R.string.mapa_danados_texto else R.string.mapa_sin_datos_texto
    val etiqueta = if (danados) ETIQUETA_DANADOS else ETIQUETA_SIN_DATOS
    val etiquetaBoton = if (danados) ETIQUETA_DANADOS_BOTON else ETIQUETA_SIN_DATOS_BOTON
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.padding(Espacio.xl).testTag(etiqueta),
    ) {
        Column(Modifier.padding(Espacio.xl), verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
            Text(
                text = stringResource(titulo),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = onAbrirMapasOffline,
                modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL).testTag(etiquetaBoton),
            ) { Text(stringResource(R.string.mapa_sin_datos_boton)) }
        }
    }
}

/**
 * Arriba: en reposo, la barra «¿A dónde?» a todo el ancho con su lista de resultados; debajo, el aviso de
 * actualización. Mientras se guía, el banner de maniobra ocupa esa franja y el aviso espera a que acabe.
 */
@Composable
private fun CabeceraMapa(
    estado: MapaUiState,
    acciones: AccionesMapa,
    navegacion: NavegacionEnMapa,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    val buscar = estado.hayDatos && navegacion.estado.enReposo
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        if (buscar) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CabeceraBusqueda(navegacion.estado, navegacion.acciones)
            }
            ListaDestinos(
                busqueda = navegacion.estado.busqueda,
                onElegir = navegacion.acciones.onElegirDestino,
                horizontal = horizontal,
            )
        }
        if (!navegacion.estado.conduciendo) {
            estado.datos.avisoActualizado?.let { AvisoActualizado(it, acciones.onDescartarAviso) }
        }
    }
}

@Composable
private fun AvisoActualizado(
    version: String,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_AVISO),
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

/**
 * Abajo: permiso de ubicación (si falta), botón de recentrar y el panel de navegación (vista previa o aviso). Las
 * atribuciones están solo en Ajustes > Acerca de.
 */
@Composable
private fun PieMapa(
    estado: MapaUiState,
    vista: EstadoVistaMapa,
    acciones: AccionesMapa,
    navegacion: NavegacionEnMapa,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        if (vista.permiso != EstadoPermisoUbicacion.CONCEDIDO) {
            TarjetaPermiso(vista.permiso, acciones.onPedirPermiso, acciones.onAbrirAjustes)
        }
        if (!vista.siguiendo && estado.posicion != null) {
            SmallFloatingActionButton(
                onClick = acciones.onRecentrar,
                modifier = Modifier.align(Alignment.End).testTag(ETIQUETA_RECENTRAR),
            ) {
                Icon(Iconos.MiUbicacion, contentDescription = stringResource(R.string.mapa_recentrar))
            }
        }
        PanelInferiorNavegacion(navegacion.estado, navegacion.acciones, vista.horizontal)
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
