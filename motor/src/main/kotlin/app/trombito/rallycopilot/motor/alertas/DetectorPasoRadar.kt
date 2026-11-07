package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import kotlin.math.hypot

/**
 * Se acaba de pasar por un radar fijo: [velocidadKmh] es la de la fijación más cercana al radar y [limiteKmh] el
 * límite que controla (`null` si no se conoce).
 */
data class PasoRadar(
    val radarId: String,
    val velocidadKmh: Double,
    val limiteKmh: Int?,
)

/**
 * Detecta el paso por los radares fijos de [radares] a partir de las fijaciones, sin necesitar ruta: un radar «se
 * cruza» cuando el coche, que venía acercándose, queda a [CRUCE_M] (30 m) o menos en su fijación más próxima y la
 * siguiente ya se aleja (o sale de [RADIO_M]). El evento se da una sola vez por pasada, en esa fijación siguiente, con
 * la velocidad de la más cercana.
 *
 * - Un radar con rumbo solo cuenta si la marcha (el rumbo de la fijación o, sin él, el del movimiento desde la
 *   anterior) difiere menos de [UMBRAL_RUMBO_GRADOS] (45°): los del sentido contrario no se registran. Sin marcha
 *   conocida, un radar con rumbo no cuenta.
 * - Solo se emite si se vio acercarse (una fijación ya pegada al radar que se aleja no es un paso).
 * - No es concurrente. No guarda posiciones más allá de la última fijación.
 */
class DetectorPasoRadar(
    radares: List<Radar>,
) {
    private val fijos = radares.filter { it.tipo == TipoRadar.FIJO }

    private class Seguimiento {
        var distanciaMinM = Double.MAX_VALUE
        var velocidadKmh = 0.0
        var marchaOk = false
        var acercandose = false
        var ultimaM = Double.MAX_VALUE
        var emitido = false
    }

    private val activos = HashMap<String, Seguimiento>()
    private var anterior: PuntoGeo? = null

    /**
     * Una fijación en [punto] a [velocidadKmh] con el [rumboGrados] del receptor (o `null`). Devuelve el paso
     * ocurrido con esta fijación, si lo hay (el primero si cruzara dos a la vez).
     */
    fun actualizar(
        punto: PuntoGeo,
        rumboGrados: Double?,
        velocidadKmh: Double,
    ): PasoRadar? {
        val marcha = rumboGrados ?: anterior?.let { rumboMovimiento(it, punto) }
        anterior = punto
        var paso: PasoRadar? = null
        for (radar in fijos) {
            val d = distancia(punto, radar.punto)
            if (d <= RADIO_M) {
                val resultado = dentro(radar, d, marcha, velocidadKmh)
                if (paso == null) paso = resultado
            } else {
                val resultado = activos.remove(radar.id)?.let { cierre(radar, it) }
                if (paso == null) paso = resultado
            }
        }
        return paso
    }

    private fun dentro(
        radar: Radar,
        d: Double,
        marcha: Double?,
        velocidadKmh: Double,
    ): PasoRadar? {
        val s = activos.getOrPut(radar.id) { Seguimiento() }
        var resultado: PasoRadar? = null
        if (d < s.distanciaMinM) {
            s.distanciaMinM = d
            s.velocidadKmh = velocidadKmh
            s.marchaOk = marchaOk(radar, marcha)
        }
        if (d < s.ultimaM && s.ultimaM != Double.MAX_VALUE) s.acercandose = true
        if (d > s.distanciaMinM && s.distanciaMinM <= CRUCE_M && !s.emitido) resultado = cierre(radar, s)
        s.ultimaM = d
        return resultado
    }

    /** El paso de [radar] si el seguimiento [s] cumple las condiciones (y lo marca como emitido), o `null`. */
    private fun cierre(
        radar: Radar,
        s: Seguimiento,
    ): PasoRadar? {
        if (s.emitido) return null
        s.emitido = true
        val cuenta = s.acercandose && s.distanciaMinM <= CRUCE_M && s.marchaOk
        return if (cuenta) PasoRadar(radar.id, s.velocidadKmh, radar.limiteKmh) else null
    }

    private fun marchaOk(
        radar: Radar,
        marcha: Double?,
    ): Boolean {
        val vigilado = radar.rumboGrados ?: return true
        return marcha != null && Rumbo.diferencia(marcha, vigilado) < UMBRAL_RUMBO_GRADOS
    }

    private fun distancia(
        a: PuntoGeo,
        b: PuntoGeo,
    ): Double = ProyeccionPlana(a).distancia(a, b)

    /** Rumbo (0 = norte, horario) del movimiento de [de] a [a]; `null` si apenas se movió (ruido del GPS). */
    private fun rumboMovimiento(
        de: PuntoGeo,
        a: PuntoGeo,
    ): Double? {
        val plano = ProyeccionPlana(de)
        val este = plano.x(a)
        val norte = plano.y(a)
        return if (hypot(este, norte) < MOVIMIENTO_MIN_M) null else Rumbo.deVector(este, norte)
    }

    companion object {
        /** Se sigue a un radar mientras el coche esté a esta distancia o menos. */
        const val RADIO_M = 100.0

        /** El radar cuenta como cruzado si la fijación más próxima quedó a esto o menos. */
        const val CRUCE_M = 30.0

        /** Un radar con rumbo solo cuenta si la marcha difiere menos de esto. */
        const val UMBRAL_RUMBO_GRADOS = 45.0
        private const val MOVIMIENTO_MIN_M = 3.0
    }
}
