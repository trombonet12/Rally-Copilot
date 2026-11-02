package app.trombito.rallycopilot.ui.analisis

import app.trombito.rallycopilot.domain.tramos.PasadaDetallada
import app.trombito.rallycopilot.domain.tramos.PuntoPasada
import app.trombito.rallycopilot.motor.analisis.AnalisisPasada
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.tramos.Cronometro
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.velocidad.Fijacion

/**
 * Un tramo recto de unos 4,4 km hacia el norte y pasadas a velocidad constante cronometradas con el `Cronometro`
 * real: los tiempos de la pasada son los del cronometraje en vivo y los puntos están en el reloj de las fijaciones.
 */
object FixtureAnalisis {
    val traza: List<PuntoGeo> = (0..40).map { PuntoGeo(39.80 + it * 0.001, 2.80) }
    val tramo = Tramo("t1", traza, traza.longitudMetros())

    private fun punto(distanciaM: Double): PuntoGeo {
        val fraccion = (distanciaM / tramo.longitudM).coerceIn(0.0, 1.0)
        return PuntoGeo(traza.first().lat + fraccion * (traza.last().lat - traza.first().lat), traza.first().lon)
    }

    /** Fijaciones cada segundo a [kmh] constantes, la primera en [t0Ms] (reloj de las fijaciones). */
    fun fijaciones(
        kmh: Double,
        t0Ms: Long = 5_000L,
    ): List<Fijacion> {
        val v = kmh / 3.6
        val total = tramo.longitudM
        val n = (total / v).toInt()
        val intermedias = (0..n).map { Fijacion(punto(it * v), t0Ms + it * 1_000L, v, null, 3.0) }
        val fin = Fijacion(punto(total), t0Ms + Math.round(total / v * 1_000.0), v, null, 3.0)
        return intermedias + fin
    }

    fun pasada(fijaciones: List<Fijacion>): Pasada {
        var ahora = 0L
        val crono = Cronometro(tramo, reloj = { ahora })
        fijaciones.forEach {
            ahora = it.tiempoMs
            crono.alFijar(it)
        }
        return checkNotNull(crono.pasada()) { "la pasada debía terminar" }
    }

    /** Una pasada guardada a [kmh]; [inicioMs] es su salida epoch. */
    fun detallada(
        id: Long,
        kmh: Double,
        inicioMs: Long,
        esMejor: Boolean = false,
    ): PasadaDetallada {
        val fijaciones = fijaciones(kmh)
        val puntos = fijaciones.map { PuntoPasada(it.punto.lat, it.punto.lon, it.tiempoMs, it.velocidadMs) }
        return PasadaDetallada(id, inicioMs, pasada(fijaciones), puntos, esMejor)
    }

    /** El análisis del motor de [pasada] contra [comparada] (opcional), como lo calcula el ViewModel. */
    fun analizar(
        pasada: PasadaDetallada,
        comparada: PasadaDetallada? = null,
    ): AnalisisPasada {
        val puntos = pasada.puntos.map { Fijacion(PuntoGeo(it.lat, it.lon), it.tiempoMs, it.velocidadMs, null, null) }
        return AnalisisPasada.de(tramo, puntos, pasada.pasada, comparada?.pasada)
    }
}
