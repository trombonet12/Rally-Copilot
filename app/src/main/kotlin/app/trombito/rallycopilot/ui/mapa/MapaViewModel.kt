package app.trombito.rallycopilot.ui.mapa

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.velocidad.EstimadorVelocidad
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.velocidad.kmh
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Presentación del mapa: combina el estado de los datos, los estilos, los ajustes de conducción (capa, relieve, icono,
 * orientación, zoom) y las fijaciones GPS (que solo se recogen mientras la pantalla está visible: la UI recoge
 * `uiState` con el ciclo de vida) con la velocidad estimada, la vía actual con su límite y el zoom automático.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MapaViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioDatosMapa,
        ubicacion: FuenteUbicacion,
        private val vias: ProveedorViaActual,
        private val ajustes: RepositorioAjustesConduccion,
        private val navegacion: ControlNavegacion,
        private val guardado: SavedStateHandle,
    ) : ViewModel() {
        /** Reloj monótono (ms), el mismo de `Fijacion.tiempoMs`; sustituible en los tests. */
        internal var reloj: () -> Long = { SystemClock.elapsedRealtime() }

        private val zoomAutomatico = ZoomAutomatico()
        private val preferencias = ajustes.ajustes
        private val siguiendo = guardado.getStateFlow(CLAVE_SIGUIENDO, true)
        private val permiso = MutableStateFlow(ubicacion.tienePermiso())

        private class Lectura(
            val posicion: Fijacion?,
            val velocidadKmh: Int?,
            val via: ViaActual?,
            /** [posicion] ajustada al camino; es la que pinta el mapa. */
            val ajustada: Fijacion? = posicion,
        )

        private val estilos =
            repositorio.estado
                .map { it.instalados }
                .distinctUntilChanged()
                .map { repositorio.estilos() }

        private val lecturas: Flow<Lectura> =
            permiso.flatMapLatest { concedido ->
                if (!concedido) {
                    flowOf(LECTURA_VACIA)
                } else {
                    val estimador = EstimadorVelocidad()
                    val sobreCamino = PosicionSobreCamino(navegacion)
                    var viaPrevia: ViaActual? = null
                    var ultima = LECTURA_VACIA
                    // Sin fijaciones (túnel, GPS perdido) la velocidad caduca por el reloj, no por una fijación nueva.
                    // Ambas corrutinas corren en el hilo del colector; solo se recoge con la pantalla visible.
                    channelFlow {
                        send(LECTURA_VACIA)
                        launch {
                            ubicacion.fijaciones().collect { fijacion ->
                                // La posición aproximada de interior (fused/red) salta decenas de metros: se pinta,
                                // pero no alimenta la velocidad derivada de la posición.
                                val precisa = (fijacion.precisionM ?: 0.0) <= PRECISION_VELOCIDAD_M
                                val ms =
                                    if (precisa) {
                                        estimador.actualizar(fijacion)
                                    } else {
                                        estimador.vigente(fijacion.tiempoMs)
                                    }
                                val velocidad = ms?.let(::kmh)
                                // Al ajuste va la velocidad estimada (la del receptor puede faltar). El rumbo mostrado
                                // sale del camino (ruta o vía), no del bruto; la cruda sigue en `posicion`.
                                // Sin rumbo del receptor (emulador, fused), el del movimiento: si no, en una vía de
                                // doble sentido el ajuste solo tendría su propio rumbo previo y se quedaba al revés.
                                val conVelocidad =
                                    fijacion.copy(
                                        velocidadMs = ms ?: fijacion.velocidadMs,
                                        rumboGrados = fijacion.rumboGrados ?: estimador.rumbo(),
                                    )

                                fun lectura(via: ViaActual?) =
                                    Lectura(fijacion, velocidad, via, sobreCamino.ajustar(conVelocidad, via))
                                // Primero la velocidad: la vía puede tardar (carga perezosa del archivo de vías).
                                ultima = lectura(viaPrevia)
                                send(ultima)
                                viaPrevia = vias.viaEn(fijacion)
                                ultima = lectura(viaPrevia)
                                send(ultima)
                            }
                        }
                        launch {
                            while (true) {
                                delay(TICK_CADUCIDAD_MS)
                                if (ultima.velocidadKmh != null && estimador.vigente(reloj()) == null) {
                                    ultima = Lectura(ultima.posicion, null, ultima.via, ultima.ajustada)
                                    send(ultima)
                                }
                            }
                        }
                    }
                }
            }

        val uiState: StateFlow<MapaUiState> =
            combine(repositorio.estado, estilos, preferencias, lecturas, siguiendo) { datos, mapas, pref, lect, sigue ->
                MapaUiState(
                    cargando = false,
                    datos = datos,
                    estilos = mapas,
                    capaElegida = pref.capa,
                    posicion = lect.posicion,
                    posicionAjustada = lect.ajustada,
                    velocidadKmh = lect.velocidadKmh,
                    via = lect.via,
                    siguiendo = sigue,
                    relieve = pref.relieve,
                    orientacion = pref.orientacion,
                    icono = pref.icono,
                    estiloManiobras = pref.estiloManiobras,
                    zoomObjetivo =
                        zoomAutomatico.actualizar(
                            lect.velocidadKmh,
                            lect.posicion?.tiempoMs,
                            pref.nivelZoom,
                        ),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), MapaUiState())

        /**
         * Última cámara del mapa (centro y zoom), para que sobreviva a recrear la actividad (giro de pantalla) o al
         * proceso. Null si el mapa nunca llegó a seguir ni a mover la cámara. No es parte de [uiState]: solo se lee al
         * crear la vista del mapa.
         */
        val camaraGuardada: CamaraMapa?
            get() =
                guardado.get<DoubleArray>(CLAVE_CAMARA)?.takeIf { it.size == TAMANO_CAMARA }?.let {
                    CamaraMapa(it[0], it[1], it[2])
                }

        fun onCamara(camara: CamaraMapa) {
            guardado[CLAVE_CAMARA] = doubleArrayOf(camara.latitud, camara.longitud, camara.zoom)
        }

        /** La cámara dejó de seguir al coche porque el usuario movió el mapa. */
        fun onSeguimientoPerdido() {
            guardado[CLAVE_SIGUIENDO] = false
        }

        fun onRecentrar() {
            guardado[CLAVE_SIGUIENDO] = true
        }

        fun onCapa(nueva: CapaMapa) = guardarAjustes { it.copy(capa = nueva) }

        fun onRelieve(activo: Boolean) = guardarAjustes { it.copy(relieve = activo) }

        fun onOrientacion(nueva: Orientacion) = guardarAjustes { it.copy(orientacion = nueva) }

        /**
         * El usuario ha pellizcado el mapa: se respeta su zoom [zoom] durante [PAUSA_ZOOM_MS] y luego el zoom
         * automático vuelve a ritmo limitado. [ahoraMs] es el reloj monótono (el mismo de las fijaciones).
         */
        fun onZoomManual(
            zoom: Double,
            ahoraMs: Long,
        ) {
            zoomAutomatico.pausar(zoom, ahoraMs)
        }

        private fun guardarAjustes(cambio: (AjustesConduccion) -> AjustesConduccion) {
            viewModelScope.launch { ajustes.guardar(cambio(ajustes.ajustes.first())) }
        }

        /** La UI avisa de si tiene la ubicación precisa (el GPS la exige); al concederse empieza a recoger el GPS. */
        fun onPermisoUbicacion(precisa: Boolean) {
            permiso.value = precisa
        }

        fun onDescartarAviso() {
            viewModelScope.launch { repositorio.descartarAviso() }
        }

        private companion object {
            const val PARADA_MS = 5_000L
            const val TICK_CADUCIDAD_MS = 1_000L

            /** Peor precisión (m) con la que una fijación cuenta para la velocidad; la misma que la sesión. */
            const val PRECISION_VELOCIDAD_M = 30.0
            const val CLAVE_SIGUIENDO = "mapa-siguiendo"
            const val CLAVE_CAMARA = "mapa-camara"
            const val TAMANO_CAMARA = 3
            val LECTURA_VACIA = Lectura(null, null, null)
        }
    }
