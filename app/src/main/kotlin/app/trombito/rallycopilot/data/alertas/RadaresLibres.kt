package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.alertas.AlertasRadar
import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.geo.AjusteCamino
import app.trombito.rallycopilot.motor.geo.CaminoPlano
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.SentidoCamino
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.voz.Frase

/**
 * Avisos de radares en modo libre (sin ruta): con la geometría de la vía actual orientada en el sentido de la marcha
 * (ve los radares tras una curva) y, además, los que queden delante en línea recta. Las posiciones de las frases se
 * expresan en el eje de quien llama (`base` más la distancia al radar). No es concurrente.
 */
internal class RadaresLibres(
    private val radares: List<Radar>,
) {
    private val enRecta = AlertasRadar(radares)
    private var guardada: ViaOrientada? = null

    private class ViaOrientada(
        val trazado: List<PuntoGeo>,
        val camino: CaminoPlano,
        val invertida: Boolean,
        val longitudM: Double,
        val radar: AlertasRadar,
    )

    /** Frases vigentes con el coche en [fijacion] sobre [via] a [v] m/s; las de la vía mandan sobre las rectas. */
    fun frases(
        fijacion: Fijacion,
        via: ViaActual?,
        v: Double,
        base: Double,
    ): List<Frase> {
        val porVia = porVia(fijacion, via, v, base)
        val rectos = enRecta.candidatasLibre(fijacion.punto, fijacion.rumboGrados, v, base)
        return porVia + rectos.filter { recto -> porVia.none { it.id == recto.id } }
    }

    /** Radares por delante en la vía actual orientada en el sentido de la marcha; vacío si no se puede orientar. */
    private fun porVia(
        fijacion: Fijacion,
        via: ViaActual?,
        v: Double,
        base: Double,
    ): List<Frase> {
        val trazado = via?.trazado?.takeIf { it.size >= 2 }
        val orientada = trazado?.let { orientar(it, via.sentido, fijacion) }
        if (trazado == null || orientada == null) return emptyList()
        val progreso = GeometriaVia.progresoM(trazado, fijacion.punto)
        val alLoLargo = if (orientada.invertida) orientada.longitudM - progreso else progreso
        return orientada.radar.candidatas(alLoLargo, v).map { f ->
            f.copy(puntoM = f.puntoM?.let { base + it - alLoLargo })
        }
    }

    /** La vía con sus puntos en el sentido de la marcha, o null si el coche no está en ella o no se sabe el sentido. */
    private fun orientar(
        trazado: List<PuntoGeo>,
        sentido: SentidoCamino,
        fijacion: Fijacion,
    ): ViaOrientada? {
        val previa = guardada?.takeIf { it.trazado === trazado }
        val camino = previa?.camino ?: CaminoPlano(trazado)
        // Sin rumbo y en sentido ADELANTE, el ajuste da la tangente en el orden de los puntos.
        val ajuste = AjusteCamino.ajustar(camino, SentidoCamino.ADELANTE, fijacion.copy(rumboGrados = null), null)
        val tangente = ajuste.rumboGrados?.takeIf { ajuste.enCamino }
        val invertida = tangente?.let { invertida(sentido, it, fijacion.rumboGrados) }
        return when {
            invertida == null -> null
            previa != null && previa.invertida == invertida -> previa
            else -> nueva(trazado, camino, invertida)
        }
    }

    private fun nueva(
        trazado: List<PuntoGeo>,
        camino: CaminoPlano,
        invertida: Boolean,
    ): ViaOrientada {
        val puntos = if (invertida) trazado.asReversed() else trazado
        val orientada =
            ViaOrientada(trazado, camino, invertida, GeometriaVia.longitudM(trazado), AlertasRadar(radares, puntos))
        guardada = orientada
        return orientada
    }

    /** Si la marcha va contra el orden de los puntos; null si no se sabe (doble sentido sin rumbo). */
    private fun invertida(
        sentido: SentidoCamino,
        tangente: Double,
        rumbo: Double?,
    ): Boolean? =
        when (sentido) {
            SentidoCamino.ADELANTE -> false
            SentidoCamino.ATRAS -> true
            SentidoCamino.AMBOS -> rumbo?.let { GeometriaVia.diferenciaRumbo(it, tangente) > RUMBO_OPUESTO }
        }

    private companion object {
        /** Una vía de doble sentido se recorre «al revés» si el rumbo difiere más de esto del orden de sus puntos. */
        const val RUMBO_OPUESTO = 90.0
    }
}
