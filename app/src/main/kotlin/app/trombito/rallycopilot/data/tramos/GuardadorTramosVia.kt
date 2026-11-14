package app.trombito.rallycopilot.data.tramos

import android.database.sqlite.SQLiteException
import android.util.Log
import app.trombito.rallycopilot.data.db.RecorridosDao
import app.trombito.rallycopilot.data.db.TramosDao
import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.di.DispatcherIo
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.tramos.DatosVia
import app.trombito.rallycopilot.domain.tramos.OrigenPasada
import app.trombito.rallycopilot.domain.tramos.RecorteTraza
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.Cobertura
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.tramos.TramoDesdeRecorrido
import app.trombito.rallycopilot.motor.vias.TramoVia
import app.trombito.rallycopilot.motor.vias.nombreTramoVia
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
 * carretera y sentido. Si ya hay un tramo `VIA` de esa carretera y sentido que cubre más de [UMBRAL_COBERTURA] del
 * trozo y por el que el cronómetro de la repetición llega a meta, el trozo se añade como pasada suya; si no, es un
 * tramo `VIA` nuevo con su primera pasada y el nombre automático [nombreTramoVia]. El tiempo de cada pasada es relativo
 * al inicio del trozo.
 *
 * Es idempotente: un tramo que ya tiene una pasada del mismo recorrido (p. ej. la detectó la conducción en vivo) no
 * recibe otra. Se serializa con un candado para que dos recorridos no creen a la vez el mismo tramo. Nunca registra
 * coordenadas.
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

        /** Añade el trozo como pasada de un tramo `VIA` existente; falso si ninguno lo cubre o no se puede repetir. */
        private suspend fun anadirAExistente(
            recorridoId: Long,
            idVia: String,
            tramo: TramoVia,
            trozo: List<PuntoRecorrido>,
        ): Boolean {
            val geometria = trozo.map { PuntoGeo(it.lat, it.lon) }
            val candidatos =
                tramos.tramosViaDe(idVia, tramo.sentido.name).mapNotNull { fila ->
                    val guardado = fila.aTramo()
                    if (guardado != null && Cobertura.fraccion(geometria, guardado.traza) > UMBRAL_COBERTURA) {
                        fila.id to guardado
                    } else {
                        null
                    }
                }
            return candidatos.any { (id, guardado) -> anadirSiCabe(recorridoId, id, guardado, trozo) }
        }

        /** Verdadero si el tramo ya tenía la pasada de este recorrido o se le ha añadido; falso si no se repite. */
        private suspend fun anadirSiCabe(
            recorridoId: Long,
            tramoId: String,
            guardado: Tramo,
            trozo: List<PuntoRecorrido>,
        ): Boolean {
            // Ya tiene una pasada de este recorrido (la conducción en vivo o un troceado anterior): no se repite.
            val yaTiene = tramos.pasadas(tramoId).any { it.recorridoId == recorridoId }
            val repetida = if (yaTiene) null else RepeticionPasada.repetir(guardado, trozo)
            if (repetida != null) {
                val origen = OrigenPasada(repetida.desdeMs, recorridoId, repetida.desdeMs, repetida.hastaMs)
                tramos.anadirPasada(repetida.pasada.aEntidad(origen, guardado.cortesExactosM()))
            }
            return yaTiene || repetida != null
        }

        private suspend fun crearNuevo(
            recorridoId: Long,
            idVia: String,
            tramo: TramoVia,
            trozo: List<PuntoRecorrido>,
        ) {
            val nuevo = TramoDesdeRecorrido.crear(nuevoId(), trozo.map { PuntoGeo(it.lat, it.lon) }) ?: return
            val repetida = RepeticionPasada.repetir(nuevo, trozo) ?: return
            val via = DatosVia(idVia, tramo.pkDesde, tramo.pkHasta, tramo.sentido, tramo.destino, recorridoId)
            val origen = OrigenPasada(repetida.desdeMs, recorridoId, repetida.desdeMs, repetida.hastaMs)
            tramos.crear(
                nuevo.aEntidad(null, reloj(), nombreTramoVia(tramo), via),
                repetida.pasada.aEntidad(origen, nuevo.cortesExactosM()),
            )
        }

        companion object {
            /** Fracción del trozo sobre un tramo `VIA` guardado a partir de la cual se considera el mismo tramo. */
            const val UMBRAL_COBERTURA = 0.9
            private const val RADIO_DESTINO_M = 5_000.0
            private const val ETIQUETA = "GuardadorTramosVia"
        }
    }
