package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.AjusteCamino
import app.trombito.rallycopilot.motor.geo.CaminoPlano
import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
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
 *   Los radares de tramo no se descartan por rumbo (el de su recta no es el de la vía): cuenta la cercanía y que el
 *   fin quede después del inicio.
 * - **Libre** ([candidatasLibre]): sin camino conocido, el «camino» es la recta que sale de la posición con el rumbo
 *   de marcha; un radar cuenta si está delante y a ≤ 30 m de esa recta. En curva solo se verá al acercarse; con la
 *   geometría de la vía actual como `ruta` saldría antes.
 *
 * Avisos (todos [app.trombito.rallycopilot.motor.voz.TipoFrase.ALERTA], id `radar-<id del radar>`):
 * - fijo: desde `clamp(v·15 s, 200, 800)` redondeado a cientos, «radar a quinientos, ochenta» (la distancia es la que
 *   queda redondeada a cientos; sin límite no se dice), y un segundo aviso a 100 m o menos, «radar, ochenta» (id
 *   `radar-<id>-cerca`; el primero deja de sonar al entrar en esos 100 m);
 * - fijo del sentido contrario (su rumbo es el opuesto, ±45°): «radar, sentido contrario» una sola vez (id
 *   `radar-<id>-contra`) desde la misma distancia que el primer aviso; los que miran de lado no cuentan. Solo los
 *   fijos: los tramos y zonas del otro sentido no se avisan. La frase existe para el banner, pero **no se dice**:
 *   el copiloto de voz la filtra con [esContrario] (información sin acción);
 * - inicio de tramo y zona móvil: desde `clamp(v·6 s, 80, 250)` m, «tramo de radar, límite ochenta» y «zona de radar
 *   móvil». El final de tramo no se anuncia (de eso se encarga [VelocidadMediaTramo]).
 */
class AlertasRadar(
    private val radares: List<Radar>,
    ruta: List<PuntoGeo>? = null,
) {
    private val camino = ruta?.let { CaminoPlano(it) }
    private val colocados: List<Colocado> =
        if (camino == null) emptyList() else radares.mapNotNull { colocar(camino, it) }.sortedBy { it.progresoM }
    private val parejas: List<Pair<Colocado, TramoRadar>> =
        colocados
            .filter { it.radar.tipo == TipoRadar.TRAMO_INICIO }
            .mapNotNull { inicio -> emparejar(inicio)?.let { inicio to it } }

    /**
     * Tramos completos de la ruta (inicio y fin con el mismo `tramo_id`, en ese orden de marcha). Se colocan por
     * cercanía a la ruta y por orden (fin después de inicio), no por el rumbo de la recta inicio-fin, que en una vía
     * con curvas no es el de la ruta.
     */
    val tramos: List<TramoRadar> = parejas.map { it.second }

    /** Lo que avisa [candidatas]: sin inicios de tramo sin pareja (la ruta pasa por ellos en el sentido contrario). */
    private val enRuta: List<Colocado> =
        colocados.filter { it.radar.tipo != TipoRadar.TRAMO_INICIO || parejas.any { (ini, _) -> ini === it } }

    /** Avisos vigentes en el progreso [posicionM] de la ruta yendo a [vMs]; vacío si no se creó con ruta. */
    fun candidatas(
        posicionM: Double,
        vMs: Double,
    ): List<Frase> = enRuta.mapNotNull { frase(it.radar, it.progresoM - posicionM, vMs, it.progresoM, it.contrario) }

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
            .sortedBy { it.first.metros }
            .mapNotNull { (d, radar) -> frase(radar, d.metros, vMs, posicionM?.plus(d.metros), d.contrario) }
    }

    /** Metros por delante (sobre la recta del rumbo) si el radar cuenta; `null` si está de lado o en otro sentido. */
    private fun delante(
        posicion: PuntoGeo,
        rumboGrados: Double,
        radar: Radar,
    ): Delante? {
        val rumbo = Math.toRadians(rumboGrados)
        val plano = ProyeccionPlana(posicion)
        val este = plano.x(radar.punto)
        val norte = plano.y(radar.punto)
        val lateralM = abs(este * cos(rumbo) - norte * sin(rumbo))
        val contrario = contrarioSiCuenta(radar, rumboGrados) ?: return null
        return if (lateralM <= DISTANCIA_CAMINO_M) Delante(este * sin(rumbo) + norte * cos(rumbo), contrario) else null
    }

    private class Delante(
        val metros: Double,
        val contrario: Boolean,
    )

    /**
     * `false` si el radar vigila el sentido de [marchaGrados] (o ambos), `true` si es un fijo del sentido contrario
     * (±45° del opuesto) y `null` si no cuenta (mira de lado, o es contrario pero no un fijo).
     */
    private fun contrarioSiCuenta(
        radar: Radar,
        marchaGrados: Double,
    ): Boolean? {
        val vigilado = radar.rumboGrados ?: return false
        val diferencia = Rumbo.diferencia(marchaGrados, vigilado)
        return when {
            diferencia < UMBRAL_RUMBO_GRADOS -> false
            diferencia > LLANO_GRADOS - UMBRAL_RUMBO_GRADOS && radar.tipo == TipoRadar.FIJO -> true
            else -> null
        }
    }

    private class Colocado(
        val radar: Radar,
        val progresoM: Double,
        val contrario: Boolean = false,
    )

    private fun colocar(
        camino: CaminoPlano,
        radar: Radar,
    ): Colocado? {
        val p = camino.masCercano(radar.punto, AjusteCamino.SIN_VENTANA)?.takeIf { it.distanciaM <= DISTANCIA_CAMINO_M }
        val esTramo = radar.tipo == TipoRadar.TRAMO_INICIO || radar.tipo == TipoRadar.TRAMO_FIN
        // Los de tramo no se descartan por rumbo (el de la recta no es el de la vía): vale la cercanía y el orden.
        val contrario =
            if (esTramo) {
                p?.let { false }
            } else {
                p?.let { contrarioSiCuenta(radar, camino.rumboEn(it, SEMIVENTANA_M)) }
            }
        return if (p == null || contrario == null) null else Colocado(radar, p.progresoM, contrario)
    }

    private fun emparejar(inicio: Colocado): TramoRadar? {
        val id = inicio.radar.tramoId ?: return null
        val fin = colocados.firstOrNull { it.radar.tipo == TipoRadar.TRAMO_FIN && it.radar.tramoId == id }
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
        contrario: Boolean,
    ): Frase? {
        if (restanteM <= 0.0) return null
        val prefijo = "$PREFIJO_ID${radar.id}"
        val aviso =
            when {
                radar.tipo == TipoRadar.FIJO -> {
                    fijo(radar, prefijo, restanteM, vMs, contrario)
                }

                contrario -> {
                    null
                }

                radar.tipo == TipoRadar.TRAMO_INICIO -> {
                    if (restanteM <= VentanasAviso.entradaM(vMs)) prefijo to textoTramo(radar) else null
                }

                radar.tipo == TipoRadar.ZONA_MOVIL -> {
                    if (restanteM <= VentanasAviso.entradaM(vMs)) prefijo to "zona de radar móvil" else null
                }

                else -> {
                    null
                }
            }
        return aviso?.let { (id, texto) -> VentanasAviso.alerta(id, texto, puntoM) }
    }

    /** Id y texto del aviso de un radar fijo según lo que queda, o `null` si aún no toca. */
    private fun fijo(
        radar: Radar,
        prefijo: String,
        restanteM: Double,
        vMs: Double,
        contrario: Boolean,
    ): Pair<String, String>? =
        when {
            restanteM > VentanasAviso.avisoRadarM(vMs) -> null
            contrario -> "$prefijo$SUFIJO_CONTRARIO" to "radar, sentido contrario"
            restanteM <= SEGUNDO_AVISO_M -> "$prefijo$SUFIJO_CERCA" to textoFijo(radar, 0.0)
            else -> prefijo to textoFijo(radar, restanteM)
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
        private const val LLANO_GRADOS = 180.0

        /** El segundo aviso de un radar fijo suena a esta distancia o menos. */
        const val SEGUNDO_AVISO_M = 100.0

        private const val PREFIJO_ID = "radar-"
        private const val SUFIJO_CERCA = "-cerca"
        private const val SUFIJO_CONTRARIO = "-contra"

        /**
         * Id del radar al que pertenece una frase de este objeto (`radar-<id>[-cerca|-contra]`), o `null` si no es de
         * radar. Se asume que ningún id de radar de los datos acaba en `-cerca` o `-contra`.
         */
        fun idDeRadar(idFrase: String): String? =
            idFrase
                .takeIf { it.startsWith(PREFIJO_ID) }
                ?.removePrefix(PREFIJO_ID)
                ?.removeSuffix(SUFIJO_CERCA)
                ?.removeSuffix(SUFIJO_CONTRARIO)

        /** La frase avisa de un radar fijo del sentido contrario. */
        fun esContrario(idFrase: String): Boolean = idFrase.startsWith(PREFIJO_ID) && idFrase.endsWith(SUFIJO_CONTRARIO)
    }
}
