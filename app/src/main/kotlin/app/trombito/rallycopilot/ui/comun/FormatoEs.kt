package app.trombito.rallycopilot.ui.comun

import java.util.Locale

/**
 * Formato único de cifras de la interfaz, con coma decimal es-ES: tiempos «01:23,4», distancias «31,9 km»,
 * tamaños «1,6 GB» y velocidades «54 km/h». Sin estado y sin dependencias de Android: se prueba en la JVM.
 */
object FormatoEs {
    private val ES: Locale = Locale.forLanguageTag("es-ES")
    private const val MS_DECIMA = 100L
    private const val DECIMAS_SEGUNDO = 10L
    private const val SEGUNDOS_MINUTO = 60L
    private const val MINUTOS_HORA = 60L
    private const val METROS_KM = 1000.0
    private const val BYTES_GB = 1024.0 * 1024.0 * 1024.0
    private const val BYTES_MB = 1024.0 * 1024.0
    private const val MAX_DECIMALES = 6

    /**
     * Tiempo de cronómetro: «mm:ss,d» (décimas truncadas, como un crono) y «h:mm:ss,d» pasada la hora. Con
     * [conDecimas] falso, «mm:ss» o «h:mm:ss». Un valor negativo se trata como cero.
     */
    fun tiempo(
        ms: Long,
        conDecimas: Boolean = true,
    ): String {
        val decimasTotales = ms.coerceAtLeast(0L) / MS_DECIMA
        val decima = decimasTotales % DECIMAS_SEGUNDO
        val segundosTotales = decimasTotales / DECIMAS_SEGUNDO
        val segundos = segundosTotales % SEGUNDOS_MINUTO
        val minutos = segundosTotales / SEGUNDOS_MINUTO % MINUTOS_HORA
        val horas = segundosTotales / (SEGUNDOS_MINUTO * MINUTOS_HORA)
        val base =
            if (horas > 0) {
                String.format(ES, "%d:%02d:%02d", horas, minutos, segundos)
            } else {
                String.format(ES, "%02d:%02d", minutos, segundos)
            }
        return if (conDecimas) "$base,$decima" else base
    }

    /** Kilómetros con una décima: «31,9 km». Un valor negativo se trata como cero. */
    fun kilometros(km: Double): String = String.format(ES, "%.1f km", km.coerceAtLeast(0.0))

    /** Distancia en metros expresada en kilómetros: «31,9 km». */
    fun kilometrosDesdeMetros(metros: Double): String = kilometros(metros / METROS_KM)

    /** «850 m» por debajo del kilómetro y «12,4 km» desde él. */
    fun distancia(metros: Double): String {
        val m = metros.coerceAtLeast(0.0)
        return if (m < METROS_KM) String.format(ES, "%d m", Math.round(m)) else kilometros(m / METROS_KM)
    }

    /** Tamaño en gigabytes con una décima: «1,6 GB». */
    fun gigabytes(bytes: Long): String = String.format(ES, "%.1f GB", bytes.coerceAtLeast(0L) / BYTES_GB)

    /** «1,6 GB» desde el gigabyte y «850 MB» por debajo. */
    fun tamano(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L)
        return if (b >= BYTES_GB) gigabytes(b) else String.format(ES, "%d MB", Math.round(b / BYTES_MB))
    }

    /** Velocidad entera: «54 km/h». */
    fun velocidad(kmh: Double): String = String.format(ES, "%d km/h", Math.round(kmh.coerceAtLeast(0.0)))

    /** Número con [decimales] y coma decimal («1,5»). */
    fun decimal(
        valor: Double,
        decimales: Int = 1,
    ): String = String.format(ES, "%.${decimales.coerceIn(0, MAX_DECIMALES)}f", valor)
}
