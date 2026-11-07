package app.trombito.rallycopilot.data.recorridos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.MuestraVelocidad
import app.trombito.rallycopilot.motor.notas.PerfilVelocidad
import app.trombito.rallycopilot.motor.notas.TablaGrados

/**
 * Factor de calibración de [TablaGrados] con la velocidad real de los [recorridos] ([PerfilVelocidad.calibrar]);
 * null si no hay curvas bastantes. Pesado: llamar en un dispatcher de cómputo. Es la única fórmula: la usan el cálculo
 * al cerrar cada recorrido y el respaldo sin caché.
 */
internal fun calcularFactorCalibracion(recorridos: List<List<PuntoRecorrido>>): Double? {
    val muestras = recorridos.map { r -> r.map { MuestraVelocidad(PuntoGeo(it.lat, it.lon), it.velocidadMs) } }
    return PerfilVelocidad.calibrar(muestras, TablaGrados()).factor
}
