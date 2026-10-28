package app.trombito.rallycopilot.ui.mapa

import android.Manifest
import android.content.ComponentCallbacks2
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
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** Centro inicial sin posición: Mallorca. */
private const val LAT_INICIAL = 39.62
private const val LON_INICIAL = 2.98
private const val ZOOM_INICIAL = 9.0
private const val ZOOM_SEGUIMIENTO = 15.0
private const val PROVEEDOR = "gps"

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
    siguiendo: Boolean,
    camara: CamaraVistaMapa,
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
    LaunchedEffect(controlador, posicion) { controlador.actualizarPosicion(posicion) }
    LaunchedEffect(controlador, siguiendo) { controlador.seguir(siguiendo) }

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
) {
    private var mapa: MapLibreMap? = null
    private var estilo: Style? = null
    private var estiloPedido: String? = null
    private var estiloAplicado: String? = null
    private var posicion: Fijacion? = null
    private var siguiendo = true
    private var ubicacionActivada = false
    private var oyenteRegistrado = false

    /** Entre onStart y onStop: fuera de ahí el mapa se está desmontando y no se deben guardar cambios de cámara. */
    private var activo = false

    /** No se guarda la cámara por defecto (Mallorca a z9): solo la restaurada o la que ya sigue al coche. */
    private var guardarCamara = camaraInicial != null
    private var zoomActual: Double? = camaraInicial?.zoom

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
            m.addOnCameraIdleListener { guardarCamaraActual(m) }
            estiloPedido?.let(::aplicarEstilo)
        }
    }

    private fun guardarCamaraActual(m: MapLibreMap) {
        if (!activo || !guardarCamara) return
        val posicionCamara = m.cameraPosition
        val centro: LatLng? = posicionCamara.target
        if (centro == null) return
        zoomActual = posicionCamara.zoom
        alCambiarCamara(CamaraMapa(centro.latitude, centro.longitude, posicionCamara.zoom))
    }

    fun cambiarEstilo(json: String) {
        estiloPedido = json
        if (mapa != null) aplicarEstilo(json)
    }

    fun actualizarPosicion(nueva: Fijacion?) {
        posicion = nueva
        asegurarUbicacion()
    }

    fun seguir(valor: Boolean) {
        siguiendo = valor
        if (ubicacionActivada) mapa?.locationComponent?.let { fijarCamara(it) }
    }

    private fun aplicarEstilo(json: String) {
        val m = mapa ?: return
        if (json == estiloAplicado) return
        estiloAplicado = json
        estilo = null
        ubicacionActivada = false
        m.setStyle(Style.Builder().fromJson(json)) { cargado ->
            estilo = cargado
            asegurarUbicacion()
        }
    }

    private fun asegurarUbicacion() {
        val m = mapa
        val cargado = estilo
        val fijacion = posicion
        if (m == null || cargado == null || fijacion == null) return
        val componente = m.locationComponent
        if (!ubicacionActivada) activarUbicacion(componente, cargado)
        componente.forceLocationUpdate(fijacion.aLocation())
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
        fijarCamara(componente)
    }

    private fun fijarCamara(componente: LocationComponent) {
        if (siguiendo) {
            componente.cameraMode = CameraMode.TRACKING_GPS
            componente.zoomWhileTracking(zoomParaSeguir(zoomActual))
        } else {
            componente.cameraMode = CameraMode.NONE
        }
    }
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
