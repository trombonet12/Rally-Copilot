package app.trombito.rallycopilot.data.detalle

import app.trombito.rallycopilot.data.datosmapa.ClienteHttp
import app.trombito.rallycopilot.data.datosmapa.RespuestaHttpException
import app.trombito.rallycopilot.motor.precarga.Tesela
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Lo que devuelve el servidor de teselas: el JPEG o "aquí no hay imagen" (mar). */
sealed interface ResultadoTesela {
    class Jpeg(
        val bytes: ByteArray,
    ) : ResultadoTesela

    data object SinCobertura : ResultadoTesela
}

/** Origen de teselas PNOA. Falla con [IOException] si no puede contestar tras los reintentos. */
fun interface FuenteTeselas {
    suspend fun obtener(tesela: Tesela): ResultadoTesela
}

/**
 * Teselas PNOA del TMS del IGN, el mismo servicio que `scripts/satelite.py` del repo de datos (y la misma fila TMS, con
 * origen abajo): JPEG, PNG transparente de ~660 B donde no hay cobertura (mar) o 404. Reintenta con espera exponencial
 * los errores de red y los 5xx/429; el resto de códigos y una respuesta que no sea imagen son definitivos.
 */
class FuenteTeselasIgn(
    private val cliente: ClienteHttp,
    private val dispatcher: CoroutineDispatcher,
    private val base: String = URL_BASE_IGN,
    private val reintentos: Int = REINTENTOS,
    private val esperar: suspend (Long) -> Unit = { delay(it) },
) : FuenteTeselas {
    override suspend fun obtener(tesela: Tesela): ResultadoTesela {
        val url = "$base/${tesela.z}/${tesela.x}/${tesela.filaTms}.jpeg"
        var ultimo: IOException? = null
        for (intento in 0 until reintentos) {
            try {
                return pedir(url)
            } catch (e: RespuestaHttpException) {
                if (e.codigo == HTTP_NO_ENCONTRADO) return ResultadoTesela.SinCobertura
                if (e.codigo < HTTP_ERROR_SERVIDOR && e.codigo != HTTP_DEMASIADAS) throw e
                ultimo = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: FormatoInesperadoException) {
                throw e
            } catch (e: IOException) {
                ultimo = e
            }
            if (intento < reintentos - 1) esperar(ESPERA_BASE_MS shl intento)
        }
        throw ultimo ?: IOException("Sin respuesta para $url")
    }

    private suspend fun pedir(url: String): ResultadoTesela =
        withContext(dispatcher) {
            val bytes = cliente.flujo(url, MAX_BYTES_TESELA).use { leer(it) }
            when {
                bytes.size >= 4 && bytes.startsWith(PNG) -> ResultadoTesela.SinCobertura
                bytes.size >= 3 && bytes.startsWith(JPEG) -> ResultadoTesela.Jpeg(bytes)
                else -> throw FormatoInesperadoException()
            }
        }

    private fun leer(entrada: java.io.InputStream): ByteArray {
        val salida = ByteArrayOutputStream()
        entrada.copyTo(salida)
        return salida.toByteArray()
    }

    private fun ByteArray.startsWith(prefijo: ByteArray) = prefijo.indices.all { this[it] == prefijo[it] }

    /** La respuesta no es JPEG ni PNG: no se guarda ni se reintenta. */
    class FormatoInesperadoException : IOException("La tesela no es una imagen")

    companion object {
        const val URL_BASE_IGN = "https://tms-pnoa-ma.idee.es/1.0.0/pnoa-ma"
        private const val REINTENTOS = 4
        private const val ESPERA_BASE_MS = 500L
        private const val MAX_BYTES_TESELA = 2L * 1024 * 1024
        private const val HTTP_NO_ENCONTRADO = 404
        private const val HTTP_DEMASIADAS = 429
        private const val HTTP_ERROR_SERVIDOR = 500
        private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    }
}
