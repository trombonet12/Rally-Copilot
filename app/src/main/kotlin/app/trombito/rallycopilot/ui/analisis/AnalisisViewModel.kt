package app.trombito.rallycopilot.ui.analisis

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.PasadaDetallada
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.motor.analisis.AnalisisPasada
import app.trombito.rallycopilot.motor.analisis.PuntoSerie
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.ui.RutaAnalisis
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * La pasada contra la que se compara por defecto: la mejor del tramo; si la analizada es la mejor, la segunda mejor (la
 * más rápida de las demás); null si no hay otra pasada.
 */
fun comparadaPorDefecto(
    analizadaId: Long,
    pasadas: List<PasadaDetallada>,
): Long? {
    val otras = pasadas.filter { it.id != analizadaId }
    return (otras.firstOrNull { it.esMejor } ?: otras.minByOrNull { it.totalMs })?.id
}

private fun analizar(
    tramo: Tramo,
    pasada: PasadaDetallada,
    comparada: Pasada?,
): AnalisisPasada {
    val puntos = pasada.puntos.map { Fijacion(PuntoGeo(it.lat, it.lon), it.tiempoMs, it.velocidadMs, null, null) }
    return AnalisisPasada.de(tramo, puntos, pasada.pasada, comparada)
}

// La mejor primero y después las demás, la más reciente antes.
private fun opcionesDe(
    pasadas: List<PasadaDetallada>,
    analizadaId: Long,
): List<OpcionComparada> =
    pasadas
        .filter { it.id != analizadaId }
        .sortedWith(compareByDescending<PasadaDetallada> { it.esMejor }.thenByDescending { it.inicioMs })
        .map { OpcionComparada(it.id, it.inicioMs, it.totalMs, it.esMejor) }

/**
 * Presentación del análisis de una pasada: carga el tramo, sus pasadas y el estilo del mapa, calcula el análisis con
 * el motor (por defecto contra la mejor pasada; se puede elegir otra o ninguna) y lleva la repetición del recorrido
 * (play/pausa, velocidad y posición) que comparten el marcador del mapa y el cursor de los gráficos.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AnalisisViewModel
    @Inject
    constructor(
        guardado: SavedStateHandle,
        private val repositorio: RepositorioTramos,
        private val datosMapa: RepositorioDatosMapa,
    ) : ViewModel() {
        /**
         * Dónde se calcula el análisis (cálculo numérico, fuera del hilo principal); sustituible en los tests antes de
         * recoger [uiState], que es cuando empieza a calcular.
         */
        internal var calculos: CoroutineDispatcher = Dispatchers.Default

        private val ruta = guardado.toRoute<RutaAnalisis>()
        private val eleccion = MutableStateFlow<Eleccion>(Eleccion.Automatica)
        private val repeticion = MutableStateFlow(EstadoRepeticion())

        private sealed interface Eleccion {
            data object Automatica : Eleccion

            data object Ninguna : Eleccion

            data class Pasada(
                val id: Long,
            ) : Eleccion
        }

        private class Carga(
            val tramo: Tramo,
            val nombre: String?,
            val pasadas: List<PasadaDetallada>,
            val actual: PasadaDetallada,
            val estiloJson: String?,
        )

        private sealed interface Datos {
            data object Cargando : Datos

            data class Problema(
                val motivo: ProblemaAnalisis,
            ) : Datos

            /** Lo leído de los repositorios, a falta de analizar. */
            class Cargado(
                val carga: Carga,
            ) : Datos

            class Listo(
                val carga: Carga,
                val comparadaId: Long?,
                val analisis: AnalisisPasada,
                val velocidadComparada: List<PuntoSerie>,
            ) : Datos
        }

        private val origen = MutableStateFlow<Datos>(Datos.Cargando)

        private val calculado: StateFlow<Datos> =
            combine(origen, eleccion) { leido, elegida -> leido to elegida }
                .mapLatest { (leido, elegida) ->
                    if (leido is Datos.Cargado) withContext(calculos) { calcular(leido.carga, elegida) } else leido
                }.stateIn(viewModelScope, SharingStarted.Lazily, Datos.Cargando)

        val uiState: StateFlow<AnalisisUiState> =
            combine(calculado, repeticion) { datos, rep -> construir(datos, rep) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), AnalisisUiState())

        init {
            viewModelScope.launch { cargar() }
            viewModelScope.launch {
                repeticion.map { it.reproduciendo }.distinctUntilChanged().collectLatest { activa ->
                    if (activa) reproducir()
                }
            }
        }

        fun onComparar(pasadaId: Long?) {
            eleccion.value = if (pasadaId == null) Eleccion.Ninguna else Eleccion.Pasada(pasadaId)
        }

        fun onAlternarRepeticion() {
            val duracion = duracionMs() ?: return
            repeticion.update { alternarRepeticion(it, duracion) }
        }

        fun onVelocidad(velocidad: VelocidadRepeticion) {
            repeticion.update { it.copy(velocidad = velocidad) }
        }

        /** Mueve la repetición a [tMs] (acotado a la duración) sin cambiar si reproduce o no. */
        fun onPosicion(tMs: Long) {
            val duracion = duracionMs() ?: return
            repeticion.update { it.copy(tMs = tMs.coerceIn(0L, duracion)) }
        }

        /** Toque en un gráfico: pausa la repetición y la lleva al instante en que se pasó por [distanciaM]. */
        fun onCursor(distanciaM: Double) {
            val datos = calculado.value as? Datos.Listo ?: return
            val pasada = datos.carga.actual.pasada
            val d = distanciaM.coerceIn(pasada.tiempos.first().distanciaM, pasada.tiempos.last().distanciaM)
            val t = Math.round(pasada.tiempoEn(d) ?: 0.0)
            repeticion.update { it.copy(tMs = t, reproduciendo = false) }
        }

        private fun duracionMs(): Long? = (calculado.value as? Datos.Listo)?.analisis?.totalMs

        private suspend fun reproducir() {
            while (true) {
                delay(PASO_MS)
                val duracion = duracionMs() ?: return
                repeticion.update { avanzarRepeticion(it, PASO_MS, duracion) }
            }
        }

        private suspend fun cargar() {
            val tramo = repositorio.tramo(ruta.tramoId)
            val pasadas = repositorio.pasadasDetalladas(ruta.tramoId)
            val actual = pasadas.firstOrNull { it.id == ruta.pasadaId }
            if (tramo == null || actual == null) {
                origen.value = Datos.Problema(ProblemaAnalisis.NO_ENCONTRADA)
                return
            }
            val nombre =
                repositorio
                    .observarResumenes(FiltroTramos.TODOS)
                    .first()
                    .firstOrNull { it.id == ruta.tramoId }
                    ?.nombre
            val estilo = datosMapa.estilos()?.vectorial
            origen.value = Datos.Cargado(Carga(tramo, nombre, pasadas, actual, estilo))
        }

        private fun calcular(
            base: Carga,
            elegida: Eleccion,
        ): Datos {
            val comparadaId =
                when (elegida) {
                    Eleccion.Automatica -> {
                        comparadaPorDefecto(base.actual.id, base.pasadas)
                    }

                    Eleccion.Ninguna -> {
                        null
                    }

                    is Eleccion.Pasada -> {
                        elegida.id.takeIf { id ->
                            id != base.actual.id &&
                                base.pasadas.any { it.id == id }
                        }
                    }
                }
            val comparada = base.pasadas.firstOrNull { it.id == comparadaId }
            return try {
                val analisis = analizar(base.tramo, base.actual, comparada?.pasada)
                // La velocidad de la comparada sale de su propio análisis (sin compararla con nada).
                val velocidadComparada = comparada?.let { analizar(base.tramo, it, null).velocidad }.orEmpty()
                Datos.Listo(base, comparada?.id, analisis, velocidadComparada)
            } catch (e: IllegalArgumentException) {
                Datos.Problema(ProblemaAnalisis.ILEGIBLE).also {
                    // Solo el tipo de la excepción: sin coordenadas ni identificadores.
                    Log.w(ETIQUETA_LOG, "No se pudo analizar la pasada: ${e.javaClass.simpleName}")
                }
            }
        }

        private fun construir(
            datos: Datos,
            rep: EstadoRepeticion,
        ): AnalisisUiState =
            when (datos) {
                Datos.Cargando, is Datos.Cargado -> {
                    AnalisisUiState()
                }

                is Datos.Problema -> {
                    AnalisisUiState(cargando = false, problema = datos.motivo)
                }

                is Datos.Listo -> {
                    val carga = datos.carga
                    val t = rep.tMs
                    AnalisisUiState(
                        cargando = false,
                        tramoNombre = carga.nombre,
                        inicioMs = carga.actual.inicioMs,
                        analisis = datos.analisis,
                        opciones = opcionesDe(carga.pasadas, carga.actual.id),
                        comparadaId = datos.comparadaId,
                        velocidadComparada = datos.velocidadComparada,
                        estiloMapaJson = carga.estiloJson,
                        repeticion = rep,
                        posicion = t?.let { datos.analisis.repeticion.en(it) },
                        cursorM = t?.let { distanciaEnTiempo(carga.actual.pasada.tiempos, it) },
                    )
                }
            }

        private companion object {
            /** Paso del reloj de la repetición: unos 20 fotogramas por segundo bastan para mover el marcador. */
            const val PASO_MS = 50L
            const val ETIQUETA_LOG = "Analisis"
        }
    }
