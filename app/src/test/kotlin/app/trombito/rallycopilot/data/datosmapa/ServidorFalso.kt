package app.trombito.rallycopilot.data.datosmapa

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Servidor HTTP local (JDK) que imita a GitHub Releases: Range, redirecciones y cortes a mitad de descarga. */
class ServidorFalso : AutoCloseable {
    data class Peticion(
        val ruta: String,
        val range: String?,
        val userAgent: String?,
    )

    class Recurso(
        val bytes: ByteArray,
        /** Responde siempre 200 con el cuerpo entero, aunque la petición traiga Range. */
        val ignorarRange: Boolean = false,
    ) {
        /** Si no es null, la próxima respuesta envía solo estos bytes de cuerpo y corta la conexión (una vez). */
        @Volatile var cortarTras: Int? = null
    }

    val peticiones = CopyOnWriteArrayList<Peticion>()
    private val recursos = ConcurrentHashMap<String, Recurso>()
    private val redirecciones = ConcurrentHashMap<String, String>()
    private val servidor =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { atender(it) }
            start()
        }

    val puerto: Int get() = servidor.address.port

    fun servir(
        ruta: String,
        recurso: Recurso,
    ): Recurso = recurso.also { recursos[ruta] = it }

    fun servir(
        ruta: String,
        bytes: ByteArray,
    ): Recurso = servir(ruta, Recurso(bytes))

    fun redirigir(
        ruta: String,
        destino: String,
    ) {
        redirecciones[ruta] = destino
    }

    fun peticionesA(ruta: String): List<Peticion> = peticiones.filter { it.ruta == ruta }

    /** Deja de escuchar (los intentos posteriores fallan con conexión rechazada). */
    fun apagar() = servidor.stop(0)

    override fun close() = servidor.stop(0)

    private fun atender(ex: HttpExchange) {
        val ruta = ex.requestURI.path
        val range = ex.requestHeaders.getFirst("Range")
        peticiones += Peticion(ruta, range, ex.requestHeaders.getFirst("User-Agent"))
        try {
            val destino = redirecciones[ruta]
            val recurso = recursos[ruta]
            when {
                destino != null -> {
                    ex.responseHeaders.add("Location", destino)
                    ex.sendResponseHeaders(302, -1)
                }

                recurso == null -> {
                    ex.sendResponseHeaders(404, -1)
                }

                else -> {
                    responder(ex, recurso, range)
                }
            }
        } finally {
            runCatching { ex.close() }
        }
    }

    private fun responder(
        ex: HttpExchange,
        recurso: Recurso,
        range: String?,
    ) {
        val total = recurso.bytes.size
        val desde =
            if (range == null || recurso.ignorarRange) {
                null
            } else {
                RANGO
                    .find(range)
                    ?.groupValues
                    ?.get(1)
                    ?.toInt()
            }
        if (desde != null && desde >= total) {
            ex.sendResponseHeaders(416, -1)
            return
        }
        val inicio = desde ?: 0
        val cuerpo = recurso.bytes.copyOfRange(inicio, total)
        if (desde != null) ex.responseHeaders.add("Content-Range", "bytes $desde-${total - 1}/$total")
        ex.sendResponseHeaders(if (desde != null) 206 else 200, cuerpo.size.toLong())
        val corte = recurso.cortarTras
        recurso.cortarTras = null
        val enviado = if (corte != null) cuerpo.copyOf(corte.coerceAtMost(cuerpo.size)) else cuerpo
        // Al cortar, close() se queja de que faltan bytes: es justo lo que se quiere (conexión rota a mitad).
        runCatching { ex.responseBody.use { it.write(enviado) } }.onFailure { if (corte == null) throw it }
    }

    private companion object {
        val RANGO = Regex("""bytes=(\d+)-""")
    }
}
