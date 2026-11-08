package app.trombito.rallycopilot.motor.geo

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** Salida y puesta del sol de un día, en instantes absolutos; null en latitudes con sol de medianoche o noche polar. */
data class EventosSol(
    val salida: Instant?,
    val puesta: Instant?,
)

/** Si toca pintar de noche ahora y cuándo conviene volver a preguntar (el siguiente umbral). */
data class FaseSolar(
    val oscuro: Boolean,
    val proximoCambio: Instant,
)

/**
 * Salida y puesta del sol sin red ni tablas: la «sunrise equation» (orden de Meeus), con error de ~1 min en las
 * latitudes de Mallorca. Sin Android; el reloj entra por parámetro.
 */
object Sol {
    /** Zona horaria de Mallorca (con cambio de hora). */
    val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

    /** Palma de Mallorca: ubicación de reserva cuando no hay ninguna conocida. */
    val PALMA = PuntoGeo(39.57, 2.65)

    /** Margen de crepúsculo: oscuro desde la puesta + 15 min hasta la salida − 15 min. */
    val CREPUSCULO: Duration = Duration.ofMinutes(15)

    private val REINTENTO: Duration = Duration.ofHours(1)

    private const val JULIANO_UNIX = 2440587.5
    private const val DIAS_UNIX_A_J2000 = 10_957L
    private const val JULIANO_J2000 = 2451545.0
    private const val MILIS_DIA = 86_400_000.0
    private const val VUELTA = 360.0
    private const val ANOMALIA_BASE = 357.5291
    private const val ANOMALIA_DIA = 0.98560028
    private const val CENTRO_1 = 1.9148
    private const val CENTRO_2 = 0.02
    private const val CENTRO_3 = 0.0003
    private const val PERIHELIO = 102.9372
    private const val LLANO = 180.0
    private const val OBLICUIDAD = 23.4397
    private const val TRANSITO_1 = 0.0053
    private const val TRANSITO_2 = 0.0069
    private const val ALTURA_HORIZONTE = -0.833
    private const val TRIPLE = 3

    /**
     * Salida y puesta del sol en [punto] el día [fecha] (fecha civil local; para las longitudes de la isla el día UTC
     * coincide con el local mientras el sol está en el cielo).
     */
    fun eventos(
        punto: PuntoGeo,
        fecha: LocalDate,
    ): EventosSol {
        // Días desde J2000 con el mediodía UTC de [fecha] como referencia.
        val n = fecha.toEpochDay() - DIAS_UNIX_A_J2000
        val jMedio = n - punto.lon / VUELTA
        val anomaliaGrados = (ANOMALIA_BASE + ANOMALIA_DIA * jMedio) % VUELTA
        val anomalia = Math.toRadians(anomaliaGrados)
        val centro = CENTRO_1 * sin(anomalia) + CENTRO_2 * sin(2 * anomalia) + CENTRO_3 * sin(TRIPLE * anomalia)
        val longitudEclipt = Math.toRadians((anomaliaGrados + centro + LLANO + PERIHELIO) % VUELTA)
        val transito = JULIANO_J2000 + jMedio + TRANSITO_1 * sin(anomalia) - TRANSITO_2 * sin(2 * longitudEclipt)
        val seno = sin(longitudEclipt) * sin(Math.toRadians(OBLICUIDAD))
        val cosDeclinacion = cos(asin(seno))
        val lat = Math.toRadians(punto.lat)
        val cosAngulo = (sin(Math.toRadians(ALTURA_HORIZONTE)) - sin(lat) * seno) / (cos(lat) * cosDeclinacion)
        if (cosAngulo !in -1.0..1.0) return EventosSol(null, null)
        val dias = Math.toDegrees(acos(cosAngulo)) / VUELTA
        return EventosSol(salida = instante(transito - dias), puesta = instante(transito + dias))
    }

    /**
     * Fase del día en [ahora] para [punto] (Palma si es null): oscuro entre la puesta + [CREPUSCULO] y la salida −
     * [CREPUSCULO]. [FaseSolar.proximoCambio] es el siguiente umbral, para programar un solo despertar. Sin sol
     * calculable (no pasa en Mallorca) se queda en oscuro y reintenta en una hora.
     */
    fun fase(
        ahora: Instant,
        punto: PuntoGeo? = null,
        zona: ZoneId = MADRID,
    ): FaseSolar {
        val lugar = punto ?: PALMA
        val hoy = ahora.atZone(zona).toLocalDate()
        val delDia = eventos(lugar, hoy)
        val claroDesde = delDia.salida?.minus(CREPUSCULO)
        val oscuroDesde = delDia.puesta?.plus(CREPUSCULO)
        return when {
            claroDesde == null || oscuroDesde == null -> {
                FaseSolar(true, ahora.plus(REINTENTO))
            }

            ahora < claroDesde -> {
                FaseSolar(true, claroDesde)
            }

            ahora < oscuroDesde -> {
                FaseSolar(false, oscuroDesde)
            }

            else -> {
                val manana = eventos(lugar, hoy.plusDays(1)).salida?.minus(CREPUSCULO)
                FaseSolar(true, manana ?: ahora.plus(REINTENTO))
            }
        }
    }

    private fun instante(juliano: Double): Instant =
        Instant.ofEpochMilli(((juliano - JULIANO_UNIX) * MILIS_DIA).toLong())
}
