package app.trombito.rallycopilot.data.copia

import app.trombito.rallycopilot.domain.copia.CopiaInvalidaException
import app.trombito.rallycopilot.domain.copia.MotivoCopiaInvalida
import app.trombito.rallycopilot.domain.copia.ResumenCopia
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

internal const val ENTRADA_BASE = "rally.db"
internal const val ENTRADA_MANIFIESTO = "manifest.json"

/** Contenido de `manifest.json`. */
@Serializable
internal data class ManifiestoCopia(
    val esquema: Int,
    val versionName: String,
    val versionCode: Long,
    val fechaMs: Long,
    val fecha: String,
    val recorridos: Int,
    val tramos: Int,
    val pasadas: Int,
) {
    fun resumen() = ResumenCopia(esquema, versionName, versionCode, fechaMs, recorridos, tramos, pasadas)
}

internal val jsonCopia = Json { ignoreUnknownKeys = true }

internal fun invalida(mensaje: String): Nothing =
    throw CopiaInvalidaException(MotivoCopiaInvalida.ARCHIVO_INVALIDO, mensaje)

/**
 * Lee el zip de una copia: el manifiesto a memoria y la base al archivo temporal. Solo admite esas dos entradas (una
 * vez cada una, sin carpetas ni rutas) y no deja pasar de [maxBytes] descomprimidos en total.
 */
internal class LectorZipCopia(
    private val maxBytes: Long,
) {
    private var manifiesto: ByteArray? = null
    private var hayBase = false
    private var total = 0L

    fun leer(
        entrada: InputStream,
        temporal: File,
    ): ManifiestoCopia {
        try {
            val zip = ZipInputStream(entrada)
            generateSequence { zip.nextEntry }.forEach { leerEntrada(zip, it.name, it.isDirectory, temporal) }
        } catch (e: ZipException) {
            throw CopiaInvalidaException(MotivoCopiaInvalida.ARCHIVO_INVALIDO, "zip estropeado", e)
        } catch (e: IOException) {
            throw CopiaInvalidaException(MotivoCopiaInvalida.NO_SE_PUEDE_ABRIR, "no se puede leer", e)
        }
        val texto = manifiesto
        if (!hayBase || texto == null) invalida("faltan entradas")
        return decodificar(texto)
    }

    private fun leerEntrada(
        zip: ZipInputStream,
        nombre: String,
        carpeta: Boolean,
        temporal: File,
    ) {
        val esperada = nombre == ENTRADA_BASE || nombre == ENTRADA_MANIFIESTO
        val repetida = if (nombre == ENTRADA_BASE) hayBase else manifiesto != null
        if (carpeta || !esperada || repetida) invalida("entrada no permitida: $nombre")
        if (nombre == ENTRADA_BASE) {
            temporal.outputStream().use { copiar(zip, it, maxBytes) }
            hayBase = true
        } else {
            val memoria = ByteArrayOutputStream()
            copiar(zip, memoria, MAX_MANIFIESTO)
            manifiesto = memoria.toByteArray()
        }
    }

    /** Copia hasta [limite] bytes de esta entrada (y sin pasar del total); si hay más, es demasiado grande. */
    private fun copiar(
        entrada: InputStream,
        salida: OutputStream,
        limite: Long,
    ) {
        val buffer = ByteArray(BUFFER)
        var propios = 0L
        while (true) {
            val leidos = entrada.read(buffer)
            if (leidos < 0) return
            propios += leidos
            total += leidos
            if (propios > limite || total > maxBytes) {
                throw CopiaInvalidaException(MotivoCopiaInvalida.DEMASIADO_GRANDE, "pasa del tamaño máximo")
            }
            salida.write(buffer, 0, leidos)
        }
    }

    private fun decodificar(texto: ByteArray): ManifiestoCopia =
        try {
            jsonCopia.decodeFromString(ManifiestoCopia.serializer(), texto.decodeToString())
        } catch (e: SerializationException) {
            throw CopiaInvalidaException(MotivoCopiaInvalida.ARCHIVO_INVALIDO, "manifiesto ilegible", e)
        } catch (e: IllegalArgumentException) {
            throw CopiaInvalidaException(MotivoCopiaInvalida.ARCHIVO_INVALIDO, "manifiesto ilegible", e)
        }

    private companion object {
        const val MAX_MANIFIESTO = 64L * 1024
        const val BUFFER = 64 * 1024
    }
}
