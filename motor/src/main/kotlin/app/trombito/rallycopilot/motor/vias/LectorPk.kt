package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.RANGO_LATITUD
import app.trombito.rallycopilot.motor.geo.RANGO_LONGITUD

/** Hito kilométrico: el punto [km] de la carretera [ref] (como la escribe OSM, «Ma-13») está en [punto]. */
data class HitoKm(
    val ref: String,
    val km: Double,
    val punto: PuntoGeo,
)

/** Hitos leídos y número de líneas descartadas por estar malformadas. */
data class ResultadoPk(
    val hitos: List<HitoKm>,
    val descartadas: Int,
)

/**
 * Lee el TSV de hitos kilométricos (`#pk v1`, id `pk` del manifest) sin tocar disco: recibe las líneas ya leídas.
 * Campos separados por tabulador: `ref`, `km` (0–[KM_MAX], decimal con punto), `lat` y `lon` (grados). Se ignoran
 * líneas en blanco y comentarios (`#`); una línea con cualquier campo ilegible se descarta y se cuenta.
 */
object LectorPk {
    const val CABECERA = "#pk v1"
    const val KM_MAX = 400.0
    private const val CAMPOS = 4
    private const val COL_KM = 1
    private const val COL_LAT = 2
    private const val COL_LON = 3

    /** @throws IllegalArgumentException si falta la cabecera `#pk v1` o es de otra versión. */
    fun leer(lineas: Sequence<String>): ResultadoPk {
        val iterador = lineas.iterator()
        val primera = if (iterador.hasNext()) iterador.next().trimStart('﻿').trimEnd() else null
        require(primera == CABECERA) { "Cabecera no válida: se esperaba «$CABECERA» y llegó «$primera»" }
        val hitos = ArrayList<HitoKm>()
        var descartadas = 0
        for (linea in iterador) {
            if (linea.isBlank() || linea.startsWith("#")) continue
            val hito = analizar(linea.trimEnd('\r', '\n'))
            if (hito == null) descartadas++ else hitos.add(hito)
        }
        return ResultadoPk(hitos, descartadas)
    }

    private fun analizar(linea: String): HitoKm? {
        val c = linea.split('	')
        if (c.size != CAMPOS) return null
        val ref = c[0].trim().takeIf { it.isNotEmpty() }
        val km = c[COL_KM].trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..KM_MAX }
        val lat = c[COL_LAT].trim().toDoubleOrNull()?.takeIf { it in RANGO_LATITUD }
        val lon = c[COL_LON].trim().toDoubleOrNull()?.takeIf { it in RANGO_LONGITUD }
        val punto = if (lat != null && lon != null) PuntoGeo(lat, lon) else null
        return if (ref != null && km != null && punto != null) HitoKm(ref, km, punto) else null
    }
}
