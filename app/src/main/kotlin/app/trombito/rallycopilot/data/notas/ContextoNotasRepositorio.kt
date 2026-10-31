package app.trombito.rallycopilot.data.notas

import android.content.Context
import android.util.Log
import app.trombito.rallycopilot.data.vias.DatosVias
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.motor.notas.ContextoVias
import app.trombito.rallycopilot.motor.vias.DatosNotasVias
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.LectorViasNotas
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Datos de las vías para generar notas: [indice] y [grafo] siempre; [contextoVias] solo si `vias-notas` está instalado
 * (sin él la hoja sale solo con curvas).
 */
class ContextoNotas(
    val indice: IndiceVias,
    val grafo: GrafoVias,
    val contextoVias: ContextoVias?,
)

/** Da el [ContextoNotas] de los datos instalados, o null si no hay vías. */
interface FuenteContextoNotas {
    suspend fun contexto(): ContextoNotas?
}

/**
 * Contexto de notas sobre las vías que da [fuente] (una única lectura compartida con el seguimiento de la vía actual)
 * y, si existe, `vias-notas-mallorca.tsv.gz`, cargado perezosamente en [dispatcher]. Si cambian las vías o el archivo
 * de notas (fecha o tamaño; p. ej. al actualizar los datos) se rehace en la siguiente petición. Un archivo de notas
 * ilegible se trata como ausente y no se reintenta hasta que cambie.
 */
@Singleton
class ContextoNotasRepositorio internal constructor(
    private val fuente: FuenteVias,
    private val archivoNotas: File,
    private val dispatcher: CoroutineDispatcher,
) : FuenteContextoNotas {
    @Inject
    constructor(
        fuente: FuenteVias,
        @ApplicationContext contexto: Context,
    ) : this(fuente, File(contexto.filesDir, RUTA_VIAS_NOTAS), Dispatchers.IO)

    private class Cargado(
        val vias: DatosVias,
        val notas: Pair<Long, Long>?,
        val contexto: ContextoNotas,
    )

    private val mutex = Mutex()
    private var cargado: Cargado? = null

    override suspend fun contexto(): ContextoNotas? =
        mutex.withLock {
            val vias = fuente.datos()
            if (vias == null) {
                cargado = null
                return@withLock null
            }
            withContext(dispatcher) {
                val notas = archivoNotas.takeIf { it.isFile }?.let { it.lastModified() to it.length() }
                val actual = cargado
                if (actual != null && actual.vias === vias && actual.notas == notas) {
                    actual.contexto
                } else {
                    val contexto = cargar(vias)
                    cargado = Cargado(vias, notas, contexto)
                    contexto
                }
            }
        }

    private fun cargar(vias: DatosVias): ContextoNotas {
        val grafo = GrafoVias(vias.vias)
        val datosNotas: DatosNotasVias? =
            archivoNotas.takeIf { it.isFile }?.let { archivo -> leer(archivo) { LectorViasNotas.leer(it) } }
        Log.i(ETIQUETA, "Contexto de notas: ${vias.vias.size} vías, datos de notas: ${datosNotas != null}")
        return ContextoNotas(vias.indice, grafo, datosNotas?.let { ContextoVias(vias.indice, grafo, it) })
    }

    private fun <T> leer(
        archivo: File,
        lector: (Sequence<String>) -> T,
    ): T? =
        try {
            GZIPInputStream(archivo.inputStream().buffered()).bufferedReader(Charsets.UTF_8).use {
                lector(it.lineSequence())
            }
        } catch (e: IOException) {
            Log.w(ETIQUETA, "Archivo ilegible (${archivo.name}): ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(ETIQUETA, "Archivo no válido (${archivo.name}): ${e.message}")
            null
        }

    private companion object {
        const val ETIQUETA = "ContextoNotas"
        const val RUTA_VIAS_NOTAS = "mapa/vias-notas-mallorca.tsv.gz"
    }
}
