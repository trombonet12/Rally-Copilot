package app.trombito.rallycopilot.domain.perfil

import app.trombito.rallycopilot.motor.analisis.Temporada
import app.trombito.rallycopilot.motor.analisis.UsoVia
import kotlinx.coroutines.flow.Flow

/** Una carretera del carné: su nombre para mostrar (el `ref` o, sin él, el nombre) y en cuántos viajes se condujo. */
data class CarreteraCarne(
    val nombre: String,
    val viajes: Int,
)

/** La [temporada] del piloto con su [carne] de carreteras, las más repetidas primero. */
data class TemporadaPiloto(
    val temporada: Temporada,
    val carne: List<CarreteraCarne>,
)

/** Cálculo de la temporada sobre los viajes terminados y las pasadas guardadas. */
interface CalculoTemporada {
    /** Emite la temporada al empezar y cada vez que cambian los viajes o las pasadas. */
    fun observar(): Flow<TemporadaPiloto>
}

/**
 * Carné de carreteras: las vías distintas de los viajes (una lista de [UsoVia] por viaje), por `ref` sin distinguir
 * mayúsculas o, sin `ref`, por nombre, con el mismo criterio que `Temporada.carreterasDistintas`. Cuenta en cuántos
 * viajes aparece cada una; orden: más viajes primero y, a igualdad, por nombre.
 */
object CarneCarreteras {
    fun de(usosPorViaje: List<List<UsoVia>>): List<CarreteraCarne> {
        val nombres = LinkedHashMap<String, String>()
        val viajes = HashMap<String, Int>()
        for (usos in usosPorViaje) {
            val delViaje = HashSet<String>()
            for (uso in usos.sortedByDescending { it.metros }) {
                val ref =
                    uso.via.ref
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                val nombre =
                    uso.via.nombre
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                val clave = ref?.let { "ref:${it.uppercase()}" } ?: nombre?.let { "nombre:${it.lowercase()}" }
                val visible = ref ?: nombre
                if (clave != null && visible != null && delViaje.add(clave)) {
                    nombres.getOrPut(clave) { visible }
                    viajes.merge(clave, 1, Int::plus)
                }
            }
        }
        return nombres
            .map { (clave, nombre) -> CarreteraCarne(nombre, viajes.getValue(clave)) }
            .sortedWith(compareByDescending<CarreteraCarne> { it.viajes }.thenBy { it.nombre.lowercase() })
    }
}
