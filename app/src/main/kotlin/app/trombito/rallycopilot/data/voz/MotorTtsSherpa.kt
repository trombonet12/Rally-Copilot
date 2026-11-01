package app.trombito.rallycopilot.data.voz

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * [SintetizadorTts] con sherpa-onnx (Piper/VITS es_ES int8). El modelo y los tokens se leen de `assets/voz/<voz>/`; el
 * `espeak-ng-data` necesita una ruta real, así que se copia una vez a `filesDir/voz/espeak-ng-data` (ver
 * [CopiaEspeak]). Solo hay un modelo en memoria: al cambiar de voz se libera el anterior. La carga es perezosa (en el
 * hilo de quien llame a [sintetizar]). Siempre hablante 0 (en sharvard es el masculino).
 */
@Singleton
class MotorTtsSherpa
    @Inject
    constructor(
        @ApplicationContext private val contexto: Context,
    ) : SintetizadorTts {
        private var cargada: VozCopiloto? = null
        private var motor: OfflineTts? = null

        override fun frecuenciaHz(voz: VozCopiloto): Int = FRECUENCIA_HZ

        @Synchronized
        override fun sintetizar(
            voz: VozCopiloto,
            texto: String,
        ): AudioPcm {
            val tts = motorPara(voz)
            val audio = generar(tts, texto)
            if (audio.sampleRate != FRECUENCIA_HZ) {
                throw ErrorSintesis("El modelo ${voz.name} da ${audio.sampleRate} Hz, se esperaban $FRECUENCIA_HZ Hz")
            }
            return AudioPcm(aPcm16(audio.samples), audio.sampleRate)
        }

        @Synchronized
        override fun liberar() {
            motor?.free()
            motor = null
            cargada = null
        }

        private fun motorPara(voz: VozCopiloto): OfflineTts {
            val actual = motor
            if (actual != null && cargada == voz) return actual
            liberar()
            val nombre = voz.name.lowercase()
            val datos = CopiaEspeak.asegurar(contexto.assets, File(contexto.filesDir, "voz"))
            val config =
                OfflineTtsConfig(
                    model =
                        OfflineTtsModelConfig(
                            vits =
                                OfflineTtsVitsModelConfig(
                                    model = "voz/$nombre/model.onnx",
                                    tokens = "voz/$nombre/tokens.txt",
                                    dataDir = datos.absolutePath,
                                ),
                            numThreads = HILOS,
                            debug = false,
                            provider = "cpu",
                        ),
                )
            val nuevo = cargar(contexto.assets, config)
            motor = nuevo
            cargada = voz
            return nuevo
        }

        // El código nativo puede lanzar excepciones de cualquier tipo (modelo corrupto, memoria): se unifican en
        // ErrorSintesis.
        @Suppress("TooGenericExceptionCaught")
        private fun cargar(
            assets: AssetManager,
            config: OfflineTtsConfig,
        ): OfflineTts =
            try {
                OfflineTts(assetManager = assets, config = config)
            } catch (e: RuntimeException) {
                throw ErrorSintesis("No se pudo cargar el modelo de voz", e)
            } catch (e: UnsatisfiedLinkError) {
                throw ErrorSintesis("Falta la biblioteca nativa de sherpa-onnx", e)
            }

        @Suppress("TooGenericExceptionCaught")
        private fun generar(
            tts: OfflineTts,
            texto: String,
        ) = try {
            tts.generate(text = texto, sid = SID, speed = 1.0f)
        } catch (e: RuntimeException) {
            throw ErrorSintesis("Falló la síntesis", e)
        }

        private fun aPcm16(muestras: FloatArray): ShortArray =
            ShortArray(muestras.size) { i ->
                (muestras[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
            }

        companion object {
            /** Frecuencia de las dos voces Piper medium. */
            const val FRECUENCIA_HZ = 22_050
            private const val HILOS = 2
            private const val SID = 0
        }
    }

/**
 * Copia de `assets/voz/espeak-ng-data` a `<destinoBase>/espeak-ng-data`, una sola vez: la carpeta lleva un marcador
 * con la versión de los datos y solo se vuelve a copiar si falta o cambia. La copia es atómica: se hace en una carpeta
 * temporal y se renombra al terminar, de modo que un corte a medias nunca deja datos incompletos.
 */
object CopiaEspeak {
    /** Subir al cambiar las voces o su `espeak-ng-data`. */
    const val VERSION = "sherpa-1.13.8-1"
    private const val ORIGEN = "voz/espeak-ng-data"
    private const val MARCADOR = ".version"
    private const val TAG = "CopiaEspeak"

    /** Devuelve la carpeta con los datos listos, copiándolos si hace falta. Lanza [ErrorSintesis] si falla. */
    @Synchronized
    fun asegurar(
        assets: AssetManager,
        destinoBase: File,
    ): File {
        val destino = File(destinoBase, "espeak-ng-data")
        if (File(destino, MARCADOR).takeIf { it.isFile }?.readText() == VERSION) return destino
        val temporal = File(destinoBase, "espeak-ng-data.tmp")
        try {
            temporal.deleteRecursively()
            destinoBase.mkdirs()
            copiarArbol(assets, ORIGEN, temporal)
            File(temporal, MARCADOR).writeText(VERSION)
            destino.deleteRecursively()
            if (!temporal.renameTo(destino)) throw IOException("No se pudo renombrar $temporal")
        } catch (e: IOException) {
            temporal.deleteRecursively()
            throw ErrorSintesis("No se pudo preparar espeak-ng-data", e)
        }
        Log.i(TAG, "espeak-ng-data copiado ($VERSION)")
        return destino
    }

    private fun copiarArbol(
        assets: AssetManager,
        origen: String,
        destino: File,
    ) {
        val hijos = assets.list(origen).orEmpty()
        if (hijos.isEmpty() && esArchivo(assets, origen)) {
            destino.parentFile?.mkdirs()
            assets.open(origen).use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
        } else {
            destino.mkdirs()
            hijos.forEach { copiarArbol(assets, "$origen/$it", File(destino, it)) }
        }
    }

    /** Una carpeta de assets vacía y un archivo dan ambos `list() == []`; solo el archivo se puede abrir. */
    private fun esArchivo(
        assets: AssetManager,
        ruta: String,
    ): Boolean =
        try {
            assets.open(ruta).close()
            true
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "Carpeta vacía en assets: $ruta", e)
            false
        }
}
