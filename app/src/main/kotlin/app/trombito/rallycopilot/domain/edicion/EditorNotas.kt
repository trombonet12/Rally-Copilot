package app.trombito.rallycopilot.domain.edicion

import app.trombito.rallycopilot.domain.escucha.MarcaConPosicion
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.flow.Flow

/**
 * Un tramo tal como se edita: su [hoja] ya fusionada con las notas editadas (las posiciones son metros de la traza
 * del tramo), sus [marcas] de «marca aquí» y cuántas ediciones lleva ([editadas]).
 */
data class TramoEditable(
    val tramo: Tramo,
    val hoja: HojaNotas,
    val marcas: List<MarcaConPosicion>,
    val editadas: Int,
)

/** Edición de las notas y los sectores de un tramo guardado (detalle del tramo). */
interface EditorTramo {
    /** El tramo editable, cada vez que cambian sus ediciones; null si el tramo no existe. */
    fun observar(tramoId: String): Flow<TramoEditable?>

    /** Aplica [accion] en [posicionM] (metros del tramo). */
    suspend fun editar(
        tramoId: String,
        posicionM: Double,
        accion: AccionEdicion,
    )

    /** Deja las notas del tramo como las generó el copiloto. */
    suspend fun restaurar(tramoId: String)

    /** Guarda los cortes de sector y microsector. */
    suspend fun guardarCortes(
        tramoId: String,
        sectores: List<Double>,
        microsectores: List<Double>,
    )
}

/** Edición de las notas de la conducción en curso desde el mapa (solo con el coche parado). */
fun interface EdicionNotasEnVivo {
    /**
     * Aplica [accion] a la nota de la hoja vigente que está en [posicionHojaM]: ligada al tramo activo (o reconocido)
     * si lo hay, o pendiente de anclarse al que se cree al cerrar la conducción. Devuelve false si no hay hoja vigente.
     */
    suspend fun editar(
        posicionHojaM: Double,
        accion: AccionEdicion,
    ): Boolean
}
