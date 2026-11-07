package app.trombito.rallycopilot.data.alertas

import android.content.Context
import android.util.Log
import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.di.DispatcherIo
import app.trombito.rallycopilot.motor.alertas.LectorRadares
import app.trombito.rallycopilot.motor.alertas.Radar
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Da los radares de los datos instalados; lista vacía si no hay archivo de radares (o es ilegible). */
interface FuenteRadares {
    suspend fun radares(): List<Radar>
}

/**
 * Lee `filesDir/mapa/radares-mallorca.tsv` (id `radares` del manifest) una sola vez y lo comparte; lo vuelve a leer
 * si el archivo cambia (fecha o tamaño; p. ej. tras actualizar los datos). Falta o ilegible = sin radares, sin error, y
 * no se reintenta hasta que cambie. Antes de mirar el archivo espera a que termine la siembra de los datos del APK.
 */
@Singleton
class CargadorRadares internal constructor(
    private val archivo: File,
    private val dispatcher: CoroutineDispatcher,
    private val siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna,
) : FuenteRadares {
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
        @DispatcherIo dispatcher: CoroutineDispatcher,
        siembra: EsperaDatosSembrados,
    ) : this(File(contexto.filesDir, RUTA_RELATIVA), dispatcher, siembra)

    private class Cargado(
        val modificado: Long,
        val tamano: Long,
        val radares: List<Radar>,
    )

    private val mutex = Mutex()
    private var cargado: Cargado? = null

    /** Veces que se ha leído el archivo de disco (para comprobar que se comparte la carga). */
    internal var lecturas: Int = 0
        private set

    override suspend fun radares(): List<Radar> {
        siembra.esperar()
        return mutex.withLock {
            withContext(dispatcher) {
                if (!archivo.isFile) {
                    cargado = null
                    return@withContext emptyList()
                }
                val modificado = archivo.lastModified()
                val tamano = archivo.length()
                val actual = cargado
                if (actual != null && actual.modificado == modificado && actual.tamano == tamano) {
                    actual.radares
                } else {
                    leer().also { cargado = Cargado(modificado, tamano, it) }
                }
            }
        }
    }

    private fun leer(): List<Radar> {
        lecturas++
        return try {
            val lectura = archivo.bufferedReader(Charsets.UTF_8).use { LectorRadares.leer(it.lineSequence()) }
            Log.i(ETIQUETA, "Radares cargados: ${lectura.radares.size} (descartados: ${lectura.descartadas})")
            lectura.radares
        } catch (e: IOException) {
            Log.w(ETIQUETA, "Archivo de radares ilegible: ${e.javaClass.simpleName}")
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.w(ETIQUETA, "Archivo de radares no válido: ${e.message}")
            emptyList()
        }
    }

    private companion object {
        const val ETIQUETA = "CargadorRadares"
        const val RUTA_RELATIVA = "mapa/radares-mallorca.tsv"
    }
}
