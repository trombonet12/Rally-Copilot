package app.trombito.rallycopilot.ui.miscarreteras

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.trombito.rallycopilot.di.DispatcherDefault
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.carreteras.MisCarreteras
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.domain.tramos.TipoTramo
import app.trombito.rallycopilot.motor.analisis.FichaCarretera
import app.trombito.rallycopilot.motor.analisis.FichasCarreteras
import app.trombito.rallycopilot.ui.RutaFichaCarretera
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import app.trombito.rallycopilot.ui.mapa.ModoCalor
import app.trombito.rallycopilot.ui.mapa.TramoCalor
import app.trombito.rallycopilot.ui.mapa.aTramosCalor
import app.trombito.rallycopilot.ui.mapa.calorGeoJsonTexto
import app.trombito.rallycopilot.ui.mistramos.agruparCarreteras
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * La ficha de la carretera de la ruta ([RutaFichaCarretera.ref] es el id de su `FichaCarretera`): junta lo conducido en
 * ella ([CalculoCarreteras]) con sus tramos de vía guardados ([RepositorioTramos]) y el estilo del mapa. El tramo
 * tocado en la regla y el modo del mapa son estado de la pantalla; el GeoJSON se construye fuera del hilo principal. Si
 * la base de datos o los puntos fallan, pasa a estado de error en vez de cerrar la app.
 */
@HiltViewModel
class FichaCarreteraViewModel
    @Inject
    constructor(
        guardado: SavedStateHandle,
        calculador: CalculoCarreteras,
        tramos: RepositorioTramos,
        private val datosMapa: RepositorioDatosMapa,
        @DispatcherDefault despachador: CoroutineDispatcher,
    ) : ViewModel() {
        private val id = guardado.toRoute<RutaFichaCarretera>().ref
        private val modo = MutableStateFlow(ModoCalor.USO)
        private val seleccionado = MutableStateFlow<String?>(null)
        private val estilo = MutableStateFlow<String?>(null)
        private var memoria: MemoriaGeoJsonFicha? = null

        val uiState: StateFlow<FichaCarreteraUiState> =
            combine(
                calculador.observar().map { it.deEstaCarretera(id) },
                tramos.observarResumenes(FiltroTramos.TODOS).map { lista -> lista.filter { it.esDeLaCarretera(id) } },
                modo,
                seleccionado,
                estilo,
            ) { conducido, deVia, m, elegido, e -> estadoDe(conducido, deVia, m, elegido, e) }
                .flowOn(despachador)
                .catch { fallo ->
                    if (fallo is SQLiteException || fallo is IOException || fallo is IllegalStateException) {
                        emit(FichaCarreteraUiState(cargando = false, error = true))
                    } else {
                        throw fallo
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), FichaCarreteraUiState())

        init {
            viewModelScope.launch {
                estilo.value =
                    try {
                        datosMapa.estilos()?.vectorial
                    } catch (_: IOException) {
                        null
                    } catch (_: IllegalStateException) {
                        null
                    }
            }
        }

        fun onModo(nuevo: ModoCalor) {
            modo.value = nuevo
        }

        /** Tocar una franja la elige; tocar la que ya está elegida la suelta. */
        fun onTramo(tramoId: String) {
            seleccionado.update { if (it == tramoId) null else tramoId }
        }

        private fun estadoDe(
            conducido: Conducido,
            deVia: List<ResumenTramo>,
            modo: ModoCalor,
            elegido: String?,
            estilo: String?,
        ): FichaCarreteraUiState {
            val previa = memoria?.takeIf { it.conducido === conducido && it.modo == modo }
            val geoJson = previa?.geoJson ?: calorGeoJsonTexto(conducido.tramos, modo)
            if (previa == null) memoria = MemoriaGeoJsonFicha(conducido, modo, geoJson)
            return FichaCarreteraUiState(
                cargando = false,
                ficha = conducido.ficha,
                grupos = agruparCarreteras(deVia),
                regla = reglaDe(deVia),
                seleccionado = elegido?.takeIf { e -> deVia.any { it.id == e } },
                modoCalor = modo,
                trazos = conducido.tramos.map { it.aTrazo() },
                geoJson = geoJson,
                esquinas = esquinasDe(conducido.tramos),
                estiloMapaJson = estilo,
            )
        }
    }

/** Lo conducido en una carretera: su ficha (null si ningún viaje pasa por ella) y sus vías. */
private class Conducido(
    val ficha: FichaCarretera?,
    val tramos: List<TramoCalor>,
)

/** El GeoJSON ya construido de [conducido] en el [modo]: no se rehace al cambiar solo el tramo elegido. */
private class MemoriaGeoJsonFicha(
    val conducido: Conducido,
    val modo: ModoCalor,
    val geoJson: String,
)

private fun MisCarreteras.deEstaCarretera(id: String): Conducido {
    val vias =
        resumen.vias
            .filter { FichasCarreteras.idCarretera(it.via) == id }
            .map { it.via.id }
            .toSet()
    return Conducido(
        ficha = resumen.fichas.firstOrNull { it.id == id },
        tramos = aTramosCalor().filter { it.viaId in vias },
    )
}

private fun ResumenTramo.esDeLaCarretera(id: String) = tipo == TipoTramo.VIA && via?.viaRef == id
