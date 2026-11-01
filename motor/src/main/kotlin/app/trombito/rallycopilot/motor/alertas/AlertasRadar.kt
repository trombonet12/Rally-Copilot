package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.AjusteCamino
import app.trombito.rallycopilot.motor.geo.CaminoPlano
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.voz.Frase
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Avisos de radares por delante en el sentido de la marcha.
 *
 * - **Con ruta** (`ruta` con al menos dos puntos, en el sentido de la marcha): cada radar se proyecta una vez sobre la
 *   polilínea y solo cuenta si queda a [DISTANCIA_CAMINO_M] (30 m) o menos y, si tiene rumbo, difiere menos de
 *   [UMBRAL_RUMBO_GRADOS] (45°) del rumbo de la ruta en ese punto. [candidatas] recibe el progreso en la ruta (m).
 *   Si la ruta pasa dos veces por el mismo radar, solo cuenta el pasaje más cercano (el primero en empate).
 * - **Libre** ([candidatasLibre]): sin camino conocido, el «camino» es la recta que sale de la posición con el rumbo
 *   de marcha; un radar cuenta si está delante y a ≤ 30 m de esa recta. En curva solo se verá al acercarse; con la
 *   geometría de la vía actual como `ruta` saldría antes.
 *
 * Avisos (todos [app.trombito.rallycopilot.motor.voz.TipoFrase.ALERTA], id `radar-<id del radar>`):
 * - fijo: desde `clamp(v·15 s, 300, 800)` redondeado a cientos, «radar a quinientos, ochenta» (la distancia es la que
 *   queda redondeada a cientos, y a menos de 50 m «radar, ochenta»; sin límite no se dice);
 * - inicio de tramo y zona móvil: desde `clamp(v·6 s, 80, 250)` m, «tramo de radar, límite ochenta» y «zona de radar
 *   móvil». El final de tramo no se anuncia (de eso se encarga [VelocidadMediaTramo]).
 */
class AlertasRadar(
    private val radares: List<Radar>,
    ruta: List<PuntoGeo>? = null,
) {
    private val camino = ruta?.let { CaminoPlano(it) }
    private val enRuta: List<Colocado> =
        if (camino == null) emptyList() else radares.mapNotNull { colocar(camino, it) }.sortedBy { it.progresoM }

    /** Tramos completos de la ruta (inicio y fin con el mismo `tramo_id`, en ese orden de marcha). */
    val tramos: List<TramoRadar> =
        enRuta.filter { it.radar.tipo == TipoRadar.TRAMO_INICIO }.mapNotNull { emparejar(it) }

    /** Avisos vigentes en el progreso [posicionM] de la ruta yendo a [vMs]; vacío si no se creó con ruta. */
    fun candidatas(
        posicionM: Double,
        vMs: Double,
    ): List<Frase> = enRuta.mapNotNull { frase(it.radar, it.progresoM - posicionM, vMs, it.progresoM) }

    /**
     * Avisos en modo libre desde [posicion] con [rumboGrados] (sin rumbo no se sabe qué hay por delante). [posicionM]
     * es la posición en la traza, si se lleva; con ella, `puntoM` de cada frase es esa posición más la distancia al
     * radar, y sin ella es `null`.
     */
    fun candidatasLibre(
        posicion: PuntoGeo,
        rumboGrados: Double?,
        vMs: Double,
        posicionM: Double? = null,
    ): List<Frase> {
        if (rumboGrados == null) return emptyList()
        return radares
            .mapNotNull { radar -> delante(posicion, rumboGrados, radar)?.let { it to radar } }
            .sortedBy { it.first }
            .mapNotNull { (delanteM, radar) -> frase(radar, delanteM, vMs, posicionM?.plus(delanteM)) }
    }

    /** Metros por delante (sobre la recta del rumbo) si el radar cuenta; `null` si está de lado o en otro sentido. */
    private fun delante(
        posicion: PuntoGeo,
        rumboGrados: Double,
        radar: Radar,
    ): Double? {
        val rumbo = Math.toRadians(rumboGrados)
        val escalaX = PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(posicion.lat))
        val este = Math.toRadians(radar.punto.lon - posicion.lon) * escalaX
        val norte = Math.toRadians(radar.punto.lat - posicion.lat) * PuntoGeo.RADIO_TIERRA_M
        val lateralM = abs(este * cos(rumbo) - norte * sin(rumbo))
        val vigilado = radar.rumboGrados
        val sentidoOk = vigilado == null || VentanasAviso.diferenciaRumbo(rumboGrados, vigilado) < UMBRAL_RUMBO_GRADOS
        return if (lateralM <= DISTANCIA_CAMINO_M && sentidoOk) este * sin(rumbo) + norte * cos(rumbo) else null
    }

    private class Colocado(
        val radar: Radar,
        val progresoM: Double,
    )

    private fun colocar(
        camino: CaminoPlano,
        radar: Radar,
    ): Colocado? {
        val p = camino.masCercano(radar.punto, AjusteCamino.SIN_VENTANA)
        if (p == null || p.distanciaM > DISTANCIA_CAMINO_M) return null
        val vigilado = radar.rumboGrados
        val sentidoOk =
            vigilado == null ||
                VentanasAviso.diferenciaRumbo(camino.rumboEn(p, SEMIVENTANA_M), vigilado) < UMBRAL_RUMBO_GRADOS
        return if (sentidoOk) Colocado(radar, p.progresoM) else null
    }

    private fun emparejar(inicio: Colocado): TramoRadar? {
        val id = inicio.radar.tramoId ?: return null
        val fin = enRuta.firstOrNull { it.radar.tipo == TipoRadar.TRAMO_FIN && it.radar.tramoId == id }
        return if (fin == null || fin.progresoM <= inicio.progresoM) {
            null
        } else {
            TramoRadar(id, inicio.progresoM, fin.progresoM, inicio.radar.limiteKmh ?: fin.radar.limiteKmh)
        }
    }

    private fun frase(
        radar: Radar,
        restanteM: Double,
        vMs: Double,
        puntoM: Double?,
    ): Frase? {
        if (restanteM <= 0.0) return null
        val texto =
            when (radar.tipo) {
                TipoRadar.FIJO -> if (restanteM <= VentanasAviso.avisoM(vMs)) textoFijo(radar, restanteM) else null
                TipoRadar.TRAMO_INICIO -> if (restanteM <= VentanasAviso.entradaM(vMs)) textoTramo(radar) else null
                TipoRadar.ZONA_MOVIL -> if (restanteM <= VentanasAviso.entradaM(vMs)) "zona de radar móvil" else null
                TipoRadar.TRAMO_FIN -> null
            }
        return texto?.let { VentanasAviso.alerta("radar-${radar.id}", it, puntoM) }
    }

    private fun textoFijo(
        radar: Radar,
        restanteM: Double,
    ): String {
        val distancia = VentanasAviso.aDistancia(restanteM)?.let { " $it" } ?: ""
        val limite = radar.limiteKmh?.let { ", ${FrasesNota.enPalabras(it)}" } ?: ""
        return "radar$distancia$limite"
    }

    private fun textoTramo(radar: Radar): String =
        "tramo de radar" + (radar.limiteKmh?.let { ", límite ${FrasesNota.enPalabras(it)}" } ?: "")

    companion object {
        /** Distancia máxima del radar al camino. */
        const val DISTANCIA_CAMINO_M = 30.0

        /** Un radar con rumbo solo cuenta si el rumbo de marcha difiere menos de esto. */
        const val UMBRAL_RUMBO_GRADOS = 45.0
        private const val SEMIVENTANA_M = 5.0
    }
}
