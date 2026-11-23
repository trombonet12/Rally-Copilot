package app.trombito.rallycopilot.data.tramos

import android.database.sqlite.SQLiteException
import android.util.Log
import app.trombito.rallycopilot.di.DispatcherDefault
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.domain.tramos.ProveedorTramosPorDelante
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.TramosPorDelante
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.TramoEnCamino
import app.trombito.rallycopilot.motor.tramos.TramosEnCamino
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Busca los tramos conocidos que recorre un camino (los `TramosEnCamino` de los tramos guardados). */
internal typealias BuscadorTramos = (List<PuntoGeo>) -> List<TramoEnCamino>

private const val ETIQUETA = "TramosPorDelante"

/**
 * Los tramos guardados que el camino de la conducción tiene por delante. El camino es el de las notas en curso
 * ([ProveedorNotas]): en guiado la ruta y en libre la carretera prevista, con la posición ya proyectada sobre él, así
 * que no hace falta otra fuente. Los `TramosEnCamino` (con la rejilla de trazas) se crean una vez con los tramos
 * guardados y se rehacen solo cuando cambia el conjunto de tramos (no por cada pasada nueva); la búsqueda, en
 * [CalculadorTramosPorDelante], corre fuera del hilo principal y se repite como mucho cada 200 m recorridos.
 *
 * Sin tramos guardados, sin conducción o con la base caída, emite vacío.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class TramosPorDelanteEnVivo
    @Inject
    constructor(
        private val repositorio: RepositorioTramos,
        private val notas: ProveedorNotas,
        @DispatcherDefault private val dispatcher: CoroutineDispatcher,
    ) : ProveedorTramosPorDelante {
        override val porDelante: Flow<TramosPorDelante> =
            buscadores()
                .flatMapLatest { buscador ->
                    val calculador = CalculadorTramosPorDelante(buscador)
                    notas.notas.map { calculador.calcular(it) }
                }.flowOn(dispatcher)

        /** Un buscador nuevo cada vez que cambia el conjunto de tramos guardados (por sus ids). */
        private fun buscadores(): Flow<BuscadorTramos> =
            repositorio
                .observarResumenes()
                .map { lista -> lista.mapTo(HashSet()) { it.id } }
                .distinctUntilChanged()
                .map { ids -> buscadorDe(ids.isNotEmpty()) }

        private suspend fun buscadorDe(hayTramos: Boolean): BuscadorTramos {
            val tramos = if (hayTramos) cargarTramos() else emptyList()
            if (tramos.isEmpty()) return { emptyList() }
            return TramosEnCamino(tramos)::sobre
        }

        private suspend fun cargarTramos() =
            try {
                repositorio.cronometrables().map { it.tramo }
            } catch (e: SQLiteException) {
                Log.w(ETIQUETA, "No se pudieron leer los tramos: ${e.javaClass.simpleName}")
                emptyList()
            } catch (e: IllegalArgumentException) {
                Log.w(ETIQUETA, "Tramos guardados no válidos: ${e.javaClass.simpleName}")
                emptyList()
            }
    }
