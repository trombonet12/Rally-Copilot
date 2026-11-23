package app.trombito.rallycopilot.ui.miscarreteras

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.di.DispatcherDefault
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.carreteras.MisCarreteras
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.motor.analisis.CurvasPorGrado
import app.trombito.rallycopilot.motor.analisis.FichaCarretera
import app.trombito.rallycopilot.motor.analisis.OrdenCarreteras
import app.trombito.rallycopilot.motor.analisis.ordenadasPor
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import app.trombito.rallycopilot.ui.mapa.ModoCalor
import app.trombito.rallycopilot.ui.mapa.TramoCalor
import app.trombito.rallycopilot.ui.mapa.aTramosCalor
import app.trombito.rallycopilot.ui.mapa.calorGeoJsonTexto
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
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * «Mis carreteras»: empieza cargando y, cuando [CalculoCarreteras] termina (en segundo plano), enseña el mapa de calor
 * y una ficha resumida por carretera en el orden elegido (uso, curvas o reciente). El modo del mapa (uso o velocidad)
 * y el orden son estado de la pantalla; el GeoJSON del mapa se construye fuera del hilo principal y solo se rehace si
 * cambian los datos o el modo, no el orden. Si la base de datos o los puntos fallan, pasa a estado de error en vez de
 * cerrar la app.
 */
@HiltViewModel
class MisCarreterasViewModel
    @Inject
    constructor(
        calculador: CalculoCarreteras,
        private val datosMapa: RepositorioDatosMapa,
        @DispatcherDefault despachador: CoroutineDispatcher,
    ) : ViewModel() {
        private val orden = MutableStateFlow(OrdenCarreteras.USO)
        private val modo = MutableStateFlow(ModoCalor.USO)
        private val estilo = MutableStateFlow<String?>(null)

        private var memoria: MemoriaGeoJson? = null

        val uiState: StateFlow<MisCarreterasUiState> =
            combine(calculador.observar().map { it.aBase() }, orden, modo, estilo) { base, o, m, e ->
                estadoDe(base, o, m, e)
            }.flowOn(despachador)
                .catch { fallo ->
                    if (fallo is SQLiteException || fallo is IOException || fallo is IllegalStateException) {
                        emit(MisCarreterasUiState(cargando = false, error = true))
                    } else {
                        throw fallo
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), MisCarreterasUiState())

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

        fun onOrden(nuevo: OrdenCarreteras) {
            orden.value = nuevo
        }

        fun onModo(nuevo: ModoCalor) {
            modo.value = nuevo
        }

        private fun estadoDe(
            base: BaseCarreteras,
            orden: OrdenCarreteras,
            modo: ModoCalor,
            estilo: String?,
        ): MisCarreterasUiState {
            val previa = memoria?.takeIf { it.base === base && it.modo == modo }
            val geoJson = previa?.geoJson ?: calorGeoJsonTexto(base.tramos, modo)
            if (previa == null) memoria = MemoriaGeoJson(base, modo, geoJson)
            return MisCarreterasUiState(
                cargando = false,
                recorridos = base.recorridos,
                curvas = base.curvas,
                trazos = base.tramos.map { it.aTrazo() },
                fichas = base.fichas.ordenadasPor(orden),
                orden = orden,
                modoCalor = modo,
                geoJson = geoJson,
                esquinas = esquinasDe(base.tramos),
                estiloMapaJson = estilo,
                hayVias = base.hayVias,
            )
        }
    }

/** Lo calculado a partir de los recorridos, listo para combinar con orden, modo y estilo. */
private class BaseCarreteras(
    val recorridos: Int,
    val curvas: CurvasPorGrado,
    val tramos: List<TramoCalor>,
    val fichas: List<FichaCarretera>,
    val hayVias: Boolean,
)

/** El GeoJSON ya construido de [base] en el [modo]: no se rehace al cambiar solo el orden. */
private class MemoriaGeoJson(
    val base: BaseCarreteras,
    val modo: ModoCalor,
    val geoJson: String,
)

private fun MisCarreteras.aBase() =
    BaseCarreteras(
        recorridos = resumen.recorridos,
        curvas = resumen.curvas,
        tramos = aTramosCalor(),
        fichas = resumen.fichas,
        hayVias = hayVias,
    )
