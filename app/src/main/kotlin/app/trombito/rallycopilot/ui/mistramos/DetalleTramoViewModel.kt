package app.trombito.rallycopilot.ui.mistramos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.edicion.ListaNotasPorRevisar
import app.trombito.rallycopilot.domain.tramos.ExportaTramo
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.PasadaDetallada
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.domain.tramos.SubtramoGuardado
import app.trombito.rallycopilot.domain.tramos.TramoExportado
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.RutaTramo
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Detalle de un tramo: traza, estilo del mapa para dibujarla, pasadas contra la mejor, favorito y nombre. «Compartir»
 * escribe el tramo como GPX con sus notas ([ExportaTramo]) y lo entrega por [archivosParaCompartir] para que la
 * pantalla abra el menú Compartir de Android.
 */
@HiltViewModel
class DetalleTramoViewModel
    @Inject
    constructor(
        guardado: SavedStateHandle,
        private val repositorio: RepositorioTramos,
        private val datosMapa: RepositorioDatosMapa,
        private val exportador: ExportaTramo,
        private val notasPorRevisar: ListaNotasPorRevisar,
    ) : ViewModel() {
        private val tramoId = guardado.toRoute<RutaTramo>().tramoId
        private val renombrando = MutableStateFlow(false)
        private val estiloMapa = MutableStateFlow<String?>(null)
        private val compartir = MutableStateFlow(EstadoCompartir.LIBRE)
        private val archivos = Channel<TramoExportado>(Channel.BUFFERED)

        /** El archivo de cada «Compartir» ya escrito, para entregarlo a la app que elija el usuario. */
        val archivosParaCompartir: Flow<TramoExportado> = archivos.receiveAsFlow()

        private class Carga(
            val traza: List<PuntoGeo>,
            val pasadas: List<FilaPasada>,
            val subtramos: List<FilaSubtramo> = emptyList(),
            val porRevisar: List<FilaNotaPorRevisar> = emptyList(),
        )

        private val resumen: Flow<ResumenTramo?> =
            repositorio.observarResumenes(FiltroTramos.TODOS).map { lista -> lista.firstOrNull { it.id == tramoId } }

        // Las pasadas solo se releen si cambia su número o la mejor; renombrar o marcar favorito no las toca.
        @OptIn(ExperimentalCoroutinesApi::class)
        private val carga: Flow<Carga> =
            resumen
                .map { it?.pasadas to it?.mejorTotalMs }
                .distinctUntilChanged()
                .mapLatest {
                    Carga(
                        traza = repositorio.tramo(tramoId)?.traza.orEmpty(),
                        pasadas = filasDe(repositorio.pasadasDetalladas(tramoId)),
                    )
                }

        private val subtramos: Flow<List<FilaSubtramo>> =
            repositorio.observarSubtramosDeTramo(tramoId).map(::filasDeSubtramos)

        // Las marcas de nota mal se dejan conduciendo, no con el detalle abierto: se leen una vez por apertura.
        private val porRevisar: Flow<List<FilaNotaPorRevisar>> =
            flow { emit(notasPorRevisar.de(tramoId).map { FilaNotaPorRevisar(it.posicionM, it.texto) }) }

        private val datos: Flow<Carga> =
            combine(carga, subtramos, porRevisar) { cargada, filas, revisar ->
                Carga(cargada.traza, cargada.pasadas, filas, revisar)
            }

        val uiState: StateFlow<DetalleTramoUiState> =
            combine(
                resumen,
                datos,
                renombrando,
                estiloMapa,
                compartir,
            ) { tramo, datos, editando, estilo, estadoCompartir ->
                DetalleTramoUiState(
                    cargando = false,
                    tramo = tramo,
                    traza = datos.traza,
                    estiloMapaJson = estilo,
                    pasadas = datos.pasadas,
                    subtramos = datos.subtramos,
                    notasPorRevisar = datos.porRevisar,
                    renombrando = editando && tramo != null,
                    compartir = estadoCompartir,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), DetalleTramoUiState())

        init {
            viewModelScope.launch { estiloMapa.value = datosMapa.estilos()?.vectorial }
        }

        /** Escribe el tramo con sus notas y lo entrega para compartir; se ignora si ya se está preparando. */
        fun onCompartir() {
            if (compartir.value == EstadoCompartir.PREPARANDO) return
            compartir.value = EstadoCompartir.PREPARANDO
            viewModelScope.launch {
                val archivo = exportador.exportar(tramoId)
                if (archivo == null) {
                    compartir.value = EstadoCompartir.FALLO
                } else {
                    archivos.send(archivo)
                    compartir.value = EstadoCompartir.LIBRE
                }
            }
        }

        /** La pantalla no pudo escribir el archivo para compartir. */
        fun onFalloAlCompartir() {
            compartir.value = EstadoCompartir.FALLO
        }

        /** El aviso de que no se pudo compartir ya se mostró. */
        fun onCompartirVisto() {
            if (compartir.value == EstadoCompartir.FALLO) compartir.value = EstadoCompartir.LIBRE
        }

        fun onFavorito() {
            val tramo = uiState.value.tramo ?: return
            viewModelScope.launch { repositorio.marcarFavorito(tramo.id, !tramo.favorito) }
        }

        fun onPedirRenombrar() {
            renombrando.value = true
        }

        fun onCancelarRenombrar() {
            renombrando.value = false
        }

        /** [nombre] en blanco devuelve el nombre automático. */
        fun onRenombrar(nombre: String) {
            renombrando.value = false
            viewModelScope.launch { repositorio.renombrar(tramoId, nombre.trim().ifEmpty { null }) }
        }

        private fun filasDeSubtramos(guardados: List<SubtramoGuardado>): List<FilaSubtramo> =
            guardados
                .sortedByDescending { it.inicio }
                .mapIndexed { i, s ->
                    FilaSubtramo(i, s.inicio, s.desdeM, s.hastaM, s.tiempoMs, s.mejorMs?.let { s.tiempoMs - it })
                }

        private fun filasDe(pasadas: List<PasadaDetallada>): List<FilaPasada> {
            val mejor = pasadas.minOfOrNull { it.totalMs } ?: return emptyList()
            return pasadas.map {
                FilaPasada(it.id, it.inicioMs, it.totalMs, it.totalMs - mejor, it.esMejor)
            }
        }
    }
