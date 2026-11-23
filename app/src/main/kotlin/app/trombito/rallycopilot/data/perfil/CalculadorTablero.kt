package app.trombito.rallycopilot.data.perfil

import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.carreteras.MisCarreteras
import app.trombito.rallycopilot.domain.perfil.CalculoTablero
import app.trombito.rallycopilot.domain.perfil.TableroPiloto
import app.trombito.rallycopilot.domain.perfil.UltimoViaje
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.analisis.ConclusionesPiloto
import app.trombito.rallycopilot.motor.analisis.PerfilPiloto
import app.trombito.rallycopilot.motor.analisis.PuntoTiempo
import app.trombito.rallycopilot.motor.analisis.RecorridoRegistrado
import app.trombito.rallycopilot.motor.analisis.VelocidadPorGrado
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

private const val M_POR_KM = 1_000.0

/**
 * Tablero del piloto: junta el resumen de carreteras y la marcha por vía de [CalculoCarreteras] (que ya tiene su caché
 * por recorrido) con la velocidad de paso por grado de curva, que mide aquí y guarda en caché por id de recorrido (los
 * puntos de un recorrido terminado no cambian, y no depende de las vías), y con las pasadas de los tramos para los
 * récords. Terminar un viaje más solo calcula lo nuevo. Todo va en [dispatcher] (CPU).
 *
 * Los kilómetros y el nº de viajes salen de los recorridos terminados: los tramos guardados (de viaje o de vía) no se
 * suman, así que no se cuenta ningún kilómetro dos veces. [ahoraMs] y [zona] se piden en cada cálculo (para «este
 * mes»).
 */
class CalculadorTablero(
    private val carreteras: CalculoCarreteras,
    private val recorridos: RepositorioRecorridos,
    private val tramos: RepositorioTramos,
    private val dispatcher: CoroutineDispatcher,
    private val ahoraMs: () -> Long = System::currentTimeMillis,
    private val zona: () -> ZoneId = ZoneId::systemDefault,
) : CalculoTablero {
    private val cacheGrado = ConcurrentHashMap<Long, VelocidadPorGrado>()

    private val tramosConPasadas = TramosConPasadas(tramos)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observar(): Flow<TableroPiloto> =
        combine(
            carreteras.observar(),
            recorridos.listar(),
            tramos.observarResumenes(FiltroTramos.TODOS),
        ) { misCarreteras, lista, resumenes ->
            Triple(misCarreteras, lista, resumenes)
        }.mapLatest { (misCarreteras, lista, resumenes) -> calcular(misCarreteras, lista, resumenes) }
            .flowOn(dispatcher)

    private suspend fun calcular(
        misCarreteras: MisCarreteras,
        lista: List<Recorrido>,
        resumenes: List<ResumenTramo>,
    ): TableroPiloto {
        cacheGrado.keys.retainAll(lista.map { it.id }.toSet())
        if (lista.isEmpty()) return TableroPiloto()
        val primeroMs = lista.minOf { it.inicioMs }
        val perfil =
            PerfilPiloto.de(
                tramos = tramosConPasadas.de(resumenes),
                recorridos = lista.map { RecorridoRegistrado(it.inicioMs, it.distanciaM) },
            )
        val conclusiones =
            ConclusionesPiloto.de(
                perfil = perfil,
                carreteras = misCarreteras.resumen,
                velocidades = misCarreteras.velocidades,
                velocidadPorGrado = VelocidadPorGrado.combinar(lista.map { velocidadPorGradoDe(it.id) }),
                ultimoViaje = misCarreteras.ultimoViaje,
                ahoraMs = ahoraMs(),
                primerViajeMs = primeroMs,
                zona = zona(),
            )
        val ultimo = lista.first()
        return TableroPiloto(
            viajes = lista.size,
            kmTotales = lista.sumOf { it.distanciaM } / M_POR_KM,
            primerViajeMs = primeroMs,
            ultimoViaje = UltimoViaje(ultimo.id, ultimo.inicioMs, ultimo.distanciaM, ultimo.destinoNombre),
            conclusiones = conclusiones,
        )
    }

    private suspend fun velocidadPorGradoDe(id: Long): VelocidadPorGrado {
        currentCoroutineContext().ensureActive()
        cacheGrado[id]?.let { return it }
        val puntos = recorridos.puntos(id).map { PuntoTiempo(PuntoGeo(it.lat, it.lon), it.tiempoMs, it.velocidadMs) }
        return VelocidadPorGrado.analizar(puntos).also { cacheGrado[id] = it }
    }
}
