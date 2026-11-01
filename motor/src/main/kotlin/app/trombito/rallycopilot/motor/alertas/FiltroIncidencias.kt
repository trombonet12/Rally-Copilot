package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.AjusteCamino
import app.trombito.rallycopilot.motor.geo.CaminoPlano
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.voz.Frase
import java.time.Instant
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Una incidencia relevante para el conductor. [enRuta]: está en la ruta por delante; [distanciaM] es entonces lo que
 * falta por la ruta (0 si el coche ya está dentro de la incidencia) y, si no, la distancia en línea recta al punto o
 * segmento más cercano.
 */
data class IncidenciaCercana(
    val incidencia: Incidencia,
    val enRuta: Boolean,
    val distanciaM: Double,
)

/**
 * Decide qué incidencias importan y avisa de las de la ruta.
 *
 * - **De la ruta** (siempre, por lejos que estén): alguno de sus puntos queda a [DISTANCIA_RUTA_M] (50 m) o menos de la
 *   polilínea y por delante del coche (progreso ≥ posición). Se miran los puntos, no el segmento entero: un tramo largo
 *   cuya ruta lo cruce por el medio sin tocar sus extremos no cuenta como de la ruta (sí por cercanía).
 * - **Las demás**: a [RADIO_COCHE_M] (3 km) o menos del coche, midiendo al segmento si es un tramo. Una de la ruta que
 *   ya quedó atrás se trata como una de las demás.
 * - Orden: primero las de la ruta (por distancia), luego las demás (por distancia).
 * - Sin ruta, o sin posición en la traza en [relevantes], solo rige el radio de 3 km. Se descartan las que ya
 *   [Incidencia.caducadaEn] el instante [ahora] (si se da).
 *
 * Aviso por voz ([candidatas], solo de la ruta y solo de los [tiposAvisables]): desde la misma distancia que los
 * radares (`clamp(v·15 s, 300, 800)` redondeado a cientos), «obras a quinientos», «accidente…», «retención…»,
 * «carretera cortada…», «peligro…» (meteo) o «incidencia…» (otras); a menos de 50 m no dice la distancia. Id
 * `incidencia-<id>`, tipo ALERTA, `puntoM` = progreso de la incidencia. Dentro de una incidencia ya no se repite.
 */
class FiltroIncidencias(
    incidencias: List<Incidencia>,
    ruta: List<PuntoGeo>? = null,
    ahora: Instant? = null,
    private val tiposAvisables: Set<TipoIncidencia> = TipoIncidencia.entries.toSet(),
) {
    private class Sobre(
        val incidencia: Incidencia,
        val progresosM: List<Double>,
    )

    private val vigentes = incidencias.filter { ahora == null || !it.caducadaEn(ahora) }
    private val camino = ruta?.let { CaminoPlano(it) }
    private val sobres: List<Sobre> = vigentes.map { Sobre(it, progresosEnRuta(it)) }

    private fun progresosEnRuta(incidencia: Incidencia): List<Double> {
        if (camino == null) return emptyList()
        return incidencia.puntos.mapNotNull { p ->
            camino.masCercano(p, AjusteCamino.SIN_VENTANA)?.takeIf { it.distanciaM <= DISTANCIA_RUTA_M }?.progresoM
        }
    }

    /**
     * Incidencias relevantes con el coche en [coche]; [posicionM] es su progreso en la ruta (sin él no hay noción de
     * «por delante» y solo cuenta el radio).
     */
    fun relevantes(
        coche: PuntoGeo,
        posicionM: Double? = null,
    ): List<IncidenciaCercana> {
        val resultado =
            sobres.mapNotNull { sobre ->
                faltaPorRuta(sobre, posicionM)?.let { IncidenciaCercana(sobre.incidencia, true, it) }
                    ?: distanciaRecta(coche, sobre.incidencia.puntos)
                        .takeIf { it <= RADIO_COCHE_M }
                        ?.let { IncidenciaCercana(sobre.incidencia, false, it) }
            }
        return resultado.sortedWith(compareByDescending<IncidenciaCercana> { it.enRuta }.thenBy { it.distanciaM })
    }

    /** Avisos de voz vigentes en el progreso [posicionM] yendo a [vMs]; vacío sin ruta. */
    fun candidatas(
        posicionM: Double,
        vMs: Double,
    ): List<Frase> {
        val ventanaM = VentanasAviso.avisoM(vMs)
        return sobres
            .filter { it.incidencia.tipo in tiposAvisables }
            .mapNotNull { sobre ->
                val faltaM = faltaPorRuta(sobre, posicionM)
                if (faltaM == null || faltaM <= 0.0 || faltaM > ventanaM) null else sobre to faltaM
            }.sortedBy { it.second }
            .map { (sobre, faltaM) ->
                val distancia = VentanasAviso.aDistancia(faltaM)?.let { " $it" } ?: ""
                VentanasAviso.alerta(
                    "incidencia-${sobre.incidencia.id}",
                    palabra(sobre.incidencia.tipo) + distancia,
                    posicionM + faltaM,
                )
            }
    }

    /** Metros que faltan por la ruta hasta la incidencia (0 si el coche está dentro); `null` si no está por delante. */
    private fun faltaPorRuta(
        sobre: Sobre,
        posicionM: Double?,
    ): Double? {
        if (posicionM == null) return null
        val delante = sobre.progresosM.filter { it >= posicionM }
        return when {
            delante.isEmpty() -> null
            delante.size < sobre.progresosM.size -> 0.0
            else -> delante.min() - posicionM
        }
    }

    private fun palabra(tipo: TipoIncidencia): String =
        when (tipo) {
            TipoIncidencia.OBRAS -> "obras"
            TipoIncidencia.ACCIDENTE -> "accidente"
            TipoIncidencia.RETENCION -> "retención"
            TipoIncidencia.CORTE -> "carretera cortada"
            TipoIncidencia.METEO -> "peligro"
            TipoIncidencia.OTRA -> "incidencia"
        }

    /** Distancia en metros del [coche] al punto o a la polilínea de [puntos] (plano local alrededor del coche). */
    private fun distanciaRecta(
        coche: PuntoGeo,
        puntos: List<PuntoGeo>,
    ): Double {
        val escalaX = PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(coche.lat))
        val xs = puntos.map { Math.toRadians(it.lon - coche.lon) * escalaX }
        val ys = puntos.map { Math.toRadians(it.lat - coche.lat) * PuntoGeo.RADIO_TIERRA_M }
        if (puntos.size == 1) return hypot(xs[0], ys[0])
        return (0 until puntos.lastIndex).minOf { i -> distanciaASegmento(xs[i], ys[i], xs[i + 1], ys[i + 1]) }
    }

    /** Distancia del origen al segmento (ax, ay)–(bx, by). */
    private fun distanciaASegmento(
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val largo2 = dx * dx + dy * dy
        val t = if (largo2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / largo2).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    companion object {
        /** Distancia máxima de un punto de la incidencia a la ruta para ser «de la ruta». */
        const val DISTANCIA_RUTA_M = 50.0

        /** Radio alrededor del coche para las incidencias que no son de la ruta. */
        const val RADIO_COCHE_M = 3000.0
    }
}
