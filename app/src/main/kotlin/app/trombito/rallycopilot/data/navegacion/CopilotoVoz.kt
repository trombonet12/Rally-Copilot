package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.SelectorNotas
import app.trombito.rallycopilot.motor.voz.AvisosManiobra
import app.trombito.rallycopilot.motor.voz.CandidatasNotas
import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.PlanificadorFrases
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/**
 * Maniobras de la ruta guiada vigente y sus posiciones (m, mismo sistema que el progreso del seguidor de ruta). Se crea
 * una vez por ruta: un recálculo da una instancia nueva.
 */
class RutaVoz(
    val avisos: AvisosManiobra,
    val posicionesM: List<Double>,
)

/**
 * El copiloto de una conducción: en cada fijación decide, con las notas visibles y las maniobras del guiado, qué frase
 * toca decir y se la pasa al [locutor]; además le pide sintetizar por adelantado las próximas. Un
 * [PlanificadorFrases] por sesión: no repite lo dicho ni dice lo que llegaría tarde. Con el modo SILENCIO, o sin voz
 * elegida (`voz == null`), no habla (ni sintetiza). Las [ajustes] se siguen con [seguirAjustes]; [registro] recibe
 * una línea por frase dicha (id, metros que faltaban y velocidad: nunca coordenadas) para medir la anticipación.
 *
 * El [locutor] es de la aplicación y sobrevive a la sesión: [cerrar] solo deja de pedirle trabajo. No es concurrente:
 * [alFijar] y [cerrar] se llaman desde la corrutina de la sesión.
 */
class CopilotoVoz(
    private val locutor: Locutor,
    private val ajustes: Flow<AjustesConduccion>,
    private val registro: ((String) -> Unit)? = null,
) {
    private val selector = SelectorNotas()
    private val planificador = PlanificadorFrases(locutor::duracionEstimadaS)

    @Volatile private var actuales = AjustesConduccion()
    private var vozEnLocutor: VozCopiloto? = null
    private var ultimaPreparacion: List<String>? = null
    private var rutaAnterior: RutaVoz? = null
    private var generacionRuta = 0

    /** Mantiene al día los ajustes (modo, voz, anticipación, notas simplificadas) hasta que se cancele. */
    suspend fun seguirAjustes() {
        ajustes.collect { actuales = it }
    }

    /**
     * Una fijación: [notas] es lo último publicado por la sesión de notas (null si no hay hoja vigente), [ruta] las
     * maniobras del guiado (null en modo libre), [progresoM] el progreso sobre la ruta guiada y [vMs] la velocidad.
     */
    fun alFijar(
        notas: NotasEnCurso?,
        ruta: RutaVoz?,
        progresoM: Double?,
        vMs: Double?,
    ) {
        val posicionM = progresoM ?: notas?.posicionM
        val a = actuales
        val voz = a.voz
        if (voz == null || posicionM == null) return
        if (voz != vozEnLocutor) {
            locutor.usarVoz(voz)
            vozEnLocutor = voz
            ultimaPreparacion = null
        }
        if (ruta !== rutaAnterior) {
            rutaAnterior = ruta
            generacionRuta++
        }
        val v = vMs ?: 0.0
        val visibles = visibles(notas, a, v)
        val maniobras = maniobras(ruta, posicionM, v)
        val activas = CandidatasNotas.de(visibles, a.notasSimplificadas) + maniobras
        val previstas =
            CandidatasNotas.de(visibles.map { it.copy(activa = true) }, a.notasSimplificadas) +
                maniobras + inminentesPrevistos(ruta, posicionM)
        if (!locutor.hablando.value) {
            planificador.siguiente(activas, posicionM, vMs, a.modoCopiloto)?.let { decir(it, posicionM, vMs) }
        }
        preparar(previstas, posicionM, a)
    }

    /** Deja de pedir trabajo al locutor (vacía lo que había por sintetizar); se puede llamar más de una vez. */
    fun cerrar() {
        if (ultimaPreparacion != null) locutor.preparar(emptyList())
        ultimaPreparacion = null
    }

    private fun visibles(
        notas: NotasEnCurso?,
        a: AjustesConduccion,
        vMs: Double,
    ): List<NotaVisible> =
        notas?.let { selector.siguientes(it.hoja, it.posicionM, vMs, a.anticipacion, NOTAS_CONSIDERADAS) }.orEmpty()

    /** Frases de maniobra vigentes ahora; el id lleva la generación de la ruta para que un recálculo no las calle. */
    private fun maniobras(
        ruta: RutaVoz?,
        posicionM: Double,
        vMs: Double,
    ): List<Frase> =
        ruta
            ?.avisos
            ?.candidatas(posicionM, vMs)
            .orEmpty()
            .map(::conRuta)

    /**
     * El aviso inminente de las próximas maniobras, que aún no está vigente pero sí se puede sintetizar ya (su texto
     * no depende de la distancia).
     */
    private fun inminentesPrevistos(
        ruta: RutaVoz?,
        posicionM: Double,
    ): List<Frase> =
        ruta
            ?.posicionesM
            ?.filter { it > posicionM }
            ?.take(MANIOBRAS_PREPARADAS)
            ?.flatMap { punto ->
                ruta.avisos.candidatas(punto - 1.0, 0.0).filter {
                    it.puntoM == punto && it.id.endsWith(SUFIJO_INMINENTE)
                }
            }.orEmpty()
            .map(::conRuta)

    private fun conRuta(frase: Frase) = frase.copy(id = "r$generacionRuta-${frase.id}")

    private fun decir(
        frase: Frase,
        posicionM: Double,
        vMs: Double?,
    ) {
        locutor.decir(frase.texto)
        registro?.invoke(
            "dice id=${frase.id} faltan=${frase.puntoM?.let { (it - posicionM).roundToInt().toString() } ?: "-"}m " +
                "v=${vMs?.let { (it * MS_A_KMH).roundToInt().toString() } ?: "-"}km/h",
        )
    }

    private fun preparar(
        previstas: List<Frase>,
        posicionM: Double,
        a: AjustesConduccion,
    ) {
        val textos =
            planificador
                .proximas(previstas.distinctBy { it.id }, posicionM, a.modoCopiloto, PREPARADAS)
                .map { it.texto }
        if (textos != ultimaPreparacion) {
            ultimaPreparacion = textos
            locutor.preparar(textos)
        }
    }

    private companion object {
        /** Elementos de la hoja que se miran por delante: cantar las activas y preparar las siguientes. */
        const val NOTAS_CONSIDERADAS = 8
        const val MANIOBRAS_PREPARADAS = 3
        const val PREPARADAS = 6
        const val SUFIJO_INMINENTE = "-inminente"
        const val MS_A_KMH = 3.6
    }
}
