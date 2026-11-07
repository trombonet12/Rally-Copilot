package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.RANGO_LATITUD
import app.trombito.rallycopilot.motor.geo.RANGO_LONGITUD

/** Vías leídas y número de líneas descartadas por estar malformadas. */
data class ResultadoLectura(
    val vias: List<Via>,
    val descartadas: Int,
)

/**
 * Lee el TSV de vías de Mallorca (formato v1 de `rally-copilot-data/scripts/vias.py`) sin tocar disco: recibe las
 * líneas ya descomprimidas. Campos: id, highway, maxspeed, forward, backward, oneway, ref, name, [junction, bridge,
 * tunnel, layer], «lat,lon;…» en millonésimas de grado. Los cuatro entre corchetes solo existen en los datos nuevos
 * (13 campos); con 9 campos (datos anteriores) quedan sin informar.
 */
object LectorVias {
    const val CABECERA = "#vias-mallorca v1"
    private const val CAMPOS_ANTIGUO = 9
    private const val CAMPOS = 13
    private const val COL_MAXSPEED = 2
    private const val COL_MAXSPEED_ADELANTE = 3
    private const val COL_MAXSPEED_ATRAS = 4
    private const val COL_SENTIDO = 5
    private const val COL_REF = 6
    private const val COL_NOMBRE = 7
    private const val COL_JUNCTION = 8
    private const val COL_BRIDGE = 9
    private const val COL_TUNNEL = 10
    private const val COL_LAYER = 11
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
        if (c.size != CAMPOS && c.size != CAMPOS_ANTIGUO) return null
        val id = c[0].trim().toLongOrNull()
        val puntos = puntos(c.last())
        val nuevos = c.size == CAMPOS
        return if (id == null || puntos == null || puntos.size < 2) {
            null
        } else {
            Via(
                id = id,
                clase = c[1].trim(),
                maxspeed = campo(c[COL_MAXSPEED]),
                maxspeedAdelante = campo(c[COL_MAXSPEED_ADELANTE]),
                maxspeedAtras = campo(c[COL_MAXSPEED_ATRAS]),
                sentidoUnico = sentido(c[COL_SENTIDO]),
                ref = campo(c[COL_REF]),
                nombre = campo(c[COL_NOMBRE]),
                puntos = puntos,
                junction = if (nuevos) campo(c[COL_JUNCTION]) else null,
                bridge = if (nuevos) campo(c[COL_BRIDGE]) else null,
                tunnel = if (nuevos) campo(c[COL_TUNNEL]) else null,
                layer = if (nuevos) campo(c[COL_LAYER])?.toIntOrNull() else null,
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
            val valido = partes.size == 2 && lat != null && lon != null && lat in RANGO_LATITUD && lon in RANGO_LONGITUD
            if (!valido) return null
            resultado.add(PuntoGeo(lat, lon))
        }
        return resultado
    }
}
