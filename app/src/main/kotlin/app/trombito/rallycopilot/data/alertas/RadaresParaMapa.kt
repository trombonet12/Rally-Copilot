package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.data.vias.DatosVias
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.di.DispatcherDefault
import app.trombito.rallycopilot.domain.alertas.ProveedorRadaresMapa
import app.trombito.rallycopilot.domain.alertas.RadaresEnMapa
import app.trombito.rallycopilot.domain.alertas.TramoEnMapa
import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import app.trombito.rallycopilot.motor.alertas.TrazadoTramoRadar
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Radares de los datos más los trazados de sus tramos para el mapa. Cada tramo (entrada y salida con el mismo
 * `tramo_id`) se traza sobre las vías con [TrazadoTramoRadar] si hay datos de vías, y si no (o no encaja) en recta.
 * Los tramos del otro sentido (mismos extremos intercambiados) se dibujan una sola vez. Se calcula una vez y se
 * comparte mientras no cambien los radares ni las vías.
 */
@Singleton
class RadaresParaMapa
    @Inject
    constructor(
        private val radares: FuenteRadares,
        private val vias: FuenteVias,
        @DispatcherDefault private val dispatcher: CoroutineDispatcher,
    ) : ProveedorRadaresMapa {
        private val mutex = Mutex()
        private var base: Triple<List<Radar>, DatosVias?, RadaresEnMapa>? = null

        override suspend fun radaresEnMapa(): RadaresEnMapa {
            val lista = radares.radares()
            val datos = vias.datos()
            return mutex.withLock {
                base?.takeIf { it.first === lista && it.second === datos }?.third
                    ?: withContext(dispatcher) { RadaresEnMapa(lista, tramosDe(lista, datos)) }.also {
                        base = Triple(lista, datos, it)
                    }
            }
        }

        private fun tramosDe(
            lista: List<Radar>,
            datos: DatosVias?,
        ): List<TramoEnMapa> {
            val fines = lista.filter { it.tipo == TipoRadar.TRAMO_FIN }.associateBy { it.tramoId }
            val vistos = HashSet<Set<String>>()
            return lista
                .filter { it.tipo == TipoRadar.TRAMO_INICIO }
                .mapNotNull { inicio ->
                    val id = inicio.tramoId
                    val fin = id?.let { fines[it] }
                    val extremos = setOf(clave(inicio.punto), clave(fin?.punto))
                    if (id == null || fin == null || !vistos.add(extremos)) {
                        null
                    } else {
                        val trazado =
                            datos?.let { TrazadoTramoRadar.de(inicio.punto, fin.punto, it.indice, it.grafo) }
                                ?: listOf(inicio.punto, fin.punto)
                        TramoEnMapa(id, inicio.limiteKmh ?: fin.limiteKmh, trazado)
                    }
                }
        }

        private fun clave(punto: PuntoGeo?): String =
            punto?.let { "%.5f,%.5f".format(java.util.Locale.ROOT, it.lat, it.lon) } ?: "-"
    }
