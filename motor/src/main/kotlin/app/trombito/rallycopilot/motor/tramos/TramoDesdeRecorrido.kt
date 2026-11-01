package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.AcumuladorRecorrido
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.geo.simplificar

/**
 * Convierte un recorrido grabado en un [Tramo]: los de menos de [LONGITUD_MIN_M] (1 km, medido con la regla de
 * `AcumuladorRecorrido`) se descartan; el resto se limpia de ruido de GPS parado (pasos < 5 m), se simplifica a
 * [TOLERANCIA_M] (~5 m) y recibe los sectores (cuatro) y microsectores (uno por km) por defecto.
 */
object TramoDesdeRecorrido {
    /** Longitud mínima (m) de un recorrido para ser tramo. */
    const val LONGITUD_MIN_M = 1000.0

    /** Tolerancia (m) de la simplificación de la traza. */
    const val TOLERANCIA_M = 5.0

    /** El tramo del [recorrido] con el [id] dado, o null si mide menos de un kilómetro. */
    fun crear(
        id: String,
        recorrido: List<PuntoGeo>,
    ): Tramo? {
        val acumulador = AcumuladorRecorrido()
        val filtrados = recorrido.filter { acumulador.anadir(it) }
        val traza = filtrados.simplificar(TOLERANCIA_M)
        val corto = acumulador.distanciaM < LONGITUD_MIN_M || traza.size < 2
        return if (corto) null else Tramo(id, traza, traza.longitudMetros())
    }
}
