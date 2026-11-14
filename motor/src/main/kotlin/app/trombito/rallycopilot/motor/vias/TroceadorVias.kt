package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import kotlin.math.abs

/** Resuelve el nombre del extremo hacia el que va un [TramoVia] (recibido con `destino` a null); null si no lo sabe. */
typealias ResolutorDestino = (TramoVia) -> String?

/**
 * Trocea un viaje en tramos de vía: trozos de al menos [minLongitudM] de una misma carretera y sentido.
 *
 * - Cada punto de la traza se empareja con una vía mediante [SeguidorVia] (histéresis, rumbo entre puntos vecinos). Se
 *   agrupa por la primera `ref` de la vía y, sin ella, por su nombre; las vías sin ninguno (calles sin nombre) y las
 *   rotondas cuentan como «sin vía» y nunca forman tramo.
 * - Un cambio de vía de menos de [toleranciaCambioM] entre dos trozos de la misma carretera (cruce, rotonda) no corta.
 * - El PK sale de [indicePk] en los extremos del tramo (o el primer y último punto con hito); si crece, el sentido es
 *   `CRECIENTE`, y si baja, `DECRECIENTE`. Sin PK (o con una diferencia menor de [MIN_DELTA_PK_KM]) manda el orden de
 *   dibujo de las vías: `CRECIENTE` si se circula a favor de `via.puntos` la mayor parte del tramo.
 * - [destino]: las vías de OSM del motor no traen `destination` ni extremos con nombre, así que por defecto es null; la
 *   app puede inyectar un [ResolutorDestino] (p. ej. la localidad más cercana al final del tramo).
 */
class TroceadorVias(
    private val indiceVias: IndiceVias,
    private val indicePk: IndicePuntosKm? = null,
    private val minLongitudM: Double = 1_000.0,
    private val toleranciaCambioM: Double = 150.0,
    private val destino: ResolutorDestino = { null },
) {
    private class Muestra(
        val punto: PuntoTraza,
        val id: String?,
        val coincidencia: Coincidencia?,
    )

    private class Racha(
        val id: String?,
        val desde: Int,
        val hasta: Int,
    )

    /** Tramos de vía de la [traza] (puntos en orden de marcha con su distancia acumulada), en orden. */
    fun trocear(traza: List<PuntoTraza>): List<TramoVia> {
        if (traza.size < 2) return emptyList()
        val muestras = emparejar(traza)
        return fusionar(rachas(muestras), muestras)
            .filter { it.id != null }
            .filter { traza[it.hasta].distanciaM - traza[it.desde].distanciaM >= minLongitudM }
            .map { tramo(it, muestras) }
    }

    private fun emparejar(traza: List<PuntoTraza>): List<Muestra> {
        val seguidor = SeguidorVia(indiceVias)
        return traza.mapIndexed { i, p ->
            val coincidencia = seguidor.actualizar(p.punto, rumboEn(traza, i))
            val via = coincidencia?.via
            Muestra(p, via?.takeUnless { it.esRotonda }?.let { idDeVia(it.ref, it.nombre) }, coincidencia)
        }
    }

    /** Rumbo hacia el punto siguiente (o desde el anterior en el último); null si están a menos de 1 m. */
    private fun rumboEn(
        traza: List<PuntoTraza>,
        i: Int,
    ): Double? {
        val (a, b) = if (i < traza.lastIndex) traza[i] to traza[i + 1] else traza[i - 1] to traza[i]
        return if (a.punto.distanciaA(b.punto) < 1.0) null else Rumbo.entre(a.punto, b.punto)
    }

    private fun rachas(muestras: List<Muestra>): List<Racha> {
        val resultado = ArrayList<Racha>()
        var inicio = 0
        for (i in 1..muestras.size) {
            if (i == muestras.size || muestras[i].id != muestras[inicio].id) {
                resultado.add(Racha(muestras[inicio].id, inicio, i - 1))
                inicio = i
            }
        }
        return resultado
    }

    /** Absorbe las rachas de menos de [toleranciaCambioM] que quedan entre dos rachas con el mismo id. */
    private fun fusionar(
        originales: List<Racha>,
        muestras: List<Muestra>,
    ): List<Racha> {
        val rachas = originales.toMutableList()
        var i = 1
        while (i < rachas.size - 1) {
            val anterior = rachas[i - 1]
            val siguiente = rachas[i + 1]
            val largoM = muestras[siguiente.desde].punto.distanciaM - muestras[rachas[i].desde].punto.distanciaM
            if (anterior.id == siguiente.id && largoM < toleranciaCambioM) {
                rachas[i - 1] = Racha(anterior.id, anterior.desde, siguiente.hasta)
                rachas.removeAt(i + 1)
                rachas.removeAt(i)
                i = maxOf(i - 1, 1)
            } else {
                i++
            }
        }
        return rachas
    }

    private fun tramo(
        racha: Racha,
        muestras: List<Muestra>,
    ): TramoVia {
        val propias = muestras.subList(racha.desde, racha.hasta + 1)
        val vias = propias.mapNotNull { m -> m.coincidencia?.via?.takeIf { idDeVia(it.ref, it.nombre) == racha.id } }
        val ref = vias.mapNotNull { primeraRef(it.ref) }.masFrecuente()
        val nombre = vias.mapNotNull { it.nombre?.trim()?.takeIf(String::isNotEmpty) }.masFrecuente()
        val puntos = propias.map { it.punto.punto }
        val (pkDesde, pkHasta) = pkExtremos(ref, puntos)
        val sinDestino =
            TramoVia(
                ref = ref,
                nombreVia = nombre,
                desdeM = propias.first().punto.distanciaM,
                hastaM = propias.last().punto.distanciaM,
                pkDesde = pkDesde,
                pkHasta = pkHasta,
                sentido = sentido(pkDesde, pkHasta, propias, racha.id),
                destino = null,
                trazado = puntos,
            )
        return sinDestino.copy(destino = destino(sinDestino))
    }

    private fun pkExtremos(
        ref: String?,
        puntos: List<PuntoGeo>,
    ): Pair<Double?, Double?> {
        if (indicePk == null || ref == null) return null to null
        val primero = puntos.firstNotNullOfOrNull { indicePk.buscar(ref, it) }
        val ultimo = puntos.asReversed().firstNotNullOfOrNull { indicePk.buscar(ref, it) }
        return primero?.km to ultimo?.km
    }

    private fun sentido(
        pkDesde: Double?,
        pkHasta: Double?,
        propias: List<Muestra>,
        id: String?,
    ): SentidoVia {
        val delta = if (pkDesde != null && pkHasta != null) pkHasta - pkDesde else 0.0
        return when {
            abs(delta) < MIN_DELTA_PK_KM -> sentidoPorDibujo(propias, id)
            delta > 0.0 -> SentidoVia.CRECIENTE
            else -> SentidoVia.DECRECIENTE
        }
    }

    /** `CRECIENTE` si casi todo el tramo va a favor del orden de dibujo; empate o sin datos, desconocido. */
    private fun sentidoPorDibujo(
        propias: List<Muestra>,
        id: String?,
    ): SentidoVia {
        var adelante = 0.0
        var atras = 0.0
        for (i in 0 until propias.lastIndex) {
            val c = propias[i].coincidencia?.takeIf { idDeVia(it.via.ref, it.via.nombre) == id } ?: continue
            val largoM = propias[i + 1].punto.distanciaM - propias[i].punto.distanciaM
            if (c.adelante) adelante += largoM else atras += largoM
        }
        return when {
            adelante > atras -> SentidoVia.CRECIENTE
            atras > adelante -> SentidoVia.DECRECIENTE
            else -> SentidoVia.DESCONOCIDO
        }
    }

    private fun List<String>.masFrecuente(): String? = groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    companion object {
        /** Con menos diferencia de PK entre los extremos el sentido por PK no es fiable y manda el dibujo. */
        const val MIN_DELTA_PK_KM = 0.3
    }
}
