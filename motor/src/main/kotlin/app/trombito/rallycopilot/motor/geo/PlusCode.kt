package app.trombito.rallycopilot.motor.geo

import kotlin.math.floor
import kotlin.math.pow

private const val ALFABETO = "23456789CFGHJMPQRVWX"
private const val SEPARADOR = '+'
private const val RELLENO = '0'
private const val POSICION_SEPARADOR = 8
private const val DIGITOS_DE_PARES = 10
private const val BASE = 20
private const val FILAS = 5
private const val COLUMNAS = 4
private const val PARES = 5
private const val MAX_DIGITO_LAT = 8 // la latitud del primer par cubre 9 celdas de 20°
private const val MAX_DIGITO_LON = 17 // la longitud del primer par cubre 18 celdas de 20°
private const val PRECISION_PAR = 8_000 // celdas por grado tras los 10 dígitos de pares
private const val MAX_CELDAS_LAT = 180L * PRECISION_PAR - 1
private const val MAX_CELDAS_LON = 360L * PRECISION_PAR - 1
private const val LAT_MAX = 90.0
private const val LON_MAX = 180.0
private const val VUELTA = 360.0
private const val MIN_TRAS_SEPARADOR = 2
private const val MAX_ANTES = 8
private const val MAX_TRAS = 7

/**
 * Open Location Code («Plus Code»), implementación propia y offline: decodifica códigos completos
 * («8F94HMH2+HJ») y recupera los cortos («HMH2+HJ Palma») respecto a una referencia, como en la especificación
 * (https://github.com/google/open-location-code). Admite hasta 15 dígitos y relleno con ceros.
 */
internal object PlusCode {
    private val FORMA =
        Regex(
            """^([0-9A-Za-z]{2,$MAX_ANTES}\+[0-9A-Za-z]{0,$MAX_TRAS})(?:[\s,]+.*)?$""",
            RegexOption.DOT_MATCHES_ALL,
        )

    /**
     * Interpreta [texto] como Plus Code (con localidad opcional detrás, que se ignora) y devuelve el centro de su
     * área; los cortos se recuperan con la celda más cercana a [referencia]. Null si no es un código válido.
     */
    fun leer(
        texto: String,
        referencia: PuntoGeo,
    ): PuntoGeo? {
        val codigo =
            FORMA
                .matchEntire(texto.trim())
                ?.groupValues
                ?.get(1)
                ?.uppercase()
        return when {
            codigo == null || !esValido(codigo) -> null
            codigo.indexOf(SEPARADOR) == POSICION_SEPARADOR -> centro(codigo)
            else -> recuperar(codigo, referencia)
        }
    }

    /** Código de 10 dígitos (celda de unos 14 × 14 m) del punto dado. */
    fun codificar(
        lat: Double,
        lon: Double,
    ): String {
        var latVal = celdas(lat.coerceIn(-LAT_MAX, LAT_MAX) + LAT_MAX, MAX_CELDAS_LAT)
        var lonVal = celdas(normalizarLon(lon) + LON_MAX, MAX_CELDAS_LON)
        val digitos = CharArray(DIGITOS_DE_PARES)
        for (par in PARES - 1 downTo 0) {
            digitos[2 * par] = ALFABETO[(latVal % BASE).toInt()]
            digitos[2 * par + 1] = ALFABETO[(lonVal % BASE).toInt()]
            latVal /= BASE
            lonVal /= BASE
        }
        return String(digitos, 0, POSICION_SEPARADOR) + SEPARADOR + String(digitos, POSICION_SEPARADOR, 2)
    }

    private fun celdas(
        grados: Double,
        maximo: Long,
    ): Long = floor(grados * PRECISION_PAR).toLong().coerceIn(0L, maximo)

    private fun normalizarLon(lon: Double): Double {
        var l = lon
        while (l < -LON_MAX) l += VUELTA
        while (l >= LON_MAX) l -= VUELTA
        return l
    }

    private fun esValido(codigo: String): Boolean {
        val antes = codigo.substringBefore(SEPARADOR)
        val tras = codigo.substringAfter(SEPARADOR)
        val sinRelleno = antes.trimEnd(RELLENO)
        val relleno = antes.length - sinRelleno.length
        val completo = antes.length == MAX_ANTES
        val longitudOk = antes.length % 2 == 0 && tras.length != 1
        val rellenoOk = relleno == 0 || (completo && tras.isEmpty() && relleno % 2 == 0 && sinRelleno.length >= 2)
        val alfabetoOk = sinRelleno.all { it in ALFABETO } && tras.all { it in ALFABETO }
        val cortoOk = completo || tras.length >= MIN_TRAS_SEPARADOR
        return longitudOk && rellenoOk && alfabetoOk && cortoOk && (!completo || primeraCeldaEnRango(antes))
    }

    private fun primeraCeldaEnRango(antes: String): Boolean =
        ALFABETO.indexOf(antes[0]) <= MAX_DIGITO_LAT && ALFABETO.indexOf(antes[1]) <= MAX_DIGITO_LON

    /** Centro del área de un código completo y válido. */
    private fun centro(codigo: String): PuntoGeo {
        val digitos = codigo.filter { it != SEPARADOR && it != RELLENO }.map { ALFABETO.indexOf(it) }
        var lat = -LAT_MAX
        var lon = -LON_MAX
        var tamano = BASE.toDouble()
        val pares = minOf(digitos.size, DIGITOS_DE_PARES) / 2
        for (par in 0 until pares) {
            tamano = BASE.toDouble().pow(1 - par)
            lat += digitos[2 * par] * tamano
            lon += digitos[2 * par + 1] * tamano
        }
        var alto = tamano
        var ancho = tamano
        for (digito in digitos.drop(DIGITOS_DE_PARES)) {
            alto /= FILAS
            ancho /= COLUMNAS
            lat += (digito / COLUMNAS) * alto
            lon += (digito % COLUMNAS) * ancho
        }
        return PuntoGeo(lat + alto / 2, lon + ancho / 2)
    }

    /** Completa un código corto con el prefijo de la referencia y desplaza el área si se aleja más de media celda. */
    private fun recuperar(
        corto: String,
        referencia: PuntoGeo,
    ): PuntoGeo {
        val aRecuperar = POSICION_SEPARADOR - corto.indexOf(SEPARADOR)
        val precision = BASE.toDouble().pow(2 - aRecuperar / 2)
        val prefijo = codificar(referencia.lat, referencia.lon).take(aRecuperar)
        val area = centro(prefijo + corto)
        val dLat = area.lat - referencia.lat
        val dLon = area.lon - referencia.lon
        val lat =
            when {
                dLat > precision / 2 && area.lat - precision >= -LAT_MAX -> area.lat - precision
                dLat < -precision / 2 && area.lat + precision <= LAT_MAX -> area.lat + precision
                else -> area.lat
            }
        val lon =
            when {
                dLon > precision / 2 -> area.lon - precision
                dLon < -precision / 2 -> area.lon + precision
                else -> area.lon
            }
        return PuntoGeo(lat, normalizarLon(lon))
    }
}
