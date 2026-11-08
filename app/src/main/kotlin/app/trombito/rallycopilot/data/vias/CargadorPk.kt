package app.trombito.rallycopilot.data.vias

import android.content.Context
import android.util.Log
import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.di.DispatcherIo
import app.trombito.rallycopilot.domain.vias.ProveedorPuntoKilometrico
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.IndicePuntosKm
import app.trombito.rallycopilot.motor.vias.LectorPk
import app.trombito.rallycopilot.motor.vias.PuntoKilometrico
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lee `filesDir/mapa/pk-mallorca.tsv` (id `pk` del manifest, opcional) una sola vez y lo comparte; lo vuelve a leer
 * si el archivo cambia (fecha o tamaño; p. ej. tras actualizar los datos). Falta o ilegible = sin punto kilométrico,
 * sin error, y no se reintenta hasta que cambie. Antes de mirar el archivo espera a que termine la siembra de los
 * datos del APK.
 */
@Singleton
class CargadorPk internal constructor(
    private val archivo: File,
    private val dispatcher: CoroutineDispatcher,
    private val siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna,
) : ProveedorPuntoKilometrico {
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
        @DispatcherIo dispatcher: CoroutineDispatcher,
        siembra: EsperaDatosSembrados,
    ) : this(File(contexto.filesDir, RUTA_RELATIVA), dispatcher, siembra)

    private class Cargado(
        val modificado: Long,
        val tamano: Long,
        val indice: IndicePuntosKm?,
    )

    private val mutex = Mutex()
    private var cargado: Cargado? = null

    /** Veces que se ha leído el archivo de disco (para comprobar que se comparte la carga). */
    internal var lecturas: Int = 0
        private set

    override suspend fun puntoKilometrico(
        ref: String?,
        posicion: PuntoGeo,
    ): PuntoKilometrico? = indice()?.buscar(ref, posicion)

    private suspend fun indice(): IndicePuntosKm? {
        siembra.esperar()
        return mutex.withLock {
            withContext(dispatcher) {
                if (!archivo.isFile) {
                    cargado = null
                    return@withContext null
                }
                val modificado = archivo.lastModified()
                val tamano = archivo.length()
                val actual = cargado
                if (actual != null && actual.modificado == modificado && actual.tamano == tamano) {
                    actual.indice
                } else {
                    leer().also { cargado = Cargado(modificado, tamano, it) }
                }
            }
        }
    }

    private fun leer(): IndicePuntosKm? {
        lecturas++
        return try {
            val lectura = archivo.bufferedReader(Charsets.UTF_8).use { LectorPk.leer(it.lineSequence()) }
            Log.i(ETIQUETA, "Hitos kilométricos cargados: ${lectura.hitos.size} (descartados: ${lectura.descartadas})")
            IndicePuntosKm(lectura.hitos)
        } catch (e: IOException) {
            Log.w(ETIQUETA, "Archivo de hitos kilométricos ilegible: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(ETIQUETA, "Archivo de hitos kilométricos no válido: ${e.message}")
            null
        }
    }

    private companion object {
        const val ETIQUETA = "CargadorPk"
        const val RUTA_RELATIVA = "mapa/pk-mallorca.tsv"
    }
}
