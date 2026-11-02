package app.trombito.rallycopilot.data.escucha

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

/**
 * Copia de `assets/asr/vosk-model` a `<filesDir>/asr/vosk-model`, una sola vez: Vosk lee el modelo por ruta. Lleva un
 * marcador con la versión y solo se vuelve a copiar si falta o cambia; la copia es atómica (carpeta temporal y
 * renombrado), así que un corte a medias nunca deja un modelo incompleto.
 */
object CopiaModeloVosk {
    /** Subir al cambiar el modelo del build. */
    const val VERSION = "vosk-model-small-es-0.42-1"
    private const val ORIGEN = "asr/vosk-model"
    private const val MARCADOR = ".version"
    private const val TAG = "CopiaModeloVosk"

    /** Devuelve la carpeta del modelo lista, copiándola si hace falta. Lanza [IOException] si falla. */
    @Synchronized
    fun asegurar(
        assets: AssetManager,
        destinoBase: File,
    ): File {
        val destino = File(destinoBase, "vosk-model")
        if (File(destino, MARCADOR).takeIf { it.isFile }?.readText() == VERSION) return destino
        val temporal = File(destinoBase, "vosk-model.tmp")
        try {
            temporal.deleteRecursively()
            destinoBase.mkdirs()
            copiarArbol(assets, ORIGEN, temporal)
            File(temporal, MARCADOR).writeText(VERSION)
            destino.deleteRecursively()
            if (!temporal.renameTo(destino)) throw IOException("No se pudo renombrar la carpeta temporal del modelo")
        } catch (e: IOException) {
            temporal.deleteRecursively()
            throw e
        }
        Log.i(TAG, "Modelo de voz copiado ($VERSION)")
        return destino
    }

    private fun copiarArbol(
        assets: AssetManager,
        origen: String,
        destino: File,
    ) {
        val hijos = assets.list(origen).orEmpty()
        if (hijos.isEmpty()) {
            destino.parentFile?.mkdirs()
            assets.open(origen).use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
        } else {
            destino.mkdirs()
            hijos.forEach { copiarArbol(assets, "$origen/$it", File(destino, it)) }
        }
    }
}

/**
 * Reconocedores Vosk con el modelo pequeño español. El modelo (≈ 60 MB en disco, bastante RAM nativa) se copia y se
 * carga la primera vez que se pide y se conserva mientras viva el proceso. Cada [crear] da un reconocedor nuevo con su
 * gramática; `null` si el modelo no está o no carga.
 */
class FabricaReconocedorVosk(
    private val contexto: Context,
) : FabricaReconocedor {
    private var modelo: Model? = null

    @Synchronized
    private fun modelo(): Model? {
        modelo?.let { return it }
        return try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val carpeta = CopiaModeloVosk.asegurar(contexto.assets, File(contexto.filesDir, "asr"))
            Model(carpeta.path).also { modelo = it }
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo cargar el modelo de voz: ${e.javaClass.simpleName}")
            null
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Falta la biblioteca nativa de Vosk: ${e.javaClass.simpleName}")
            null
        }
    }

    override fun crear(gramaticaJson: String): Reconocedor? =
        modelo()?.let { ReconocedorVosk(Recognizer(it, FRECUENCIA_HZ.toFloat(), gramaticaJson)) }

    private companion object {
        const val TAG = "FabricaReconocedorVosk"
    }
}

private class ReconocedorVosk(
    private val reconocedor: Recognizer,
) : Reconocedor {
    override fun aceptar(
        muestras: ShortArray,
        n: Int,
    ): Boolean = reconocedor.acceptWaveForm(muestras, n)

    override fun parcial(): String = campo(reconocedor.partialResult, "partial")

    override fun resultado(): String = campo(reconocedor.result, "text")

    override fun fin(): String = campo(reconocedor.finalResult, "text")

    override fun close() = reconocedor.close()

    private fun campo(
        json: String,
        nombre: String,
    ): String =
        try {
            (Json.parseToJsonElement(json) as? JsonObject)
                ?.get(nombre)
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
        } catch (_: IllegalArgumentException) {
            ""
        }
}
