package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * Situación del conductor sobre la ruta. [maniobraActual] es la próxima maniobra por ejecutar (la primera cuyo punto
 * de inicio aún no se ha pasado; la salida nunca se anuncia) y [distanciaAManiobraM] lo que falta por la ruta hasta
 * ella; [maniobraSiguiente] es la que viene después. [llegadaMs] es la hora estimada de llegada en el reloj de
 * [SeguidorRuta.actualizar].
 */
data class EstadoRuta(
    val progresoM: Double,
    val distanciaRestanteM: Double,
    val tiempoRestanteS: Double,
    val llegadaMs: Long,
    val maniobraActual: Maniobra?,
    val distanciaAManiobraM: Double,
    val maniobraSiguiente: Maniobra?,
    val distanciaALaRutaM: Double,
    val llegado: Boolean,
)

/**
 * Proyecta posiciones GPS sobre la polilínea de la [ruta]. Tras la primera posición solo se aceptan puntos entre
 * 30 m por detrás y 500 m por delante de la anterior (así una calle paralela o el tramo de vuelta no engañan),
 * salvo salto: si en esa ventana la ruta queda a más de 60 m pero en otro punto está a 30 m o menos, se da por bueno.
 */
class SeguidorRuta(
    private val ruta: Ruta,
) {
    private val linea = Polilinea(ruta.puntos)
    private val final = ruta.puntos.last()
    private var progresoPrevio: Double? = null

    fun actualizar(
        punto: PuntoGeo,
        ahoraMs: Long,
    ): EstadoRuta {
        val x = linea.x(punto)
        val y = linea.y(punto)
        val proyeccion = elegir(x, y)
        progresoPrevio = proyeccion.progresoM
        val restante = (linea.longitudM - proyeccion.progresoM).coerceAtLeast(0.0)
        val tiempoRestante = tiempoRestante(proyeccion.progresoM)
        val indiceManiobra =
            ruta.maniobras.indexOfFirst {
                it.tipo != TipoManiobra.SALIDA && linea.progresoEn(it.inicioIndice) >= proyeccion.progresoM
            }
        val actual = ruta.maniobras.getOrNull(indiceManiobra)
        return EstadoRuta(
            progresoM = proyeccion.progresoM,
            distanciaRestanteM = restante,
            tiempoRestanteS = tiempoRestante,
            llegadaMs = ahoraMs + (tiempoRestante * MS_POR_S).roundToLong(),
            maniobraActual = actual,
            distanciaAManiobraM = actual?.let { linea.progresoEn(it.inicioIndice) - proyeccion.progresoM } ?: 0.0,
            maniobraSiguiente = if (actual == null) null else ruta.maniobras.getOrNull(indiceManiobra + 1),
            distanciaALaRutaM = proyeccion.distanciaM,
            llegado = restante <= RADIO_LLEGADA_M && punto.distanciaA(final) <= RADIO_LLEGADA_M,
        )
    }

    private fun elegir(
        x: Double,
        y: Double,
    ): Proyeccion {
        val previo = progresoPrevio
        val ventana = previo?.let { linea.proyectar(x, y, it - RETROCESO_MAX_M, it + AVANCE_MAX_M) }
        val aceptarVentana = previo != null && ventana != null && ventana.distanciaM <= LEJOS_M
        return if (aceptarVentana) {
            ventana
        } else {
            val global = linea.masCercana(x, y)
            val esSalto =
                global.distanciaM <= SOBRE_RUTA_M && (ventana == null || global.distanciaM < ventana.distanciaM)
            if (previo == null || esSalto) global else ventana ?: global
        }
    }

    private fun tiempoRestante(progreso: Double): Double {
        if (ruta.maniobras.isEmpty()) {
            return ruta.tiempoS * (linea.longitudM - progreso).coerceAtLeast(0.0) /
                linea.longitudM
        }
        return ruta.maniobras.sumOf { m ->
            val ini = linea.progresoEn(m.inicioIndice)
            val fin = linea.progresoEn(m.finIndice)
            val fraccion =
                when {
                    progreso >= fin -> 0.0
                    progreso <= ini || fin == ini -> 1.0
                    else -> (fin - progreso) / (fin - ini)
                }
            m.tiempoS * fraccion
        }
    }

    private companion object {
        const val RETROCESO_MAX_M = 30.0
        const val AVANCE_MAX_M = 500.0
        const val LEJOS_M = 60.0
        const val SOBRE_RUTA_M = 30.0
        const val RADIO_LLEGADA_M = 30.0
        const val MS_POR_S = 1000.0
    }
}

private class Proyeccion(
    val progresoM: Double,
    val distanciaM: Double,
)

/** Polilínea en proyección plana local (equirectangular, latitud media) con la distancia acumulada de cada vértice. */
private class Polilinea(
    puntos: List<PuntoGeo>,
) {
    private val escalaX = PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(puntos.sumOf { it.lat } / puntos.size))
    private val xs = DoubleArray(puntos.size) { Math.toRadians(puntos[it].lon) * escalaX }
    private val ys = DoubleArray(puntos.size) { Math.toRadians(puntos[it].lat) * PuntoGeo.RADIO_TIERRA_M }
    private val acumulada = DoubleArray(puntos.size)

    init {
        require(puntos.size >= 2) { "Una ruta necesita al menos dos puntos" }
        for (i in 1 until puntos.size) acumulada[i] = acumulada[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
    }

    val longitudM: Double get() = acumulada.last()

    fun x(p: PuntoGeo): Double = Math.toRadians(p.lon) * escalaX

    fun y(p: PuntoGeo): Double = Math.toRadians(p.lat) * PuntoGeo.RADIO_TIERRA_M

    fun progresoEn(indice: Int): Double = acumulada[indice.coerceIn(0, acumulada.lastIndex)]

    /** Punto de la polilínea más cercano a (x, y), sin restricción de progreso. */
    fun masCercana(
        x: Double,
        y: Double,
    ): Proyeccion =
        proyectar(x, y, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
            ?: Proyeccion(0.0, hypot(x - xs[0], y - ys[0]))

    /** Punto de la polilínea más cercano a (x, y) cuyo progreso está en [desde]..[hasta]; null si ninguno lo está. */
    fun proyectar(
        x: Double,
        y: Double,
        desde: Double,
        hasta: Double,
    ): Proyeccion? {
        var mejor: Proyeccion? = null
        for (i in 0 until acumulada.lastIndex) {
            val largo = acumulada[i + 1] - acumulada[i]
            if (largo > 0.0 && acumulada[i + 1] >= desde && acumulada[i] <= hasta) {
                val dx = xs[i + 1] - xs[i]
                val dy = ys[i + 1] - ys[i]
                val tLibre = ((x - xs[i]) * dx + (y - ys[i]) * dy) / (largo * largo)
                val tMin = ((desde - acumulada[i]) / largo).coerceIn(0.0, 1.0)
                val tMax = ((hasta - acumulada[i]) / largo).coerceIn(0.0, 1.0)
                val t = tLibre.coerceIn(tMin, tMax)
                val distancia = hypot(x - (xs[i] + t * dx), y - (ys[i] + t * dy))
                if (mejor == null ||
                    distancia < mejor.distanciaM
                ) {
                    mejor = Proyeccion(acumulada[i] + t * largo, distancia)
                }
            }
        }
        return mejor
    }
}
