package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.RANGO_LATITUD
import app.trombito.rallycopilot.motor.geo.RANGO_LONGITUD

/** Clase de radar. */
enum class TipoRadar {
    /** Cabina o cámara fija. */
    FIJO,

    /** Entrada de un tramo con velocidad media; pareja de un [TRAMO_FIN] con el mismo `tramoId`. */
    TRAMO_INICIO,

    /** Salida de un tramo con velocidad media. */
    TRAMO_FIN,

    /** Zona donde la DGT suele poner radar móvil (aproximada: un punto, no una ubicación exacta). */
    ZONA_MOVIL,
}

/**
 * Un radar. [rumboGrados] es el sentido de marcha que vigila (grados desde el norte); `null` = ambos sentidos.
 * [limiteKmh] es el límite que controla, si se conoce. [tramoId] solo existe en los de tramo.
 */
data class Radar(
    val id: String,
    val tipo: TipoRadar,
    val punto: PuntoGeo,
    val rumboGrados: Double?,
    val limiteKmh: Int?,
    val tramoId: String?,
    val fuente: String?,
)

/** Radares leídos y número de líneas descartadas por estar malformadas o repetidas. */
data class ResultadoRadares(
    val radares: List<Radar>,
    val descartadas: Int,
)

/**
 * Lee el TSV de radares (`#radares v1`) sin tocar disco: recibe las líneas ya descomprimidas. Campos separados por
 * tabulador: `tipo` (FIJO | TRAMO_INICIO | TRAMO_FIN | ZONA_MOVIL), `id`, `lat`, `lon` (grados decimales), `rumbo`
 * (0–360; vacío = ambos sentidos), `limite_kmh` (vacío = desconocido), `tramo_id` (obligatorio en los de tramo) y
 * `fuente`. Se ignoran líneas en blanco y comentarios (`#`). Una línea con cualquier campo ilegible, o con un `id`
 * ya visto, se descarta y se cuenta (mejor sin radar que con uno mal orientado).
 */
object LectorRadares {
    const val CABECERA = "#radares v1"
    private const val CAMPOS = 8
    private const val COL_LAT = 2
    private const val COL_LON = 3
    private const val COL_RUMBO = 4
    private const val COL_LIMITE = 5
    private const val COL_TRAMO = 6
    private const val COL_FUENTE = 7
    private const val VUELTA = 360.0
    private val LIMITE_KMH = 1..250

    /** @throws IllegalArgumentException si falta la cabecera `#radares v1` o es de otra versión. */
    fun leer(lineas: Sequence<String>): ResultadoRadares {
        val iterador = lineas.iterator()
        val primera = if (iterador.hasNext()) iterador.next().trimStart('﻿').trimEnd() else null
        require(primera == CABECERA) { "Cabecera no válida: se esperaba «$CABECERA» y llegó «$primera»" }
        val radares = ArrayList<Radar>()
        val vistos = HashSet<String>()
        var descartadas = 0
        for (linea in iterador) {
            if (linea.isBlank() || linea.startsWith("#")) continue
            val radar = analizar(linea.trimEnd('\r', '\n'))
            if (radar == null || !vistos.add(radar.id)) descartadas++ else radares.add(radar)
        }
        return ResultadoRadares(radares, descartadas)
    }

    private fun analizar(linea: String): Radar? {
        val c = linea.split('\t')
        if (c.size != CAMPOS) return null
        val tipo = TipoRadar.entries.firstOrNull { it.name == c[0].trim() }
        val punto = punto(c[COL_LAT], c[COL_LON])
        val rumbo = opcional(c[COL_RUMBO]) { it.toDoubleOrNull()?.takeIf { v -> v in 0.0..VUELTA }?.rem(VUELTA) }
        val limite = opcional(c[COL_LIMITE]) { it.toIntOrNull()?.takeIf { v -> v in LIMITE_KMH } }
        val tramo = c[COL_TRAMO].trim().ifEmpty { null }
        val esTramo = tipo == TipoRadar.TRAMO_INICIO || tipo == TipoRadar.TRAMO_FIN
        val radar =
            if (tipo == null || punto == null) {
                null
            } else {
                val fuente = c[COL_FUENTE].trim().ifEmpty { null }
                Radar(c[1].trim(), tipo, punto, rumbo.valor, limite.valor, if (esTramo) tramo else null, fuente)
            }
        val opcionalesMal = rumbo.ilegible || limite.ilegible
        val tramoMal = esTramo && tramo == null
        return radar?.takeIf { it.id.isNotEmpty() && !opcionalesMal && !tramoMal }
    }

    private fun punto(
        lat: String,
        lon: String,
    ): PuntoGeo? {
        val la = lat.trim().toDoubleOrNull()?.takeIf { it in RANGO_LATITUD }
        val lo = lon.trim().toDoubleOrNull()?.takeIf { it in RANGO_LONGITUD }
        return if (la == null || lo == null) null else PuntoGeo(la, lo)
    }

    /** Campo opcional: vacío = ausente (válido); con texto que [conversion] no entiende = ilegible. */
    private class Opcional<T>(
        val valor: T?,
        val ilegible: Boolean,
    )

    private fun <T> opcional(
        texto: String,
        conversion: (String) -> T?,
    ): Opcional<T> {
        val t = texto.trim()
        if (t.isEmpty()) return Opcional(null, false)
        val v = conversion(t)
        return Opcional(v, v == null)
    }
}
