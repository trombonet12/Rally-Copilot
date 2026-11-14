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
 *   agrupa por la primera `ref` de la vía; las vías sin `ref` (calles con nombre) y las rotondas cuentan como «sin
 *   vía» y nunca forman tramo.
 * - Un cambio de vía de menos de [toleranciaCambioM] entre dos trozos de la misma carretera (cruce, rotonda) no corta.
 * - El PK sale del [EjeCarretera] de la `ref` ([ejes]): la proyección del primer y último punto del trozo sobre el eje
 *   (con hitos de OSM calibrado; sin ellos, estimado desde el extremo más cercano a Palma, y [TramoVia.pkEstimado]).
 *   El sentido es `CRECIENTE` si el PK sube y `DECRECIENTE` si baja, sin depender del orden de dibujo de las vías (en
 *   las autovías hay un `way` por calzada y ese orden siempre coincide con la marcha). Solo si no hay eje se usa
 *   [indicePk] y, sin PK (o con menos de [MIN_DELTA_PK_KM] de diferencia), el orden de dibujo.
 * - [destino]: las vías de OSM del motor no traen `destination` ni extremos con nombre, así que por defecto es null; la
 *   app puede inyectar un [ResolutorDestino] (p. ej. la localidad más cercana al final del tramo).
 */
class TroceadorVias(
    private val indiceVias: IndiceVias,
    private val indicePk: IndicePuntosKm? = null,
    private val minLongitudM: Double = 1_000.0,
    private val toleranciaCambioM: Double = 150.0,
    private val destino: ResolutorDestino = { null },
    private val ejes: EjesCarreteras = EjesCarreteras(indiceVias.vias, indicePk),
) {
    private val resolutorPk = ResolutorPk(ejes, indicePk)

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
            Muestra(p, via?.takeUnless { it.esRotonda }?.let { idDeVia(it.ref, null) }, coincidencia)
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
        val vias = propias.mapNotNull { m -> m.coincidencia?.via?.takeIf { idDeVia(it.ref, null) == racha.id } }
        val ref = vias.mapNotNull { primeraRef(it.ref) }.masFrecuente()
        val nombre = vias.mapNotNull { it.nombre?.trim()?.takeIf(String::isNotEmpty) }.masFrecuente()
        val puntos = propias.map { it.punto.punto }
        val pk = resolutorPk.de(ref, puntos)
        val sinDestino =
            TramoVia(
                ref = ref,
                nombreVia = nombre,
                desdeM = propias.first().punto.distanciaM,
                hastaM = propias.last().punto.distanciaM,
                pkDesde = pk.desde,
                pkHasta = pk.hasta,
                sentido = sentido(pk, propias, racha.id),
                destino = null,
                trazado = puntos,
                pkEstimado = pk.estimado,
            )
        return sinDestino.copy(destino = destino(sinDestino))
    }

    private fun sentido(
        pk: PkTramo,
        propias: List<Muestra>,
        id: String?,
    ): SentidoVia {
        val delta = if (pk.desde != null && pk.hasta != null) pk.hasta - pk.desde else 0.0
        return when {
            abs(delta) < pk.minDeltaKm -> sentidoPorDibujo(propias, id)
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
            val c = propias[i].coincidencia?.takeIf { idDeVia(it.via.ref, null) == id } ?: continue
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
