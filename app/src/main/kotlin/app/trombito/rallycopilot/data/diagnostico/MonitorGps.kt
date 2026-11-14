package app.trombito.rallycopilot.data.diagnostico

import app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico
import app.trombito.rallycopilot.motor.velocidad.DetectorSenalGps
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.velocidad.PrecisionGps
import java.util.Locale

/**
 * Resume en el registro lo que hace el GPS durante una conducción: cambios «sin señal» / «recuperado» (con el mismo
 * [DetectorSenalGps] que la banda del mapa) y, cada [resumenCadaMs], las fijaciones por minuto y la precisión media.
 * Todo con el reloj monótono de las fijaciones; no escribe nunca coordenadas. Una instancia por conducción; se toca
 * desde una sola corrutina.
 */
class MonitorGps(
    private val registro: RegistroDiagnostico,
    private val resumenCadaMs: Long = RESUMEN_MS,
) {
    private val detector = DetectorSenalGps()
    private var sinSenal = false
    private var inicioVentanaMs: Long? = null
    private var fijaciones = 0
    private var utiles = 0
    private var sumaPrecisionM = 0.0
    private var conPrecision = 0

    fun fijacion(
        fijacion: Fijacion,
        ahoraMs: Long,
    ) {
        if (inicioVentanaMs == null) inicioVentanaMs = ahoraMs
        detector.fijacion(fijacion, ahoraMs)
        fijaciones++
        if (PrecisionGps.esUtil(fijacion.precisionM)) utiles++
        fijacion.precisionM?.let {
            sumaPrecisionM += it
            conPrecision++
        }
        tick(ahoraMs)
    }

    /** Llamar ~1 vez por segundo aunque no lleguen fijaciones (la pérdida de señal se nota por el silencio). */
    fun tick(ahoraMs: Long) {
        val inicio = inicioVentanaMs ?: ahoraMs.also { inicioVentanaMs = it }
        val nueva = detector.sinSenal(ahoraMs)
        if (nueva != sinSenal) {
            sinSenal = nueva
            if (nueva) {
                val hace = detector.ultimaUtilMs?.let { (ahoraMs - it) / MS_POR_S }
                registro.aviso(ETIQUETA, "gps sin senal ultima_fijacion_util_hace_s=${hace ?: -1}")
            } else {
                registro.info(ETIQUETA, "gps recuperado")
            }
        }
        val transcurrido = ahoraMs - inicio
        if (transcurrido >= resumenCadaMs) {
            resumir(transcurrido)
            inicioVentanaMs = ahoraMs
        }
    }

    private fun resumir(transcurridoMs: Long) {
        val porMinuto = fijaciones * MS_POR_MIN.toDouble() / transcurridoMs
        val media = if (conPrecision > 0) sumaPrecisionM / conPrecision else null
        registro.info(
            ETIQUETA,
            "gps resumen fijaciones_min=${formato(porMinuto)} utiles=$utiles total=$fijaciones " +
                "precision_media_m=${media?.let(::formato) ?: "n/d"} sin_senal=$sinSenal",
        )
        fijaciones = 0
        utiles = 0
        sumaPrecisionM = 0.0
        conPrecision = 0
    }

    private fun formato(valor: Double): String = String.format(Locale.ROOT, "%.1f", valor)

    private companion object {
        const val ETIQUETA = "Gps"
        const val RESUMEN_MS = 60_000L
        const val MS_POR_S = 1_000L
        const val MS_POR_MIN = 60_000L
    }
}
