package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.geo.AjusteCamino
import app.trombito.rallycopilot.motor.geo.CaminoPlano
import app.trombito.rallycopilot.motor.geo.PosicionAjustada
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.SentidoCamino
import app.trombito.rallycopilot.motor.velocidad.Fijacion

/**
 * Posición y rumbo que pinta el mapa: la fijación ajustada al CAMINO (ver [AjusteCamino]) y no el rumbo bruto del GPS,
 * que en el móvil sale torcido. Con estado (rumbo mostrado, caminos preparados): una instancia por colección de
 * fijaciones, usada desde un solo hilo.
 *
 * Camino: en guiado, la ruta activa (sentido de la ruta, buscando cerca del progreso que publica el servicio, por si
 * la ruta pasa dos veces por el mismo sitio); en libre, o si en guiado la fijación se sale de la ruta, la geometría
 * de la vía actual. Sin camino a menos de 25 m (salida de la vía, cambio de sentido, sin datos de vías) la fijación
 * pasa tal cual. La grabación, el seguimiento de la ruta y las notas no pasan por aquí: usan la fijación cruda.
 */
internal class PosicionSobreCamino(
    private val navegacion: ControlNavegacion,
) {
    private class Preparado {
        private var puntos: List<PuntoGeo>? = null
        private var camino: CaminoPlano? = null

        /** Reutiliza el camino mientras sea la misma lista (misma ruta o misma vía). */
        fun de(nuevos: List<PuntoGeo>): CaminoPlano? {
            if (nuevos !== puntos) {
                puntos = nuevos
                camino = if (nuevos.size >= MIN_PUNTOS) CaminoPlano(nuevos) else null
            }
            return camino
        }
    }

    private val ruta = Preparado()
    private val via = Preparado()
    private var rumbo: Double? = null

    /** [fijacion] ya lleva la velocidad estimada (la del receptor puede faltar); [viaActual], la vía bajo ella. */
    fun ajustar(
        fijacion: Fijacion,
        viaActual: ViaActual?,
    ): Fijacion {
        val porRuta = ajustarARuta(fijacion)
        val porVia = if (porRuta?.enCamino == true) null else ajustarAVia(fijacion, viaActual)
        val elegida = listOfNotNull(porRuta, porVia).firstOrNull { it.enCamino } ?: porVia ?: porRuta
        // Sin rumbo (receptor sin él y sin movimiento estimado) se conserva el último mostrado: un rumbo vacío llega al
        // mapa como 0° y el marcador gira al norte y vuelve.
        if (elegida == null) {
            rumbo = fijacion.rumboGrados ?: rumbo
            return fijacion.copy(rumboGrados = rumbo)
        }
        rumbo = elegida.rumboGrados ?: rumbo
        return fijacion.copy(punto = elegida.punto, rumboGrados = rumbo)
    }

    private fun ajustarARuta(fijacion: Fijacion): PosicionAjustada? {
        val guiando = navegacion.estado.value as? EstadoNavegacion.Guiando
        val camino = guiando?.let { ruta.de(it.ruta.puntos) } ?: return null
        val progreso = guiando.progreso?.progresoM
        val ventana = progreso?.let { (it - ATRAS_M)..(it + ADELANTE_M) } ?: AjusteCamino.SIN_VENTANA
        return AjusteCamino.ajustar(camino, SentidoCamino.ADELANTE, fijacion, rumbo, ventana)
    }

    private fun ajustarAVia(
        fijacion: Fijacion,
        viaActual: ViaActual?,
    ): PosicionAjustada? {
        val camino = viaActual?.let { via.de(it.trazado) } ?: return null
        return AjusteCamino.ajustar(camino, viaActual.sentido, fijacion, rumbo)
    }

    private companion object {
        const val MIN_PUNTOS = 2

        /** Margen del tramo de ruta donde se busca, respecto al progreso publicado (puede ir algo retrasado). */
        const val ATRAS_M = 200.0
        const val ADELANTE_M = 800.0
    }
}
