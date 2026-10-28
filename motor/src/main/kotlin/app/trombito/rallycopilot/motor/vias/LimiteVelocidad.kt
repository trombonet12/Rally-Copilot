package app.trombito.rallycopilot.motor.vias

import kotlin.math.roundToInt

/**
 * Límite de velocidad a partir de las etiquetas `maxspeed` de OSM. Solo se devuelve un valor numérico explícito:
 * sin dato (o con valores simbólicos como `ES:urban`) no se muestra ni se estima límite.
 */
object LimiteVelocidad {
    private const val KMH_POR_MPH = 1.609344
    private val RANGO_VALIDO = 5..150
    private val NUMERICO = Regex("""(\d{1,3})\s*(mph|km/h|kmh)?""")

    /** «50» → 50; «50 mph» → 80; simbólicos, listas («50;30»), vacío o fuera de 5..150 km/h → null. */
    fun kmh(valor: String?): Int? {
        val coincidencia = valor?.let { NUMERICO.matchEntire(it.trim().lowercase()) }
        val numero = coincidencia?.groupValues?.get(1)?.toInt()
        val esMph = coincidencia?.groupValues?.get(2) == "mph"
        val kmh = if (esMph && numero != null) (numero * KMH_POR_MPH).roundToInt() else numero
        return kmh?.takeIf { it in RANGO_VALIDO }
    }

    /**
     * Límite que rige en el sentido de la marcha: `maxspeed:forward` si [adelante] (la marcha sigue el sentido de
     * dibujo) o `maxspeed:backward` si no; si ese no existe o no es numérico, cae a `maxspeed`.
     */
    fun aplicable(
        via: Via,
        adelante: Boolean,
    ): Int? = kmh(if (adelante) via.maxspeedAdelante else via.maxspeedAtras) ?: kmh(via.maxspeed)
}
