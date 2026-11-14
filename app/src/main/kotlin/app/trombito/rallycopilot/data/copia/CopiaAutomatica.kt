package app.trombito.rallycopilot.data.copia

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import app.trombito.rallycopilot.data.diagnostico.carpetaExternaPrivada
import app.trombito.rallycopilot.domain.copia.CopiaAutomaticaDatos
import app.trombito.rallycopilot.domain.copia.CopiaDatos
import app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Escribe la copia de [CopiaDatos] en `getExternalFilesDir(null)/copias/`: `rally-ultima.zip` (siempre la más reciente,
 * escrita a `.tmp` y renombrada de forma atómica) y una fechada `rally-AAAA-MM-DD-HHmm.zip`, de las que solo quedan las
 * [MAX_FECHADAS] más nuevas. Un [Mutex] impide que dos copias se solapen y cualquier error se registra (sin datos) y se
 * devuelve como `false`: nunca rompe la app. Sin almacenamiento externo, o por debajo de la API 30 (la carpeta externa
 * sería legible por otras apps), no hace nada. El interruptor vive en un
 * archivo de preferencias propio (como el tutorial), no en Room: no viaja en la copia de seguridad.
 */
class CopiaAutomatica(
    private val contexto: Context,
    private val copia: CopiaDatos,
    private val dispatcher: CoroutineDispatcher,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val zona: ZoneId = ZoneId.systemDefault(),
    private val registro: RegistroDiagnostico = RegistroDiagnostico.Nulo,
) : CopiaAutomaticaDatos {
    // Perezosas: Hilt crea esta clase en el hilo principal durante el arranque y las preferencias son disco.
    private val preferencias: SharedPreferences by lazy {
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
    }
    private val _activa by lazy { MutableStateFlow(preferencias.getBoolean(CLAVE_ACTIVA, true)) }
    private val cerrojo = Mutex()

    override val activa: StateFlow<Boolean> by lazy { _activa.asStateFlow() }

    override fun activar(valor: Boolean) {
        preferencias.edit().putBoolean(CLAVE_ACTIVA, valor).apply()
        _activa.value = valor
    }

    override suspend fun copiarAhora(): Boolean = cerrojo.withLock { copiar(ahora()) }

    /** Al terminar un viaje: si ya hay una fechada de hoy, solo se refresca `rally-ultima.zip` (sin otra fechada). */
    override suspend fun copiarAlTerminar(): Boolean =
        if (_activa.value) cerrojo.withLock { copiar(ahora(), soloUltimaSiHayDeHoy = true) } else false

    override suspend fun copiarSiToca(ahoraMs: Long): Boolean {
        if (!_activa.value) return false
        // La comprobación va dentro del cerrojo: dos llamadas seguidas no copian dos veces.
        return cerrojo.withLock { if (tocaCopiar(ahoraMs)) copiar(ahoraMs) else false }
    }

    private suspend fun tocaCopiar(ahoraMs: Long): Boolean =
        withContext(dispatcher) {
            val ultima = fechadas(carpeta(crear = false)).firstOrNull() ?: return@withContext true
            ahoraMs - ultima.lastModified() > UMBRAL_MS
        }

    @Suppress("TooGenericExceptionCaught") // la copia automática jamás debe tumbar la app, falle lo que falle
    private suspend fun copiar(
        ahoraMs: Long,
        soloUltimaSiHayDeHoy: Boolean = false,
    ): Boolean =
        withContext(dispatcher) {
            val destino = carpeta(crear = true) ?: return@withContext false
            val ultima = File(destino, NOMBRE_ULTIMA)
            val temporal = File(destino, NOMBRE_ULTIMA + SUFIJO_TMP)
            val fechada = File(destino, nombreFechada(ahoraMs))
            val temporalFechada = File(destino, fechada.name + SUFIJO_TMP)
            val dia = DIA.format(Instant.ofEpochMilli(ahoraMs).atZone(zona))
            val hayDeHoy = soloUltimaSiHayDeHoy && fechadas(destino).any { it.name.startsWith("rally-$dia-") }
            try {
                temporal.outputStream().use { copia.exportar(it) }
                mover(temporal, ultima)
                if (hayDeHoy) {
                    registro.info(ETIQUETA, "copia automatica refrescada bytes=${ultima.length()}")
                    return@withContext true
                }
                Files.copy(ultima.toPath(), temporalFechada.toPath(), StandardCopyOption.REPLACE_EXISTING)
                mover(temporalFechada, fechada)
                fechada.setLastModified(ahoraMs)
                fechadas(destino).drop(MAX_FECHADAS).forEach { it.delete() }
                registro.info(ETIQUETA, "copia automatica hecha bytes=${ultima.length()}")
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(ETIQUETA, "No se pudo guardar la copia automática: ${e.javaClass.simpleName}")
                registro.error(ETIQUETA, "copia automatica fallida tipo=${e.javaClass.simpleName}")
                false
            } finally {
                temporal.delete()
                temporalFechada.delete()
            }
        }

    private fun mover(
        origen: File,
        destino: File,
    ) {
        Files.move(
            origen.toPath(),
            destino.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    /** Las fechadas, de la más nueva a la más antigua (el nombre ordena por fecha). */
    private fun fechadas(destino: File?): List<File> =
        destino
            ?.listFiles { archivo -> PATRON_FECHADA.matches(archivo.name) }
            .orEmpty()
            .sortedByDescending { it.name }

    private fun carpeta(crear: Boolean): File? {
        val raiz = carpetaExternaPrivada(contexto) ?: return null
        val carpeta = File(raiz, CARPETA)
        if (crear) carpeta.mkdirs()
        return carpeta.takeIf { it.isDirectory }
    }

    private fun nombreFechada(ahoraMs: Long): String =
        "rally-${FORMATO.format(Instant.ofEpochMilli(ahoraMs).atZone(zona))}.zip"

    companion object {
        const val CARPETA = "copias"
        const val NOMBRE_ULTIMA = "rally-ultima.zip"
        const val MAX_FECHADAS = 5

        /** Una vez al día con margen: la última fechada debe tener más de 20 h para volver a copiar al arrancar. */
        const val UMBRAL_MS = 20L * 60 * 60 * 1000

        private const val ARCHIVO = "copia_automatica"
        private const val CLAVE_ACTIVA = "activa"
        private const val SUFIJO_TMP = ".tmp"
        private const val ETIQUETA = "CopiaAutomatica"
        private val FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
        private val DIA = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val PATRON_FECHADA = Regex("""rally-\d{4}-\d{2}-\d{2}-\d{4}\.zip""")
    }
}
