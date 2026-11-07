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
import java.io.InputStream

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
    fun asegurar(
        assets: AssetManager,
        destinoBase: File,
    ): File = asegurar(OrigenAssets(assets), destinoBase)

    @Synchronized
    internal fun asegurar(
        assets: OrigenModelo,
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
        assets: OrigenModelo,
        origen: String,
        destino: File,
    ) {
        val hijos = assets.list(origen)
        if (hijos.isEmpty()) {
            destino.parentFile?.mkdirs()
            assets.open(origen).use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
        } else {
            destino.mkdirs()
            hijos.forEach { copiarArbol(assets, "$origen/$it", File(destino, it)) }
        }
    }
}

/** De dónde sale el modelo que se copia a disco (los assets del APK; un doble en las pruebas). */
internal interface OrigenModelo {
    /** Nombres de los hijos de [ruta]; vacío si es un archivo. */
    fun list(ruta: String): List<String>

    fun open(ruta: String): InputStream
}

private class OrigenAssets(
    private val assets: AssetManager,
) : OrigenModelo {
    override fun list(ruta: String): List<String> = assets.list(ruta).orEmpty().toList()

    override fun open(ruta: String): InputStream = assets.open(ruta)
}

/** Un modelo Vosk cargado en memoria nativa; costura para probar [FabricaReconocedorVosk] sin JNI. */
internal interface ModeloNativo : AutoCloseable {
    fun reconocedor(gramaticaJson: String): Reconocedor
}

/** Carga el modelo de [carpeta]; lanza [IOException] si está incompleto o [UnsatisfiedLinkError] sin biblioteca. */
internal fun interface CargadorModeloVosk {
    fun cargar(carpeta: File): ModeloNativo
}

private object CargadorVoskNativo : CargadorModeloVosk {
    override fun cargar(carpeta: File): ModeloNativo {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val modelo = Model(carpeta.path)
        return object : ModeloNativo {
            override fun reconocedor(gramaticaJson: String): Reconocedor =
                ReconocedorVosk(Recognizer(modelo, FRECUENCIA_HZ.toFloat(), gramaticaJson))

            override fun close() = modelo.close()
        }
    }
}

/**
 * Reconocedores Vosk con el modelo pequeño español. El modelo (≈ 60 MB en disco, bastante RAM nativa) se copia y se
 * carga la primera vez que se pide y se conserva hasta [liberar] (fin de la conducción o poca memoria). Cada [crear]
 * da un reconocedor nuevo con su gramática; `null` si el modelo no está o no carga.
 */
class FabricaReconocedorVosk internal constructor(
    private val copiar: () -> File,
    private val cargador: CargadorModeloVosk,
) : FabricaReconocedor {
    constructor(contexto: Context) : this(
        { CopiaModeloVosk.asegurar(contexto.assets, File(contexto.filesDir, "asr")) },
        CargadorVoskNativo,
    )

    private var modelo: ModeloNativo? = null

    @Synchronized
    private fun modelo(): ModeloNativo? {
        modelo?.let { return it }
        return try {
            cargador.cargar(copiar()).also { modelo = it }
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo cargar el modelo de voz: ${e.javaClass.simpleName}")
            null
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Falta la biblioteca nativa de Vosk: ${e.javaClass.simpleName}")
            null
        }
    }

    override fun crear(gramaticaJson: String): Reconocedor? = modelo()?.reconocedor(gramaticaJson)

    /** Copia el modelo a disco (si falta) sin cargarlo: así la primera orden no espera a la copia de 57 MB. */
    override fun precopiar() {
        try {
            copiar()
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo precopiar el modelo de voz: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Libera el modelo de la memoria nativa. Los reconocedores ya creados siguen valiendo (Vosk cuenta referencias
     * del modelo); el siguiente [crear] lo carga de nuevo desde disco.
     */
    @Synchronized
    override fun liberar() {
        modelo?.close()
        modelo = null
    }

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
