package app.trombito.rallycopilot.data.detalle

import app.trombito.rallycopilot.motor.precarga.Tesela
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException

/** Cómo acabó una pasada del [DescargadorDetalle]. */
sealed interface ResultadoDetalle {
    /** Todo lo pedido ya está resuelto ([descargadas] nuevas, [sinCobertura] de mar anotadas). */
    data class Completo(
        val descargadas: Int,
        val sinCobertura: Int,
    ) : ResultadoDetalle

    /** Se llegó al límite de espacio; lo bajado hasta entonces se conserva. */
    data class LimiteAlcanzado(
        val descargadas: Int,
    ) : ResultadoDetalle
}

/**
 * Baja las teselas que faltan en [almacen] con [simultaneas] peticiones a la vez y las guarda por lotes de [lote]. No
 * repite las ya resueltas (guardadas o de mar), por lo que se puede relanzar: reanuda donde se quedó. Antes de cada
 * lote comprueba el espacio: [bytesFuera] (lo demás que cuenta contra el límite) más el archivo no puede llegar a
 * [limiteBytes]; el último lote puede pasarse unos cientos de KB. Un fallo de red tras los reintentos de la fuente
 * guarda lo ya bajado del lote y se propaga como [IOException].
 */
class DescargadorDetalle(
    private val almacen: AlmacenDetalle,
    private val fuente: FuenteTeselas,
    private val bytesFuera: () -> Long,
    private val limiteBytes: () -> Long,
    private val simultaneas: Int = SIMULTANEAS,
    private val lote: Int = LOTE,
) {
    suspend fun descargar(
        teselas: Collection<Tesela>,
        alProgreso: suspend (hechas: Int, totales: Int) -> Unit = { _, _ -> },
    ): ResultadoDetalle {
        val resueltas = almacen.resueltas()
        val pendientes = teselas.distinct().filter { AlmacenDetalle.clave(it) !in resueltas }
        var hechas = 0
        var descargadas = 0
        var sinCobertura = 0
        val cupo = Semaphore(simultaneas)
        alProgreso(0, pendientes.size)
        for (trozo in pendientes.chunked(lote)) {
            if (bytesFuera() + almacen.bytes() >= limiteBytes()) return ResultadoDetalle.LimiteAlcanzado(descargadas)
            val (conDatos, mar, error) = bajarLote(trozo, cupo)
            almacen.guardar(conDatos, mar)
            descargadas += conDatos.size
            sinCobertura += mar.size
            hechas += conDatos.size + mar.size
            alProgreso(hechas, pendientes.size)
            if (error != null) throw error
        }
        return ResultadoDetalle.Completo(descargadas, sinCobertura)
    }

    private class Lote(
        val conDatos: Map<Tesela, ByteArray>,
        val mar: List<Tesela>,
        val error: IOException?,
    ) {
        operator fun component1() = conDatos

        operator fun component2() = mar

        operator fun component3() = error
    }

    private suspend fun bajarLote(
        trozo: List<Tesela>,
        cupo: Semaphore,
    ): Lote =
        coroutineScope {
            val respuestas =
                trozo
                    .map { t ->
                        async {
                            try {
                                t to cupo.withPermit { fuente.obtener(t) }
                            } catch (e: IOException) {
                                t to e
                            }
                        }
                    }.awaitAll()
            val conDatos = LinkedHashMap<Tesela, ByteArray>()
            val mar = ArrayList<Tesela>()
            var error: IOException? = null
            for ((t, r) in respuestas) {
                when (r) {
                    is ResultadoTesela.Jpeg -> conDatos[t] = r.bytes
                    ResultadoTesela.SinCobertura -> mar += t
                    is IOException -> error = error ?: r
                }
            }
            Lote(conDatos, mar, error)
        }

    companion object {
        const val SIMULTANEAS = 4
        const val LOTE = 32
    }
}
