package app.trombito.rallycopilot.data.carreteras

import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.data.tramos.FuenteIndicesVias
import app.trombito.rallycopilot.data.vias.DatosVias
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.di.DispatcherDefault
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.carreteras.MisCarreteras
import app.trombito.rallycopilot.domain.resumen.ContextoVias
import app.trombito.rallycopilot.domain.resumen.FuenteContextoVias
import app.trombito.rallycopilot.motor.analisis.AgregadoCarreteras
import app.trombito.rallycopilot.motor.analisis.AnalisisMarcha
import app.trombito.rallycopilot.motor.analisis.AnalisisRecorrido
import app.trombito.rallycopilot.motor.analisis.FichasCarreteras
import app.trombito.rallycopilot.motor.analisis.OrientacionPorEjes
import app.trombito.rallycopilot.motor.analisis.OrientacionVia
import app.trombito.rallycopilot.motor.analisis.PuntoTiempo
import app.trombito.rallycopilot.motor.analisis.VelocidadVias
import app.trombito.rallycopilot.motor.analisis.ViajeParaFichas
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estadísticas de «Mis carreteras»: lee los recorridos terminados y sus puntos, los asigna a las vías de OSM y suma
 * pasadas, kilómetros y curvas ([AgregadoCarreteras]) y la velocidad media en marcha por vía ([VelocidadVias], con los
 * tiempos de los puntos). Todo el cálculo va en [dispatcher] (CPU). El análisis de cada recorrido (los dos) se guarda
 * en caché por id mientras vivan las mismas vías, así que abrir de nuevo la pantalla (o terminar un recorrido más) solo
 * calcula lo nuevo. Sin vías instaladas siguen saliendo los kilómetros y las curvas. Con los ejes de las carreteras
 * ([FuenteIndicesVias], los mismos que dan el sentido a los tramos de vía) orienta cada vía y reparte el ritmo de las
 * fichas por carretera ([FichasCarreteras]) entre km crecientes y decrecientes; sin ellos, queda sin sentido.
 */
@Singleton
class CalculadorCarreteras
    @Inject
    constructor(
        private val recorridos: RepositorioRecorridos,
        private val vias: FuenteVias,
        @DispatcherDefault private val dispatcher: CoroutineDispatcher,
        private val indices: FuenteIndicesVias,
    ) : CalculoCarreteras,
        FuenteContextoVias {
        /** Sin ejes de carreteras: las fichas no orientan las vías (todo el ritmo va a «sin sentido»). */
        constructor(
            recorridos: RepositorioRecorridos,
            vias: FuenteVias,
            dispatcher: CoroutineDispatcher,
        ) : this(recorridos, vias, dispatcher, FuenteIndicesVias { null })

        /** Los dos análisis de un recorrido: carreteras (pasadas, curvas) y marcha (velocidad en marcha por vía). */
        private class Analizado(
            val carreteras: AnalisisRecorrido,
            val marcha: AnalisisMarcha,
        )

        private val cache = ConcurrentHashMap<Long, Analizado>()

        @Volatile
        private var viasDeLaCache: DatosVias? = null

        /** Emite el resumen al empezar y cada vez que cambia la lista de recorridos terminados. */
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observar(): Flow<MisCarreteras> =
            recorridos
                .listar()
                .mapLatest { lista -> calcular(lista) }
                .flowOn(dispatcher)

        /**
         * Las vías para el resumen del viaje [recorridoId]: cómo asignar sus puntos a una vía y lo acumulado en el
         * resto de recorridos terminados (sin él). Aprovecha la caché de [observar], así que solo analiza lo que falte.
         */
        override suspend fun contextoSin(recorridoId: Long): ContextoVias =
            withContext(dispatcher) {
                val ids =
                    recorridos
                        .listar()
                        .first()
                        .map { it.id }
                        .filter { it != recorridoId }
                val datos = vias.datos()
                val analisis = analisisDe(ids, datos)
                val historico = VelocidadVias.agregar(analisis.map { it.marcha }).associateBy { it.via.id }
                ContextoVias(coincidenciaDe(datos), historico)
            }

        private fun coincidenciaDe(datos: DatosVias?) =
            VelocidadVias.CoincidenciaDe { punto, rumbo ->
                datos?.indice?.cercana(punto, rumbo, AgregadoCarreteras.RADIO_VIA_M)
            }

        private suspend fun calcular(lista: List<Recorrido>): MisCarreteras {
            val ids = lista.map { it.id }
            val datos = vias.datos()
            val analisis = analisisDe(ids, datos)
            cache.keys.retainAll(ids.toSet())
            val viajes =
                lista.zip(analisis) { recorrido, hecho ->
                    ViajeParaFichas(recorrido.inicioMs, hecho.carreteras, hecho.marcha)
                }
            return MisCarreteras(
                resumen =
                    AgregadoCarreteras
                        .agregar(analisis.map { it.carreteras })
                        .copy(fichas = FichasCarreteras.agregar(viajes, orientacionDe(datos))),
                hayVias = datos != null,
                velocidades = VelocidadVias.agregar(analisis.map { it.marcha }),
                // `listar()` va del más reciente al más antiguo.
                ultimoViaje = analisis.firstOrNull()?.marcha,
            )
        }

        /** Cómo orientar las vías con los ejes de las carreteras; sin vías o sin ejes, no se orienta ninguna. */
        private suspend fun orientacionDe(datos: DatosVias?): OrientacionVia =
            if (datos == null) {
                OrientacionVia { null }
            } else {
                indices.indices()?.let { OrientacionPorEjes(it.ejes) } ?: OrientacionVia { null }
            }

        /** Los análisis de [ids] (de la caché o calculados ahora); la caché se descarta si cambiaron las vías. */
        private suspend fun analisisDe(
            ids: List<Long>,
            datos: DatosVias?,
        ): List<Analizado> {
            if (datos !== viasDeLaCache) {
                cache.clear()
                viasDeLaCache = datos
            }
            val viaDe =
                AgregadoCarreteras.ViaDe { punto, rumbo ->
                    datos?.indice?.cercana(punto, rumbo, AgregadoCarreteras.RADIO_VIA_M)?.via
                }
            val coincidenciaDe = coincidenciaDe(datos)
            return ids.map { id ->
                currentCoroutineContext().ensureActive()
                cache[id] ?: analizar(id, datos != null, viaDe, coincidenciaDe).also { cache[id] = it }
            }
        }

        private suspend fun analizar(
            id: Long,
            hayVias: Boolean,
            viaDe: AgregadoCarreteras.ViaDe,
            coincidenciaDe: VelocidadVias.CoincidenciaDe,
        ): Analizado {
            val puntos = recorridos.puntos(id)
            val marcha =
                if (hayVias) {
                    VelocidadVias.analizar(
                        puntos.map { PuntoTiempo(PuntoGeo(it.lat, it.lon), it.tiempoMs, it.velocidadMs) },
                        coincidenciaDe,
                    )
                } else {
                    AnalisisMarcha(emptyList())
                }
            return Analizado(AgregadoCarreteras.analizar(puntos.map { PuntoGeo(it.lat, it.lon) }, viaDe), marcha)
        }
    }
