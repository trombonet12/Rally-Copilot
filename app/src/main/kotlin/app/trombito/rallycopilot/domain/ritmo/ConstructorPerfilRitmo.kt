package app.trombito.rallycopilot.domain.ritmo

import app.trombito.rallycopilot.motor.analisis.Marcha
import app.trombito.rallycopilot.motor.analisis.VelocidadVia
import app.trombito.rallycopilot.motor.ruta.IdVia
import app.trombito.rallycopilot.motor.ruta.PerfilRitmo
import app.trombito.rallycopilot.motor.ruta.RitmoVia
import app.trombito.rallycopilot.motor.ruta.ViajeHistorico

/**
 * Perfil de ritmo de Joan a partir de lo que ha conducido: velocidad media real por carretera y factor personal.
 *
 * Clave de cada carretera ([IdVia]) = la primera de sus refs (`Ma-13;Ma-1130` -> `Ma-13`) o, sin ref, su nombre: es lo
 * que Valhalla declara en `Maniobra.vias`, que es por donde [app.trombito.rallycopilot.motor.ruta.EstimadorLlegada]
 * busca. Como Valhalla puede citar la vía por su nombre aunque tenga ref, también se registra por su nombre, pero solo
 * si ese nombre es siempre de la misma ref (o de ninguna): un «Carrer Major» repetido en cada pueblo no se registra.
 * Las vías sueltas de OSM de una misma carretera se suman por metros y tiempo (nunca medias de medias); sus pasadas
 * son las de la vía más recorrida.
 */
object ConstructorPerfilRitmo {
    private class Acumulado {
        var marcha = Marcha()
        var pasadas = 0
    }

    private class Candidata(
        val ref: String?,
        val nombre: String?,
        val velocidad: VelocidadVia,
    )

    fun construir(
        velocidades: List<VelocidadVia>,
        viajes: List<ViajeHistorico>,
    ): PerfilRitmo {
        val candidatas =
            velocidades
                .filter { it.velMediaKmh != null }
                .map { Candidata(limpia(it.via.ref?.substringBefore(';')), limpia(it.via.nombre), it) }
        val refsDeNombre = HashMap<String, MutableSet<String?>>()
        for (c in candidatas) c.nombre?.let { refsDeNombre.getOrPut(it) { HashSet() } += c.ref }
        val porClave = LinkedHashMap<String, Acumulado>()
        for (c in candidatas) {
            c.ref?.let { sumar(porClave, it, c.velocidad) }
            if (c.nombre != null && refsDeNombre[c.nombre]?.size == 1) sumar(porClave, c.nombre, c.velocidad)
        }
        val vias =
            porClave
                .mapNotNull { (clave, a) ->
                    a.marcha.velMediaKmh?.let { IdVia(clave) to RitmoVia(it, a.pasadas, a.marcha.metros / M_POR_KM) }
                }.toMap()
        return PerfilRitmo(vias, PerfilRitmo.factorDesdeHistorico(viajes))
    }

    private fun limpia(texto: String?): String? = texto?.trim()?.takeIf { it.isNotEmpty() }

    private fun sumar(
        destino: MutableMap<String, Acumulado>,
        clave: String,
        v: VelocidadVia,
    ) {
        val a = destino.getOrPut(clave) { Acumulado() }
        a.marcha += v.total
        a.pasadas = maxOf(a.pasadas, v.pasadas)
    }

    private const val M_POR_KM = 1_000.0
}
