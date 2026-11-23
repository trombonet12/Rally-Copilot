package app.trombito.rallycopilot.data.tramos

import android.database.sqlite.SQLiteException
import android.util.Log
import app.trombito.rallycopilot.data.db.RecorridoEntity
import app.trombito.rallycopilot.data.db.RecorridosDao
import app.trombito.rallycopilot.data.db.SubtramoEntity
import app.trombito.rallycopilot.data.db.SubtramosDao
import app.trombito.rallycopilot.data.db.TramosDao
import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.di.DispatcherIo
import app.trombito.rallycopilot.domain.tramos.RecorteTraza
import app.trombito.rallycopilot.motor.vias.nombreTramoViaConDestino
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
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
 * recorrido terminado y cronometrado que aún no lo está ([GuardadorTramosVia.trocearYMarcar]; los no cronometrados
 * solo se marcan, y un fallo del motor en un recorrido lo marca sin propagarse) y sitúa en su carretera los subtramos
 * que caen en un solo tramo de vía. Los tramos-viaje, sus pasadas y los subtramos no se borran ni se modifican (los
 * subtramos solo ganan carretera, kilómetros y sentido; los que no caen en una sola carretera se marcan como
 * intentados y no se reintentan).
 *
 * Es idempotente y reanudable: cada recorrido se marca al acabar y un tramo no recibe dos veces la pasada del mismo
 * recorrido, así que repetirlo (o continuar tras cortarlo) deja el mismo resultado. Sin vías instaladas no hace nada y
 * [ResumenMigracionVias.pendiente] queda verdadero para reintentarlo en el próximo arranque. Un fallo de la base no se
 * propaga. Se lanza tras la siembra de los datos, en segundo plano.
 */
@Singleton
@Suppress("LongParameterList") // los DAO, el guardador y las fuentes de vías y lugares
class MigradorTramosVia
    @Inject
    constructor(
        private val recorridos: RecorridosDao,
        private val tramos: TramosDao,
        private val subtramos: SubtramosDao,
        private val guardador: GuardadorTramosVia,
        private val indices: FuenteIndicesVias,
        private val lugares: LugaresCercanos,
        @DispatcherIo private val dispatcher: CoroutineDispatcher,
    ) {
        /** Una pasada de la migración; devuelve lo hecho (también si la base falla: lo ya hecho queda guardado). */
        @Suppress("TooGenericExceptionCaught") // ver el último catch: el arranque no puede caerse
        suspend fun migrar(): ResumenMigracionVias {
            var hechos = 0
            var situados = 0
            var pendiente = false
            try {
                val cronometrados = CronometradosPorRecorrido()
                for (r in withContext(dispatcher) { recorridos.recorridosSinTrocear() }) {
                    val resultado = migrarRecorrido(r, cronometrados)
                    // Sin vías instaladas ninguno saldrá: se deja todo pendiente para el próximo arranque.
                    pendiente = resultado == ResultadoTroceado.SIN_DATOS
                    if (pendiente) break
                    if (resultado == ResultadoTroceado.HECHO) hechos++
                }
                if (!pendiente) situados = situarSubtramos()
                if (!pendiente) ponerDestinos()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLiteException) {
                Log.w(ETIQUETA, "Migración de tramos de vía interrumpida: ${e.javaClass.simpleName}")
                pendiente = true
            } catch (e: IllegalStateException) {
                Log.w(ETIQUETA, "Migración de tramos de vía interrumpida: ${e.javaClass.simpleName}")
                pendiente = true
            } catch (e: RuntimeException) {
                // Nunca debe tumbar el arranque: lo que falle fuera de un recorrido (p. ej. situar subtramos).
                Log.w(ETIQUETA, "Migración de tramos de vía interrumpida: ${e.javaClass.simpleName}")
                pendiente = true
            }
            return ResumenMigracionVias(hechos, situados, pendiente)
        }

        /** Trocea un recorrido cronometrado; los demás solo se marcan. Null si no se troceó (marcado, sin tramos). */
        private suspend fun migrarRecorrido(
            recorrido: RecorridoEntity,
            cronometrados: CronometradosPorRecorrido,
        ): ResultadoTroceado? {
            if (cronometrados.incluye(recorrido)) return trocearSinPropagar(recorrido.id)
            // Sin cronometrar (opción desactivada o reconocimiento): no se trocea, pero se marca.
            withContext(dispatcher) { recorridos.marcarTroceado(recorrido.id) }
            return null
        }

        /**
         * Trocea un recorrido. Un fallo del motor (`require`, índice fuera de rango) no debe tumbar la app ni repetirse
         * en cada arranque: se registra y se marca el recorrido como troceado (devuelve null). Un fallo de la base o
         * la cancelación sí se propagan.
         */
        @Suppress("TooGenericExceptionCaught") // un recorrido raro no puede impedir arrancar ni migrar los demás
        private suspend fun trocearSinPropagar(id: Long): ResultadoTroceado? =
            try {
                trocearUno(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLiteException) {
                throw e
            } catch (e: RuntimeException) {
                Log.w(ETIQUETA, "Recorrido sin trocear por un fallo del motor: ${e.javaClass.simpleName}")
                withContext(dispatcher) { recorridos.marcarTroceado(id) }
                null
            }

        /**
         * Qué recorridos se cronometraron: los que tienen una pasada guardada (por su id o, en las pasadas antiguas sin
         * id de recorrido, por caer su salida dentro del recorrido) o algún subtramo. Se carga una vez, al pedirlo.
         */
        private inner class CronometradosPorRecorrido {
            private val idsConPasada = HashSet<Long>()
            private val iniciosDePasada = ArrayList<Long>()
            private var cargado = false

            suspend fun incluye(recorrido: RecorridoEntity): Boolean =
                withContext(dispatcher) {
                    if (!cargado) cargar()
                    val fin = recorrido.finMs ?: Long.MAX_VALUE
                    recorrido.id in idsConPasada ||
                        iniciosDePasada.any { it in recorrido.inicioMs..fin } ||
                        subtramos.observarDeRecorrido(recorrido.id).first().isNotEmpty()
                }

            private suspend fun cargar() {
                for (t in tramos.tramos()) {
                    for (p in tramos.pasadas(t.id)) {
                        p.recorridoId?.let { idsConPasada += it }
                        iniciosDePasada += p.inicio
                    }
                }
                cargado = true
            }
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
            } else {
                // Sin una carretera única: queda intentado y no se vuelve a mirar en cada arranque.
                subtramos.marcarNoSituable(subtramo.id)
            }
            return idVia != null
        }

        /**
         * Pone hacia dónde va («hacia Manacor» o «hacia el norte») a los tramos de vía que no lo saben y se lo añade
         * a su nombre automático. Nunca toca el nombre que puso el usuario ni un destino ya guardado; es idempotente.
         */
        private suspend fun ponerDestinos() {
            withContext(dispatcher) {
                for (fila in tramos.tramosViaSinDestino()) {
                    val traza = fila.aTramo()?.traza.orEmpty()
                    if (traza.size < 2) continue
                    val destino = lugares.destinoDe(traza.first(), traza.last())
                    val nombre = fila.nombreAutomatico?.let { nombreTramoViaConDestino(it, destino) }
                    tramos.ponerDestinoVia(fila.id, destino, nombre)
                }
            }
        }

        private companion object {
            const val ETIQUETA = "MigradorTramosVia"

            /** Parte del subtramo que debe quedar en una misma carretera y sentido para situarlo en ella. */
            const val FRACCION_EN_UNA_VIA = 0.8
        }
    }
