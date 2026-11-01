package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.SelectorNotas
import app.trombito.rallycopilot.motor.voz.AvisosManiobra
import app.trombito.rallycopilot.motor.voz.CandidatasNotas
import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.IdsEstablesNotas
import app.trombito.rallycopilot.motor.voz.PlanificadorFrases
import app.trombito.rallycopilot.motor.voz.TipoFrase
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
@Suppress("TooManyFunctions") // un solo planificador por sesión; los ayudantes comparten su estado
class CopilotoVoz(
    private val locutor: Locutor,
    private val ajustes: Flow<AjustesConduccion>,
    private val registro: ((String) -> Unit)? = null,
) {
    private val selector = SelectorNotas()
    private val planificador = PlanificadorFrases(locutor::duracionEstimadaS)
    private val ids = IdsEstablesNotas()

    @Volatile private var actuales = AjustesConduccion()
    private var vozEnLocutor: VozCopiloto? = null
    private var ultimaPreparacion: List<String>? = null
    private var rutaAnterior: RutaVoz? = null
    private var generacionRuta = 0
    private val porDecir = ArrayList<FrasePorDecir>()

    /**
     * Una parada intermedia alcanzada ([numero]: cuántas van, desde 1): se dice [texto] en cuanto el altavoz esté libre
     * (como una maniobra, sin posición: no caduca por distancia) y, si no cabe en [VIGENCIA_PARADA_FIJACIONES]
     * fijaciones, se pierde.
     */
    fun anunciarParada(
        numero: Int,
        texto: String,
    ) {
        porDecir += FrasePorDecir(Frase("parada-$numero", texto, TipoFrase.MANIOBRA, null), VIGENCIA_PARADA_FIJACIONES)
    }

    /**
     * Una frase de cronometraje (delta de un sector o de la meta): se dice si el modo la permite (solo COMPLETO) en
     * cuanto el altavoz esté libre y, si no cabe en [VIGENCIA_PARADA_FIJACIONES] fijaciones, se pierde.
     */
    fun anunciarCrono(frase: Frase) {
        porDecir += FrasePorDecir(frase, VIGENCIA_PARADA_FIJACIONES)
    }

    /** Mantiene al día los ajustes (modo, voz, anticipación, notas simplificadas) hasta que se cancele. */
    suspend fun seguirAjustes() {
        ajustes.collect { actuales = it }
    }

    /**
     * Una fijación: [notas] es lo último publicado por la sesión de notas (null si no hay hoja vigente), [ruta] las
     * maniobras del guiado (null en modo libre), [progresoM] el progreso sobre la ruta guiada y [vMs] la velocidad.
     * [alertas] son las candidatas ALERTA de la fijación (radares, tramos, incidencias, exceso), en el mismo eje que
     * `progresoM` o, en libre, que la posición de las notas (o 0 si no hay notas): se suman a las activas, no se
     * sintetizan por adelantado (su texto depende de la distancia) y los modos las filtran como al resto.
     */
    fun alFijar(
        notas: NotasEnCurso?,
        ruta: RutaVoz?,
        progresoM: Double?,
        vMs: Double?,
        alertas: List<Frase> = emptyList(),
    ) {
        val sinPosicion = alertas.isEmpty() && porDecir.isEmpty()
        val posicionM = progresoM ?: notas?.posicionM ?: if (sinPosicion) null else 0.0
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
        val idDe = idsDe(notas)
        val pendientes = pendientesVigentes()
        val activas = CandidatasNotas.de(visibles, a.notasSimplificadas, idDe) + maniobras + alertas + pendientes
        val previstas =
            CandidatasNotas.de(visibles.map { it.copy(activa = true) }, a.notasSimplificadas, idDe) +
                maniobras + inminentesPrevistos(ruta, posicionM)
        if (!locutor.hablando.value) {
            planificador.siguiente(activas, posicionM, vMs, a.modoCopiloto)?.let {
                porDecir.removeAll { p -> p.frase.id == it.id }
                decir(it, posicionM, vMs)
            }
        }
        preparar(previstas, posicionM, a)
    }

    /** Las paradas y frases de cronometraje por decir aún vigentes; cada fijación gasta una de su vigencia. */
    private fun pendientesVigentes(): List<Frase> {
        porDecir.removeAll { it.fijacionesRestantes <= 0 }
        val frases = porDecir.map { it.frase }
        porDecir.forEach { it.fijacionesRestantes-- }
        return frases
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

    /**
     * Id de cada elemento de la hoja, independiente del origen de la hoja: si se conoce la traza, la misma curva en una
     * hoja regenerada (modo libre cada ~500 m, o tras un recálculo) conserva su id y no se canta dos veces.
     */
    private fun idsDe(notas: NotasEnCurso?): (ElementoHoja) -> String {
        val puntoEn = notas?.puntoEn ?: return { "nota-${it.posicionM}" }
        return { ids.id(it, puntoEn(it.posicionM)) }
    }

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

        /** Fijaciones (≈ s) que una parada alcanzada espera a que el altavoz esté libre antes de perderse. */
        const val VIGENCIA_PARADA_FIJACIONES = 15
    }
}

private class FrasePorDecir(
    val frase: Frase,
    var fijacionesRestantes: Int,
)
