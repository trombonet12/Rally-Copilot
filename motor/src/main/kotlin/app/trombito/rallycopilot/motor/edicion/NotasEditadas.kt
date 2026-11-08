package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso

/** Qué hace una edición sobre las notas de un tramo. */
sealed interface AccionEdicion {
    /** Cambia la nota automática cercana por [nota] (conserva su posición y sus avisos). Sin nota cercana, la añade. */
    data class Sustituir(
        val nota: Nota,
    ) : AccionEdicion

    /** Quita la nota cercana. */
    data object Quitar : AccionEdicion

    /** Añade [nota] en la posición de la edición. */
    data class Anadir(
        val nota: Nota,
    ) : AccionEdicion

    /** Marca la nota cercana con «cuidado». */
    data object Cuidado : AccionEdicion

    /**
     * Fusiona la nota cercana con la siguiente en una sola: dirección y posición de la primera, el grado más cerrado,
     * los matices combinados y el fin de la segunda. Sin nota siguiente, no hace nada. La nota resultante se puede
     * seguir editando con [Sustituir].
     */
    data object Fusionar : AccionEdicion

    /**
     * Divide la nota cercana en dos copias contiguas, cortando en la posición de la edición si cae dentro de la curva
     * o, si no, por la mitad. Cada mitad se puede seguir editando con [Sustituir].
     */
    data object Dividir : AccionEdicion

    /** Añade un aviso suelto de [tipo] en la posición de la edición. */
    data class AnadirAviso(
        val tipo: TipoAviso,
    ) : AccionEdicion

    /** Quita el aviso suelto (automático o añadido) más cercano a la posición de la edición. */
    data object QuitarAviso : AccionEdicion
}

/**
 * Una edición de notas anclada a un TRAMO (no a una ruta ni a una hoja concreta): [posicionM] son metros sobre la
 * traza del tramo y [marcaTiempoMs] ordena las ediciones (las posteriores se aplican después). Se guarda la edición,
 * no el resultado, para que sobreviva a regenerar las notas automáticas (ver [FusionNotas]).
 */
data class NotasEditadas(
    val tramoId: String,
    val posicionM: Double,
    val accion: AccionEdicion,
    val marcaTiempoMs: Long,
) {
    init {
        require(posicionM.isFinite()) { "Posición inválida: $posicionM" }
    }
}
