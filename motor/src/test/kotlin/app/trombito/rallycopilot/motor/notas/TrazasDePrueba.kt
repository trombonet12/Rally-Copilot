package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Trazas deterministas para los tests del motor de notas: sintéticas (en metros) y GPX reales de `docs/rutas/`. */
object TrazasDePrueba {
    private const val LAT0 = 39.5
    private const val LON0 = 2.6
    private val ESCALA_Y = Math.toDegrees(1.0 / PuntoGeo.RADIO_TIERRA_M)
    private val ESCALA_X = ESCALA_Y / cos(Math.toRadians(LAT0))

    /** Convierte metros locales (x este, y norte) a puntos WGS84 cerca de Mallorca. */
    fun enPlano(metros: List<Pair<Double, Double>>): List<PuntoGeo> =
        metros.map { (x, y) -> PuntoGeo(LAT0 + y * ESCALA_Y, LON0 + x * ESCALA_X) }

    /** Recta hacia el norte de [largoM] metros con un punto cada [pasoM]. */
    fun recta(
        largoM: Double,
        pasoM: Double = 5.0,
    ): List<PuntoGeo> = enPlano((0..(largoM / pasoM).toInt()).map { 0.0 to it * pasoM })

    /**
     * Recta de [entradaM] hacia el norte, arco de [radioM] y [anguloGrados] (izquierda si [izquierda]) y recta de
     * [salidaM]; un punto cada [pasoM]. Devuelve metros locales.
     */
    @Suppress("LongParameterList")
    fun arcoEnMetros(
        radioM: Double,
        anguloGrados: Double,
        izquierda: Boolean = true,
        entradaM: Double = 200.0,
        salidaM: Double = 200.0,
        pasoM: Double = 5.0,
    ): List<Pair<Double, Double>> {
        val signo = if (izquierda) 1.0 else -1.0
        val puntos = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until (entradaM / pasoM).toInt()) puntos += 0.0 to i * pasoM
        val theta = Math.toRadians(anguloGrados)
        val pasosArco = (radioM * theta / pasoM).toInt().coerceAtLeast(1)
        for (i in 0..pasosArco) {
            val a = theta * i / pasosArco
            puntos += signo * radioM * (cos(a) - 1.0) to entradaM + radioM * sin(a)
        }
        val (x0, y0) = puntos.last()
        val dx = -signo * sin(theta)
        val dy = cos(theta)
        for (i in 1..(salidaM / pasoM).toInt()) puntos += (x0 + dx * i * pasoM) to (y0 + dy * i * pasoM)
        return puntos
    }

    /** Lee los `trkpt` de un GPX de `docs/rutas/` (los tests corren con `motor/` como directorio de trabajo). */
    fun cargarGpx(nombre: String): List<PuntoGeo> {
        val texto = File("../docs/rutas/$nombre").readText()
        val patron = Regex("""<trkpt\s+lat="(-?[\d.]+)"\s+lon="(-?[\d.]+)"""")
        return patron
            .findAll(texto)
            .map { PuntoGeo(it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }
            .toList()
    }
}
