package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.roundToLong

/** Polilínea codificada de Valhalla (algoritmo de Google con precisión de 6 decimales, pares «lat, lon»). */
object Polyline6 {
    private const val ESCALA = 1_000_000.0
    private const val PRIMER_CARACTER = 63
    private const val ULTIMO_CARACTER = 126
    private const val BITS_POR_CARACTER = 5
    private const val MASCARA = 0x1f
    private const val CONTINUA = 0x20
    private const val MAX_DESPLAZAMIENTO = 60

    /** @throws IllegalArgumentException si la cadena tiene caracteres no válidos o termina a medias. */
    fun decodificar(s: String): List<PuntoGeo> {
        val puntos = ArrayList<PuntoGeo>()
        val cursor = intArrayOf(0)
        var lat = 0L
        var lon = 0L
        while (cursor[0] < s.length) {
            lat += siguienteValor(s, cursor)
            lon += siguienteValor(s, cursor)
            puntos.add(PuntoGeo(lat / ESCALA, lon / ESCALA))
        }
        return puntos
    }

    fun codificar(puntos: List<PuntoGeo>): String {
        val sb = StringBuilder()
        var lat = 0L
        var lon = 0L
        for (p in puntos) {
            val nuevaLat = (p.lat * ESCALA).roundToLong()
            val nuevaLon = (p.lon * ESCALA).roundToLong()
            escribir(sb, nuevaLat - lat)
            escribir(sb, nuevaLon - lon)
            lat = nuevaLat
            lon = nuevaLon
        }
        return sb.toString()
    }

    private fun siguienteValor(
        s: String,
        cursor: IntArray,
    ): Long {
        var resultado = 0L
        var desplazamiento = 0
        while (true) {
            require(cursor[0] < s.length) { "Polyline truncada" }
            val c = s[cursor[0]++].code
            require(c in PRIMER_CARACTER..ULTIMO_CARACTER) { "Carácter no válido en la polyline: ${s[cursor[0] - 1]}" }
            val valor = c - PRIMER_CARACTER
            require(desplazamiento <= MAX_DESPLAZAMIENTO) { "Valor demasiado grande en la polyline" }
            resultado = resultado or ((valor and MASCARA).toLong() shl desplazamiento)
            desplazamiento += BITS_POR_CARACTER
            if (valor and CONTINUA == 0) break
        }
        return if (resultado and 1L != 0L) (resultado shr 1).inv() else resultado shr 1
    }

    private fun escribir(
        sb: StringBuilder,
        delta: Long,
    ) {
        var v = if (delta < 0) (delta shl 1).inv() else delta shl 1
        while (v >= CONTINUA) {
            sb.append((((v and MASCARA.toLong()) or CONTINUA.toLong()) + PRIMER_CARACTER).toInt().toChar())
            v = v shr BITS_POR_CARACTER
        }
        sb.append((v + PRIMER_CARACTER).toInt().toChar())
    }
}
