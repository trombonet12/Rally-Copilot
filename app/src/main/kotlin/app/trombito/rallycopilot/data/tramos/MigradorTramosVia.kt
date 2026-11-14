package app.trombito.rallycopilot.data.tramos

import android.database.sqlite.SQLiteException
import android.util.Log
import app.trombito.rallycopilot.data.db.RecorridosDao
import app.trombito.rallycopilot.data.db.SubtramoEntity
import app.trombito.rallycopilot.data.db.SubtramosDao
import app.trombito.rallycopilot.data.db.TramosDao
import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.di.DispatcherIo
import app.trombito.rallycopilot.domain.tramos.RecorteTraza
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que hizo una ejecución del [MigradorTramosVia]: recorridos troceados, subtramos situados y si quedó pendiente. */
data class ResumenMigracionVias(
    val recorridos: Int,
    val subtramos: Int,
    val pendiente: Boolean,
)

/**
 * Migra los datos guardados antes de los tramos de vía (H23), sin tocar nada de lo que ya existe: trocea cada
 * recorrido terminado que aún no lo está ([GuardadorTramosVia.trocearYMarcar]) y sitúa en su carretera los subtramos
 * que caen en un solo tramo de vía. Los tramos-viaje, sus pasadas y los subtramos no se borran ni se modifican (los
 * subtramos solo ganan carretera, kilómetros y sentido).
 *
 * Es idempotente y reanudable: cada recorrido se marca al acabar y un tramo no recibe dos veces la pasada del mismo
 * recorrido, así que repetirlo (o continuar tras cortarlo) deja el mismo resultado. Sin vías instaladas no hace nada y
 * [ResumenMigracionVias.pendiente] queda verdadero para reintentarlo en el próximo arranque. Un fallo de la base no se
 * propaga. Se lanza tras la siembra de los datos, en segundo plano.
 */
@Singleton
class MigradorTramosVia
    @Inject
    constructor(
        private val recorridos: RecorridosDao,
        private val tramos: TramosDao,
        private val subtramos: SubtramosDao,
        private val guardador: GuardadorTramosVia,
        private val indices: FuenteIndicesVias,
        @DispatcherIo private val dispatcher: CoroutineDispatcher,
    ) {
        /** Una pasada de la migración; devuelve lo hecho (también si la base falla: lo ya hecho queda guardado). */
        suspend fun migrar(): ResumenMigracionVias {
            var hechos = 0
            var situados = 0
            var pendiente = false
            try {
                for (r in withContext(dispatcher) { recorridos.recorridosSinTrocear() }) {
                    if (trocearUno(r.id) != ResultadoTroceado.HECHO) {
                        // Sin vías instaladas ninguno saldrá: se deja todo pendiente para el próximo arranque.
                        pendiente = true
                        break
                    }
                    hechos++
                }
                if (!pendiente) situados = situarSubtramos()
            } catch (e: SQLiteException) {
                Log.w(ETIQUETA, "Migración de tramos de vía interrumpida: ${e.javaClass.simpleName}")
                pendiente = true
            } catch (e: IllegalStateException) {
                Log.w(ETIQUETA, "Migración de tramos de vía interrumpida: ${e.javaClass.simpleName}")
                pendiente = true
            }
            return ResumenMigracionVias(hechos, situados, pendiente)
        }

        /** Trocea y marca un recorrido; sin puntos que trocear solo se marca, para no reintentarlo cada arranque. */
        private suspend fun trocearUno(id: Long): ResultadoTroceado {
            val puntos = withContext(dispatcher) { puntosDe(id) }
            if (puntos.size >= 2) return guardador.trocearYMarcar(id, puntos)
            withContext(dispatcher) { recorridos.marcarTroceado(id) }
            return ResultadoTroceado.HECHO
        }

        private suspend fun puntosDe(id: Long): List<PuntoRecorrido> =
            recorridos.puntos(id).map { PuntoRecorrido(it.lat, it.lon, it.tiempoMs, it.velocidadMs) }

        /** Sitúa los subtramos sin carretera cuyo trozo está casi entero en un único tramo de vía; devuelve cuántos. */
        private suspend fun situarSubtramos(): Int =
            withContext(dispatcher) {
                val datos = indices.indices()
                if (datos == null) 0 else subtramos.sinVia().count { situar(it, datos) }
            }

        private suspend fun situar(
            subtramo: SubtramoEntity,
            datos: IndicesDeVias,
        ): Boolean {
            val referencia = tramos.tramo(subtramo.tramoRefId)?.aTramo()
            val trozo =
                referencia?.let { RecorteTraza.subpolilinea(it.traza, subtramo.desdeM, subtramo.hastaM) }.orEmpty()
            val minimoM = (trozo.lastOrNull()?.distanciaM ?: 0.0) * FRACCION_EN_UNA_VIA
            val troceador = datos.troceador(minimoM)
            val unico = if (trozo.size < 2) null else troceador.trocear(trozo).singleOrNull()
            val idVia = unico?.idVia
            if (unico != null && idVia != null) {
                subtramos.situarEnVia(subtramo.id, idVia, unico.pkDesde, unico.pkHasta, unico.sentido.name)
            }
            return idVia != null
        }

        private companion object {
            const val ETIQUETA = "MigradorTramosVia"

            /** Parte del subtramo que debe quedar en una misma carretera y sentido para situarlo en ella. */
            const val FRACCION_EN_UNA_VIA = 0.8
        }
    }
