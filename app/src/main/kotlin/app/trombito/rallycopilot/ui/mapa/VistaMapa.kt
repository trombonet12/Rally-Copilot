package app.trombito.rallycopilot.ui.mapa

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.PosicionFantasma
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.ui.alertas.BocadilloMapa
import app.trombito.rallycopilot.ui.alertas.MarcadorIncidencia
import app.trombito.rallycopilot.ui.alertas.RadaresUi
import app.trombito.rallycopilot.ui.comun.recurso
import org.maplibre.android.LibraryLoader
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory

/** Centro inicial sin posición: Mallorca. */
private const val LAT_INICIAL = 39.62
private const val LON_INICIAL = 2.98
private const val ZOOM_INICIAL = 9.0
private const val ZOOM_SEGUIMIENTO = 15.0
private const val PROVEEDOR = "gps"
private const val DURACION_TRANSICION_MS = 750L
private const val BEARING_NORTE = 0.0

/** Capas del estilo que forman el relieve (sombreado y curvas de nivel); si alguna no existe, se ignora. */
internal val CAPAS_RELIEVE = listOf("relieve-sombra", "curvas-linea", "curvas-indice", "curvas-etiqueta")

/** Por debajo de este zoom (vista de la isla) no es un zoom de conducción: al seguir se usa [ZOOM_SEGUIMIENTO]. */
private const val ZOOM_MINIMO_SEGUIMIENTO = 12.0

/**
 * Zoom con el que la cámara sigue al coche: el que tenía el usuario (o el restaurado tras girar) si es de conducción;
 * sin cámara previa o con la vista de toda la isla (z9 inicial), ~15.
 */
internal fun zoomParaSeguir(zoomActual: Double?): Double =
    if (zoomActual == null || zoomActual < ZOOM_MINIMO_SEGUIMIENTO) ZOOM_SEGUIMIENTO else zoomActual

/** Lo que la vista del mapa intercambia con la pantalla sobre la cámara. */
data class CamaraVistaMapa(
    /** Cámara a restaurar al crear la vista (p. ej. tras girar la pantalla); null = centro inicial de Mallorca. */
    val inicial: CamaraMapa? = null,
    /** La cámara se ha detenido en un nuevo sitio: se guarda para restaurarla. */
    val onCambio: (CamaraMapa) -> Unit = {},
    /** El usuario movió el mapa y la cámara dejó de seguir al coche. */
    val onSeguimientoPerdido: () -> Unit = {},
    /** El usuario cambió el zoom con un gesto: zoom final y reloj monótono (ms) para pausar el zoom automático. */
    val onZoomManual: (zoom: Double, ahoraMs: Long) -> Unit = { _, _ -> },
    /** Toque en el mapa: el id de la incidencia tocada o null si se tocó otra cosa. */
    val onToqueIncidencia: (id: String?) -> Unit = {},
    /** Toque en una nota del mapa (solo hay notas pintadas con el coche parado): su posición en la hoja. */
    val onToqueNota: (posicionM: Double) -> Unit = {},
    /** Mantener pulsado el mapa: el punto y el nombre del lugar o la calle bajo el dedo (null si no hay). */
    val onMantenerPulsado: (punto: PuntoGeo, nombre: String?) -> Unit = { _, _ -> },
    /** Toque en el mapa que no es de una nota ni de una incidencia: el punto tocado (lo usa el modo dibujo). */
    val onToque: (punto: PuntoGeo) -> Unit = {},
)

/** Lo que el mapa pinta según los ajustes de conducción y el zoom automático. */
data class PreferenciasVistaMapa(
    /** La cámara sigue al coche (se pierde al mover el mapa). */
    val siguiendo: Boolean = true,
    val relieve: Boolean = false,
    val icono: IconoPosicion = IconoPosicion.FLECHA_WRC,
    val orientacion: Orientacion = Orientacion.RUMBO,
    /** Zoom automático a aplicar mientras la cámara sigue al coche; null = el del usuario. */
    val zoomObjetivo: Double? = null,
    /** Ruta dibujada sobre las vías (vista previa o guiado); null = ninguna. */
    val ruta: RutaEnMapa? = null,
    /** Marcadores de incidencias de tráfico (icono por tipo); vacío = ninguno. */
    val incidencias: List<MarcadorIncidencia> = emptyList(),
    /** Notas de la hoja tocables para editarlas (con el coche parado); vacío = ninguna. */
    val notas: List<MarcadorNota> = emptyList(),
    /** Estela de por dónde se ha pasado en el recorrido en curso (gris, bajo la ruta); vacía = ninguna. */
    val estela: List<List<PuntoGeo>> = emptyList(),
    /** Todos los radares y los segmentos de tramo, siempre visibles; vacío = ninguno. */
    val radares: RadaresUi = RadaresUi(),
    /**
     * Píxeles que tapa por abajo la tarjeta del punto elegido (su alto más 24 dp); 0 = sin tarjeta. Con un punto
     * elegido la cámara lo centra en lo que queda libre, sobre la tarjeta.
     */
    val reservaPuntoPx: Int = 0,
    /** Bocadillo de lo que se acerca o se acaba de pasar, anclado a su marcador; null = ninguno. */
    val bocadillo: BocadilloMapa? = null,
    /**
     * Alto (px, desde abajo) de la zona de conducción de la pantalla (píldora y botones con lo que llevan encima) ya
     * estable; el coche se coloca por encima. 0 = sin medir: altura de siempre. Lo rellena [VistaMapa] desde
     * [LocalZonaInferior].
     */
    val zonaInferiorPx: Int = 0,
    /** Coche fantasma de la mejor pasada (translúcido, bajo el coche real); null = ninguno. */
    val fantasma: PosicionFantasma? = null,
)

/**
 * Carga la biblioteca nativa de MapLibre (12,8 MiB) fuera del hilo principal, al arrancar la app: así el primer mapa
 * no la espera. Solo esto es seguro fuera del principal: `MapLibre.getInstance` y `MapView` son `@UiThread` y siguen
 * creándose en [VistaMapa]. Si no carga (JVM de pruebas), el mapa lo vuelve a intentar al abrirse.
 */
fun precargarBibliotecaMapa() {
    try {
        LibraryLoader.load()
    } catch (e: UnsatisfiedLinkError) {
        Log.w("VistaMapa", "No se pudo precargar la biblioteca del mapa: ${e.javaClass.simpleName}")
    }
}

/**
 * El mapa MapLibre dentro de Compose. Sin lógica de negocio: recibe el estilo ya resuelto, la última posición y si
 * la cámara debe seguirla; avisa cuando el usuario mueve el mapa y el seguimiento se pierde, y de cada cambio de
 * cámara para que se pueda restaurar al recrear la actividad (giro de pantalla). El logo, la atribución
 * y el botón de información de MapLibre se desactivan: la atribución la dibuja la pantalla, siempre visible.
 */
@Composable
fun VistaMapa(
    estiloJson: String,
    posicion: Fijacion?,
    camara: CamaraVistaMapa,
    preferencias: PreferenciasVistaMapa,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val actual = rememberUpdatedState(camara)
    val vista =
        remember {
            MapLibre.getInstance(contexto)
            MapLibre.setConnected(false)
            MapView(contexto)
        }
    val controlador =
        remember(vista) {
            ControladorMapa(
                vista = vista,
                camaraInicial = camara.inicial,
                alCambiarCamara = { actual.value.onCambio(it) },
                alPerderSeguimiento = { actual.value.onSeguimientoPerdido() },
                alCambiarZoomManual = { zoom, ahora -> actual.value.onZoomManual(zoom, ahora) },
                alTocarIncidencia = { actual.value.onToqueIncidencia(it) },
                alTocarNota = { actual.value.onToqueNota(it) },
                alMantenerPulsado = { punto, nombre -> actual.value.onMantenerPulsado(punto, nombre) },
                alTocar = { actual.value.onToque(it) },
            )
        }

    DisposableEffect(vista, ciclo) {
        val puente = PuenteCicloVida(vista, controlador::activar)
        ciclo.lifecycle.addObserver(puente)
        controlador.iniciar()
        val memoria =
            object : ComponentCallbacks2 {
                override fun onConfigurationChanged(nueva: Configuration) = Unit

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onLowMemory() = vista.onLowMemory()

                override fun onTrimMemory(nivel: Int) = Unit
            }
        contexto.registerComponentCallbacks(memoria)
        onDispose {
            contexto.unregisterComponentCallbacks(memoria)
            ciclo.lifecycle.removeObserver(puente)
            puente.cerrar()
            controlador.detenerAnimaciones()
        }
    }
    LaunchedEffect(controlador, estiloJson) { controlador.cambiarEstilo(estiloJson) }
    // Posición y preferencias van juntas: una fijación nueva cancela la animación de zoom, que se pide después.
    val zonaInferiorPx = LocalZonaInferior.current?.establePx?.intValue
    val efectivas = if (zonaInferiorPx == null) preferencias else preferencias.copy(zonaInferiorPx = zonaInferiorPx)
    LaunchedEffect(controlador, posicion, efectivas) { controlador.actualizar(posicion, efectivas) }

    val descripcion = stringResource(R.string.mapa_vista_descripcion)
    AndroidView(factory = { vista }, modifier = modifier.semantics { contentDescription = descripcion })
}

/** Reenvía el ciclo de vida de Compose al `MapView` (que no es lifecycle-aware) sin repetir ni saltarse pasos. */
internal class PuenteCicloVida(
    private val vista: MapView,
    private val alActivar: (Boolean) -> Unit,
) : LifecycleEventObserver {
    private var creada = false
    private var iniciada = false
    private var reanudada = false
    private var destruida = false

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event,
    ) {
        if (destruida) return
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                crear()
            }

            Lifecycle.Event.ON_START -> {
                crear()
                iniciada = true
                vista.onStart()
                alActivar(true)
            }

            Lifecycle.Event.ON_RESUME -> {
                reanudada = true
                vista.onResume()
            }

            Lifecycle.Event.ON_PAUSE -> {
                pausar()
            }

            Lifecycle.Event.ON_STOP -> {
                detener()
            }

            Lifecycle.Event.ON_DESTROY -> {
                cerrar()
            }

            else -> {}
        }
    }

    /** La composición sale antes que el ciclo de vida: completa los pasos pendientes hasta destruir la vista. */
    fun cerrar() {
        if (destruida) return
        pausar()
        detener()
        destruida = true
        if (creada) vista.onDestroy()
    }

    private fun crear() {
        if (!creada) {
            creada = true
            vista.onCreate(null)
        }
    }

    private fun pausar() {
        if (reanudada) {
            reanudada = false
            vista.onPause()
        }
    }

    private fun detener() {
        if (iniciada) {
            iniciada = false
            alActivar(false)
            vista.onStop()
        }
    }
}

/** Estado imperativo del mapa: estilo, componente de ubicación (sin motor propio) y cámara. */
@Suppress("LongParameterList", "TooManyFunctions") // un callback por gesto del mapa; una capa por función
private class ControladorMapa(
    private val vista: MapView,
    private val camaraInicial: CamaraMapa?,
    private val alCambiarCamara: (CamaraMapa) -> Unit,
    private val alPerderSeguimiento: () -> Unit,
    alCambiarZoomManual: (Double, Long) -> Unit,
    private val alTocarIncidencia: (String?) -> Unit,
    private val alTocarNota: (Double) -> Unit,
    private val alMantenerPulsado: (PuntoGeo, String?) -> Unit,
    private val alTocar: (PuntoGeo) -> Unit,
) {
    private var mapa: MapLibreMap? = null
    private var estilo: Style? = null
    private val capaEstela = CapaEstela()
    private val capaFantasma = CapaFantasma()
    private var estiloPedido: String? = null
    private var estiloAplicado: String? = null
    private var posicion: Fijacion? = null

    /** Último rumbo mostrado: se conserva cuando una fijación llega sin rumbo. */
    private var ultimoRumbo: Double? = null
    private var preferencias = PreferenciasVistaMapa()
    private var ubicacionActivada = false
    private var oyenteRegistrado = false
    private val descarte = DescarteSeguimiento()
    private val zoom = ZoomSeguimiento(camaraInicial?.zoom, alCambiarZoomManual)

    /** Entre onStart y onStop: fuera de ahí el mapa se está desmontando y no se deben guardar cambios de cámara. */
    private var activo = false

    /** No se guarda la cámara por defecto (Mallorca a z9): solo la restaurada o la que ya sigue al coche. */
    private var guardarCamara = camaraInicial != null

    fun activar(valor: Boolean) {
        activo = valor
    }

    /** Al salir de la composición: corta el deslizamiento del fantasma antes de que se destruya la vista. */
    fun detenerAnimaciones() = capaFantasma.detener()

    fun iniciar() {
        vista.getMapAsync { m ->
            mapa = m
            m.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
                isCompassEnabled = false
                // Girar e inclinar con dos dedos (petición de Joan, H5B): el gesto suelta el seguimiento y «recentrar»
                // vuelve al modo elegido (rumbo arriba inclinado o norte).
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
            }
            // Inclinar con prioridad sobre girar (ver [ajustarGestosMapa]): con el SDK por defecto costaba inclinar.
            ajustarGestosMapa(m.gesturesManager, vista.resources.displayMetrics.density)
            val centro = camaraInicial?.let { LatLng(it.latitud, it.longitud) } ?: LatLng(LAT_INICIAL, LON_INICIAL)
            m.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition
                        .Builder()
                        .target(centro)
                        .zoom(camaraInicial?.zoom ?: ZOOM_INICIAL)
                        .build(),
                ),
            )
            zoom.escuchar(m) { activo }
            m.addOnMapClickListener { punto ->
                val pantalla = m.projection.toScreenLocation(punto)
                val densidad = vista.resources.displayMetrics.density
                val nota = CapaNotas.posicionEn(m, pantalla.x, pantalla.y, densidad)
                if (nota != null) {
                    alTocarNota(nota)
                    return@addOnMapClickListener true
                }
                val id = CapaIncidencias.idEn(m, pantalla.x, pantalla.y, densidad)
                alTocarIncidencia(id)
                if (id == null) alTocar(PuntoGeo(punto.latitude, punto.longitude))
                id != null
            }
            m.addOnMapLongClickListener { punto ->
                val pantalla = m.projection.toScreenLocation(punto)
                val nombre = NombreDelPunto.en(m, pantalla.x, pantalla.y, vista.resources.displayMetrics.density)
                alMantenerPulsado(PuntoGeo(punto.latitude, punto.longitude), nombre)
                true
            }
            m.addOnCameraIdleListener { guardarCamaraActual(m) }
            // El alto del mapa cambia al girar o al abrir un panel: el marcador debe seguir en su sitio.
            vista.addOnLayoutChangeListener { _, izq, arr, der, aba, oIzq, oArr, oDer, oAba ->
                val cambio = (der - izq) != (oDer - oIzq) || (aba - arr) != (oAba - oArr)
                if (cambio && ubicacionActivada && preferencias.siguiendo) {
                    m.locationComponent.rellenarSeguimiento(relleno(), 0L)
                }
            }
            estiloPedido?.let(::aplicarEstilo)
        }
    }

    private fun guardarCamaraActual(m: MapLibreMap) {
        if (!activo || !guardarCamara) return
        val posicionCamara = m.cameraPosition
        val centro: LatLng? = posicionCamara.target
        if (centro == null) return
        zoom.vigente = posicionCamara.zoom
        alCambiarCamara(CamaraMapa(centro.latitude, centro.longitude, posicionCamara.zoom))
    }

    fun cambiarEstilo(json: String) {
        estiloPedido = json
        if (mapa != null) aplicarEstilo(json)
    }

    /** Aplica la posición y las preferencias nuevas (solo lo que cambió) y, al final, el zoom automático. */
    fun actualizar(
        nueva: Fijacion?,
        nuevas: PreferenciasVistaMapa,
    ) {
        val previas = preferencias
        preferencias = nuevas
        posicion = nueva
        nueva?.rumboGrados?.let { ultimoRumbo = it }
        val componente = mapa?.locationComponent?.takeIf { ubicacionActivada }
        if (nuevas.relieve != previas.relieve) estilo?.let { aplicarRelieve(it, nuevas.relieve) }
        aplicarRuta(previas.ruta, nuevas.ruta)
        centrarPuntoElegido(previas, nuevas)
        actualizarMarcadores(previas, nuevas)
        if (componente != null && nuevas.icono != previas.icono) {
            componente.applyStyle(opcionesUbicacion(vista.context, nuevas.icono))
        }
        val cambioCamara = nuevas.siguiendo != previas.siguiendo || nuevas.orientacion != previas.orientacion
        if (componente != null && cambioCamara) {
            fijarCamara(componente)
        } else if (componente != null) {
            moverCocheConLaZona(componente, previas, nuevas)
        }
        asegurarUbicacion()
        // Después de la posición: una fijación nueva cancela la animación de zoom en curso.
        if (ubicacionActivada && nuevas.siguiendo) {
            mapa?.locationComponent?.let { zoom.aplicar(it, nuevas.zoomObjetivo) }
        }
    }

    /** La zona de abajo cambió con la cámara ya siguiendo: el coche sube o baja con el relleno animado. */
    private fun moverCocheConLaZona(
        componente: LocationComponent,
        previas: PreferenciasVistaMapa,
        nuevas: PreferenciasVistaMapa,
    ) {
        val cambio = nuevas.siguiendo && nuevas.zonaInferiorPx != previas.zonaInferiorPx
        if (cambio && componente.cameraMode != CameraMode.NONE) {
            componente.rellenarSeguimiento(relleno(), DURACION_RELLENO_ZONA_MS)
        }
    }

    /** Redibuja los marcadores (incidencias y notas editables) que hayan cambiado. */
    private fun actualizarMarcadores(
        previas: PreferenciasVistaMapa,
        nuevas: PreferenciasVistaMapa,
    ) {
        val capas = estilo ?: return
        if (nuevas.incidencias != previas.incidencias) CapaIncidencias.actualizar(capas, nuevas.incidencias)
        if (nuevas.radares != previas.radares) CapaRadares.actualizar(capas, nuevas.radares)
        if (nuevas.bocadillo != previas.bocadillo) CapaBocadillo.actualizar(capas, vista.context, nuevas.bocadillo)
        if (nuevas.notas != previas.notas) CapaNotas.actualizar(capas, nuevas.notas)
        if (nuevas.estela != previas.estela) capaEstela.actualizar(capas, nuevas.estela)
        if (nuevas.fantasma != previas.fantasma || nuevas.icono != previas.icono) {
            capaFantasma.actualizar(capas, vista.context, nuevas.icono, nuevas.fantasma)
        }
    }

    private fun aplicarEstilo(json: String) {
        val m = mapa ?: return
        if (json == estiloAplicado) return
        estiloAplicado = json
        estilo = null
        capaFantasma.detener()
        ubicacionActivada = false
        m.setStyle(Style.Builder().fromJson(json)) { cargado ->
            estilo = cargado
            aplicarRelieve(cargado, preferencias.relieve)
            // Antes de activar la ubicación: la línea de la ruta queda debajo de la flecha de posición, y la estela
            // (que se instala antes) debajo de la ruta.
            capaEstela.instalar(cargado, preferencias.estela)
            CapaRuta.instalar(cargado, vista.context, preferencias.ruta)
            CapaRadares.instalar(cargado, vista.context, preferencias.radares)
            CapaIncidencias.instalar(cargado, vista.context, preferencias.incidencias)
            CapaNotas.instalar(cargado, preferencias.notas)
            // El fantasma, antes de la ubicación y del bocadillo: queda por debajo del coche real.
            capaFantasma.instalar(cargado, vista.context, preferencias.icono, preferencias.fantasma)
            CapaBocadillo.instalar(cargado, vista.context, preferencias.bocadillo)
            asegurarUbicacion()
        }
    }

    /** Redibuja la línea si cambian los puntos y encuadra la cámara al abrir la vista previa o cambiar de ruta. */
    private fun aplicarRuta(
        previa: RutaEnMapa?,
        nueva: RutaEnMapa?,
    ) {
        if (nueva == previa) return
        val cambiaronPuntos = nueva?.puntos != previa?.puntos
        // Puntos, destino, paradas o punto elegido: la fuente es barata de actualizar.
        estilo?.let { CapaRuta.actualizar(it, nueva) }
        val nuevoEncuadre = cambiaronPuntos || previa?.encuadrar != true
        if (nueva != null && nueva.encuadrar && nuevoEncuadre) {
            val m = mapa ?: return
            // Sin seguimiento: el componente de ubicación recolocaría la cámara con cada fijación.
            if (ubicacionActivada) m.locationComponent.cameraMode = CameraMode.NONE
            // La vista previa encuadra la ruta entera: sin el relleno del seguimiento (vuelve al recentrar).
            m.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0))
            m.encuadrarRuta(nueva.puntos, vista.resources.displayMetrics.density, vista.height, nueva.paradas.size)
        }
    }

    /**
     * Al elegir un punto (mantener pulsado) la tarjeta ocupa el pie del mapa: la cámara lo centra con un relleno
     * inferior igual a la tarjeta más 24 dp, para que el pin no quede debajo. Deja de seguir al coche.
     */
    private fun centrarPuntoElegido(
        previas: PreferenciasVistaMapa,
        nuevas: PreferenciasVistaMapa,
    ) {
        val punto = nuevas.ruta?.elegido ?: return
        val m = mapa ?: return
        val reserva = nuevas.reservaPuntoPx
        val cambio = punto != previas.ruta?.elegido || reserva != previas.reservaPuntoPx
        val encuadrando = nuevas.ruta?.encuadrar == true
        if (reserva > 0 && cambio && !encuadrando) {
            if (ubicacionActivada) m.locationComponent.cameraMode = CameraMode.NONE
            val posicionCamara =
                CameraPosition
                    .Builder()
                    .target(LatLng(punto.lat, punto.lon))
                    .padding(0.0, 0.0, 0.0, reserva.toDouble())
                    .build()
            m.easeCamera(CameraUpdateFactory.newCameraPosition(posicionCamara), DURACION_ENCUADRE_MS)
        }
    }

    private fun asegurarUbicacion() {
        val m = mapa
        val cargado = estilo
        val fijacion = posicion
        if (m == null || cargado == null || fijacion == null) return
        val componente = m.locationComponent
        val primera = !ubicacionActivada
        if (primera) activarUbicacion(componente, cargado)
        if (!ubicacionActivada) return
        componente.forceLocationUpdate(fijacion.aLocation(ultimoRumbo))
        // Con la primera fijación ya dentro: la transición de seguimiento necesita una posición a la que ir.
        if (primera) fijarCamara(componente)
    }

    private fun activarUbicacion(
        componente: LocationComponent,
        cargado: Style,
    ) {
        // Sin el permiso preciso no hay posición que dibujar (y el componente lo exige).
        val permiso = vista.context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (permiso != PackageManager.PERMISSION_GRANTED) return
        componente.activateLocationComponent(
            LocationComponentActivationOptions
                .builder(vista.context, cargado)
                .useDefaultLocationEngine(false)
                .locationComponentOptions(opcionesUbicacion(vista.context, preferencias.icono))
                .build(),
        )
        componente.isLocationComponentEnabled = true
        componente.renderMode = RenderMode.GPS
        if (!oyenteRegistrado) {
            oyenteRegistrado = true
            componente.addOnCameraTrackingChangedListener(
                object : OnCameraTrackingChangedListener {
                    override fun onCameraTrackingDismissed() {
                        if (activo && descarte.esDelUsuario()) alPerderSeguimiento()
                    }

                    override fun onCameraTrackingChanged(currentMode: Int) = Unit
                },
            )
        }
        ubicacionActivada = true
        guardarCamara = true
    }

    /** Relleno superior del seguimiento: el coche sobre la zona inferior de la pantalla (ver [fraccionMarcador]). */
    private fun relleno(): Double {
        val densidad = vista.resources.displayMetrics.density
        val fraccion = fraccionMarcador(vista.width, vista.height, preferencias.zonaInferiorPx, densidad)
        return rellenoSuperiorSeguimiento(vista.width, vista.height, fraccion).toDouble()
    }

    private fun fijarCamara(componente: LocationComponent) {
        descarte.aplicando {
            if (preferencias.siguiendo) {
                // El zoom va dentro de la transición al modo de seguimiento: `zoomWhileTracking` suelto lo cancela cada
                // fijación nueva (1 Hz) y la cámara se quedaba al zoom inicial. Rumbo arriba: el mapa gira con el GPS;
                // norte arriba: solo sigue la posición y la transición lleva el rumbo a 0.
                val norte = preferencias.orientacion == Orientacion.NORTE
                val destino = preferencias.zoomObjetivo ?: zoomParaSeguir(zoom.vigente)
                // El marcador queda abajo (relleno fijo del mapa) y el mapa se inclina con el rumbo arriba; la
                // inclinación va dentro de la transición, igual que el zoom, para que las fijaciones de 1 Hz no la
                // cancelen. El relleno se fija en la cámara ANTES de pedir el modo (después cancelaría la transición)
                // y el componente lo anota después: así sabe en qué valor está si la vista previa lo puso a 0.
                // Se pasa antes por NONE: pedir TRACKING_GPS desde TRACKING (o al revés) no aplica la inclinación ni
                // el rumbo de la transición y el mapa se quedaba plano; desde NONE (recentrar, primera fijación) sí.
                // El descarte que avisa MapLibre al pasar por NONE lo provoca la app (ver [DescarteSeguimiento]).
                val arriba = relleno()
                componente.cameraMode = CameraMode.NONE
                mapa?.moveCamera(CameraUpdateFactory.paddingTo(0.0, arriba, 0.0, 0.0))
                componente.setCameraMode(
                    if (norte) CameraMode.TRACKING else CameraMode.TRACKING_GPS,
                    DURACION_TRANSICION_MS,
                    destino,
                    if (norte) BEARING_NORTE else null,
                    inclinacionSeguimiento(preferencias.orientacion),
                    null,
                )
                componente.rellenarSeguimiento(arriba, 0L)
                zoom.yaAplicado(destino)
            } else {
                // Al dejar de seguir no se fuerza nada: el relleno y la inclinación se quedan como estaban.
                componente.cameraMode = CameraMode.NONE
            }
        }
    }
}

/**
 * Relleno superior de la cámara mientras sigue al coche, para que el marcador quede en la parte baja de la pantalla.
 * Va por el componente de ubicación (como el zoom y la inclinación): un `moveCamera` suelto cancelaría la transición
 * de seguimiento y el mapa dejaría de seguir.
 */
private fun LocationComponent.rellenarSeguimiento(
    relleno: Double,
    duracionMs: Long,
) {
    paddingWhileTracking(doubleArrayOf(0.0, relleno, 0.0, 0.0), duracionMs)
}

/** Muestra u oculta el relieve y las curvas de nivel; las capas que el estilo no tenga se ignoran. */
private fun aplicarRelieve(
    estilo: Style,
    visible: Boolean,
) {
    val visibilidad = PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE)
    CAPAS_RELIEVE.forEach { id -> estilo.getLayer(id)?.setProperties(visibilidad) }
}

/** Icono de posición sin círculo ni sombra de fondo y sin círculo de precisión. */
private fun opcionesUbicacion(
    contexto: Context,
    icono: IconoPosicion,
): LocationComponentOptions {
    val dibujo = icono.recurso()
    return LocationComponentOptions
        .builder(contexto)
        .gpsDrawable(dibujo)
        .foregroundDrawable(dibujo)
        .foregroundDrawableStale(dibujo)
        .bearingDrawable(dibujo)
        .backgroundDrawable(R.drawable.ic_posicion_vacio)
        .backgroundDrawableStale(R.drawable.ic_posicion_vacio)
        .accuracyAlpha(0f)
        .accuracyAnimationEnabled(false)
        .elevation(0f)
        .build()
}

/**
 * La fijación como `Location` para el marcador. Sin rumbo (receptor sin él y sin movimiento estimado) se usa
 * [ultimoRumbo], el último mostrado: una `Location` sin rumbo vale 0° y el mapa giraría al norte y de vuelta.
 */
internal fun Fijacion.aLocation(ultimoRumbo: Double? = null): Location =
    Location(PROVEEDOR).also { l ->
        l.latitude = punto.lat
        l.longitude = punto.lon
        l.time = tiempoMs
        l.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        velocidadMs?.let { l.speed = it.toFloat() }
        (rumboGrados ?: ultimoRumbo)?.let { l.bearing = it.toFloat() }
        precisionM?.let { l.accuracy = it.toFloat() }
    }
