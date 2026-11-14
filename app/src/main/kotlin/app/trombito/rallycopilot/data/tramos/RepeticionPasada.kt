package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.Cronometro
import app.trombito.rallycopilot.motor.tramos.EventoCrono
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.velocidad.Fijacion

/** Una pasada reconstruida de puntos grabados: [desdeMs] es su salida en el reloj de esos puntos. */
internal class PasadaRepetida(
    val pasada: Pasada,
    val desdeMs: Long,
) {
    val hastaMs: Long get() = desdeMs + pasada.totalMs
}

/** Repite puntos grabados por el cronómetro de un tramo: los tiempos salen de los puntos, no de una conducción viva. */
internal object RepeticionPasada {
    /** La pasada de [puntos] por [tramo], o null si no cruzan su salida y su meta. */
    fun repetir(
        tramo: Tramo,
        puntos: List<PuntoRecorrido>,
    ): PasadaRepetida? {
        var ahora = 0L
        val cronometro = Cronometro(tramo, reloj = { ahora })
        var desdeMs = 0L
        for (p in puntos) {
            ahora = p.tiempoMs
            val eventos = cronometro.alFijar(Fijacion(PuntoGeo(p.lat, p.lon), p.tiempoMs, p.velocidadMs, null, null))
            eventos.filterIsInstance<EventoCrono.Salida>().forEach { desdeMs = it.tInicioMs }
            if (cronometro.estado == Cronometro.Estado.TERMINADO) break
        }
        return cronometro.pasada()?.let { PasadaRepetida(it, desdeMs) }
    }
}
