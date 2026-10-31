package app.trombito.rallycopilot.data.enlaces

import app.trombito.rallycopilot.domain.enlaces.ResultadoResolucion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val CORTO = "https://maps.app.goo.gl/AbCdEf"
private const val CON_COORDENADAS = "https://www.google.com/maps/place/Soller/@39.7667,2.7154,17z"

/** Guion sin red: cada URL redirige según [redirecciones] o responde 200; anota las peticiones. */
private class Guion {
    class Peticion(
        val metodo: String,
        val url: String,
        val cabeceras: Map<String, String>,
        val sigueRedirecciones: Boolean,
        val timeoutMs: Int,
    )

    val peticiones = mutableListOf<Peticion>()
    val redirecciones = mutableMapOf<String, String>()
    var fallosHead = 0
    var codigoHead: Int? = null
    var fallarSiempre: IOException? = null

    fun abrir(url: URL): HttpURLConnection {
        val cabeceras = mutableMapOf<String, String>()
        val destino = redirecciones[url.toString()]
        return object : HttpURLConnection(url) {
            override fun connect() = Unit

            override fun disconnect() = Unit

            override fun usingProxy() = false

            override fun getResponseCode(): Int {
                peticiones +=
                    Peticion(requestMethod, url.toString(), cabeceras.toMap(), instanceFollowRedirects, connectTimeout)
                fallarSiempre?.let { throw it }
                if (requestMethod == "HEAD") {
                    if (fallosHead > 0) {
                        fallosHead--
                        throw IOException("HEAD no soportado")
                    }
                    codigoHead?.let { return it }
                }
                return if (destino != null) 302 else 200
            }

            override fun getHeaderField(name: String?): String? = if (name == "Location") destino else null

            override fun setRequestProperty(
                key: String,
                value: String,
            ) {
                cabeceras[key] = value
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ResolvedorEnlacesHttpTest {
    private val guion = Guion()
    private var red = true
    private val registros = mutableListOf<String>()

    private fun TestScope.resolvedor() =
        ResolvedorEnlacesHttp({ red }, StandardTestDispatcher(testScheduler), guion::abrir, registros::add)

    @Test
    fun `solo se aceptan enlaces cortos de la lista cerrada y no se conecta con los demas`() =
        runTest {
            val r = resolvedor()
            listOf(
                "http://maps.app.goo.gl/AbCdEf",
                "https://goo.gl/otra",
                "https://goo.gl.evil.com/maps/x",
                "https://maps.app.goo.gl.evil.com/x",
                "https://evil.com/maps.app.goo.gl",
                "https://user@maps.app.goo.gl/x",
                "https://www.google.com/maps",
                "ftp://maps.app.goo.gl/x",
                "no es una url",
            ).forEach { assertEquals(it, ResultadoResolucion.NoPermitido, r.resolver(it)) }
            assertTrue(guion.peticiones.isEmpty())
        }

    @Test
    fun `acepta goo gl barra maps`() =
        runTest {
            guion.redirecciones["https://goo.gl/maps/xyz"] = CON_COORDENADAS
            assertEquals(ResultadoResolucion.Url(CON_COORDENADAS), resolvedor().resolver("https://goo.gl/maps/xyz"))
        }

    @Test
    fun `sin red devuelve SinRed sin conectar`() =
        runTest {
            red = false
            assertEquals(ResultadoResolucion.SinRed, resolvedor().resolver(CORTO))
            assertTrue(guion.peticiones.isEmpty())
        }

    @Test
    fun `para en cuanto la redireccion trae coordenadas`() =
        runTest {
            guion.redirecciones[CORTO] = CON_COORDENADAS
            guion.redirecciones[CON_COORDENADAS] = "https://www.google.com/otra"
            assertEquals(ResultadoResolucion.Url(CON_COORDENADAS), resolvedor().resolver(CORTO))
            assertEquals(listOf(CORTO), guion.peticiones.map { it.url })
        }

    @Test
    fun `sigue varios saltos hasta las coordenadas`() =
        runTest {
            guion.redirecciones[CORTO] = "https://consent.google.com/ml?continue=x"
            guion.redirecciones["https://consent.google.com/ml?continue=x"] = "/maps/relativa"
            guion.redirecciones["https://consent.google.com/maps/relativa"] = CON_COORDENADAS
            assertEquals(ResultadoResolucion.Url(CON_COORDENADAS), resolvedor().resolver(CORTO))
            assertEquals(3, guion.peticiones.size)
        }

    @Test
    fun `cinco saltos sin coordenadas son el limite y a los seis es error`() =
        runTest {
            val saltos = (0..6).map { "https://www.google.com/s$it" }
            guion.redirecciones[CORTO] = saltos[0]
            (0 until 6).forEach { guion.redirecciones[saltos[it]] = saltos[it + 1] }
            assertEquals(ResultadoResolucion.Error, resolvedor().resolver(CORTO))
            assertEquals(6, guion.peticiones.size)
        }

    @Test
    fun `una cadena de cinco redirecciones que termina sin redirigir devuelve la ultima URL`() =
        runTest {
            val saltos = (0..4).map { "https://www.google.com/s$it" }
            guion.redirecciones[CORTO] = saltos[0]
            (0 until 4).forEach { guion.redirecciones[saltos[it]] = saltos[it + 1] }
            assertEquals(ResultadoResolucion.Url(saltos[4]), resolvedor().resolver(CORTO))
        }

    @Test
    fun `rechaza redirecciones a http o a hosts fuera de la lista`() =
        runTest {
            listOf(
                "http://www.google.com/maps",
                "https://evil.com/maps",
                "https://google.com.evil.com/maps",
                "https://notgoogle.com/maps",
                "https://www.google.fr/maps",
                "https://usuario@www.google.com/maps",
            ).forEach { destino ->
                guion.redirecciones.clear()
                guion.redirecciones[CORTO] = destino
                assertEquals(destino, ResultadoResolucion.NoPermitido, resolvedor().resolver(CORTO))
            }
        }

    @Test
    fun `acepta destinos en google com, google es, goo gl y maps app goo gl`() =
        runTest {
            listOf(
                "https://google.com/x",
                "https://www.google.es/x",
                "https://maps.google.com/x",
                "https://goo.gl/x",
                "https://maps.app.goo.gl/otro",
            ).forEach { destino ->
                guion.redirecciones.clear()
                guion.redirecciones[CORTO] = destino
                assertEquals(destino, ResultadoResolucion.Url(destino), resolvedor().resolver(CORTO))
            }
        }

    @Test
    fun `usa HEAD sin seguir redirecciones, con timeouts de 5 s y un user agent neutro`() =
        runTest {
            guion.redirecciones[CORTO] = CON_COORDENADAS
            resolvedor().resolver(CORTO)
            val p = guion.peticiones.single()
            assertEquals("HEAD", p.metodo)
            assertFalse(p.sigueRedirecciones)
            assertEquals(5_000, p.timeoutMs)
            assertTrue(p.cabeceras.keys.none { it.equals("Cookie", ignoreCase = true) })
            assertTrue(p.cabeceras.getValue("User-Agent").startsWith("Mozilla/5.0"))
        }

    @Test
    fun `si HEAD falla usa GET`() =
        runTest {
            guion.fallosHead = 1
            guion.redirecciones[CORTO] = CON_COORDENADAS
            assertEquals(ResultadoResolucion.Url(CON_COORDENADAS), resolvedor().resolver(CORTO))
            assertEquals(listOf("HEAD", "GET"), guion.peticiones.map { it.metodo })
        }

    @Test
    fun `si HEAD no esta permitido usa GET`() =
        runTest {
            guion.codigoHead = 405
            guion.redirecciones[CORTO] = CON_COORDENADAS
            assertEquals(ResultadoResolucion.Url(CON_COORDENADAS), resolvedor().resolver(CORTO))
            assertEquals(listOf("HEAD", "GET"), guion.peticiones.map { it.metodo })
        }

    @Test
    fun `un fallo de red es Error`() =
        runTest {
            guion.fallarSiempre = IOException("caido")
            assertEquals(ResultadoResolucion.Error, resolvedor().resolver(CORTO))
            assertTrue(registros.isNotEmpty() && registros.none { "goo.gl" in it || "caido" in it })
        }

    @Test
    fun `una redireccion sin Location es Error`() =
        runTest {
            guion.codigoHead = 302
            assertEquals(ResultadoResolucion.Error, resolvedor().resolver(CORTO))
        }
}
