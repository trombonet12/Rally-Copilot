package app.trombito.rallycopilot.data.diagnostico

import app.trombito.rallycopilot.domain.diagnostico.NivelRegistro
import app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Escribe líneas `AAAA-MM-DD HH:mm:ss.SSS NIVEL etiqueta mensaje` en `registros/rally.log` dentro de [carpeta]
 * (`getExternalFilesDir(null)`), con rotación 2 × [maxBytes]: al llenarse, `rally.log` pasa a `rally.1.log` (el
 * anterior se borra). [registrar] solo encola (cola acotada que descarta lo más viejo: jamás bloquea) y un hilo de
 * [dispatcher] escribe. Nunca lanza: sin carpeta, con el disco lleno o con cualquier fallo, la línea se pierde.
 *
 * Filtro defensivo ([FiltroRegistro]): una línea con pinta de coordenadas o de secreto no se escribe. [activo] se
 * consulta en cada línea (el interruptor «Copia automática»: apagado, no registra).
 */
class RegistroDiagnosticoArchivo(
    private val carpeta: () -> File?,
    private val activo: () -> Boolean,
    dispatcher: CoroutineDispatcher,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val zona: ZoneId = ZoneId.systemDefault(),
    private val maxBytes: Long = MAX_BYTES,
) : RegistroDiagnostico {
    private val cola = Channel<String>(CAPACIDAD_COLA, BufferOverflow.DROP_OLDEST)
    private val cerrojo = Any()

    init {
        CoroutineScope(SupervisorJob() + dispatcher).launch {
            for (linea in cola) escribir(linea)
        }
    }

    override fun registrar(
        nivel: NivelRegistro,
        etiqueta: String,
        mensaje: String,
    ) {
        if (!permitido()) return
        formatear(nivel, etiqueta, mensaje).forEach { cola.trySend(it) }
    }

    override fun registrarSincrono(
        nivel: NivelRegistro,
        etiqueta: String,
        mensaje: String,
    ) {
        if (!permitido()) return
        formatear(nivel, etiqueta, mensaje).forEach(::escribir)
    }

    @Suppress("TooGenericExceptionCaught") // el interruptor viene de fuera: que falle no debe tumbar nada
    private fun permitido(): Boolean = runCatching { activo() }.getOrDefault(false)

    /** Una línea por cada línea del mensaje (las trazas de excepción), sin las que el filtro rechaza. */
    private fun formatear(
        nivel: NivelRegistro,
        etiqueta: String,
        mensaje: String,
    ): List<String> {
        val marca = FORMATO.format(Instant.ofEpochMilli(ahora()).atZone(zona))
        return mensaje
            .lineSequence()
            .filter { it.isNotBlank() }
            .filterNot(FiltroRegistro::sospechosa)
            .map { "$marca ${nivel.name} $etiqueta $it" }
            .toList()
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // disco lleno, solo lectura, sin almacenamiento
    private fun escribir(linea: String) {
        try {
            synchronized(cerrojo) {
                val destino = File(carpeta() ?: return, CARPETA)
                destino.mkdirs()
                if (!destino.isDirectory) return
                val actual = File(destino, NOMBRE)
                val bytes = (linea + "\n").toByteArray(Charsets.UTF_8)
                if (actual.length() + bytes.size > maxBytes) rotar(destino, actual)
                actual.appendBytes(bytes)
            }
        } catch (e: Exception) {
            // Sin sitio donde escribir: se pierde la línea (el registro es solo ayuda).
        }
    }

    private fun rotar(
        destino: File,
        actual: File,
    ) {
        val anterior = File(destino, NOMBRE_ANTERIOR)
        anterior.delete()
        if (!actual.renameTo(anterior)) actual.delete()
    }

    companion object {
        const val CARPETA = "registros"
        const val NOMBRE = "rally.log"
        const val NOMBRE_ANTERIOR = "rally.1.log"
        const val MAX_BYTES = 1L * 1024 * 1024
        private const val CAPACIDAD_COLA = 2_000
        private val FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }
}

/** Rechaza líneas que parecen llevar coordenadas o secretos: el registro no debe contenerlos nunca. */
object FiltroRegistro {
    private val PAREJA_DECIMALES =
        Regex("""(?<![\w.,])-?\d{1,3}[.,]\d{2,}\s*[,;/]\s*-?\d{1,3}[.,]\d{2,}(?![\w.])""")
    private val CLAVE_UBICACION =
        Regex("""\b(lat|lon|lng|latitud|longitud|latitude|longitude)\w*\s*[=:]\s*-?\d""", RegexOption.IGNORE_CASE)
    private val SECRETO =
        Regex(
            """(api[_-]?key|token|secret|password|contrase\w+|keystore)\s*[=:]\s*\S+""",
            RegexOption.IGNORE_CASE,
        )

    fun sospechosa(linea: String): Boolean =
        PAREJA_DECIMALES.containsMatchIn(linea) || CLAVE_UBICACION.containsMatchIn(linea) ||
            SECRETO.containsMatchIn(linea)
}
