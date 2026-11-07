package app.trombito.rallycopilot.data.vias

import android.content.Context
import android.util.Log
import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.LectorVias
import app.trombito.rallycopilot.motor.vias.Via
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
 * Vías de `vias-mallorca.tsv.gz` ya leídas, con su [indice] espacial y su [grafo] de nodos (se construye al crear los
 * datos, en el hilo de carga). Inmutable y compartido por quien lo pida.
 */
class DatosVias(
    val vias: List<Via>,
    val indice: IndiceVias,
    val grafo: GrafoVias = GrafoVias(vias),
)

/** Da las [DatosVias] de los datos instalados, o null si no hay vías (ausentes o ilegibles). */
interface FuenteVias {
    suspend fun datos(): DatosVias?

    /**
     * Las vías ya cargadas, sin tocar el disco ni cambiar de hilo (null si aún no se cargaron o no hay). Para el uso
     * por fijación; [datos] revalida el archivo y se llama al empezar una conducción (REN-16).
     */
    fun enMemoria(): DatosVias? = null
}

/**
 * Única lectura en memoria de `filesDir/mapa/vias-mallorca.tsv.gz`, compartida por el seguimiento de la vía actual y
 * la generación de notas. Carga perezosa en [dispatcher] la primera vez que se pide y de nuevo si el archivo cambia
 * (fecha o tamaño), p. ej. tras actualizar los datos. Si falta o es ilegible devuelve null y no se reintenta hasta
 * que el archivo cambie. Antes de mirar el archivo espera a que termine la siembra de los datos del APK.
 */
@Singleton
class CargadorVias internal constructor(
    private val archivo: File,
    private val dispatcher: CoroutineDispatcher,
    private val siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna,
) : FuenteVias {
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
        siembra: EsperaDatosSembrados,
    ) : this(File(contexto.filesDir, RUTA_RELATIVA), Dispatchers.IO, siembra)

    private class Cargado(
        val modificado: Long,
        val tamano: Long,
        val datos: DatosVias?,
    )

    private val mutex = Mutex()

    @Volatile
    private var cargado: Cargado? = null

    /** Veces que se ha leído el archivo de disco (para comprobar que se comparte la carga). */
    internal var lecturas: Int = 0
        private set

    override suspend fun datos(): DatosVias? {
        siembra.esperar()
        return leerDatos()
    }

    override fun enMemoria(): DatosVias? = cargado?.datos

    private suspend fun leerDatos(): DatosVias? =
        mutex.withLock {
            withContext(dispatcher) {
                if (!archivo.isFile) {
                    cargado = null
                    return@withContext null
                }
                val modificado = archivo.lastModified()
                val tamano = archivo.length()
                val actual = cargado
                if (actual != null && actual.modificado == modificado && actual.tamano == tamano) {
                    actual.datos
                } else {
                    val datos = cargar()
                    cargado = Cargado(modificado, tamano, datos)
                    datos
                }
            }
        }

    private fun cargar(): DatosVias? {
        lecturas++
        return try {
            val lectura =
                GZIPInputStream(archivo.inputStream().buffered()).bufferedReader(Charsets.UTF_8).use {
                    LectorVias.leer(it.lineSequence())
                }
            Log.i(ETIQUETA, "Vías cargadas: ${lectura.vias.size} (descartadas: ${lectura.descartadas})")
            DatosVias(lectura.vias, IndiceVias(lectura.vias))
        } catch (e: IOException) {
            Log.w(ETIQUETA, "Archivo de vías ilegible: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(ETIQUETA, "Archivo de vías no válido: ${e.message}")
            null
        }
    }

    private companion object {
        const val ETIQUETA = "CargadorVias"
        const val RUTA_RELATIVA = "mapa/vias-mallorca.tsv.gz"
    }
}
