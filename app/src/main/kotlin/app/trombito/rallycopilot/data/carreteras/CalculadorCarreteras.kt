package app.trombito.rallycopilot.data.carreteras

import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.data.vias.DatosVias
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.di.DispatcherDefault
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.carreteras.MisCarreteras
import app.trombito.rallycopilot.motor.analisis.AgregadoCarreteras
import app.trombito.rallycopilot.motor.analisis.AnalisisRecorrido
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estadísticas de «Mis carreteras»: lee los recorridos terminados y sus puntos, los asigna a las vías de OSM y suma
 * pasadas, kilómetros y curvas ([AgregadoCarreteras]). Todo el cálculo va en [dispatcher] (CPU). El análisis de cada
 * recorrido se guarda en caché por id mientras vivan las mismas vías, así que abrir de nuevo la pantalla (o terminar un
 * recorrido más) solo calcula lo nuevo. Sin vías instaladas siguen saliendo los kilómetros y las curvas.
 */
@Singleton
class CalculadorCarreteras
    @Inject
    constructor(
        private val recorridos: RepositorioRecorridos,
        private val vias: FuenteVias,
        @DispatcherDefault private val dispatcher: CoroutineDispatcher,
    ) : CalculoCarreteras {
        private val cache = ConcurrentHashMap<Long, AnalisisRecorrido>()

        @Volatile
        private var viasDeLaCache: DatosVias? = null

        /** Emite el resumen al empezar y cada vez que cambia la lista de recorridos terminados. */
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observar(): Flow<MisCarreteras> =
            recorridos
                .listar()
                .mapLatest { lista -> calcular(lista.map { it.id }) }
                .flowOn(dispatcher)

        private suspend fun calcular(ids: List<Long>): MisCarreteras {
            val datos = vias.datos()
            if (datos !== viasDeLaCache) {
                cache.clear()
                viasDeLaCache = datos
            }
            cache.keys.retainAll(ids.toSet())
            val viaDe =
                AgregadoCarreteras.ViaDe { punto, rumbo ->
                    datos?.indice?.cercana(punto, rumbo, AgregadoCarreteras.RADIO_VIA_M)?.via
                }
            val analisis =
                ids.map { id ->
                    currentCoroutineContext().ensureActive()
                    cache[id] ?: AgregadoCarreteras.analizar(puntosDe(id), viaDe).also { cache[id] = it }
                }
            return MisCarreteras(AgregadoCarreteras.agregar(analisis), hayVias = datos != null)
        }

        private suspend fun puntosDe(id: Long): List<PuntoGeo> = recorridos.puntos(id).map { PuntoGeo(it.lat, it.lon) }
    }
