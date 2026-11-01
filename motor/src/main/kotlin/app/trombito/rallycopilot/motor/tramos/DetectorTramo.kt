package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Cambios de estado de un tramo guardado durante una conducción. */
sealed interface EventoTramo {
    val tramoId: String

    /** Se cruzó la salida del tramo; [tInicioMs] es el instante (reloj de las fijaciones) del cruce. */
    data class Entrada(
        override val tramoId: String,
        val tInicioMs: Long,
    ) : EventoTramo

    /** Corte de sector o microsector (o la meta, que también llega como [Completado]) del tramo. */
    data class Corte(
        override val tramoId: String,
        val corte: EventoCrono,
    ) : EventoTramo

    /** Meta cruzada: [pasada] completa lista para guardar y comparar. */
    data class Completado(
        override val tramoId: String,
        val pasada: Pasada,
    ) : EventoTramo

    /** Desvío del tramo: se abandona sin pasada. */
    data class Abandono(
        override val tramoId: String,
    ) : EventoTramo
}

/**
 * Detecta, durante una conducción, que se entra en alguno de los [tramos] guardados aunque la conducción no empiece
 * en él (map matching contra tramos guardados), lo cronometra y avisa de cortes, meta y abandono.
 *
 * - Entrar: una fijación a [RADIO_ENTRADA_M] (30 m) o menos del primer punto y con rumbo de marcha compatible con la
 *   salida (< [ANGULO_ENTRADA_GRADOS] = 45°). El rumbo es el del GPS si se mueve; si no, el calculado respecto a la
 *   fijación anterior (> 3 m). Sin rumbo conocido no se entra. Tras terminar o abandonar, un tramo solo vuelve a
 *   armarse cuando la conducción se aleja de su salida más del radio de entrada.
 * - Seguir: cada candidato lleva su [Cronometro] y la fijación debe estar a [Cronometro.LATERAL_MAX_M] (50 m) o menos.
 * - Desvío: fuera de 50 m durante más de [DESVIO_MS] (5 s) o a más de [DESVIO_INMEDIATO_M] (150 m) de golpe se
 *   abandona sin pasada. Parado no cierra nada.
 * - Meta: pasada completa. Pueden estar activos varios tramos candidatos a la vez.
 */
class DetectorTramo(
    private val tramos: List<Tramo>,
    private val reloj: () -> Long,
) {
    private class Candidato(
        val cronometro: Cronometro,
        var fueraDesdeMs: Long? = null,
        var entradaEmitida: Boolean = false,
    )

    private val rumboSalida: Map<String, Double>
    private val activos = LinkedHashMap<String, Candidato>()
    private val armados = HashSet<String>()
    private var previa: Fijacion? = null

    init {
        require(tramos.map { it.id }.toSet().size == tramos.size) { "Ids de tramo repetidos" }
        rumboSalida =
            tramos.associate { it.id to TrazaPlana(it.traza, it.longitudM).rumboEn(0.0, SEMIVENTANA_RUMBO_M) }
        armados += tramos.map { it.id }
    }

    /** Ids de los tramos que se están siguiendo (incluye los que aún no han cruzado la salida). */
    val idsActivos: Set<String> get() = activos.keys.toSet()

    /** Cronómetro del tramo en curso [tramoId] (para el delta y el HUD en vivo), o null si no se sigue. */
    fun cronometro(tramoId: String): Cronometro? = activos[tramoId]?.cronometro

    /** Incorpora una fijación (en orden temporal) y devuelve lo ocurrido. */
    fun alFijar(f: Fijacion): List<EventoTramo> {
        val eventos = mutableListOf<EventoTramo>()
        for (id in activos.keys.toList()) seguir(id, f, eventos)
        buscarEntradas(f, eventos)
        previa = f
        return eventos
    }

    private fun seguir(
        id: String,
        f: Fijacion,
        eventos: MutableList<EventoTramo>,
    ) {
        val candidato = activos.getValue(id)
        for (e in candidato.cronometro.alFijar(f)) traducir(id, candidato, e, eventos)
        val pasada = candidato.cronometro.pasada()
        if (pasada != null) {
            activos.remove(id)
            armados.remove(id)
            eventos += EventoTramo.Completado(id, pasada)
        } else if (seDesvio(candidato, f)) {
            activos.remove(id)
            armados.remove(id)
            if (candidato.entradaEmitida) eventos += EventoTramo.Abandono(id)
        }
    }

    private fun traducir(
        id: String,
        candidato: Candidato,
        e: EventoCrono,
        eventos: MutableList<EventoTramo>,
    ) {
        when (e) {
            is EventoCrono.Salida -> {
                candidato.entradaEmitida = true
                eventos += EventoTramo.Entrada(id, e.tInicioMs)
            }

            else -> {
                eventos += EventoTramo.Corte(id, e)
            }
        }
    }

    private fun seDesvio(
        candidato: Candidato,
        f: Fijacion,
    ): Boolean {
        val lateral = candidato.cronometro.lateralM ?: Double.POSITIVE_INFINITY
        candidato.fueraDesdeMs = if (lateral > Cronometro.LATERAL_MAX_M) candidato.fueraDesdeMs ?: f.tiempoMs else null
        val desde = candidato.fueraDesdeMs
        return desde != null && (lateral > DESVIO_INMEDIATO_M || f.tiempoMs - desde > DESVIO_MS)
    }

    private fun buscarEntradas(
        f: Fijacion,
        eventos: MutableList<EventoTramo>,
    ) {
        val rumbo = rumboDeMarcha(f)
        for (tramo in tramos) {
            if (tramo.id in activos) continue
            if (f.punto.distanciaA(tramo.traza.first()) > RADIO_ENTRADA_M) {
                armados += tramo.id
            } else if (tramo.id in armados && rumbo != null && compatible(rumbo, tramo)) {
                entrar(tramo, f, eventos)
            }
        }
    }

    private fun compatible(
        rumbo: Double,
        tramo: Tramo,
    ): Boolean = diferencia(rumbo, rumboSalida.getValue(tramo.id)) < ANGULO_ENTRADA_GRADOS

    private fun entrar(
        tramo: Tramo,
        f: Fijacion,
        eventos: MutableList<EventoTramo>,
    ) {
        val candidato = Candidato(Cronometro(tramo, reloj))
        activos[tramo.id] = candidato
        armados.remove(tramo.id)
        previa?.let { p -> for (e in candidato.cronometro.alFijar(p)) traducir(tramo.id, candidato, e, eventos) }
        seguir(tramo.id, f, eventos)
    }

    /** Rumbo de marcha: el del GPS si se mueve; si no, el de la fijación anterior a esta (si dista más de 3 m). */
    private fun rumboDeMarcha(f: Fijacion): Double? {
        val enMarcha = (f.velocidadMs ?: Double.MAX_VALUE) >= VELOCIDAD_PARADO_MS
        val bruto = f.rumboGrados?.takeIf { it.isFinite() && enMarcha }
        val anterior = previa?.takeIf { it.punto.distanciaA(f.punto) > DISTANCIA_MIN_RUMBO_M }
        return when {
            bruto != null -> ((bruto % GRADOS_VUELTA) + GRADOS_VUELTA) % GRADOS_VUELTA
            anterior != null -> rumboEntre(anterior.punto, f.punto)
            else -> null
        }
    }

    companion object {
        /** Radio (m) alrededor del primer punto en que cuenta como «cruzar la salida». */
        const val RADIO_ENTRADA_M = 30.0

        /** Diferencia máxima (grados) entre el rumbo de marcha y el de la salida. */
        const val ANGULO_ENTRADA_GRADOS = 45.0

        /** Tiempo fuera de la traza (ms) a partir del cual se abandona. */
        const val DESVIO_MS = 5_000L

        /** Distancia a la traza (m) a la que se abandona de inmediato. */
        const val DESVIO_INMEDIATO_M = 150.0

        private fun rumboEntre(
            a: PuntoGeo,
            b: PuntoGeo,
        ): Double {
            val lat1 = Math.toRadians(a.lat)
            val lat2 = Math.toRadians(b.lat)
            val dLon = Math.toRadians(b.lon - a.lon)
            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            return (Math.toDegrees(atan2(y, x)) + GRADOS_VUELTA) % GRADOS_VUELTA
        }

        private fun diferencia(
            a: Double,
            b: Double,
        ): Double {
            val d = abs(a - b) % GRADOS_VUELTA
            return if (d > GRADOS_LLANO) GRADOS_VUELTA - d else d
        }

        private const val SEMIVENTANA_RUMBO_M = 10.0
        private const val VELOCIDAD_PARADO_MS = 1.5
        private const val DISTANCIA_MIN_RUMBO_M = 3.0
        private const val GRADOS_VUELTA = 360.0
        private const val GRADOS_LLANO = 180.0
    }
}
