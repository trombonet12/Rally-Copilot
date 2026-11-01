package app.trombito.rallycopilot.motor.analisis

/** Un punto de la traza: ref de la vía (p. ej. «Ma-2141»), nombre de la calle y municipio; todo opcional. */
data class PasoVia(
    val ref: String? = null,
    val nombre: String? = null,
    val municipio: String? = null,
)

/**
 * Nombre corto de un tramo a partir de las vías que recorre su traza. Con refs: «Ma-2141 · Escorca», o
 * «Ma-10 Pollença → Lluc» si cambia de municipio; las refs repetidas se comprimen y varias se unen con « / ». Sin refs
 * se usa el nombre de calle más frecuente y, si no hay, solo los municipios. Nunca pasa de [MAX_CARACTERES].
 */
object NombreTramo {
    /** Longitud máxima del nombre. */
    const val MAX_CARACTERES = 40

    private const val SIN_NOMBRE = "Tramo sin nombre"
    private const val SEPARADOR_REFS = " / "
    private const val FLECHA = " → "
    private const val PUNTO = " · "
    private const val ELIPSIS = "…"

    /** Nombre de tramo para [pasos] (en orden a lo largo de la traza), de [maxCaracteres] como mucho. */
    fun de(
        pasos: List<PasoVia>,
        maxCaracteres: Int = MAX_CARACTERES,
    ): String {
        val refs = comprimir(pasos.mapNotNull { limpio(it.ref) })
        val municipios = pasos.mapNotNull { limpio(it.municipio) }
        val origen = municipios.firstOrNull()
        val destino = municipios.lastOrNull()
        val cambia = origen != null && destino != null && !origen.equals(destino, ignoreCase = true)
        val calle =
            pasos
                .mapNotNull { limpio(it.nombre) }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
        val principal =
            if (refs.isNotEmpty()) refs else listOfNotNull(calle)
        val nombre =
            if (principal.isEmpty()) {
                zona(origen, destino, cambia)
            } else {
                recortarEn(principal, origen, destino, cambia, maxCaracteres)
            }
        return nombre?.let { if (it.length > maxCaracteres) it.take(maxCaracteres - 1).trimEnd() + ELIPSIS else it }
            ?: SIN_NOMBRE
    }

    private fun zona(
        origen: String?,
        destino: String?,
        cambia: Boolean,
    ): String? = if (cambia) "$origen$FLECHA$destino" else origen

    /** Une vías y municipio; si no cabe quita vías desde el final y luego el municipio de destino. */
    private fun recortarEn(
        vias: List<String>,
        origen: String?,
        destino: String?,
        cambia: Boolean,
        maxCaracteres: Int,
    ): String {
        var n = vias.size
        var texto = componer(vias.take(n), origen, destino, cambia)
        while (texto.length > maxCaracteres && n > 1) {
            n--
            texto = componer(vias.take(n), origen, destino, cambia)
        }
        if (texto.length > maxCaracteres && cambia) texto = componer(vias.take(n), origen, destino, false)
        return texto
    }

    private fun componer(
        vias: List<String>,
        origen: String?,
        destino: String?,
        cambia: Boolean,
    ): String {
        val base = vias.joinToString(SEPARADOR_REFS)
        return when {
            cambia -> "$base $origen$FLECHA$destino"
            origen != null -> "$base$PUNTO$origen"
            else -> base
        }
    }

    private fun limpio(texto: String?): String? = texto?.trim()?.takeIf { it.isNotEmpty() }

    /** Quita las repeticiones consecutivas y las vueltas a una vía ya citada (sin distinguir mayúsculas). */
    private fun comprimir(refs: List<String>): List<String> {
        val vistas = mutableSetOf<String>()
        return refs.filter { vistas.add(it.lowercase()) }
    }
}
