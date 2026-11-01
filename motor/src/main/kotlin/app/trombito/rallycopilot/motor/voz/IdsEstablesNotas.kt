package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.ElementoHoja

/**
 * Da ids de frase estables a los elementos de la hoja aunque la hoja se regenere con otro origen (la posición en
 * metros cambia, la curva no): dos elementos del mismo tipo y dirección a menos de [radioM] se consideran el mismo.
 * Recuerda los vistos durante toda la conducción (unos pocos por kilómetro). Un registro por sesión; no es concurrente.
 */
class IdsEstablesNotas(
    private val radioM: Double = RADIO_POR_DEFECTO_M,
) {
    private class Visto(
        val clave: String,
        val punto: PuntoGeo,
        val id: String,
    )

    private val vistos = ArrayList<Visto>()

    /** Id del [elemento] cuyo inicio está en [punto]: el de uno ya visto a menos de [radioM], o uno nuevo. */
    fun id(
        elemento: ElementoHoja,
        punto: PuntoGeo,
    ): String {
        val clave = clave(elemento)
        val conocido = vistos.firstOrNull { it.clave == clave && it.punto.distanciaA(punto) < radioM }
        if (conocido != null) return conocido.id
        val nuevo = Visto(clave, punto, "$clave-${vistos.size + 1}")
        vistos += nuevo
        return nuevo.id
    }

    private fun clave(elemento: ElementoHoja): String =
        when (elemento) {
            is ElementoHoja.DeNota -> "nota-${elemento.nota.direccion.name}"
            is ElementoHoja.DeAviso -> "aviso-${elemento.aviso.tipo.name}"
        }

    private companion object {
        const val RADIO_POR_DEFECTO_M = 15.0
    }
}
