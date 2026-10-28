package app.trombito.rallycopilot.data.vias

import android.content.Context
import android.util.Log
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.LectorVias
import app.trombito.rallycopilot.motor.vias.LimiteVelocidad
import app.trombito.rallycopilot.motor.vias.SeguidorVia
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
 * Vía y límite de velocidad a partir de `filesDir/mapa/vias-mallorca.tsv.gz` (paquete de datos). Carga perezosa en
 * [dispatcher] la primera vez que se pide y de nuevo si el archivo cambia (fecha o tamaño), p. ej. tras actualizar los
 * datos. Si falta o es ilegible, [viaEn] devuelve null (y no se reintenta hasta que el archivo cambie).
 */
@Singleton
class ProveedorViaActualOsm internal constructor(
    private val archivo: File,
    private val dispatcher: CoroutineDispatcher,
) : ProveedorViaActual {
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
    ) : this(File(contexto.filesDir, RUTA_RELATIVA), Dispatchers.IO)

    private class Cargado(
        val modificado: Long,
        val tamano: Long,
        val seguidor: SeguidorVia?,
    )

    private val mutex = Mutex()
    private var cargado: Cargado? = null

    override suspend fun viaEn(fijacion: Fijacion): ViaActual? =
        mutex.withLock {
            val seguidor = seguidorActual() ?: return@withLock null
            val coincidencia = seguidor.actualizar(fijacion.punto, fijacion.rumboGrados) ?: return@withLock null
            val via = coincidencia.via
            ViaActual(via.nombre, via.ref, LimiteVelocidad.aplicable(via, coincidencia.adelante))
        }

    private suspend fun seguidorActual(): SeguidorVia? =
        withContext(dispatcher) {
            if (!archivo.isFile) {
                cargado = null
                return@withContext null
            }
            val modificado = archivo.lastModified()
            val tamano = archivo.length()
            val actual = cargado
            if (actual != null && actual.modificado == modificado && actual.tamano == tamano) {
                actual.seguidor
            } else {
                val seguidor = cargar()
                cargado = Cargado(modificado, tamano, seguidor)
                seguidor
            }
        }

    private fun cargar(): SeguidorVia? =
        try {
            val lectura =
                GZIPInputStream(archivo.inputStream().buffered()).bufferedReader(Charsets.UTF_8).use {
                    LectorVias.leer(it.lineSequence())
                }
            Log.i(ETIQUETA, "Vías cargadas: ${lectura.vias.size} (descartadas: ${lectura.descartadas})")
            SeguidorVia(IndiceVias(lectura.vias))
        } catch (e: IOException) {
            Log.w(ETIQUETA, "Archivo de vías ilegible: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(ETIQUETA, "Archivo de vías no válido: ${e.message}")
            null
        }

    private companion object {
        const val ETIQUETA = "ProveedorViaActualOsm"
        const val RUTA_RELATIVA = "mapa/vias-mallorca.tsv.gz"
    }
}
