package app.trombito.rallycopilot.domain.tramos

import app.trombito.rallycopilot.motor.tramos.PosicionFantasma
import kotlinx.coroutines.flow.StateFlow

/** Tramo que se está cronometrando: lo que la UI necesita para la barra de progreso y las marcas. */
data class TramoCronometrado(
    val id: String,
    val nombre: String?,
    val longitudM: Double,
    val metaM: Double,
    val sectoresM: List<Double>,
    val microsectoresM: List<Double>,
)

/** Tipo de corte de un tramo. */
enum class TipoCorte { SECTOR, MICROSECTOR }

/**
 * Un sector o microsector ya cerrado. [tiempoMs] es el acumulado desde la salida hasta su final y [duracionMs] lo que
 * costó él solo. [diferenciaMs] compara ese sector con el mismo de la mejor pasada y [deltaAcumuladoMs] el acumulado
 * con el de la mejor (negativo = más rápido); null si no hay mejor pasada con la que comparar.
 */
data class ParcialCrono(
    val tipo: TipoCorte,
    val numero: Int,
    val distanciaM: Double,
    val tiempoMs: Long,
    val duracionMs: Long,
    val diferenciaMs: Long?,
    val deltaAcumuladoMs: Long?,
)

/**
 * Cómo acabó una pasada: [deltaMs] frente a la mejor anterior (null si no la había); [nuevoMejor] si la mejora.
 * [tramoId] y [pasadaId] identifican la pasada ya guardada para abrir su análisis desde la tarjeta de meta; null
 * mientras no se haya guardado (sin ellos la tarjeta no ofrece «Ver análisis»).
 */
data class ResultadoCrono(
    val totalMs: Long,
    val deltaMs: Long?,
    val nuevoMejor: Boolean,
    val mejorAnteriorMs: Long?,
    val tramoId: String? = null,
    val pasadaId: Long? = null,
)

/**
 * Subtramo en marcha (trozo de 300 m o más de un tramo guardado, en su sentido): [recorridoM] hechos, [transcurridoMs]
 * desde su inicio y [deltaMs] contra el mejor tiempo previo en ese trozo (negativo = más rápido; null sin referencia).
 *
 * Con referencia lleva lo mismo que un tramo conocido: [longitudM] es lo que queda de trazado de referencia desde el
 * inicio del trozo hasta su meta (0 si no se conoce), [sectoresM] y [microsectoresM] los cortes del tramo de la
 * referencia que caen dentro, en metros desde el inicio del trozo; [parciales] los ya cerrados (también relativos al
 * inicio del trozo y numerados desde 1), [ultimaMarca] el último y [fantasma] dónde iba la pasada de referencia a este
 * mismo instante del subtramo.
 */
@Suppress("LongParameterList") // los mismos datos que un tramo vivo
data class SubtramoVivo(
    val recorridoM: Double,
    val deltaMs: Long?,
    val transcurridoMs: Long,
    val longitudM: Double = 0.0,
    val sectoresM: List<Double> = emptyList(),
    val microsectoresM: List<Double> = emptyList(),
    val parciales: List<ParcialCrono> = emptyList(),
    val ultimaMarca: ParcialCrono? = null,
    val fantasma: PosicionFantasma? = null,
)

/** Qué clase de trozo cronometrado es el [SegmentoVivo]. */
enum class TipoSegmento { TRAMO, SUBTRAMO }

/**
 * Lo que el HUD pinta de un trozo en marcha, sea un tramo guardado o un subtramo: el mismo panel para los dos. [nombre]
 * es el del tramo si lo tiene; [longitudM] la distancia hasta la meta (0 si no se conoce: barra sin avance); [deltaMs],
 * [parciales] y [fantasma] solo existen con [hayReferencia] (mejor pasada con la que comparar).
 */
@Suppress("LongParameterList") // los datos de un trozo vivo
data class SegmentoVivo(
    val tipo: TipoSegmento,
    val nombre: String?,
    val longitudM: Double,
    val transcurridoMs: Long,
    val deltaMs: Long?,
    val progresoM: Double,
    val sectoresM: List<Double>,
    val microsectoresM: List<Double>,
    val parciales: List<ParcialCrono>,
    val ultimaMarca: ParcialCrono?,
    val hayReferencia: Boolean,
    val fantasma: PosicionFantasma?,
) {
    /** Metros que faltan hasta la meta del trozo si se conoce ([longitudM] > 0); null si no (el HUD usa otra). */
    val restanteM: Double? get() = if (longitudM > 0.0) (longitudM - progresoM).coerceAtLeast(0.0) else null
}

/**
 * Subtramo recién cerrado, para la tarjeta de unos segundos: [longitudM] recorridos en [tiempoMs] frente a [mejorMs]
 * (null sin referencia) y [momentoMs] el instante epoch en que se cerró.
 */
data class SubtramoResultado(
    val longitudM: Double,
    val tiempoMs: Long,
    val mejorMs: Long?,
    val momentoMs: Long,
)

/**
 * El cronómetro de la conducción en curso, para el HUD. Con [tramo] es un tramo guardado (ya sea que se arrancó en él o
 * que se entró a mitad de la conducción) y [hayMejorPasada] dice si hay un tiempo de referencia («tramo conocido»: HUD
 * completo con [deltaMs]); sin [tramo] es solo el cronómetro de la conducción desde Conducir, en [transcurridoMs].
 *
 * [transcurridoMs] se refiere al instante [actualizadoEnMs] (ms epoch): la UI puede seguir contando entre fijaciones
 * sumando `ahora - actualizadoEnMs` mientras [resultado] sea null. [progresoM] es la distancia recorrida del tramo (0
 * sin tramo), [parciales] los sectores y microsectores cerrados por orden de cierre y [ultimaMarca] el último. Al
 * cruzar la meta llega el [resultado] y el cronómetro se queda en el total hasta que se entre en otro tramo.
 *
 * [enMarcha] es false mientras la conducción está «lista» (se pulsó Conducir y el coche aún no ha salido): no hay
 * tiempo que contar y la UI muestra «LISTO» en vez de 00:00. [esperaSalidaManual] es true en ese estado cuando la
 * salida es manual: la UI ofrece el botón «SALIDA» (cuenta atrás) en vez de esperar a que el coche se mueva.
 *
 * [subtramo] es el subtramo en marcha; es null mientras haya un tramo completo vivo (manda el tramo). [ultimoSubtramo]
 * es el último subtramo cerrado en la conducción (la UI lo enseña unos segundos desde su `momentoMs`).
 *
 * [fantasma] es dónde iba el coche de la mejor pasada a este mismo instante del cronómetro (coche fantasma del mapa);
 * null sin tramo vivo, sin mejor pasada o antes de que el fantasma salga.
 *
 * [segmento] unifica tramo y subtramo para el HUD: el tramo si lo hay (manda) y, si no, el subtramo en marcha.
 */
data class CronoEnCurso(
    val tramo: TramoCronometrado?,
    val transcurridoMs: Long,
    val actualizadoEnMs: Long,
    val progresoM: Double = 0.0,
    val deltaMs: Long? = null,
    val parciales: List<ParcialCrono> = emptyList(),
    val ultimaMarca: ParcialCrono? = null,
    val hayMejorPasada: Boolean = false,
    val resultado: ResultadoCrono? = null,
    val enMarcha: Boolean = true,
    val esperaSalidaManual: Boolean = false,
    val subtramo: SubtramoVivo? = null,
    val ultimoSubtramo: SubtramoResultado? = null,
    val fantasma: PosicionFantasma? = null,
) {
    /** El trozo vivo común para el HUD: el tramo (manda) o, sin él, el subtramo en marcha; null si no hay ninguno. */
    val segmento: SegmentoVivo?
        get() = tramo?.let(::segmentoDeTramo) ?: subtramo?.let(::segmentoDeSubtramo)

    private fun segmentoDeTramo(t: TramoCronometrado) =
        SegmentoVivo(
            tipo = TipoSegmento.TRAMO,
            nombre = t.nombre,
            longitudM = t.metaM,
            transcurridoMs = transcurridoMs,
            deltaMs = deltaMs,
            progresoM = progresoM,
            sectoresM = t.sectoresM,
            microsectoresM = t.microsectoresM,
            parciales = parciales,
            ultimaMarca = ultimaMarca,
            hayReferencia = hayMejorPasada,
            fantasma = fantasma,
        )

    private fun segmentoDeSubtramo(s: SubtramoVivo) =
        SegmentoVivo(
            tipo = TipoSegmento.SUBTRAMO,
            nombre = null,
            longitudM = s.longitudM,
            transcurridoMs = s.transcurridoMs,
            deltaMs = s.deltaMs,
            progresoM = s.recorridoM,
            sectoresM = s.sectoresM,
            microsectoresM = s.microsectoresM,
            parciales = s.parciales,
            ultimaMarca = s.ultimaMarca,
            hayReferencia = s.deltaMs != null,
            fantasma = s.fantasma,
        )
}

/** Da la salida del cronómetro de la conducción en curso (salida manual); sin conducción no hace nada. */
interface ControlSalidaCrono {
    fun darSalida()
}

/** Cronómetro en vivo de la sesión de navegación; null si no hay conducción en curso. */
interface ProveedorCrono {
    val crono: StateFlow<CronoEnCurso?>
}
