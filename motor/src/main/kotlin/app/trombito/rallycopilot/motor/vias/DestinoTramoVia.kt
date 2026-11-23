package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo

/** Importancia de un lugar habitado: un municipio o un pueblo mandan sobre una aldea a distancias parecidas. */
enum class TipoLugar { MUNICIPIO, PUEBLO, ALDEA }

/** Un lugar habitado con su [nombre] tal como se dice y su [punto]. */
data class LugarNombrado(
    val nombre: String,
    val punto: PuntoGeo,
    val tipo: TipoLugar,
)

/**
 * Hacia dónde va un tramo de vía, dicho para la frase «hacia …»: el pueblo (o municipio) más cercano a la meta si hay
 * alguno a [RADIO_PUEBLO_M] o menos; si no, el rumbo de la salida a la meta en ocho sectores («el norte»,
 * «el sureste»).
 * Una aldea solo gana a un pueblo si queda más de [VENTAJA_PUEBLO_M] metros más cerca de la meta.
 */
object DestinoTramoVia {
    /** Radio máximo, desde la meta, para llamar «pueblo» al destino. */
    const val RADIO_PUEBLO_M = 3_000.0

    /** Metros que se le suman a la distancia de una aldea al compararla con un pueblo o municipio. */
    const val VENTAJA_PUEBLO_M = 1_500.0

    private const val GRADOS_SECTOR = 45.0
    private val RUMBOS =
        listOf(
            "el norte",
            "el noreste",
            "el este",
            "el sureste",
            "el sur",
            "el suroeste",
            "el oeste",
            "el noroeste",
        )

    /** El destino de un tramo que va de [salida] a [meta]; nunca null. */
    fun de(
        salida: PuntoGeo,
        meta: PuntoGeo,
        lugares: List<LugarNombrado>,
    ): String = pueblo(meta, lugares) ?: rumbo(Rumbo.entre(salida, meta))

    /** El rumbo [grados] (0 = norte, horario) en palabras: «el norte», «el noreste»… */
    fun rumbo(grados: Double): String {
        val sector = Math.round(Rumbo.normalizar(grados) / GRADOS_SECTOR).toInt() % RUMBOS.size
        return RUMBOS[sector]
    }

    private fun pueblo(
        meta: PuntoGeo,
        lugares: List<LugarNombrado>,
    ): String? =
        lugares
            .filter { it.nombre.isNotBlank() }
            .map { it to meta.distanciaA(it.punto) }
            .filter { (_, distanciaM) -> distanciaM <= RADIO_PUEBLO_M }
            .minByOrNull { (lugar, distanciaM) ->
                distanciaM + if (lugar.tipo == TipoLugar.ALDEA) VENTAJA_PUEBLO_M else 0.0
            }?.first
            ?.nombre
            ?.trim()
}

private const val CON_DESTINO = " · hacia "

/** Añade « · hacia [destino]» a un nombre automático de tramo de vía que aún no lo lleva; si lo lleva, no lo toca. */
fun nombreTramoViaConDestino(
    nombre: String,
    destino: String,
): String = if (nombre.contains(CON_DESTINO)) nombre else "$nombre$CON_DESTINO$destino"
