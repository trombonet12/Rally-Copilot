package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Vías leídas y número de líneas descartadas por estar malformadas. */
data class ResultadoLectura(
    val vias: List<Via>,
    val descartadas: Int,
)

/**
 * Lee el TSV de vías de Mallorca (formato v1 de `rally-copilot-data/scripts/vias.py`) sin tocar disco: recibe las
 * líneas ya descomprimidas. Campos: id, highway, maxspeed, forward, backward, oneway, ref, name, «lat,lon;…» en
 * millonésimas de grado.
 */
object LectorVias {
    const val CABECERA = "#vias-mallorca v1"
    private const val CAMPOS = 9
    private const val ESCALA = 1_000_000.0

    /** @throws IllegalArgumentException si falta la cabecera `#vias-mallorca v1` o es de otra versión. */
    fun leer(lineas: Sequence<String>): ResultadoLectura {
        val iterador = lineas.iterator()
        val primera = if (iterador.hasNext()) iterador.next().trimStart('\uFEFF').trimEnd() else null
        require(primera == CABECERA) { "Cabecera no válida: se esperaba «$CABECERA» y llegó «$primera»" }
        val vias = ArrayList<Via>()
        var descartadas = 0
        for (linea in iterador) {
            if (linea.isBlank()) continue
            val via = analizar(linea.trimEnd('\r', '\n'))
            if (via == null) descartadas++ else vias.add(via)
        }
        return ResultadoLectura(vias, descartadas)
    }

    private fun analizar(linea: String): Via? {
        val c = linea.split('\t')
        if (c.size != CAMPOS) return null
        val id = c[0].trim().toLongOrNull()
        val puntos = puntos(c[8])
        return if (id == null || puntos == null || puntos.size < 2) {
            null
        } else {
            Via(
                id = id,
                clase = c[1].trim(),
                maxspeed = campo(c[2]),
                maxspeedAdelante = campo(c[3]),
                maxspeedAtras = campo(c[4]),
                sentidoUnico = sentido(c[5]),
                ref = campo(c[6]),
                nombre = campo(c[7]),
                puntos = puntos,
            )
        }
    }

    private fun campo(texto: String): String? = texto.trim().ifEmpty { null }

    private fun sentido(texto: String): SentidoUnico =
        when (texto.trim().lowercase()) {
            "yes", "true", "1" -> SentidoUnico.ADELANTE
            "-1" -> SentidoUnico.ATRAS
            else -> SentidoUnico.NO
        }

    private fun micro(texto: String?): Double? = texto?.trim()?.toLongOrNull()?.let { it / ESCALA }

    /** Null si algún par no es «lat,lon» entero o cae fuera de rango. */
    private fun puntos(texto: String): List<PuntoGeo>? {
        val resultado = ArrayList<PuntoGeo>()
        for (par in texto.split(';')) {
            val partes = par.split(',')
            val lat = micro(partes.getOrNull(0))
            val lon = micro(partes.getOrNull(1))
            val valido = partes.size == 2 && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
            if (!valido) return null
            resultado.add(PuntoGeo(lat, lon))
        }
        return resultado
    }
}
