package app.trombito.rallycopilot.data.enlaces

import android.util.Log
import app.trombito.rallycopilot.domain.enlaces.ResolvedorEnlaces
import app.trombito.rallycopilot.domain.enlaces.ResultadoResolucion
import app.trombito.rallycopilot.motor.geo.EnlaceUbicacion
import app.trombito.rallycopilot.motor.geo.ResultadoEnlace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

private const val ETIQUETA = "ResolvedorEnlaces"
private const val TIMEOUT_MS = 5_000
private const val MAX_SALTOS = 5
private const val USER_AGENT = "Mozilla/5.0 (Android) RallyCopilot"
private val CODIGOS_REDIRECCION = setOf(301, 302, 303, 307, 308)
private val CODIGOS_SIN_HEAD = setOf(400, 403, 405, 501)

/**
 * Sigue a mano la redirección de un enlace corto de Google Maps (HEAD, o GET sin leer el cuerpo si HEAD falla).
 * Solo https; el enlace inicial solo puede ser `maps.app.goo.gl` o `goo.gl/maps…`, y cada salto solo puede ir a
 * `*.google.com`, `*.google.es`, `maps.app.goo.gl` o `goo.gl`. Para en cuanto una redirección ya trae coordenadas.
 * Sin cookies ni datos del usuario; los logs solo llevan el tipo de error, nunca la URL.
 */
class ResolvedorEnlacesHttp(
    private val hayRed: () -> Boolean,
    private val dispatcher: CoroutineDispatcher,
    private val abrirConexion: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val registrar: (String) -> Unit = { Log.w(ETIQUETA, it) },
) : ResolvedorEnlaces {
    override suspend fun resolver(url: String): ResultadoResolucion {
        val inicial = parsear(url)
        return when {
            inicial == null || !esEnlaceCortoPermitido(inicial) -> ResultadoResolucion.NoPermitido
            !hayRed() -> ResultadoResolucion.SinRed
            else -> withContext(dispatcher) { seguir(inicial) }
        }
    }

    private sealed interface Paso {
        data class Fin(
            val resultado: ResultadoResolucion,
        ) : Paso

        data class Salto(
            val url: URL,
        ) : Paso
    }

    private suspend fun seguir(inicial: URL): ResultadoResolucion {
        var actual = inicial
        var saltos = 0
        var resultado: ResultadoResolucion? = null
        while (resultado == null) {
            coroutineContext.ensureActive()
            when (val paso = paso(actual, saltos++)) {
                is Paso.Salto -> actual = paso.url
                is Paso.Fin -> resultado = paso.resultado
            }
        }
        return resultado
    }

    /** Pide [actual]: termina (sin redirección, fallo, destino prohibido o coordenadas) o salta al siguiente. */
    private fun paso(
        actual: URL,
        saltosHechos: Int,
    ): Paso {
        val location =
            try {
                consultar(actual)
            } catch (e: IOException) {
                registrar("Fallo al resolver el enlace: ${e.javaClass.simpleName}")
                return Paso.Fin(ResultadoResolucion.Error)
            }
        val siguiente = location?.let { runCatching { URL(actual, it) }.getOrNull() }
        return when {
            location == null -> {
                Paso.Fin(ResultadoResolucion.Url(actual.toString()))
            }

            saltosHechos >= MAX_SALTOS -> {
                Paso.Fin(ResultadoResolucion.Error)
            }

            siguiente == null || !esDestinoPermitido(siguiente) -> {
                Paso.Fin(ResultadoResolucion.NoPermitido)
            }

            EnlaceUbicacion.leer(siguiente.toString()) is ResultadoEnlace.Punto -> {
                Paso.Fin(ResultadoResolucion.Url(siguiente.toString()))
            }

            else -> {
                Paso.Salto(siguiente)
            }
        }
    }

    /** Devuelve la cabecera `Location` si [url] redirige, o null si no es una redirección. */
    private fun consultar(url: URL): String? {
        val conHead =
            try {
                pedir(url, "HEAD")
            } catch (e: IOException) {
                registrar("HEAD fallido: ${e.javaClass.simpleName}")
                null
            }
        val respuesta = if (conHead == null || conHead.codigo in CODIGOS_SIN_HEAD) pedir(url, "GET") else conHead
        if (respuesta.codigo !in CODIGOS_REDIRECCION) return null
        return respuesta.location ?: throw IOException("Redirección sin Location")
    }

    private class Respuesta(
        val codigo: Int,
        val location: String?,
    )

    private fun pedir(
        url: URL,
        metodo: String,
    ): Respuesta {
        val con = abrirConexion(url)
        try {
            con.instanceFollowRedirects = false
            con.requestMethod = metodo
            con.connectTimeout = TIMEOUT_MS
            con.readTimeout = TIMEOUT_MS
            con.useCaches = false
            con.setRequestProperty("User-Agent", USER_AGENT)
            con.setRequestProperty("Accept-Encoding", "identity")
            return Respuesta(con.responseCode, con.getHeaderField("Location"))
        } finally {
            con.disconnect() // no se lee el cuerpo
        }
    }

    internal companion object {
        private val SUFIJOS_PERMITIDOS = listOf(".google.com", ".google.es")
        private val HOSTS_PERMITIDOS = setOf("google.com", "google.es", "maps.app.goo.gl", "goo.gl")

        fun parsear(texto: String): URL? = runCatching { URL(texto.trim()) }.getOrNull()

        fun esEnlaceCortoPermitido(url: URL): Boolean {
            val host = url.host.lowercase()
            return url.protocol == "https" &&
                url.userInfo == null &&
                (host == "maps.app.goo.gl" || (host == "goo.gl" && url.path.startsWith("/maps")))
        }

        fun esDestinoPermitido(url: URL): Boolean {
            val host = url.host.lowercase()
            return url.protocol == "https" &&
                url.userInfo == null &&
                (host in HOSTS_PERMITIDOS || SUFIJOS_PERMITIDOS.any { host.endsWith(it) })
        }
    }
}
