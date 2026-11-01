package app.trombito.rallycopilot.data.alertas

import android.util.Log
import app.trombito.rallycopilot.data.datosmapa.ClienteHttp
import app.trombito.rallycopilot.data.datosmapa.RespuestaHttpException
import app.trombito.rallycopilot.data.datosmapa.TopeExcedidoException
import app.trombito.rallycopilot.domain.alertas.ErrorIncidencias
import app.trombito.rallycopilot.domain.alertas.IncidenciasVigentes
import app.trombito.rallycopilot.domain.alertas.RepositorioIncidencias
import app.trombito.rallycopilot.motor.alertas.LectorDatex
import app.trombito.rallycopilot.motor.alertas.ResultadoDatex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Feed DATEX II v3.7 de situaciones de tráfico del NAP de la DGT (sin autenticación). */
const val URL_DATEX_DGT = "https://nap.dgt.es/datex2/v3/dgt/SituationPublication/datex2_v37.xml"

/**
 * Incidencias de la DGT: descarga [url] con [http] (solo https a `nap.dgt.es`, gzip, tope de [TOPE_BYTES]), la lee en
 * streaming en [dispatcher] y guarda la última copia buena en [directorio] (`incidencias-dgt.xml.gz` más el instante en
 * `incidencias-dgt.instante`). Si la descarga o la lectura fallan, la copia anterior sigue siendo la vigente y el error
 * queda en [IncidenciasVigentes.errorUltimo]; sin red se usa la copia con su antigüedad. Las caducadas se descartan al
 * cargar. Es la única llamada de red de las alertas y nunca registra coordenadas.
 *
 * [reloj] da ms epoch. Tras un intento terminado no se vuelve a pedir hasta pasados
 * [INTERVALO_MS] (o [INTERVALO_TRAS_ERROR_MS] si falló); un intento cancelado no cuenta.
 */
class RepositorioIncidenciasDgt internal constructor(
    private val http: ClienteHttp,
    private val url: String,
    private val directorio: File,
    private val reloj: () -> Long,
    private val dispatcher: CoroutineDispatcher,
    private val topeBytes: Long = TOPE_BYTES,
) : RepositorioIncidencias {
    private val estado = MutableStateFlow(IncidenciasVigentes())
    override val incidencias: StateFlow<IncidenciasVigentes> = estado.asStateFlow()

    private val ocupado = Mutex()
    private var copiaCargada = false
    private var ultimoIntentoMs: Long? = null
    private var ultimoFallo = false

    private val copia get() = File(directorio, NOMBRE_COPIA)
    private val instante get() = File(directorio, NOMBRE_INSTANTE)
    private val temporal get() = File(directorio, "$NOMBRE_COPIA.tmp")

    override suspend fun refrescar() {
        if (!ocupado.tryLock()) return
        try {
            withContext(dispatcher) {
                if (!copiaCargada) cargarCopia()
                if (tocaDescargar()) descargar()
            }
        } finally {
            ocupado.unlock()
        }
    }

    private fun tocaDescargar(): Boolean {
        val ultimo = ultimoIntentoMs ?: return true
        val pasado = reloj() - ultimo
        val minimo = if (ultimoFallo) INTERVALO_TRAS_ERROR_MS else INTERVALO_MS
        // Un reloj que retrocede no debe bloquear el refresco.
        return pasado < 0 || pasado >= minimo
    }

    /** Lee la copia de disco (si hay) como estado inicial; una copia ilegible se ignora y se borra. */
    private fun cargarCopia() {
        copiaCargada = true
        val vigentes = leerCopia() ?: return
        estado.value = vigentes
    }

    private fun leerCopia(): IncidenciasVigentes? {
        val obtenidas = leerInstante()
        val resultado =
            if (!copia.isFile) {
                null
            } else {
                try {
                    abrirCopia(copia).use { LectorDatex.leer(it) }
                } catch (e: IOException) {
                    Log.w(ETIQUETA, "Copia de incidencias ilegible: ${e.javaClass.simpleName}")
                    null
                }
            }
        val vigentes =
            if (resultado is ResultadoDatex.Ok && obtenidas != null) {
                IncidenciasVigentes(sinCaducadas(resultado), obtenidas, null)
            } else {
                null
            }
        if (vigentes == null) {
            copia.delete()
            instante.delete()
        }
        return vigentes
    }

    private fun abrirCopia(archivo: File) = GZIPInputStream(archivo.inputStream().buffered())

    private fun leerInstante(): Instant? =
        try {
            instante
                .takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.toLongOrNull()
                ?.let(Instant::ofEpochMilli)
        } catch (e: IOException) {
            Log.w(ETIQUETA, "Instante de las incidencias ilegible: ${e.javaClass.simpleName}")
            null
        }

    private fun sinCaducadas(resultado: ResultadoDatex.Ok) =
        Instant.ofEpochMilli(reloj()).let { ahora -> resultado.incidencias.filterNot { it.caducadaEn(ahora) } }

    private fun descargar() {
        val error =
            try {
                if (descargarYGuardar()) null else ErrorIncidencias.DATOS
            } catch (e: TopeExcedidoException) {
                Log.w(ETIQUETA, "Incidencias: ${e.javaClass.simpleName}")
                ErrorIncidencias.DEMASIADO_GRANDE
            } catch (e: RespuestaHttpException) {
                Log.w(ETIQUETA, "Incidencias: HTTP ${e.codigo}")
                ErrorIncidencias.SERVIDOR
            } catch (e: IOException) {
                Log.w(ETIQUETA, "No se pudieron descargar las incidencias: ${e.javaClass.simpleName}")
                ErrorIncidencias.SIN_RED
            } finally {
                temporal.delete()
            }
        // Aquí solo se llega si el intento terminó (con éxito o error): uno cancelado no cuenta.
        ultimoIntentoMs = reloj()
        ultimoFallo = error != null
        if (error != null) estado.value = estado.value.copy(errorUltimo = error)
    }

    /** Descarga a un temporal, lo valida leyéndolo y, si es bueno, lo deja como copia y publica el estado. */
    private fun descargarYGuardar(): Boolean {
        directorio.mkdirs()
        http.flujo(url, topeBytes).use { entrada ->
            GZIPOutputStream(temporal.outputStream().buffered()).use { entrada.copyTo(it) }
        }
        val resultado = abrirCopia(temporal).use { LectorDatex.leer(it) }
        if (resultado !is ResultadoDatex.Ok) {
            Log.w(ETIQUETA, "Incidencias no válidas: ${(resultado as ResultadoDatex.Error).motivo}")
            return false
        }
        val obtenidas = Instant.ofEpochMilli(reloj())
        Files.move(temporal.toPath(), copia.toPath(), StandardCopyOption.REPLACE_EXISTING)
        instante.writeText(obtenidas.toEpochMilli().toString())
        estado.value = IncidenciasVigentes(sinCaducadas(resultado), obtenidas, null)
        Log.i(ETIQUETA, "Incidencias descargadas: ${resultado.incidencias.size} de ${resultado.situacionesLeidas}")
        return true
    }

    companion object {
        /** Tope de bytes (descomprimidos) que se admiten del feed; el real ronda los 6 MB. */
        const val TOPE_BYTES = 20L * 1024 * 1024
        const val INTERVALO_MS = 5 * 60_000L
        const val INTERVALO_TRAS_ERROR_MS = 60_000L
        private const val NOMBRE_COPIA = "incidencias-dgt.xml.gz"
        private const val NOMBRE_INSTANTE = "incidencias-dgt.instante"
        private const val ETIQUETA = "RepositorioIncidencias"
    }
}
