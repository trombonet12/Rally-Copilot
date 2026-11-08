package app.trombito.rallycopilot.ui.mapa

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.datos.PrecargaDetalle
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Presentación del mapa: combina el estado de los datos, los estilos, los ajustes de conducción (capa, relieve, icono,
 * orientación, zoom) y las fijaciones GPS (que solo se recogen mientras la pantalla está visible: la UI recoge
 * `uiState` con el ciclo de vida) con la velocidad estimada, la vía actual con su límite y el zoom automático.
 */
@HiltViewModel
class MapaViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioDatosMapa,
        private val canalizacion: CanalizacionGps,
        private val ajustes: RepositorioAjustesConduccion,
        private val navegacion: ControlNavegacion,
        private val guardado: SavedStateHandle,
        precarga: PrecargaDetalle = PrecargaDetalle.Ninguna,
    ) : ViewModel() {
        private val zoomAutomatico = ZoomAutomatico()

        /**
         * Estela de por dónde se ha pasado en el recorrido en curso (ver [CanalizacionGps.estela]); se borra al
         * terminar la conducción o al cambiar de modo (libre / guiado).
         */
        val estela: StateFlow<List<List<PuntoGeo>>> = canalizacion.estela

        init {
            viewModelScope.launch {
                navegacion.estado.map(::claveRecorrido).distinctUntilChanged().collect {
                    canalizacion.limpiarEstela()
                }
            }
        }

        private val preferencias = ajustes.ajustes
        private val siguiendo = guardado.getStateFlow(CLAVE_SIGUIENDO, true)
        private val gestosMutable = MutableStateFlow<Int?>(null)

        /**
         * Gestos del usuario sobre el mapa desde que dejó de seguir al coche (null = ninguno): cada gesto lo incrementa
         * y recentrar lo borra. La UI reinicia con él la cuenta atrás del recentrado automático en marcha.
         */
        val gestos: StateFlow<Int?> = gestosMutable

        private val estilos =
            // Se recarga el estilo al cambiar lo instalado o al aparecer/desaparecer el detalle (no con cada tesela).
            combine(
                repositorio.estado.map { it.instalados },
                precarga.estado.map { it.teselas > 0 },
            ) { datos, detalle ->
                datos to detalle
            }.distinctUntilChanged()
                .map { repositorio.estilos() }

        val uiState: StateFlow<MapaUiState> =
            combine(
                repositorio.estado,
                estilos,
                preferencias,
                canalizacion.lecturas,
                siguiendo,
            ) { datos, mapas, pref, lect, sigue ->
                MapaUiState(
                    cargando = false,
                    datos = datos,
                    estilos = mapas,
                    capaElegida = pref.capa,
                    posicion = lect.posicion,
                    posicionAjustada = lect.ajustada,
                    velocidadKmh = lect.velocidadKmh,
                    via = lect.via,
                    viaCoincide = lect.viaCoincide,
                    sinSenalGps = lect.sinSenal,
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
            gestosMutable.value = null
        }

        fun onCapa(nueva: CapaMapa) = guardarAjustes { it.copy(capa = nueva) }

        /**
         * Activar el relieve sin sus datos (ya no van en el APK) lanza su descarga: mientras baja, el mapa sigue sin
         * relieve y `uiState.datos.complemento(RELIEVE).descarga` dice cómo va (también la notificación de descargas).
         */
        fun onRelieve(activo: Boolean) {
            guardarAjustes { it.copy(relieve = activo) }
            if (!activo) return
            viewModelScope.launch {
                val datos = repositorio.estado.first()
                val relieve = datos.complemento(Complemento.RELIEVE)
                if (datos.instalados != null && !relieve.instalado && !relieve.descargando) {
                    repositorio.descargar(Complemento.RELIEVE)
                }
            }
        }

        fun onOrientacion(nueva: Orientacion) = guardarAjustes { it.copy(orientacion = nueva) }

        /**
         * El usuario ha hecho un gesto en el mapa (arrastre, pellizco o giro; se avisa al empezar y al terminar): se
         * respeta su zoom [zoom] durante [PAUSA_ZOOM_MS] y luego el zoom automático vuelve a ritmo limitado. [ahoraMs]
         * es el reloj monótono (el mismo de las fijaciones). Con la cámara ya suelta cuenta además como gesto para el
         * recentrado automático ([gestos]).
         */
        fun onZoomManual(
            zoom: Double,
            ahoraMs: Long,
        ) {
            zoomAutomatico.pausar(zoom, ahoraMs)
            if (!siguiendo.value) gestosMutable.update { (it ?: 0) + 1 }
        }

        private fun guardarAjustes(cambio: (AjustesConduccion) -> AjustesConduccion) {
            viewModelScope.launch { ajustes.guardar(cambio(ajustes.ajustes.first())) }
        }

        /** La UI avisa de si tiene la ubicación precisa (el GPS la exige); al concederse empieza a recoger el GPS. */
        fun onPermisoUbicacion(precisa: Boolean) {
            canalizacion.permisoConcedido(precisa)
        }

        fun onDescartarAviso() {
            viewModelScope.launch { repositorio.descartarAviso() }
        }

        private companion object {
            const val CLAVE_SIGUIENDO = "mapa-siguiendo"
            const val CLAVE_CAMARA = "mapa-camara"
            const val TAMANO_CAMARA = 3
        }
    }
