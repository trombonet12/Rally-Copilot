package app.trombito.rallycopilot.ui.navegacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.EnlaceUbicacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.ResultadoEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private const val ESPERA_BUSQUEDA_MS = 250L
private const val PARADA_MS = 5_000L
private const val LONGITUD_MINIMA_BUSQUEDA = 2

/**
 * Presentación de la navegación sobre el mapa: búsqueda de destino (con espera de ~250 ms al teclear), vista previa
 * con las dos rutas calculadas en paralelo desde la última fijación, y el estado de la conducción que publica
 * [ControlNavegacion]. La posición se la da la pantalla del mapa con [onPosicion] (ya recoge el GPS con el ciclo de
 * vida y el permiso).
 */
@HiltViewModel
@OptIn(FlowPreview::class)
class NavegacionViewModel
    @Inject
    constructor(
        private val buscador: Buscador,
        private val enrutador: Enrutador,
        private val control: ControlNavegacion,
    ) : ViewModel() {
        private val texto = MutableStateFlow("")
        private val consulta = MutableStateFlow("")
        private val busqueda = MutableStateFlow<BusquedaDestino>(BusquedaDestino.Inactiva)
        private val vistaPrevia = MutableStateFlow<VistaPrevia?>(null)
        private var posicion: PuntoGeo? = null
        private var calculo: Job? = null

        val uiState: StateFlow<NavegacionUiState> =
            combine(texto, busqueda, vistaPrevia, control.estado) { t, b, v, c ->
                // Con una conducción en marcha (o su aviso final) no hay búsqueda ni vista previa.
                val reposo = c is EstadoNavegacion.Inactiva
                NavegacionUiState(
                    texto = t,
                    busqueda = if (reposo) b else BusquedaDestino.Inactiva,
                    vistaPrevia = if (reposo) v else null,
                    control = c,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), NavegacionUiState())

        init {
            viewModelScope.launch {
                consulta.debounce(ESPERA_BUSQUEDA_MS).collectLatest { escrito ->
                    busqueda.value = interpretar(buscador, escrito, posicion)
                }
            }
        }

        /** La pantalla da la última fijación conocida (origen de las rutas y referencia de cercanía al buscar). */
        fun onPosicion(punto: PuntoGeo?) {
            if (punto == null) return
            posicion = punto
            val previa = vistaPrevia.value
            if (previa != null && previa.sinPosicion) calcular(previa.destino)
        }

        fun onTexto(nuevo: String) {
            texto.value = nuevo
            consulta.value = nuevo
            if (nuevo.trim().length < LONGITUD_MINIMA_BUSQUEDA) busqueda.value = BusquedaDestino.Inactiva
            // Editar el texto abandona la vista previa y vuelve a la búsqueda.
            if (vistaPrevia.value != null) cerrarVistaPrevia()
        }

        fun elegirDestino(destino: Destino) {
            texto.value = destino.nombre
            consulta.value = ""
            busqueda.value = BusquedaDestino.Inactiva
            calcular(destino)
        }

        fun elegirPerfil(perfil: PerfilRuta) {
            vistaPrevia.update { it?.copy(perfil = perfil) }
        }

        fun empezar() {
            val previa = vistaPrevia.value ?: return
            val ruta = previa.rutaElegida ?: return
            cancelar()
            control.empezar(previa.destino, previa.perfil, ruta)
        }

        fun conducirLibre() {
            cancelar()
            control.empezarLibre()
        }

        /** Termina la conducción (también cierra el aviso de llegada o de error). */
        fun terminar() = control.terminar()

        /** Borra el campo y cancela la vista previa si la hay. */
        fun cancelar() {
            cerrarVistaPrevia()
            texto.value = ""
            consulta.value = ""
            busqueda.value = BusquedaDestino.Inactiva
        }

        private fun calcular(destino: Destino) {
            calculo?.cancel()
            val origen = posicion
            vistaPrevia.value = VistaPrevia(destino, sinPosicion = origen == null)
            if (origen == null) return
            calculo =
                viewModelScope.launch {
                    PerfilRuta.entries.forEach { perfil ->
                        launch {
                            val opcion = enrutador.ruta(origen, destino.punto, perfil).aOpcion()
                            vistaPrevia.update { if (it?.destino == destino) it.conOpcion(perfil, opcion) else it }
                        }
                    }
                }
        }

        private fun cerrarVistaPrevia() {
            calculo?.cancel()
            vistaPrevia.value = null
        }
    }

private suspend fun interpretar(
    buscador: Buscador,
    escrito: String,
    cerca: PuntoGeo?,
): BusquedaDestino {
    val limpio = escrito.trim()
    if (limpio.length < LONGITUD_MINIMA_BUSQUEDA) return BusquedaDestino.Inactiva
    return when (val enlace = EnlaceUbicacion.leer(limpio)) {
        is ResultadoEnlace.Punto -> {
            BusquedaDestino.Ubicacion(
                Destino(enlace.nombre ?: coordenadas(enlace.punto), enlace.punto),
                sinNombre = enlace.nombre == null,
            )
        }

        is ResultadoEnlace.NecesitaRed -> {
            BusquedaDestino.NecesitaRed
        }

        ResultadoEnlace.NoReconocido -> {
            BusquedaDestino.Resultados(buscador.buscar(limpio, cerca))
        }
    }
}

private fun coordenadas(punto: PuntoGeo): String = String.format(Locale.ROOT, "%.5f, %.5f", punto.lat, punto.lon)

private fun ResultadoRuta.aOpcion(): OpcionRuta =
    when (this) {
        is ResultadoRuta.Ok -> OpcionRuta.Lista(ruta)
        ResultadoRuta.SinDatos -> OpcionRuta.SinDatos
        is ResultadoRuta.Error -> OpcionRuta.Error(motivo)
    }
