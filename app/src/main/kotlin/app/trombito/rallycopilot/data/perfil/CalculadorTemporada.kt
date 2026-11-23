package app.trombito.rallycopilot.data.perfil

import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.data.vias.DatosVias
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.domain.perfil.CalculoTemporada
import app.trombito.rallycopilot.domain.perfil.CarneCarreteras
import app.trombito.rallycopilot.domain.perfil.TemporadaPiloto
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.analisis.AgregadoCarreteras
import app.trombito.rallycopilot.motor.analisis.AnalisisRecorrido
import app.trombito.rallycopilot.motor.analisis.RecorridoTemporada
import app.trombito.rallycopilot.motor.analisis.Temporada
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Temporada del piloto: construye [Temporada] con los viajes terminados (fecha de inicio, distancia, curvas y vías de
 * cada uno) y las pasadas de los tramos guardados (récords), más el carné de carreteras. El análisis de cada viaje
 * (puntos a vías y curvas) se guarda en caché por id de recorrido mientras vivan las mismas vías, así que terminar un
 * viaje más solo analiza el nuevo. Todo va en [dispatcher] (CPU), nunca en el hilo principal. [ahoraMs] y [zona] se
 * piden en cada cálculo (racha y «este mes»).
 *
 * Sin vías instaladas siguen saliendo los kilómetros, las curvas y la racha; no hay carreteras ni carné.
 */
class CalculadorTemporada(
    private val recorridos: RepositorioRecorridos,
    private val tramos: RepositorioTramos,
    private val vias: FuenteVias,
    private val dispatcher: CoroutineDispatcher,
    private val ahoraMs: () -> Long = System::currentTimeMillis,
    private val zona: () -> ZoneId = ZoneId::systemDefault,
) : CalculoTemporada {
    private val cache = ConcurrentHashMap<Long, AnalisisRecorrido>()

    @Volatile
    private var viasDeLaCache: DatosVias? = null

    private val tramosConPasadas = TramosConPasadas(tramos)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observar(): Flow<TemporadaPiloto> =
        combine(recorridos.listar(), tramos.observarResumenes(FiltroTramos.TODOS)) { lista, resumenes ->
            lista to resumenes
        }.mapLatest { (lista, resumenes) -> calcular(lista, resumenes) }
            .flowOn(dispatcher)

    private suspend fun calcular(
        lista: List<Recorrido>,
        resumenes: List<ResumenTramo>,
    ): TemporadaPiloto {
        val datos = vias.datos()
        if (datos !== viasDeLaCache) {
            cache.clear()
            viasDeLaCache = datos
        }
        cache.keys.retainAll(lista.map { it.id }.toSet())
        val viaDe =
            AgregadoCarreteras.ViaDe { punto, rumbo ->
                datos?.indice?.cercana(punto, rumbo, AgregadoCarreteras.RADIO_VIA_M)?.via
            }
        val analizados = lista.map { it to analisisDe(it.id, viaDe) }
        val temporada =
            Temporada.de(
                recorridos = analizados.map { (r, a) -> RecorridoTemporada.de(r.inicioMs, a, r.distanciaM) },
                tramos = tramosConPasadas.de(resumenes),
                zona = zona(),
                ahoraMs = ahoraMs(),
            )
        return TemporadaPiloto(temporada, CarneCarreteras.de(analizados.map { it.second.usos }))
    }

    private suspend fun analisisDe(
        id: Long,
        viaDe: AgregadoCarreteras.ViaDe,
    ): AnalisisRecorrido {
        currentCoroutineContext().ensureActive()
        cache[id]?.let { return it }
        val puntos = recorridos.puntos(id).map { PuntoGeo(it.lat, it.lon) }
        return AgregadoCarreteras.analizar(puntos, viaDe).also { cache[id] = it }
    }
}
