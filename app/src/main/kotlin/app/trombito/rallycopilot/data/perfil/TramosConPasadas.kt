package app.trombito.rallycopilot.data.perfil

import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.analisis.PasadaRegistrada
import app.trombito.rallycopilot.motor.analisis.TramoRegistrado
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap

/** Un tramo solo tiene récords de verdad con dos pasadas o más (la primera no mejora nada). */
private const val PASADAS_PARA_RECORD = 2

/**
 * Lee las pasadas de los tramos con dos pasadas o más (lo único que puede dar récords) y las guarda en caché por id de
 * tramo mientras no cambie su «huella» (nº de pasadas y último uso), para que recalcular solo lea lo nuevo.
 */
internal class TramosConPasadas(
    private val tramos: RepositorioTramos,
) {
    /** Pasadas de un tramo con la «huella» (nº de pasadas y último uso) con que se leyeron. */
    private class PasadasLeidas(
        val huella: Pair<Int, Long>,
        val pasadas: List<PasadaRegistrada>,
    )

    private val cache = ConcurrentHashMap<String, PasadasLeidas>()

    suspend fun de(resumenes: List<ResumenTramo>): List<TramoRegistrado> {
        val candidatos = resumenes.filter { it.pasadas >= PASADAS_PARA_RECORD }
        cache.keys.retainAll(candidatos.map { it.id }.toSet())
        return candidatos.map { resumen ->
            TramoRegistrado(
                tramoId = resumen.id,
                nombre = resumen.nombre,
                // La distancia cronometrada llega a la meta, no al final de la traza.
                distanciaM = Tramo.metaDe(resumen.longitudM),
                pasadas = pasadasDe(resumen),
            )
        }
    }

    private suspend fun pasadasDe(resumen: ResumenTramo): List<PasadaRegistrada> {
        currentCoroutineContext().ensureActive()
        val huella = resumen.pasadas to resumen.ultimoUsoMs
        cache[resumen.id]?.takeIf { it.huella == huella }?.let { return it.pasadas }
        val leidas =
            tramos
                .pasadasDetalladas(resumen.id)
                .map { PasadaRegistrada(it.id.toString(), it.pasada, it.inicioMs) }
        cache[resumen.id] = PasadasLeidas(huella, leidas)
        return leidas
    }
}
