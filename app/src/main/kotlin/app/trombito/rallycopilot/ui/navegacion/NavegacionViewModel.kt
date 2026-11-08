package app.trombito.rallycopilot.ui.navegacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.enlaces.ResolvedorEnlaces
import app.trombito.rallycopilot.domain.enlaces.ResultadoResolucion
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EdicionParadas
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.RepositorioGuiadoPendiente
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.EnlaceUbicacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.ResultadoEnlace
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private const val ESPERA_BUSQUEDA_MS = 250L
private const val MS_SEGUNDO = 1_000.0
private const val LONGITUD_MINIMA_BUSQUEDA = 2

/**
 * Presentación de la navegación sobre el mapa: búsqueda de destino (con espera de ~250 ms al teclear), vista previa
 * con las tres rutas calculadas en paralelo desde la última fijación, y el estado de la conducción que publica
 * [ControlNavegacion], más «Seguir a <destino>» tras la muerte del proceso. La posición se la da la pantalla del mapa
 * con [onPosicion] (ya recoge el GPS con el ciclo de vida y el permiso).
 */
@HiltViewModel
@OptIn(FlowPreview::class)
@Suppress("TooManyFunctions") // un ViewModel para búsqueda, vista previa, conducción y «Seguir a»
class NavegacionViewModel
    @Suppress("LongParameterList") // el reloj solo lo cambian las pruebas; el constructor de Hilt no lo lleva
    internal constructor(
        private val buscador: Buscador,
        private val enrutador: Enrutador,
        private val control: ControlNavegacion,
        private val resolvedor: ResolvedorEnlaces,
        private val guiadoPendiente: RepositorioGuiadoPendiente,
        private val ajustes: RepositorioAjustesConduccion,
        private val reloj: () -> Long,
    ) : ViewModel() {
        @Inject
        constructor(
            buscador: Buscador,
            enrutador: Enrutador,
            control: ControlNavegacion,
            resolvedor: ResolvedorEnlaces,
            guiadoPendiente: RepositorioGuiadoPendiente,
            ajustes: RepositorioAjustesConduccion,
        ) : this(buscador, enrutador, control, resolvedor, guiadoPendiente, ajustes, System::currentTimeMillis)

        private val texto = MutableStateFlow("")
        private val consulta = MutableStateFlow("")
        private val busqueda = MutableStateFlow<BusquedaDestino>(BusquedaDestino.Inactiva)
        private val vistaPrevia = MutableStateFlow<VistaPrevia?>(null)
        private val puntoElegido = MutableStateFlow<PuntoElegido?>(null)
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

        private val resumenLlegada = MutableStateFlow<ResumenTrayecto?>(null)

        /** La casa de los ajustes (null sin fijar), para «Ir a casa» en la barra y «Casa» en la tarjeta del punto. */
        private val casa: Flow<PuntoGeo?> = ajustes.ajustes.map { it.casa }.distinctUntilChanged()

        private val _casaGuardada = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Un aviso cada vez que la tarjeta del punto fija la casa («Casa guardada»). */
        val casaGuardada: SharedFlow<Unit> = _casaGuardada.asSharedFlow()

        private data class Extras(
            val oferta: OfertaSeguir?,
            val punto: PuntoElegido?,
            val resumen: ResumenTrayecto?,
            val casa: PuntoGeo?,
        )

        private val seguirYPunto: Flow<Extras> =
            combine(ofertaSeguir, puntoElegido, resumenLlegada, casa) { oferta, punto, resumen, casa ->
                Extras(oferta, punto, resumen, casa)
            }

        /** La búsqueda abierta con la lupa del panel mientras se guía. */
        private val buscandoGuiando = MutableStateFlow(false)

        private val busquedaYLupa: Flow<Pair<BusquedaDestino, Boolean>> =
            combine(busqueda, buscandoGuiando) { b, lupa -> b to lupa }

        val uiState: StateFlow<NavegacionUiState> =
            combine(texto, busquedaYLupa, vistaPrevia, control.estado, seguirYPunto) { t, busc, v, c, extra ->
                val (b, lupa) = busc
                // Guiando (o con su aviso final) no hay vista previa; la búsqueda solo si se abrió con la lupa.
                val reposo = c is EstadoNavegacion.Inactiva || c is EstadoNavegacion.Libre
                val buscando = lupa && c is EstadoNavegacion.Guiando
                val busquedaVisible = if (reposo || buscando) b else BusquedaDestino.Inactiva
                val previaVisible = if (reposo) v else null
                val hayBusqueda = busquedaVisible != BusquedaDestino.Inactiva
                NavegacionUiState(
                    texto = t,
                    busqueda = busquedaVisible,
                    vistaPrevia = previaVisible,
                    control = c,
                    buscando = buscando,
                    seguir = extra.oferta.takeIf { reposo && previaVisible == null && !hayBusqueda },
                    // La tarjeta del punto se ve en reposo y guiando; el aviso de llegada o de error la cierra.
                    puntoElegido = extra.punto.takeIf { reposo || c is EstadoNavegacion.Guiando },
                    resumenLlegada = extra.resumen.takeIf { c is EstadoNavegacion.Llegada },
                    casa = extra.casa,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), NavegacionUiState())

        init {
            viewModelScope.launch { seguirTrayecto() }
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

        /**
         * Anota cuándo empieza un guiado y qué longitud tenía la ruta inicial, para dar el tiempo y los kilómetros
         * en la tarjeta de llegada (el estado de llegada solo trae el destino).
         */
        private suspend fun seguirTrayecto() {
            var inicioMs: Long? = null
            var distanciaM = 0.0
            control.estado.collect { estado ->
                when (estado) {
                    is EstadoNavegacion.Guiando -> {
                        if (inicioMs == null) {
                            inicioMs = reloj()
                            distanciaM = estado.ruta.longitudM
                        }
                    }

                    is EstadoNavegacion.Llegada -> {
                        inicioMs?.let {
                            val tiempoS = (reloj() - it) / MS_SEGUNDO
                            resumenLlegada.value = ResumenTrayecto(tiempoS, distanciaM)
                        }
                        inicioMs = null
                    }

                    else -> {
                        inicioMs = null
                        resumenLlegada.value = null
                    }
                }
            }
        }

        /** La pantalla da la última fijación conocida (origen de las rutas y referencia de cercanía al buscar). */
        fun onPosicion(punto: PuntoGeo?) {
            if (punto == null) return
            posicion = punto
            val previa = vistaPrevia.value
            if (previa != null && previa.sinPosicion) calcular(previa.destino, previa.paradas, previa.perfil)
            if (faseSeguir.value == FaseSeguir.SIN_POSICION) seguirPendiente()
        }

        fun onTexto(nuevo: String) {
            resolucion?.cancel()
            texto.value = nuevo
            consulta.value = nuevo
            if (nuevo.trim().length < LONGITUD_MINIMA_BUSQUEDA) busqueda.value = BusquedaDestino.Inactiva
            // La vista previa se conserva al buscar: así un resultado puede añadirse como parada de esa ruta.
        }

        /** La lupa del panel (solo guiando): abre la búsqueda encima del mapa. */
        fun abrirBusquedaGuiando() {
            if (guiando()) buscandoGuiando.value = true
        }

        /** Cierra la búsqueda abierta con la lupa y borra su texto. */
        fun cerrarBusquedaGuiando() {
            buscandoGuiando.value = false
            resolucion?.cancel()
            texto.value = ""
            consulta.value = ""
            busqueda.value = BusquedaDestino.Inactiva
        }

        /**
         * Elige [destino]: en reposo abre la vista previa (conservando sus paradas); guiando cambia el destino final,
         * conserva las paradas pendientes y el control recalcula desde la posición actual sin dejar de guiar.
         */
        fun elegirDestino(destino: Destino) {
            if (guiando()) {
                puntoElegido.value = null
                cerrarBusquedaGuiando()
                control.cambiarDestino(destino)
                return
            }
            resolucion?.cancel()
            puntoElegido.value = null
            texto.value = destino.nombre
            consulta.value = ""
            busqueda.value = BusquedaDestino.Inactiva
            // Cambiar de destino mantiene las paradas ya puestas; para empezar de cero se cancela la ruta.
            calcular(destino, vistaPrevia.value?.paradas.orEmpty())
        }

        /** Mantener pulsado el mapa en [punto]: abre la tarjeta con [nombre] (el del lugar o la calle, si lo hay). */
        fun onMantenerPulsado(
            punto: PuntoGeo,
            nombre: String?,
        ) {
            puntoElegido.value = PuntoElegido(punto, nombre?.trim()?.takeIf { it.isNotEmpty() })
        }

        /** Cierra la tarjeta del punto (X o toque fuera). */
        fun cerrarPunto() {
            puntoElegido.value = null
        }

        /** «Ir aquí»: el punto de la tarjeta es el destino y se abre la vista previa de rutas. */
        fun irAqui() {
            puntoElegido.value?.let { elegirDestino(it.destino) }
        }

        /** «Casa» de la tarjeta del punto: lo fija como casa de los ajustes, cierra la tarjeta y avisa. */
        fun fijarCasaDelPunto() {
            val punto = puntoElegido.value?.punto ?: return
            puntoElegido.value = null
            viewModelScope.launch {
                ajustes.fijarCasa(punto)
                _casaGuardada.emit(Unit)
            }
        }

        /** «Añadir parada» de la tarjeta del punto. */
        fun anadirParadaDelPunto() {
            puntoElegido.value?.let { anadirParada(it.destino) }
        }

        /**
         * Añade [parada] al final de las intermedias: en un guiado, con recálculo desde la posición actual; en la
         * vista previa, recalculando sus rutas. Sin ruta que editar, se comporta como [elegirDestino].
         */
        fun anadirParada(parada: Destino) {
            puntoElegido.value = null
            val previa = vistaPrevia.value
            when {
                guiando() -> {
                    cerrarBusquedaGuiando()
                    control.anadirParada(parada)
                }

                previa != null -> {
                    resolucion?.cancel()
                    texto.value = previa.destino.nombre
                    consulta.value = ""
                    busqueda.value = BusquedaDestino.Inactiva
                    editarPrevia { EdicionParadas.anadir(it, parada) }
                }

                else -> {
                    elegirDestino(parada)
                }
            }
        }

        fun quitarParada(indice: Int) {
            if (guiando()) control.quitarParada(indice) else editarPrevia { EdicionParadas.quitar(it, indice) }
        }

        fun moverParada(
            desde: Int,
            hasta: Int,
        ) {
            if (guiando()) {
                control.moverParada(
                    desde,
                    hasta,
                )
            } else {
                editarPrevia { EdicionParadas.mover(it, desde, hasta) }
            }
        }

        private fun guiando(): Boolean = control.estado.value is EstadoNavegacion.Guiando

        /** Cambia las paradas de la vista previa y recalcula las rutas; sin cambios no hace nada. */
        private fun editarPrevia(cambio: (List<Destino>) -> List<Destino>) {
            val previa = vistaPrevia.value ?: return
            val nuevas = cambio(previa.paradas)
            if (nuevas != previa.paradas) calcular(previa.destino, nuevas, previa.perfil)
        }

        fun elegirPerfil(perfil: PerfilRuta) {
            vistaPrevia.update { it?.copy(perfil = perfil) }
        }

        fun empezar() {
            val previa = vistaPrevia.value ?: return
            val ruta = previa.rutaElegida ?: return
            cancelar()
            control.empezar(previa.destino, previa.perfil, ruta, previa.paradas)
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
                    val paradas = pendiente.paradas.map { it.punto }
                    val ruta =
                        enrutador.rutaConParadas(origen, paradas, pendiente.destino.punto, pendiente.perfil) as?
                            ResultadoRuta.Ok
                    if (ruta == null) {
                        faseSeguir.value = FaseSeguir.ERROR
                    } else {
                        faseSeguir.value = FaseSeguir.LISTA
                        cancelar()
                        control.empezar(pendiente.destino, pendiente.perfil, ruta.ruta, pendiente.paradas)
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
            puntoElegido.value = null
            buscandoGuiando.value = false
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
            puntoElegido.value = null
            buscandoGuiando.value = false
            texto.value = ""
            consulta.value = ""
            busqueda.value = BusquedaDestino.Inactiva
        }

        /** «Probar otra vez»: repite el cálculo de las rutas de la vista previa abierta (misma parada y perfil). */
        fun reintentarRutas() {
            vistaPrevia.value?.let { calcular(it.destino, it.paradas, it.perfil) }
        }

        /**
         * Calcula las tres rutas (rápida, equilibrada y con curvas) hacia [destino] pasando por [paradas] (en ese
         * orden) desde la última fijación.
         */
        private fun calcular(
            destino: Destino,
            paradas: List<Destino> = emptyList(),
            perfil: PerfilRuta = PerfilRuta.MAS_RAPIDA,
        ) {
            calculo?.cancel()
            val origen = posicion
            vistaPrevia.value = VistaPrevia(destino, perfil, sinPosicion = origen == null, paradas = paradas)
            if (origen == null) return
            val puntos = paradas.map { it.punto }
            calculo =
                viewModelScope.launch {
                    PerfilRuta.entries.forEach { p ->
                        launch {
                            val opcion = enrutador.rutaMedida(origen, puntos, destino.punto, p).aOpcion()
                            vistaPrevia.update {
                                if (it?.destino == destino && it.paradas == paradas) it.conOpcion(p, opcion) else it
                            }
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
            val items = buscador.buscar(limpio, cerca)
            BusquedaDestino.Resultados(items, cerca?.let { yo -> items.map { yo.distanciaA(it.punto) } }.orEmpty())
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
        is ResultadoRuta.Ok -> OpcionRuta.Lista(ruta, curvasM)
        ResultadoRuta.SinDatos -> OpcionRuta.SinDatos
        is ResultadoRuta.Error -> OpcionRuta.Error(motivo)
    }
