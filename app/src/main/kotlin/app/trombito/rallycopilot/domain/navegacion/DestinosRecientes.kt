package app.trombito.rallycopilot.domain.navegacion

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Cuántos destinos recientes se recuerdan. */
const val MAX_DESTINOS_RECIENTES = 3

/** Los últimos destinos con los que se empezó una ruta, para repetirlos con un toque. */
interface DestinosRecientes {
    /** Del más reciente al más antiguo (como mucho [MAX_DESTINOS_RECIENTES]). */
    val recientes: Flow<List<Destino>>

    /** Anota [destino] como el más reciente. */
    fun registrar(destino: Destino)
}

/** Sin memoria de destinos: para las pruebas que no la necesitan. */
object SinDestinosRecientes : DestinosRecientes {
    override val recientes: Flow<List<Destino>> = flowOf(emptyList())

    override fun registrar(destino: Destino) = Unit
}

/**
 * La lista tras elegir [nuevo]: va el primero, sin duplicados (mismo punto o mismo nombre, el viejo desaparece) y
 * con un máximo de [maximo].
 */
fun List<Destino>.conReciente(
    nuevo: Destino,
    maximo: Int = MAX_DESTINOS_RECIENTES,
): List<Destino> = (listOf(nuevo) + filterNot { it.punto == nuevo.punto || it.nombre == nuevo.nombre }).take(maximo)
