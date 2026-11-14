package app.trombito.rallycopilot.motor.busqueda

/** [calle] tal como se escribió (primer segmento) y el [municipio] del índice si la dirección lo traía. */
data class DireccionLeida(
    val calle: String,
    val municipio: String?,
)

/**
 * Lee una dirección pegada (p. ej. de Google: «Carrer de Ramon Berenguer III, 5, Bajos, Norte, 07002 Palma, Balearic
 * Islands»). Con comas, el primer segmento es la calle y el municipio se busca en los demás segmentos (sin el código
 * postal de 5 dígitos, y con «Palma de Mallorca» = «Palma»); el resto (portal, piso y puerta, barrio desconocido,
 * región y país) se descarta. Sin comas, todo el texto es la calle. El número de portal pegado a la calle lo quita
 * después [ConsultaBusqueda].
 */
object LectorDireccion {
    private val CODIGO_POSTAL = Regex("""\d{5}""")

    fun leer(texto: String): DireccionLeida {
        val segmentos = texto.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (segmentos.size <= 1) return DireccionLeida(segmentos.firstOrNull().orEmpty(), null)
        val municipio = segmentos.drop(1).firstNotNullOfOrNull { municipioDe(it) }
        return DireccionLeida(segmentos.first(), municipio)
    }

    private fun municipioDe(segmento: String): String? {
        val sinCodigoPostal =
            NormalizadorNombre
                .normalizar(segmento)
                .split(' ')
                .filter { !CODIGO_POSTAL.matches(it) }
                .joinToString(" ")
        return MunicipiosMallorca.oficial(sinCodigoPostal)
    }
}
