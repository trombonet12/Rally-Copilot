package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.motor.alertas.AlertasRadar
import app.trombito.rallycopilot.motor.alertas.FiltroIncidencias
import app.trombito.rallycopilot.motor.alertas.Incidencia
import app.trombito.rallycopilot.motor.alertas.IncidenciaCercana
import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.voz.Frase
import java.time.Instant

/**
 * Radares e incidencias sobre la carretera predicha por delante en modo libre (la traza sobre la que se miden las
 * notas, unos 2 km). Las posiciones de las frases están en el eje de la predicción, que es el de las notas.
 */
internal class CarreteraPredicha(
    val traza: List<PuntoGeo>,
    val radares: List<Radar>,
    val incidencias: List<Incidencia>,
    val construidaMs: Long,
    val radar: AlertasRadar,
    val filtro: FiltroIncidencias,
)

/** La [carretera] y el coche en [posicionM] sobre ella. */
internal class PosicionEnPredicha(
    private val carretera: CarreteraPredicha,
    private val posicionM: Double,
) {
    fun radares(vMs: Double): List<Frase> = carretera.radar.candidatas(posicionM, vMs)

    fun avisosIncidencias(vMs: Double): List<Frase> = carretera.filtro.candidatas(posicionM, vMs)

    fun incidencias(coche: PuntoGeo): List<IncidenciaCercana> = carretera.filtro.relevantes(coche, posicionM)
}

/**
 * Guarda la última [CarreteraPredicha]: se rehace al cambiar la traza, los radares o las incidencias, y cada
 * [REFILTRO_MS] para que caduquen las incidencias. Los radares se recolocan solo si cambian la traza o los radares.
 */
internal class CacheCarreteraPredicha {
    private var actual: CarreteraPredicha? = null

    fun obtener(
        traza: List<PuntoGeo>,
        radares: List<Radar>,
        incidencias: List<Incidencia>,
        ahoraMs: Long,
    ): CarreteraPredicha {
        val previa = actual
        val mismaBase = previa != null && previa.traza === traza && previa.radares === radares
        val vigente =
            previa != null && mismaBase && previa.incidencias === incidencias &&
                ahoraMs - previa.construidaMs in 0L until REFILTRO_MS
        val resultado =
            if (vigente && previa != null) {
                previa
            } else {
                CarreteraPredicha(
                    traza,
                    radares,
                    incidencias,
                    ahoraMs,
                    previa?.takeIf { mismaBase }?.radar ?: AlertasRadar(radares, traza),
                    FiltroIncidencias(incidencias, traza, Instant.ofEpochMilli(ahoraMs)),
                )
            }
        actual = resultado
        return resultado
    }

    companion object {
        /** Las incidencias se refiltran al menos cada minuto: así las que caducan salen aunque el feed no cambie. */
        const val REFILTRO_MS = 60_000L
    }
}
