@file:Suppress("TooManyFunctions") // la pantalla del mapa agrupa sus zonas (mapa, roadbook, cabecera, pie y avisos)

package app.trombito.rallycopilot.ui.mapa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.alertas.BannerAlerta
import app.trombito.rallycopilot.ui.alertas.TarjetaIncidencia
import app.trombito.rallycopilot.ui.comun.BandaSinGps
import app.trombito.rallycopilot.ui.comun.ChipIconoWrc
import app.trombito.rallycopilot.ui.comun.DegradadoSuperiorMapa
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.LocalSinSenalGps
import app.trombito.rallycopilot.ui.comun.MantenerPantallaEncendida
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteTarjeta
import app.trombito.rallycopilot.ui.copiloto.BurbujaEscucha
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.navegacion.AccionesNavegacion
import app.trombito.rallycopilot.ui.navegacion.BannerParada
import app.trombito.rallycopilot.ui.navegacion.BusquedaDestino
import app.trombito.rallycopilot.ui.navegacion.BusquedaGuiando
import app.trombito.rallycopilot.ui.navegacion.CabeceraBusqueda
import app.trombito.rallycopilot.ui.navegacion.ListaDestinos
import app.trombito.rallycopilot.ui.navegacion.ListaParadas
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.navegacion.OpcionesPunto
import app.trombito.rallycopilot.ui.navegacion.PanelInferiorNavegacion
import app.trombito.rallycopilot.ui.navegacion.TarjetaPunto
import app.trombito.rallycopilot.ui.navegacion.TarjetaSeguir
import app.trombito.rallycopilot.ui.navegacion.notas.CarreteNotas
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon
import app.trombito.rallycopilot.ui.tramos.CronoSuperior

const val ETIQUETA_CARGANDO = "mapa-cargando"
const val ETIQUETA_IR_A_CASA = "mapa-ir-a-casa"
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
private const val DURACION_AVISO_MS = 4_000L
internal val ANCHO_BANNER_PARADA = 520.dp

// Lados de los insets del sistema que respeta cada zona. Nunca el teclado: solo la búsqueda es consciente de él.
private val LADOS_MAPA_HORIZONTAL = WindowInsetsSides.Top + WindowInsetsSides.Horizontal
private val LADOS_MAPA_VERTICAL = WindowInsetsSides.Top + WindowInsetsSides.Horizontal

/** Insets del sistema (barras y recorte de pantalla) sin el teclado: la hoja de abajo no sube con él. */
@Composable
internal fun insetsSinTeclado(): WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/** Estado de la vista del mapa que no sale del ViewModel: permiso de ubicación y si la cámara sigue al coche. */
data class EstadoVistaMapa(
    val permiso: EstadoPermisoUbicacion = EstadoPermisoUbicacion.CONCEDIDO,
    val siguiendo: Boolean = true,
    /** Pantalla apaisada: la hoja va abajo a la izquierda y los botones a la derecha. Lo fija [MapaScreen]. */
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
    /** La ✕ de la hoja en modo libre: sale del mapa. */
    val onSalir: () -> Unit = {},
    /** Píxeles que tapa la tarjeta del punto elegido (su alto más el margen; 0 sin tarjeta): el mapa los reserva. */
    val onReservaInferiorPunto: (Int) -> Unit = {},
)

/**
 * Mapa a pantalla completa con la hoja de conducción abajo (96-112 dp; en horizontal, abajo a la izquierda, 320 dp).
 * Sobre la hoja van la píldora de velocidad y límite (a la izquierda) y la columna de botones redondos (a la
 * derecha): fijos el micrófono, el copiloto y silenciar (64 dp, para conducir) y la brújula; capas, notas grandes y
 * recentrar se esconden por encima de 20 km/h y vuelven al parar. No hay flecha de volver (se usa el atrás del
 * sistema). El mapa en sí lo pone
 * [mapa] (en producción
 * [VistaMapa]) para poder probar la pantalla sin MapLibre. [navegacion] trae la barra «¿A dónde?», la vista previa de
 * rutas y el hueco del panel de guiado (banner a todo el ancho arriba). [copiloto] trae el modo y el diálogo de la voz
 * del primer uso. [alertas] trae el banner de radar o incidencia, la velocidad pasada de límite, la media del radar de
 * tramo y la tarjeta de la incidencia tocada en el mapa. [crono] trae el cronómetro: el HUD arriba si el tramo es
 * conocido y la tarjeta de meta; con un tramo nuevo o sin tramo, un cronómetro en la hoja. [superposicion] pinta encima
 * de todo lo demás (editor de notas y micrófono grande del reconocimiento).
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
    superposicion: @Composable BoxScope.() -> Unit = {},
) {
    MantenerPantallaEncendida()
    var abierto by rememberSaveable { mutableStateOf(MenuMapa.NINGUNO) }
    val menu = MenuAbierto(abierto) { abierto = it }
    DialogoVozSiToca(copiloto)
    val modoAnterior = rememberModoAnterior(copiloto.estado.modo)
    // Capas y notas grandes se esconden por encima de 20 km/h y vuelven al parar (con histéresis).
    val rapido = rememberMarchaRapida(estado.velocidadKmh)
    val botones =
        if (estado.hayDatos || copiloto.estado.notasGrandes) {
            botonesDeLaColumna(estado, vista, acciones, navegacion, copiloto, menu, modoAnterior)
        } else {
            BotonesMapa()
        }
    val zona = ZonaMapaContenido(estado, vista, acciones, navegacion, menu, copiloto, alertas, crono, botones)
    val tactilesVisibles = !rapido || copiloto.estado.notasGrandes
    CompositionLocalProvider(LocalSinSenalGps provides estado.sinSenalGps) {
        BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val horizontal = maxWidth > maxHeight
            ZonaMapa(zona.orientada(horizontal, tactilesVisibles), mapa, Modifier.fillMaxSize())
            // Encima de todo: el editor de notas (parado) y el micrófono grande del reconocimiento.
            superposicion()
        }
    }
}

/** El último modo del copiloto con voz: al quitar el silencio se vuelve a él. */
@Composable
private fun rememberModoAnterior(modo: ModoCopiloto): ModoCopiloto {
    var anterior by rememberSaveable { mutableStateOf(ModoCopiloto.POR_DEFECTO) }
    if (modo != ModoCopiloto.SILENCIO && modo != anterior) anterior = modo
    return anterior
}

/** Lo que pinta la zona del mapa, agrupado. */
@Suppress("LongParameterList") // los bloques de la pantalla, agrupados para no pasarlos uno a uno
internal class ZonaMapaContenido(
    val estado: MapaUiState,
    val vista: EstadoVistaMapa,
    val acciones: AccionesMapa,
    val navegacion: NavegacionEnMapa,
    val menu: MenuAbierto,
    val copiloto: CopilotoEnMapa,
    val alertas: AlertasUiState,
    val crono: CronoEnCurso?,
    val botones: BotonesMapa,
    /** Los botones secundarios (capas, notas grandes) están a la vista: no se va deprisa. */
    val tactilesVisibles: Boolean = true,
) {
    /** El mismo contenido con la zona en vertical o en apaisado. */
    fun orientada(
        horizontal: Boolean,
        tactilesVisibles: Boolean,
    ) = ZonaMapaContenido(
        estado,
        vista.copy(horizontal = horizontal),
        acciones,
        navegacion,
        menu,
        copiloto,
        alertas,
        crono,
        botones,
        tactilesVisibles,
    )
}

@Composable
private fun ZonaMapa(
    contenido: ZonaMapaContenido,
    mapa: @Composable (String, Modifier) -> Unit,
    modifier: Modifier,
) {
    if (contenido.copiloto.estado.notasGrandes) {
        RoadbookEnZona(contenido, modifier)
    } else {
        ZonaConMapa(contenido, mapa, modifier)
    }
}

@Composable
private fun ZonaConMapa(
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
    // Sin tarjeta de punto el mapa no reserva nada por abajo.
    val sinPunto = navegacion.estado.puntoElegido == null
    LaunchedEffect(sinPunto) { if (sinPunto) acciones.onReservaInferiorPunto(0) }
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
                // Bajo la barra de estado: los rótulos y escudos del mapa no chocan con la hora ni los iconos.
                DegradadoSuperiorMapa(Modifier.align(Alignment.TopCenter))
            }
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(insetsSinTeclado().only(lados)).padding(Espacio.s)) {
            CabeceraMapa(
                ContenidoCabecera(estado, acciones, navegacion, contenido.alertas, contenido.crono),
                barra,
                vista.horizontal,
                Modifier.align(Alignment.TopStart),
            )
            if (estado.hayDatos) {
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
            }
        }
        ZonaInferior(contenido, barra)
        MenuSobreMapa(contenido.copiloto, contenido.menu, vista.horizontal)
    }
}

/**
 * El modo de notas grandes en lugar del mapa: la tarjeta de permiso si falta, el menú del copiloto sobre ella y,
 * debajo de todo, el roadbook. Las notas, alertas y el cronómetro son los mismos que en el mapa. La hoja de abajo y la
 * columna de botones son las del mapa: el roadbook les deja sitio.
 */
@Composable
private fun RoadbookEnZona(
    contenido: ZonaMapaContenido,
    modifier: Modifier,
) {
    val vista = contenido.vista
    val navegacion = contenido.navegacion
    val lados = if (vista.horizontal) LADOS_MAPA_HORIZONTAL else LADOS_MAPA_VERTICAL
    Box(modifier) {
        ZonaRoadbook(
            ContenidoRoadbook(
                notas = navegacion.notas,
                guiando = navegacion.estado.control as? EstadoNavegacion.Guiando,
                alertas = contenido.alertas,
                crono = contenido.crono,
            ),
            horizontal = vista.horizontal,
            modifier = Modifier.windowInsetsPadding(insetsSinTeclado().only(lados)),
        )
        ZonaInferior(contenido, barra = null)
        MenuSobreMapa(contenido.copiloto, contenido.menu, vista.horizontal)
    }
}

// Zona de abajo: avisos, píldora de velocidad, columna de botones y hoja de conducción (o la tarjeta de la fase).

private val ANCHO_HOJA_LATERAL = 320.dp
private val ANCHO_FASE_LATERAL = 440.dp

/** Hay una tarjeta de planificación o de fin (punto elegido, vista previa, llegada o error) que sustituye a la hoja. */
internal fun hayFaseInferior(nav: NavegacionUiState): Boolean =
    nav.puntoElegido != null ||
        nav.control is EstadoNavegacion.Llegada ||
        nav.control is EstadoNavegacion.Error ||
        (nav.enReposo && nav.vistaPrevia != null)

/**
 * Abajo del mapa. En vertical: avisos, la fila de la píldora (izquierda) y los botones (derecha) y, debajo, la hoja a
 * todo el ancho. En horizontal: la hoja de 320 dp abajo a la izquierda con la píldora y los avisos encima y los botones
 * a la derecha. Cuando hay una tarjeta de punto, vista previa, llegada o error, ella ocupa el sitio de la hoja.
 * [barra] es el campo «¿A dónde?» (null en el modo de notas grandes, que no lo tiene).
 */
@Composable
private fun BoxScope.ZonaInferior(
    contenido: ZonaMapaContenido,
    barra: EstadoBarraDestino?,
) {
    val vista = contenido.vista
    val estado = contenido.estado
    val nav = contenido.navegacion.estado
    val hoja = !hayFaseInferior(nav)
    val confirmacion = rememberConfirmacionTerminar()
    // El «atrás» del sistema también pide el segundo toque con el crono o la grabación en marcha, como la ✕.
    val requiere = terminarPideConfirmacion(nav.control, contenido.crono)
    BackHandler(enabled = requiere) { confirmacion.pulsar(requiere, contenido.acciones.onSalir) }
    val avisos: @Composable () -> Unit = {
        if (estado.hayDatos) AvisosDelPie(contenido)
        AvisoToqueOtraVez(confirmacion)
    }
    val pildora: @Composable () -> Unit = { PildoraDelPie(contenido) }
    val fase: @Composable () -> Unit = { if (estado.hayDatos) FaseInferior(contenido) }
    val laHoja: @Composable (Modifier) -> Unit = { HojaDeLaZona(contenido, barra, confirmacion, it) }
    val laterales = insetsSinTeclado().only(WindowInsetsSides.Horizontal)
    if (vista.horizontal) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(laterales), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.width(if (hoja) ANCHO_HOJA_LATERAL else ANCHO_FASE_LATERAL)) {
                Column(
                    Modifier.padding(horizontal = Espacio.s),
                    verticalArrangement = Arrangement.spacedBy(Espacio.s),
                ) {
                    avisos()
                    pildora()
                    if (!hoja) Box(Modifier.navigationBarsPadding().padding(bottom = Espacio.s)) { Column { fase() } }
                }
                if (hoja) {
                    Spacer(Modifier.height(Espacio.s))
                    laHoja(Modifier)
                }
            }
            Spacer(Modifier.weight(1f))
            GrupoDerecho(contenido, Modifier.navigationBarsPadding().padding(end = Espacio.s, bottom = Espacio.s))
        }
    } else {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(laterales)) {
            Column(Modifier.padding(horizontal = Espacio.s), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                avisos()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) { pildora() }
                    GrupoDerecho(contenido, Modifier)
                }
                if (!hoja) Box(Modifier.navigationBarsPadding().padding(bottom = Espacio.s)) { Column { fase() } }
            }
            if (hoja) {
                Spacer(Modifier.height(Espacio.s))
                laHoja(Modifier)
            }
        }
    }
}

/** La burbuja de escucha, justo sobre la píldora de velocidad, a la izquierda de la columna de botones. */
@Composable
private fun PildoraDelPie(contenido: ZonaMapaContenido) {
    val estado = contenido.estado
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        BurbujaEscucha(contenido.copiloto.estado.escuchando, contenido.copiloto.estado.dialogo)
        PildoraConduccion(
            velocidadKmh = estado.velocidadKmh,
            limiteKmh = estado.limiteKmh,
            alertas = contenido.alertas.panel,
            sinSenal = estado.sinSenalGps,
            limiteDesconocido = estado.limiteDesconocido,
        )
    }
}

/** La hoja de conducción con lo que hace cada uno de sus botones. */
@Composable
private fun HojaDeLaZona(
    contenido: ZonaMapaContenido,
    barra: EstadoBarraDestino?,
    confirmacion: ConfirmacionTerminar,
    modifier: Modifier,
) {
    val nav = contenido.navegacion.estado
    // Con el cronómetro o la grabación en marcha la ✕ pide un segundo toque; si no, un toque basta.
    val requiere = terminarPideConfirmacion(nav.control, contenido.crono)
    HojaConduccion(
        DatosHoja(contenido.estado, nav.control, contenido.crono, cierreArmado = confirmacion.armada),
        AccionesHoja(
            onSalir = { confirmacion.pulsar(requiere, contenido.acciones.onSalir) },
            onTerminar = { confirmacion.pulsar(requiere, contenido.navegacion.acciones.onTerminar) },
            onBuscarDestino = barra?.let { { it.pedirFoco() } },
            onBuscarGuiando = contenido.navegacion.acciones.onAbrirBusqueda,
        ),
        modifier,
    )
}

/** El panel de capas (si está abierto) a la izquierda de la columna de botones redondos. */
@Composable
private fun GrupoDerecho(
    contenido: ZonaMapaContenido,
    modifier: Modifier,
) {
    val botones = contenido.botones
    if (botones.fijos.isEmpty() && botones.tactiles.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.Bottom) {
        if (contenido.menu.actual == MenuMapa.CAPAS) PanelCapas(contenido.estado, contenido.acciones)
        ColumnaBotonesMapa(botones, contenido.tactilesVisibles, contenido.vista.horizontal)
    }
}

/**
 * Lo que flota sobre la píldora: la tarjeta de la incidencia tocada, el permiso de ubicación si falta, las paradas del
 * guiado, el aviso de mapas actualizados y el de descarga del relieve. Las atribuciones están solo en Ajustes >
 * Acerca de.
 */
@Composable
private fun AvisosDelPie(contenido: ZonaMapaContenido) {
    val vista = contenido.vista
    val acciones = contenido.acciones
    val navegacion = contenido.navegacion
    val nav = navegacion.estado
    val guiando = nav.control as? EstadoNavegacion.Guiando
    var paradasAbiertas by rememberSaveable { mutableStateOf(false) }
    contenido.alertas.seleccionada?.let {
        TarjetaIncidencia(it, contenido.alertas.frescura, acciones.onCerrarIncidencia)
    }
    if (vista.permiso != EstadoPermisoUbicacion.CONCEDIDO) {
        TarjetaPermiso(vista.permiso, acciones.onPedirPermiso, acciones.onAbrirAjustes)
    }
    if (guiando != null && guiando.paradas.isNotEmpty()) {
        ParadasDelGuiado(guiando, navegacion.acciones, paradasAbiertas, vista.horizontal) {
            paradasAbiertas = !paradasAbiertas
        }
    }
    AvisoDelPie(contenido.estado, nav.conduciendo, acciones.onDescartarAviso)
    AvisoRelieveDescargando(
        contenido.estado.datos
            .complemento(Complemento.RELIEVE)
            .descarga,
    )
}

/** Lo que sustituye a la hoja: la tarjeta del punto elegido y la vista previa de rutas, la llegada o el error. */
@Composable
private fun FaseInferior(contenido: ZonaMapaContenido) {
    val navegacion = contenido.navegacion
    val nav = navegacion.estado
    val acciones = contenido.acciones
    val densidad = LocalDensity.current
    val barraInferior = WindowInsets.navigationBars.getBottom(densidad)
    val margen = with(densidad) { (Espacio.s + MARGEN_PUNTO_SOBRE_TARJETA).roundToPx() }
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        nav.puntoElegido?.let {
            val opciones = OpcionesPunto(puedeAnadir = !nav.paradasLlenas)
            TarjetaPunto(
                it,
                opciones,
                navegacion.acciones,
                Modifier.onSizeChanged { tamano ->
                    acciones.onReservaInferiorPunto(tamano.height + barraInferior + margen)
                },
            )
        }
        PanelInferiorNavegacion(nav, navegacion.acciones, contenido.vista.horizontal)
    }
}

/** Margen entre el pin del punto elegido y la tarjeta que lo podría tapar. */
private val MARGEN_PUNTO_SOBRE_TARJETA = 24.dp

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
 * Arriba: en reposo, la barra «¿A dónde?» a todo el ancho con su lista de resultados; debajo, el carrete de notas de
 * la carretera y el banner de alerta (modo libre) y «Seguir a <destino>» si hay un guiado a medias. Mientras se guía,
 * el banner de maniobra, el carrete de notas y el banner de alerta ocupan esa franja. Con notas en el carrete la
 * barra se esconde (el carrete sube a su sitio): reaparece al tocar el mapa y se vuelve a esconder a los 8 s sin
 * toques, nunca con el campo enfocado, texto escrito, resultados o vista previa abiertos. El aviso de mapas
 * actualizados va abajo ([PieMapa]).
 */
@Composable
private fun CabeceraMapa(
    contenido: ContenidoCabecera,
    barra: EstadoBarraDestino,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    val estado = contenido.estado
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
                    CabeceraBusqueda(
                        navegacion.estado,
                        navegacion.acciones,
                        pedirFoco = barra.focoPendiente,
                        onFocoPedido = { barra.focoPendiente = false },
                    ) { barra.enfocada = it }
                }
                AccesoACasa(navegacion)
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
                CarreteNotas(navegacion.notas, compacta = horizontal)
                BannerAlerta(contenido.alertas.aviso, contenido.alertas.frescura, compacto = horizontal)
                CronoSuperior(contenido.crono, compacto = horizontal)
                BandaSinGps()
            }
            navegacion.estado.seguir?.let {
                TarjetaSeguir(it, navegacion.acciones.onSeguir, navegacion.acciones.onDescartarSeguir)
            }
        }
    }
}

/** Primer acceso rápido bajo la barra «¿A dónde?»: «Ir a casa», si hay casa y no hay nada escrito ni abierto. */
@Composable
private fun AccesoACasa(navegacion: NavegacionEnMapa) {
    val estado = navegacion.estado
    val casa = estado.casa ?: return
    if (estado.texto.isNotEmpty() || estado.busqueda !is BusquedaDestino.Inactiva || estado.vistaPrevia != null) return
    val nombre = stringResource(R.string.navegacion_ui_casa_nombre)
    ChipIconoWrc(
        Iconos.Casa,
        stringResource(R.string.navegacion_ui_ir_a_casa),
        modifier = Modifier.testTag(ETIQUETA_IR_A_CASA),
        onClick = { navegacion.acciones.onElegirDestino(Destino(nombre, casa)) },
    )
}

/**
 * Aviso de mapas actualizados: tarjeta de carbón abajo, sobre el panel y debajo de todo lo demás (no tapa las notas),
 * que se descarta sola a los 4 s; la X lo cierra antes.
 */
@Composable
private fun AvisoActualizado(
    version: String,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alCerrar by rememberUpdatedState(onCerrar)
    LaunchedEffect(version) {
        // Por fotogramas (no con `delay`): el reloj de los fotogramas es el que se adelanta en las pruebas.
        val inicio = withFrameMillis { it }
        var transcurrido = 0L
        while (transcurrido < DURACION_AVISO_MS) transcurrido = withFrameMillis { it } - inicio
        alCerrar()
    }
    TarjetaWrc(
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_AVISO),
        variante = VarianteTarjeta.MAPA,
        relleno = Espacio.s,
    ) {
        Row(Modifier.padding(start = Espacio.s), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.mapa_aviso_actualizado),
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSobreCarbon,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCerrar, modifier = Modifier.testTag(ETIQUETA_AVISO_CERRAR)) {
                Icon(
                    imageVector = Iconos.Cerrar,
                    contentDescription = stringResource(R.string.mapa_aviso_cerrar),
                    tint = TextoSobreCarbon,
                )
            }
        }
    }
}

/** El aviso de mapas actualizados, si toca: solo con datos nuevos y sin conducir. */
@Composable
private fun AvisoDelPie(
    estado: MapaUiState,
    conduciendo: Boolean,
    onDescartar: () -> Unit,
) {
    if (conduciendo) return
    estado.datos.avisoActualizado?.let { AvisoActualizado(it, onDescartar) }
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
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(OPACIDAD_SUPERFICIE_SOBRE_MAPA),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_PERMISO),
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
