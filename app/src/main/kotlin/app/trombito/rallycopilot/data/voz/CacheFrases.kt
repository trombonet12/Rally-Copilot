package app.trombito.rallycopilot.data.voz

import android.util.Log
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Caché en disco de frases ya sintetizadas: PCM de 16 bits mono (little-endian, sin cabecera, a la frecuencia del
 * modelo) en `<raiz>/<voz>/<sha256(texto)>.pcm` (a velocidad normal) o `<raiz>/<voz>-rapida/<sha256(texto)>.pcm`: cada
 * velocidad de habla tiene su carpeta, así que cambiarla no mezcla audios. Expulsa lo menos usado cuando se pasa de
 * [maxBytes].
 *
 * El orden de uso se lleva en memoria (se reconstruye al arrancar con la fecha de modificación, que cada lectura
 * renueva). Seguro para varios hilos. Un archivo ilegible o con tamaño impar se trata como si no existiera.
 */
class CacheFrases(
    private val raiz: File,
    private val maxBytes: Long = MAX_BYTES_POR_DEFECTO,
) {
    private val lock = Any()
    private var indice: LinkedHashMap<String, Long>? = null
    private var total = 0L

    /** `true` si la frase ya está guardada (no cuenta como uso). */
    fun contiene(
        voz: VozCopiloto,
        texto: String,
        velocidad: VelocidadHabla = VelocidadHabla.NORMAL,
    ): Boolean = synchronized(lock) { indiceCargado().containsKey(clave(voz, texto, velocidad)) }

    /** Tamaño en bytes del PCM guardado, o `null` si no está. No cuenta como uso. */
    fun bytes(
        voz: VozCopiloto,
        texto: String,
        velocidad: VelocidadHabla = VelocidadHabla.NORMAL,
    ): Long? = synchronized(lock) { indiceCargado()[clave(voz, texto, velocidad)] }

    /** Lee la frase y la marca como la más reciente; `null` si no está o no se puede leer. */
    fun leer(
        voz: VozCopiloto,
        texto: String,
        velocidad: VelocidadHabla = VelocidadHabla.NORMAL,
    ): ShortArray? =
        synchronized(lock) {
            val clave = clave(voz, texto, velocidad)
            val mapa = indiceCargado()
            if (!mapa.containsKey(clave)) return@synchronized null
            val archivo = archivo(clave)
            try {
                val bytes = archivo.readBytes()
                val muestras = ShortArray(bytes.size / BYTES_POR_MUESTRA)
                ByteBuffer
                    .wrap(bytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                    .get(muestras)
                mapa.remove(clave) // reinserta al final: la más reciente
                mapa[clave] = bytes.size.toLong()
                archivo.setLastModified(System.currentTimeMillis())
                muestras
            } catch (e: IOException) {
                Log.w(TAG, "Frase de caché ilegible, se descarta", e)
                olvidar(clave)
                null
            }
        }

    /** Guarda la frase (escritura atómica) y expulsa lo menos usado si se pasa del límite. */
    fun guardar(
        voz: VozCopiloto,
        texto: String,
        muestras: ShortArray,
        velocidad: VelocidadHabla = VelocidadHabla.NORMAL,
    ) {
        synchronized(lock) {
            val clave = clave(voz, texto, velocidad)
            val mapa = indiceCargado()
            val bytes =
                ByteBuffer
                    .allocate(muestras.size * BYTES_POR_MUESTRA)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .apply { asShortBuffer().put(muestras) }
                    .array()
            val destino = archivo(clave)
            try {
                destino.parentFile?.mkdirs()
                val temporal = File(destino.parentFile, destino.name + ".tmp")
                temporal.writeBytes(bytes)
                mover(temporal, destino)
                total -= mapa.remove(clave) ?: 0L
                mapa[clave] = bytes.size.toLong()
                total += bytes.size
                expulsar(protegida = clave)
            } catch (e: IOException) {
                Log.w(TAG, "No se pudo guardar la frase en la caché", e)
            }
        }
    }

    /** Bytes ocupados por la caché. */
    fun tamanoTotal(): Long =
        synchronized(lock) {
            indiceCargado()
            total
        }

    private fun expulsar(protegida: String) {
        val mapa = indiceCargado()
        val iterador = mapa.entries.iterator()
        while (total > maxBytes && iterador.hasNext()) {
            val entrada = iterador.next()
            if (entrada.key == protegida) continue
            archivo(entrada.key).delete()
            total -= entrada.value
            iterador.remove()
        }
    }

    private fun olvidar(clave: String) {
        archivo(clave).delete()
        total -= indiceCargado().remove(clave) ?: 0L
    }

    private fun indiceCargado(): LinkedHashMap<String, Long> =
        indice ?: LinkedHashMap<String, Long>().also { mapa ->
            indice = mapa
            val existentes =
                raiz
                    .listFiles { f -> f.isDirectory }
                    .orEmpty()
                    .flatMap { carpeta ->
                        carpeta
                            .listFiles { f -> f.name.endsWith(EXTENSION) }
                            .orEmpty()
                            .toList()
                    }
            existentes.filter { f -> f.length() % BYTES_POR_MUESTRA != 0L }.forEach { f -> f.delete() }
            existentes
                .filter { f -> f.exists() }
                .sortedBy { f -> f.lastModified() }
                .forEach { f ->
                    mapa["${f.parentFile?.name}/${f.name.removeSuffix(EXTENSION)}"] = f.length()
                    total += f.length()
                }
            expulsar(protegida = "")
        }

    private fun archivo(clave: String) = File(raiz, clave + EXTENSION)

    private fun mover(
        origen: File,
        destino: File,
    ) {
        try {
            Files.move(origen.toPath(), destino.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Log.w(TAG, "Renombrado atómico no disponible, se reemplaza", e)
            Files.move(origen.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val MAX_BYTES_POR_DEFECTO = 40L * 1024 * 1024
        private const val BYTES_POR_MUESTRA = 2
        private const val EXTENSION = ".pcm"
        private const val TAG = "CacheFrases"

        /**
         * Clave de caché `<voz>/<sha256 hex del texto>` a velocidad normal y `<voz>-<velocidad>/<sha256>` (p. ej.
         * `davefx-rapida/…`) a cualquier otra.
         */
        fun clave(
            voz: VozCopiloto,
            texto: String,
            velocidad: VelocidadHabla = VelocidadHabla.NORMAL,
        ): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(texto.toByteArray(Charsets.UTF_8))
            val sufijo = if (velocidad == VelocidadHabla.NORMAL) "" else "-" + velocidad.name.lowercase()
            return voz.name.lowercase() + sufijo + "/" + hash.joinToString("") { "%02x".format(it) }
        }
    }
}
