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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.alertas.BannerAlerta
import app.trombito.rallycopilot.ui.alertas.TarjetaIncidencia
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.MantenerPantallaEncendida
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.DialogoVozCopiloto
import app.trombito.rallycopilot.ui.navegacion.AccionesNavegacion
import app.trombito.rallycopilot.ui.navegacion.BannerParada
import app.trombito.rallycopilot.ui.navegacion.BusquedaDestino
import app.trombito.rallycopilot.ui.navegacion.BusquedaGuiando
import app.trombito.rallycopilot.ui.navegacion.CabeceraBusqueda
import app.trombito.rallycopilot.ui.navegacion.ListaDestinos
import app.trombito.rallycopilot.ui.navegacion.ListaParadas
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.OpcionesPunto
import app.trombito.rallycopilot.ui.navegacion.PanelInferiorNavegacion
import app.trombito.rallycopilot.ui.navegacion.TarjetaPunto
import app.trombito.rallycopilot.ui.navegacion.TarjetaSeguir
import app.trombito.rallycopilot.ui.navegacion.notas.FranjaNotas
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.tramos.CronoSuperior

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
private val ALTO_MINIMO_TACTIL = 48.dp
private val ANCHO_BANNER_PARADA = 520.dp

// Altura del pie del guiado (una fila; con paradas, dos) más su margen: lo que queda por encima es de la tarjeta.
private val RESERVA_PIE_GUIADO = 72.dp
private val RESERVA_PIE_GUIADO_PARADAS = 104.dp

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
    /** Cierra la tarjeta de la incidencia tocada en el mapa. */
    val onCerrarIncidencia: () -> Unit = {},
)

/**
 * Mapa a pantalla completa con el panel negro de velocidad: abajo en vertical, a la derecha en horizontal. Los botones
 * (capas, modo del copiloto, rumbo/norte y terminar) van en ese panel, a los lados de la velocidad; no hay flecha de
 * volver (se usa el atrás del sistema). El mapa en sí lo pone [mapa] (en producción [VistaMapa]) para poder probar la
 * pantalla sin MapLibre. [navegacion] trae la barra «¿A dónde?», la vista previa de rutas, el botón de terminar y el
 * hueco del panel de guiado (banner a todo el ancho arriba). [copiloto] trae el modo (selector en el panel) y el
 * diálogo de la voz del primer uso. [alertas] trae el banner de radar o incidencia, la velocidad en rojo, la media
 * del radar de tramo y la tarjeta de la incidencia tocada en el mapa. [crono] trae el cronómetro: el HUD arriba si el
 * tramo es conocido y la tarjeta de meta; con un tramo nuevo o sin tramo, un cronómetro pequeño en el panel.
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
    copiloto: CopilotoEnMapa = CopilotoEnMapa(),
    alertas: AlertasUiState = AlertasUiState(),
    crono: CronoEnCurso? = null,
) {
    MantenerPantallaEncendida()
    var abierto by rememberSaveable { mutableStateOf(MenuMapa.NINGUNO) }
    val menu = MenuAbierto(abierto) { abierto = it }
    val botones =
        if (estado.hayDatos) botonesDelPanel(estado, acciones, navegacion, copiloto, menu) else BotonesPanel()
    // Parado planificando (búsqueda, tarjeta de un punto o vista previa) el panel inferior se encoge para ver más mapa.
    val compacto = rememberPanelCompacto(estado.velocidadKmh, navegacion.estado.planificando)
    DialogoVozSiToca(copiloto)
    val zona = ZonaMapaContenido(estado, vista, acciones, navegacion, menu, copiloto, alertas, crono)
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val horizontal = maxWidth > maxHeight
        if (horizontal) {
            Row(Modifier.fillMaxSize()) {
                ZonaMapa(
                    zona.orientada(horizontal = true),
                    mapa,
                    Modifier.weight(1f).fillMaxHeight(),
                )
                PanelDeVelocidad(
                    PanelEnMapa(estado, botones, alertas, enColumna = true, compacto = compacto, crono = crono),
                    Modifier
                        .width(anchoPanelLateral(botones))
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(LADOS_PANEL_LATERAL)),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ZonaMapa(
                    zona.orientada(horizontal = false),
                    mapa,
                    Modifier.weight(1f).fillMaxWidth(),
                )
                PanelDeVelocidad(
                    PanelEnMapa(estado, botones, alertas, enColumna = false, compacto = compacto, crono = crono),
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(LADOS_PANEL_INFERIOR)),
                )
            }
        }
    }
}

/** Lo que pinta la zona del mapa, agrupado. */
@Suppress("LongParameterList") // los bloques de la pantalla, agrupados para no pasarlos uno a uno
private class ZonaMapaContenido(
    val estado: MapaUiState,
    val vista: EstadoVistaMapa,
    val acciones: AccionesMapa,
    val navegacion: NavegacionEnMapa,
    val menu: MenuAbierto,
    val copiloto: CopilotoEnMapa,
    val alertas: AlertasUiState,
    val crono: CronoEnCurso?,
) {
    /** El mismo contenido con la zona en vertical o en apaisado. */
    fun orientada(horizontal: Boolean) =
        ZonaMapaContenido(
            estado,
            vista.copy(horizontal = horizontal),
            acciones,
            navegacion,
            menu,
            copiloto,
            alertas,
            crono,
        )
}

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
    val barra = remember { EstadoBarraDestino() }
    val buscar by rememberUpdatedState(estado.hayDatos && navegacion.estado.enReposo)
    Box(modifier.alTocar(barra) { if (buscar) barra.tocar() }) {
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
            CabeceraMapa(
                ContenidoCabecera(estado, acciones, navegacion, contenido.alertas, contenido.crono),
                barra,
                vista.horizontal,
                Modifier.align(Alignment.TopStart),
            )
            if (estado.hayDatos) {
                PieMapa(
                    ContenidoPie(estado, vista, acciones, navegacion, contenido.alertas),
                    Modifier.align(Alignment.BottomStart),
                )
                (navegacion.estado.control as? EstadoNavegacion.Guiando)?.let {
                    navegacion.panelGuiado(
                        this,
                        it,
                        vista.horizontal,
                        navegacion.notas,
                        contenido.alertas,
                        contenido.crono,
                    )
                }
                // La búsqueda abierta con la lupa tapa el banner de arriba; los menús del panel siguen encima.
                if (navegacion.estado.buscando) {
                    BusquedaGuiando(
                        navegacion.estado,
                        navegacion.acciones,
                        vista.horizontal,
                        Modifier.align(Alignment.TopStart),
                    )
                }
                // Encima de todo y pegado al panel inferior, junto al botón que lo abre.
                val pegado = Modifier.align(if (vista.horizontal) Alignment.BottomEnd else Alignment.BottomStart)
                MenuSobreMapa(estado, acciones, contenido.copiloto, contenido.menu, pegado)
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

/** Lo que pinta la cabecera del mapa, agrupado. */
private class ContenidoCabecera(
    val estado: MapaUiState,
    val acciones: AccionesMapa,
    val navegacion: NavegacionEnMapa,
    val alertas: AlertasUiState,
    val crono: CronoEnCurso?,
)

/**
 * Arriba: en reposo, la barra «¿A dónde?» a todo el ancho con su lista de resultados; debajo, la franja de notas de
 * la carretera y el banner de alerta (modo libre), «Seguir a <destino>» si hay un guiado a medias y el aviso de
 * actualización. Mientras se guía, el banner de maniobra, la franja de notas y el banner de alerta ocupan esa franja
 * y el aviso espera a que acabe. Con notas en la
 * franja la barra se esconde (la franja sube a su sitio): reaparece al tocar el mapa y se vuelve a esconder a los 8 s
 * sin toques, nunca con el campo enfocado, texto escrito, resultados o vista previa abiertos.
 */
@Composable
private fun CabeceraMapa(
    contenido: ContenidoCabecera,
    barra: EstadoBarraDestino,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    val estado = contenido.estado
    val acciones = contenido.acciones
    val navegacion = contenido.navegacion
    val buscar = estado.hayDatos && navegacion.estado.enReposo
    val hayNotas = navegacion.notas.visibles.isNotEmpty()
    val retenida =
        barraDestinoRetenida(
            enfocada = barra.enfocada,
            hayTexto = navegacion.estado.texto.isNotEmpty(),
            hayBusqueda = navegacion.estado.busqueda !is BusquedaDestino.Inactiva,
            hayVistaPrevia = navegacion.estado.vistaPrevia != null,
        )
    OcultarBarraTrasInactividad(barra, buscar && barraDestinoCuentaAtras(hayNotas, barra.reclamada, retenida))
    LaunchedEffect(buscar) { if (!buscar) barra.ocultar() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        if (buscar) {
            if (barraDestinoVisible(hayNotas, barra.reclamada, retenida)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CabeceraBusqueda(navegacion.estado, navegacion.acciones) { barra.enfocada = it }
                }
            }
            ListaDestinos(
                busqueda = navegacion.estado.busqueda,
                onElegir = navegacion.acciones.onElegirDestino,
                horizontal = horizontal,
                anadirParada =
                    navegacion.acciones.onAnadirParada.takeIf {
                        navegacion.estado.puedeAnadirParada && !navegacion.estado.paradasLlenas
                    },
            )
            // Sin búsqueda ni vista previa abiertas: lo que importa de la carretera y, si toca, retomar el guiado.
            if (navegacion.estado.busqueda is BusquedaDestino.Inactiva && navegacion.estado.vistaPrevia == null) {
                FranjaNotas(navegacion.notas, compacta = horizontal)
                BannerAlerta(contenido.alertas.aviso, contenido.alertas.frescura, compacto = horizontal)
                CronoSuperior(contenido.crono, compacto = horizontal)
            }
            navegacion.estado.seguir?.let {
                TarjetaSeguir(it, navegacion.acciones.onSeguir, navegacion.acciones.onDescartarSeguir)
            }
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

/** Lo que pinta el pie del mapa, agrupado. */
private class ContenidoPie(
    val estado: MapaUiState,
    val vista: EstadoVistaMapa,
    val acciones: AccionesMapa,
    val navegacion: NavegacionEnMapa,
    val alertas: AlertasUiState,
)

/**
 * Abajo: la tarjeta de la incidencia tocada en el mapa, permiso de ubicación (si falta), botón de recentrar y el
 * panel de navegación (vista previa o aviso). Las atribuciones están solo en Ajustes > Acerca de.
 */
@Composable
private fun PieMapa(
    contenido: ContenidoPie,
    modifier: Modifier = Modifier,
) {
    val estado = contenido.estado
    val vista = contenido.vista
    val acciones = contenido.acciones
    val navegacion = contenido.navegacion
    val nav = navegacion.estado
    val guiando = nav.control as? EstadoNavegacion.Guiando
    var paradasAbiertas by rememberSaveable { mutableStateOf(false) }
    // En el guiado el pie con llegada y tiempo ocupa el borde de abajo: lo de este bloque queda encima.
    val reserva =
        when {
            guiando?.progreso == null || (nav.puntoElegido == null && nav.paradas.isEmpty()) -> 0.dp
            nav.paradas.isEmpty() -> RESERVA_PIE_GUIADO
            else -> RESERVA_PIE_GUIADO_PARADAS
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = reserva),
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        contenido.alertas.seleccionada?.let {
            TarjetaIncidencia(it, contenido.alertas.frescura, acciones.onCerrarIncidencia)
        }
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
        nav.puntoElegido?.let {
            val opciones = OpcionesPunto(puedeAnadir = !nav.paradasLlenas)
            TarjetaPunto(it, opciones, navegacion.acciones)
        }
        if (guiando != null && guiando.paradas.isNotEmpty()) {
            ParadasDelGuiado(guiando, navegacion.acciones, paradasAbiertas, vista.horizontal) {
                paradasAbiertas = !paradasAbiertas
            }
        }
        PanelInferiorNavegacion(navegacion.estado, navegacion.acciones, vista.horizontal)
    }
}

/** Banner «Parada 1 de 3» del guiado y, si se toca, la lista para quitar o reordenar las que faltan. */
@Composable
private fun ParadasDelGuiado(
    guiando: EstadoNavegacion.Guiando,
    acciones: AccionesNavegacion,
    abierta: Boolean,
    horizontal: Boolean,
    onAlternar: () -> Unit,
) {
    val siguiente = guiando.paradasHechas + 1
    val total = guiando.paradasHechas + guiando.paradas.size
    if (abierta) {
        ListaParadas(
            paradas = guiando.paradas,
            destino = guiando.destino,
            acciones = acciones,
            primerNumero = siguiente,
            horizontal = horizontal,
        )
    }
    BannerParada(
        numero = siguiente,
        total = total,
        nombre = guiando.paradas.first().nombre,
        abierta = abierta,
        onAlternar = onAlternar,
        modifier = Modifier.widthIn(max = ANCHO_BANNER_PARADA),
    )
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
