package app.trombito.rallycopilot.data.notas

import android.content.Context
import android.util.Log
import app.trombito.rallycopilot.motor.notas.ContextoVias
import app.trombito.rallycopilot.motor.vias.DatosNotasVias
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.LectorVias
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
 * Carga perezosa, una sola vez y en [dispatcher], de `filesDir/mapa/vias-mallorca.tsv.gz` y, si existe,
 * `vias-notas-mallorca.tsv.gz`. Si alguno cambia (fecha o tamaño; p. ej. al actualizar los datos) se recarga en la
 * siguiente petición. Los archivos ilegibles se tratan como ausentes y no se reintentan hasta que cambien.
 */
@Singleton
class ContextoNotasRepositorio internal constructor(
    private val archivoVias: File,
    private val archivoNotas: File,
    private val dispatcher: CoroutineDispatcher,
) : FuenteContextoNotas {
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
    ) : this(
        File(contexto.filesDir, RUTA_VIAS),
        File(contexto.filesDir, RUTA_VIAS_NOTAS),
        Dispatchers.IO,
    )

    private data class Firma(
        val vias: Pair<Long, Long>,
        val notas: Pair<Long, Long>?,
    )

    private class Cargado(
        val firma: Firma,
        val contexto: ContextoNotas?,
    )

    private val mutex = Mutex()
    private var cargado: Cargado? = null

    override suspend fun contexto(): ContextoNotas? =
        mutex.withLock {
            withContext(dispatcher) {
                if (!archivoVias.isFile) {
                    cargado = null
                    return@withContext null
                }
                val firma =
                    Firma(
                        archivoVias.lastModified() to archivoVias.length(),
                        archivoNotas.takeIf { it.isFile }?.let { it.lastModified() to it.length() },
                    )
                val actual = cargado
                if (actual != null && actual.firma == firma) {
                    actual.contexto
                } else {
                    val contexto = cargar()
                    cargado = Cargado(firma, contexto)
                    contexto
                }
            }
        }

    private fun cargar(): ContextoNotas? {
        val lectura = leer(archivoVias) { LectorVias.leer(it) } ?: return null
        val indice = IndiceVias(lectura.vias)
        val grafo = GrafoVias(lectura.vias)
        val datosNotas: DatosNotasVias? =
            archivoNotas.takeIf { it.isFile }?.let { archivo -> leer(archivo) { LectorViasNotas.leer(it) } }
        Log.i(ETIQUETA, "Contexto de notas: ${lectura.vias.size} vías, datos de notas: ${datosNotas != null}")
        return ContextoNotas(indice, grafo, datosNotas?.let { ContextoVias(indice, grafo, it) })
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
        const val RUTA_VIAS = "mapa/vias-mallorca.tsv.gz"
        const val RUTA_VIAS_NOTAS = "mapa/vias-notas-mallorca.tsv.gz"
    }
}
