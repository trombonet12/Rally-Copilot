package app.trombito.rallycopilot.data.tramos

import android.database.sqlite.SQLiteException
import android.util.Log
import app.trombito.rallycopilot.data.db.PasadaEntity
import app.trombito.rallycopilot.data.db.RecorridosDao
import app.trombito.rallycopilot.data.db.TramoEntity
import app.trombito.rallycopilot.data.db.TramosDao
import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.di.DispatcherIo
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.tramos.DatosVia
import app.trombito.rallycopilot.domain.tramos.OrigenPasada
import app.trombito.rallycopilot.domain.tramos.RecorteTraza
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.tramos.TramoDesdeRecorrido
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.motor.vias.TramoVia
import app.trombito.rallycopilot.motor.vias.nombreTramoVia
import app.trombito.rallycopilot.motor.vias.solapeTramosVia
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Cómo acabó trocear un recorrido. */
enum class ResultadoTroceado {
    /** Troceado y guardado (aunque no saliera ningún tramo): se puede marcar como hecho. */
    HECHO,

    /** No hay vías instaladas: no se hizo nada y conviene reintentarlo cuando las haya. */
    SIN_DATOS,
}

/**
 * Trocea un viaje en tramos de vía (`TroceadorVias`) y los guarda: cada trozo de al menos un kilómetro de una
 * carretera y sentido. Si ya hay tramos `VIA` de esa carretera y sentido que solapan al menos [UMBRAL_SOLAPE] con el
 * trozo, no se crea otro: el trozo se añade como pasada de cada uno por el que el cronómetro de la repetición llega a
 * meta; si no hay ninguno, es un tramo `VIA` nuevo con su primera pasada y el nombre automático [nombreTramoVia]. El
 * tiempo de cada pasada es relativo al inicio del trozo.
 *
 * Es idempotente: un tramo que ya tiene esa pasada del recorrido (p. ej. la detectó la conducción en vivo, mismo
 * intervalo de puntos) no recibe otra; si se pasó dos veces por el trozo, se guardan las dos pasadas. Lo que no se
 * puede guardar (trozo que no cruza la salida y la meta) se registra con su motivo. Se serializa con un candado para
 * que dos recorridos no creen a la vez el mismo tramo. Nunca registra coordenadas.
 */
@Singleton
@Suppress("LongParameterList") // los colaboradores de guardado más el reloj y el generador de ids de las pruebas
class GuardadorTramosVia
    internal constructor(
        private val tramos: TramosDao,
        private val recorridos: RecorridosDao,
        private val indices: FuenteIndicesVias,
        private val buscador: Buscador,
        private val dispatcher: CoroutineDispatcher,
        private val nuevoId: () -> String,
        private val reloj: () -> Long,
    ) {
        @Inject
        constructor(
            tramos: TramosDao,
            recorridos: RecorridosDao,
            indices: FuenteIndicesVias,
            buscador: Buscador,
            @DispatcherIo dispatcher: CoroutineDispatcher,
        ) : this(
            tramos,
            recorridos,
            indices,
            buscador,
            dispatcher,
            { UUID.randomUUID().toString() },
            System::currentTimeMillis,
        )

        private val candado = Mutex()

        /** Trocea [puntos] (los del recorrido [recorridoId], en orden) y guarda lo que sale; no marca el recorrido. */
        suspend fun trocear(
            recorridoId: Long,
            puntos: List<PuntoRecorrido>,
        ): ResultadoTroceado =
            candado.withLock {
                withContext(dispatcher) {
                    val datos = indices.indices() ?: return@withContext ResultadoTroceado.SIN_DATOS
                    val traza = RecorteTraza.conDistancias(puntos.map { PuntoGeo(it.lat, it.lon) })
                    for (tramo in datos.troceador().trocear(traza)) {
                        val trozo = puntos.filterIndexed { i, _ -> traza[i].distanciaM in tramo.desdeM..tramo.hastaM }
                        guardarTramo(recorridoId, tramo, trozo)
                    }
                    ResultadoTroceado.HECHO
                }
            }

        /** Como [trocear] y, si acaba bien, marca el recorrido como troceado. */
        suspend fun trocearYMarcar(
            recorridoId: Long,
            puntos: List<PuntoRecorrido>,
        ): ResultadoTroceado {
            val resultado = trocear(recorridoId, puntos)
            if (resultado == ResultadoTroceado.HECHO) withContext(dispatcher) { recorridos.marcarTroceado(recorridoId) }
            return resultado
        }

        private suspend fun guardarTramo(
            recorridoId: Long,
            tramo: TramoVia,
            trozo: List<PuntoRecorrido>,
        ) {
            val idVia = tramo.idVia
            if (idVia == null || trozo.size < 2) return
            try {
                val conDestino = tramo.copy(destino = destinoDe(tramo))
                if (!anadirAExistente(recorridoId, idVia, conDestino, trozo)) {
                    crearNuevo(recorridoId, idVia, conDestino, trozo)
                }
            } catch (e: IllegalArgumentException) {
                Log.w(ETIQUETA, "Trozo de vía descartado: ${e.javaClass.simpleName}")
            }
        }

        /** El lugar más cercano al final del trozo (el destino); null si no hay índice o ninguno está cerca. */
        private suspend fun destinoDe(tramo: TramoVia): String? {
            val final = tramo.trazado.lastOrNull() ?: return null
            return try {
                buscador
                    .lugarMasCercano(final, RADIO_DESTINO_M)
                    ?.nombre
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            } catch (_: SQLiteException) {
                null
            }
        }

        /**
         * Si ya hay tramos `VIA` de esta carretera y sentido que solapan al menos [UMBRAL_SOLAPE] con el trozo, no se
         * crea otro: el trozo se añade como pasada de cada uno que cruce entero (el cronómetro de la repetición recorta
         * la pasada a su salida y meta). Devuelve verdadero si había alguno que solapara, se añadiera pasada o no.
         */
        private suspend fun anadirAExistente(
            recorridoId: Long,
            idVia: String,
            tramo: TramoVia,
            trozo: List<PuntoRecorrido>,
        ): Boolean {
            val solapados =
                tramos.tramosViaDe(idVia, tramo.sentido.name).mapNotNull { fila ->
                    val guardado = fila.aTramo()
                    if (guardado != null && solapeTramosVia(tramo, fila.comoTramoVia(guardado)) >= UMBRAL_SOLAPE) {
                        fila.id to guardado
                    } else {
                        null
                    }
                }
            for ((id, guardado) in solapados) anadirSiCabe(recorridoId, id, guardado, trozo)
            return solapados.isNotEmpty()
        }

        /** El tramo `VIA` guardado como [TramoVia], con lo justo para medir el solape con otro. */
        private fun TramoEntity.comoTramoVia(guardado: Tramo) =
            TramoVia(
                ref = viaRef,
                nombreVia = null,
                desdeM = 0.0,
                hastaM = guardado.longitudM,
                pkDesde = pkDesde,
                pkHasta = pkHasta,
                sentido = SentidoVia.entries.firstOrNull { it.name == sentido } ?: SentidoVia.DESCONOCIDO,
                destino = null,
                trazado = guardado.traza,
            )

        /**
         * Verdadero si el tramo ya tenía esta pasada o se le ha añadido; falso si el trozo no cruza su salida y su meta
         * (y entonces se registra el motivo: el trozo no se pierde en silencio). Una pasada es la misma si es del mismo
         * recorrido y ocupa casi el mismo intervalo de sus puntos (la conducción en vivo o un troceado anterior ya la
         * guardaron); otra pasada del mismo recorrido por el mismo trozo (se pasó dos veces) sí se guarda.
         */
        private suspend fun anadirSiCabe(
            recorridoId: Long,
            tramoId: String,
            guardado: Tramo,
            trozo: List<PuntoRecorrido>,
        ): Boolean {
            val repetida = RepeticionPasada.repetir(guardado, trozo)
            if (repetida == null) {
                Log.w(ETIQUETA, "Trozo de vía sin añadir a un tramo guardado: no cruza su salida y su meta")
                return false
            }
            val yaGuardada =
                tramos.pasadas(tramoId).any { it.recorridoId == recorridoId && it.esLaMisma(repetida) }
            if (!yaGuardada) {
                val origen = OrigenPasada(repetida.desdeMs, recorridoId, repetida.desdeMs, repetida.hastaMs)
                tramos.anadirPasada(repetida.pasada.aEntidad(origen, guardado.cortesExactosM()))
            }
            return true
        }

        /** Misma pasada: intervalos de puntos que solapan la mitad del más corto (sin intervalo, siempre la misma). */
        private fun PasadaEntity.esLaMisma(otra: PasadaRepetida): Boolean {
            val desde = puntosDesdeMs
            val hasta = puntosHastaMs
            if (desde == null || hasta == null) return true // pasada antigua sin intervalo: una por recorrido
            val comun = minOf(hasta, otra.hastaMs) - maxOf(desde, otra.desdeMs)
            val corto = minOf(hasta - desde, otra.hastaMs - otra.desdeMs)
            return comun > 0 && comun >= corto * UMBRAL_SOLAPE
        }

        private suspend fun crearNuevo(
            recorridoId: Long,
            idVia: String,
            tramo: TramoVia,
            trozo: List<PuntoRecorrido>,
        ) {
            val nuevo = TramoDesdeRecorrido.crear(nuevoId(), trozo.map { PuntoGeo(it.lat, it.lon) })
            if (nuevo == null) {
                Log.w(ETIQUETA, "Trozo de vía sin guardar: menos de un kilómetro útil tras limpiar la traza")
                return
            }
            val repetida = RepeticionPasada.repetir(nuevo, trozo)
            if (repetida == null) {
                Log.w(ETIQUETA, "Trozo de vía sin guardar: su pasada no cruza la salida y la meta del tramo nuevo")
                return
            }
            val via = DatosVia(idVia, tramo.pkDesde, tramo.pkHasta, tramo.sentido, tramo.destino, recorridoId)
            val origen = OrigenPasada(repetida.desdeMs, recorridoId, repetida.desdeMs, repetida.hastaMs)
            tramos.crear(
                nuevo.aEntidad(null, reloj(), nombreTramoVia(tramo), via),
                repetida.pasada.aEntidad(origen, nuevo.cortesExactosM()),
            )
        }

        companion object {
            /** Solape (respecto al más corto) con un tramo `VIA` guardado a partir del cual es el mismo tramo. */
            const val UMBRAL_SOLAPE = 0.5
            private const val RADIO_DESTINO_M = 5_000.0
            private const val ETIQUETA = "GuardadorTramosVia"
        }
    }
