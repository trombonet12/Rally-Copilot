package app.trombito.rallycopilot.ui.navegacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.enlaces.ResolvedorEnlaces
import app.trombito.rallycopilot.domain.enlaces.ResultadoResolucion
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.RepositorioGuiadoPendiente
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.EnlaceUbicacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.ResultadoEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
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
 * [ControlNavegacion], más «Seguir a <destino>» tras la muerte del proceso. La posición se la da la pantalla del mapa
 * con [onPosicion] (ya recoge el GPS con el ciclo de vida y el permiso).
 */
@HiltViewModel
@OptIn(FlowPreview::class)
@Suppress("TooManyFunctions") // un ViewModel para búsqueda, vista previa, conducción y «Seguir a»
class NavegacionViewModel
    @Inject
    constructor(
        private val buscador: Buscador,
        private val enrutador: Enrutador,
        private val control: ControlNavegacion,
        private val resolvedor: ResolvedorEnlaces,
        private val guiadoPendiente: RepositorioGuiadoPendiente,
    ) : ViewModel() {
        private val texto = MutableStateFlow("")
        private val consulta = MutableStateFlow("")
        private val busqueda = MutableStateFlow<BusquedaDestino>(BusquedaDestino.Inactiva)
        private val vistaPrevia = MutableStateFlow<VistaPrevia?>(null)
        private var posicion: PuntoGeo? = null
        private var permiso: Boolean? = null
        private var calculo: Job? = null
        private var resolucion: Job? = null
        private var seguimiento: Job? = null
        private val faseSeguir = MutableStateFlow(FaseSeguir.LISTA)

        private val ofertaSeguir: Flow<OfertaSeguir?> =
            combine(guiadoPendiente.pendiente, faseSeguir) { pendiente, fase ->
                pendiente?.let { OfertaSeguir(it.destino, fase) }
            }

        val uiState: StateFlow<NavegacionUiState> =
            combine(texto, busqueda, vistaPrevia, control.estado, ofertaSeguir) { t, b, v, c, oferta ->
                // Guiando (o con su aviso final) no hay búsqueda ni vista previa; en modo libre sí.
                val reposo = c is EstadoNavegacion.Inactiva || c is EstadoNavegacion.Libre
                val busquedaVisible = if (reposo) b else BusquedaDestino.Inactiva
                val previaVisible = if (reposo) v else null
                val hayBusqueda = busquedaVisible != BusquedaDestino.Inactiva
                NavegacionUiState(
                    texto = t,
                    busqueda = busquedaVisible,
                    vistaPrevia = previaVisible,
                    control = c,
                    seguir = oferta.takeIf { reposo && previaVisible == null && !hayBusqueda },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), NavegacionUiState())

        init {
            viewModelScope.launch {
                consulta.debounce(ESPERA_BUSQUEDA_MS).collectLatest { escrito ->
                    val enlace = EnlaceUbicacion.leer(escrito.trim())
                    resolucion?.cancel()
                    if (enlace is ResultadoEnlace.NecesitaRed) {
                        // Enlace corto: se sigue por red aparte para que un texto nuevo la cancele al instante.
                        busqueda.value = BusquedaDestino.Resolviendo
                        resolucion = viewModelScope.launch { busqueda.value = resolverEnlace(resolvedor, enlace.url) }
                    } else {
                        busqueda.value = interpretar(buscador, escrito, posicion)
                    }
                }
            }
        }

        /** La pantalla da la última fijación conocida (origen de las rutas y referencia de cercanía al buscar). */
        fun onPosicion(punto: PuntoGeo?) {
            if (punto == null) return
            posicion = punto
            val previa = vistaPrevia.value
            if (previa != null && previa.sinPosicion) calcular(previa.destino)
            if (faseSeguir.value == FaseSeguir.SIN_POSICION) seguirPendiente()
        }

        fun onTexto(nuevo: String) {
            resolucion?.cancel()
            texto.value = nuevo
            consulta.value = nuevo
            if (nuevo.trim().length < LONGITUD_MINIMA_BUSQUEDA) busqueda.value = BusquedaDestino.Inactiva
            // Editar el texto abandona la vista previa y vuelve a la búsqueda.
            if (vistaPrevia.value != null) {
                calculo?.cancel()
                vistaPrevia.value = null
            }
        }

        fun elegirDestino(destino: Destino) {
            resolucion?.cancel()
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

        /**
         * «Seguir a <destino>»: recalcula desde la posición actual hasta el destino del guiado pendiente, con el mismo
         * perfil, y empieza el guiado. Sin fijación GPS espera a la primera; si la ruta falla, se puede reintentar.
         */
        fun seguirPendiente() {
            if (faseSeguir.value == FaseSeguir.CALCULANDO) return
            seguimiento?.cancel()
            seguimiento =
                viewModelScope.launch {
                    val pendiente = guiadoPendiente.pendiente.first() ?: return@launch
                    val origen = posicion
                    if (origen == null) {
                        faseSeguir.value = FaseSeguir.SIN_POSICION
                        return@launch
                    }
                    faseSeguir.value = FaseSeguir.CALCULANDO
                    val ruta = (enrutador.ruta(origen, pendiente.destino.punto, pendiente.perfil) as? ResultadoRuta.Ok)
                    if (ruta == null) {
                        faseSeguir.value = FaseSeguir.ERROR
                    } else {
                        faseSeguir.value = FaseSeguir.LISTA
                        cancelar()
                        control.empezar(pendiente.destino, pendiente.perfil, ruta.ruta)
                    }
                }
        }

        /** Descarta «Seguir a <destino>»: borra el guiado pendiente. */
        fun descartarSeguir() {
            seguimiento?.cancel()
            faseSeguir.value = FaseSeguir.LISTA
            viewModelScope.launch { guiadoPendiente.borrar() }
        }

        /**
         * La pantalla informa del permiso de ubicación (preciso). Con el mapa abierto la sesión de reposo es el modo
         * libre: arranca al abrir con permiso o en cuanto se concede, y no se repite al girar la pantalla (la
         * pantalla se recompone, este ViewModel no).
         */
        fun onPermisoUbicacion(concedido: Boolean) {
            if (permiso == concedido) return
            permiso = concedido
            if (concedido) {
                val actual = control.estado.value
                if (actual is EstadoNavegacion.Inactiva || actual is EstadoNavegacion.Error) control.empezarLibre()
            }
        }

        /**
         * Termina el guiado (o cierra el aviso de llegada o de error) y vuelve al modo libre si hay permiso; sin él,
         * para todo. El servicio no se para entre medias: el modo libre sustituye a la sesión.
         */
        fun terminar() {
            if (permiso == true) control.empezarLibre() else control.terminar()
        }

        /** Al salir del mapa (o cerrar la app) se para todo, también el guiado. */
        override fun onCleared() {
            if (control.estado.value !is EstadoNavegacion.Inactiva) control.terminar()
        }

        /** Borra el campo y cancela la vista previa si la hay. */
        fun cancelar() {
            calculo?.cancel()
            resolucion?.cancel()
            vistaPrevia.value = null
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
            ubicacion(enlace)
        }

        is ResultadoEnlace.NecesitaRed -> {
            BusquedaDestino.Resolviendo
        }

        ResultadoEnlace.NoReconocido -> {
            BusquedaDestino.Resultados(buscador.buscar(limpio, cerca))
        }
    }
}

private suspend fun resolverEnlace(
    resolvedor: ResolvedorEnlaces,
    url: String,
): BusquedaDestino =
    when (val resultado = resolvedor.resolver(url)) {
        is ResultadoResolucion.Url -> {
            (EnlaceUbicacion.leer(resultado.final) as? ResultadoEnlace.Punto)?.let(::ubicacion)
                ?: BusquedaDestino.EnlaceNoAbierto
        }

        ResultadoResolucion.SinRed -> {
            BusquedaDestino.SinRed
        }

        ResultadoResolucion.NoPermitido, ResultadoResolucion.Error -> {
            BusquedaDestino.EnlaceNoAbierto
        }
    }

private fun ubicacion(enlace: ResultadoEnlace.Punto): BusquedaDestino.Ubicacion =
    BusquedaDestino.Ubicacion(
        Destino(enlace.nombre ?: coordenadas(enlace.punto), enlace.punto),
        sinNombre = enlace.nombre == null,
    )

private fun coordenadas(punto: PuntoGeo): String = String.format(Locale.ROOT, "%.5f, %.5f", punto.lat, punto.lon)

private fun ResultadoRuta.aOpcion(): OpcionRuta =
    when (this) {
        is ResultadoRuta.Ok -> OpcionRuta.Lista(ruta)
        ResultadoRuta.SinDatos -> OpcionRuta.SinDatos
        is ResultadoRuta.Error -> OpcionRuta.Error(motivo)
    }
