package app.trombito.rallycopilot.ui.mapa

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.os.SystemClock
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
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.ui.comun.recurso
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
)

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
        }
    }
    LaunchedEffect(controlador, estiloJson) { controlador.cambiarEstilo(estiloJson) }
    // Posición y preferencias van juntas: una fijación nueva cancela la animación de zoom, que se pide después.
    LaunchedEffect(controlador, posicion, preferencias) { controlador.actualizar(posicion, preferencias) }

    val descripcion = stringResource(R.string.mapa_vista_descripcion)
    AndroidView(factory = { vista }, modifier = modifier.semantics { contentDescription = descripcion })
}

/** Reenvía el ciclo de vida de Compose al `MapView` (que no es lifecycle-aware) sin repetir ni saltarse pasos. */
private class PuenteCicloVida(
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
private class ControladorMapa(
    private val vista: MapView,
    private val camaraInicial: CamaraMapa?,
    private val alCambiarCamara: (CamaraMapa) -> Unit,
    private val alPerderSeguimiento: () -> Unit,
    alCambiarZoomManual: (Double, Long) -> Unit,
) {
    private var mapa: MapLibreMap? = null
    private var estilo: Style? = null
    private var estiloPedido: String? = null
    private var estiloAplicado: String? = null
    private var posicion: Fijacion? = null
    private var preferencias = PreferenciasVistaMapa()
    private var ubicacionActivada = false
    private var oyenteRegistrado = false
    private val zoom = ZoomSeguimiento(camaraInicial?.zoom, alCambiarZoomManual)

    /** Entre onStart y onStop: fuera de ahí el mapa se está desmontando y no se deben guardar cambios de cámara. */
    private var activo = false

    /** No se guarda la cámara por defecto (Mallorca a z9): solo la restaurada o la que ya sigue al coche. */
    private var guardarCamara = camaraInicial != null

    fun activar(valor: Boolean) {
        activo = valor
    }

    fun iniciar() {
        vista.getMapAsync { m ->
            mapa = m
            m.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
                isCompassEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
            }
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
            m.addOnCameraIdleListener { guardarCamaraActual(m) }
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
        val componente = mapa?.locationComponent?.takeIf { ubicacionActivada }
        if (nuevas.relieve != previas.relieve) estilo?.let { aplicarRelieve(it, nuevas.relieve) }
        if (componente != null && nuevas.icono != previas.icono) {
            componente.applyStyle(opcionesUbicacion(vista.context, nuevas.icono))
        }
        val cambioCamara = nuevas.siguiendo != previas.siguiendo || nuevas.orientacion != previas.orientacion
        if (componente != null && cambioCamara) fijarCamara(componente)
        asegurarUbicacion()
        // Después de la posición: una fijación nueva cancela la animación de zoom en curso.
        if (ubicacionActivada && nuevas.siguiendo) {
            mapa?.locationComponent?.let { zoom.aplicar(it, nuevas.zoomObjetivo) }
        }
    }

    private fun aplicarEstilo(json: String) {
        val m = mapa ?: return
        if (json == estiloAplicado) return
        estiloAplicado = json
        estilo = null
        ubicacionActivada = false
        m.setStyle(Style.Builder().fromJson(json)) { cargado ->
            estilo = cargado
            aplicarRelieve(cargado, preferencias.relieve)
            asegurarUbicacion()
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
        componente.forceLocationUpdate(fijacion.aLocation())
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
                        if (activo) alPerderSeguimiento()
                    }

                    override fun onCameraTrackingChanged(currentMode: Int) = Unit
                },
            )
        }
        ubicacionActivada = true
        guardarCamara = true
    }

    private fun fijarCamara(componente: LocationComponent) {
        if (preferencias.siguiendo) {
            // El zoom va dentro de la transición al modo de seguimiento: `zoomWhileTracking` suelto lo cancela cada
            // fijación nueva (1 Hz) y la cámara se quedaba al zoom inicial. Rumbo arriba: el mapa gira con el GPS;
            // norte arriba: solo sigue la posición y la transición lleva el rumbo a 0.
            val norte = preferencias.orientacion == Orientacion.NORTE
            val destino = preferencias.zoomObjetivo ?: zoomParaSeguir(zoom.vigente)
            componente.setCameraMode(
                if (norte) CameraMode.TRACKING else CameraMode.TRACKING_GPS,
                DURACION_TRANSICION_MS,
                destino,
                if (norte) BEARING_NORTE else null,
                null,
                null,
            )
            zoom.yaAplicado(destino)
        } else {
            componente.cameraMode = CameraMode.NONE
        }
    }
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

private fun Fijacion.aLocation(): Location =
    Location(PROVEEDOR).also { l ->
        l.latitude = punto.lat
        l.longitude = punto.lon
        l.time = tiempoMs
        l.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        velocidadMs?.let { l.speed = it.toFloat() }
        rumboGrados?.let { l.bearing = it.toFloat() }
        precisionM?.let { l.accuracy = it.toFloat() }
    }
