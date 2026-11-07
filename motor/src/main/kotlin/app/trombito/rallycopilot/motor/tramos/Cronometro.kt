package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.velocidad.MS_POR_S

/** Lo que ocurre al avanzar por un tramo. [tMs] son milisegundos desde la salida; [distanciaM] desde la salida. */
sealed interface EventoCrono {
    val distanciaM: Double
    val tMs: Long

    /** Se cruzó la salida; [tInicioMs] es el instante (reloj de las fijaciones) del cruce. */
    data class Salida(
        val tInicioMs: Long,
    ) : EventoCrono {
        override val distanciaM: Double get() = 0.0
        override val tMs: Long get() = 0L
    }

    /** Se completó el sector [numero] (1 es el primero). */
    data class CorteSector(
        val numero: Int,
        override val distanciaM: Double,
        override val tMs: Long,
    ) : EventoCrono

    /** Se completó el microsector [numero] (1 es el primero). */
    data class CorteMicro(
        val numero: Int,
        override val distanciaM: Double,
        override val tMs: Long,
    ) : EventoCrono

    /** Se cruzó la meta: [tMs] es el tiempo total. */
    data class Meta(
        override val distanciaM: Double,
        override val tMs: Long,
    ) : EventoCrono
}

/**
 * Cronometra una pasada por un [Tramo] a partir de fijaciones GPS. Avanza con la distancia proyectada sobre la traza,
 * que es monótona: una vuelta atrás pequeña (ruido) no retrocede y la ventana de búsqueda es estrecha, de modo que dos
 * pasos por el mismo sitio no se confunden. Los cortes de sector y microsector, la salida y la meta se interpolan
 * linealmente entre las dos fijaciones que los rodean, así que no dependen de la frecuencia del GPS.
 *
 * Sin cierre automático por estar parado: un parado sin avance no cierra nada y su tiempo cuenta en el total (el
 * tiempo de la última fijación sin avance se actualiza, para que al reanudar la interpolación parta del arranque).
 * Fijaciones a más de [lateralMaxM] de la traza se ignoran. La meta es [Tramo.metaM] (10 m antes del final).
 * [reloj] (ms, misma escala que `Fijacion.tiempoMs`) solo se usa para el cronómetro en vivo, [transcurridoMs].
 */
class Cronometro(
    private val tramo: Tramo,
    private val reloj: () -> Long,
    private val lateralMaxM: Double = LATERAL_MAX_M,
) {
    /** Fase de la pasada. */
    enum class Estado { EN_ESPERA, EN_CURSO, TERMINADO }

    private class Referencia(
        val progresoM: Double,
        val tMs: Long,
    )

    private class Corte(
        val distanciaM: Double,
        val esSector: Boolean,
        val numero: Int,
    )

    private val traza = TrazaPlana(tramo.traza, tramo.longitudM)
    private val cortes: List<Corte> =
        (
            tramo.sectores.mapIndexed { i, d -> Corte(d, true, i + 1) } +
                tramo.microsectores.mapIndexed { i, d -> Corte(d, false, i + 1) }
        ).sortedWith(compareBy<Corte> { it.distanciaM }.thenByDescending { it.esSector })
    private val marcas = mutableListOf<MarcaTiempo>()
    private var previa: Referencia? = null
    private var tInicioMs = 0L
    private var ref = Referencia(0.0, 0L)
    private var totalMs = 0L

    /** Fase actual. */
    var estado: Estado = Estado.EN_ESPERA
        private set

    /** Distancia lateral (m) de la última fijación a la traza; null si aún no hay ninguna o no cayó en la ventana. */
    var lateralM: Double? = null
        private set

    /** Metros recorridos sobre la traza (el máximo alcanzado); 0 antes de la salida. */
    val progresoM: Double get() = if (estado == Estado.EN_ESPERA) 0.0 else ref.progresoM

    /** Tiempo (ms) desde la salida según [reloj]; 0 en espera y el total una vez terminado. */
    fun transcurridoMs(): Long =
        when (estado) {
            Estado.EN_ESPERA -> 0L
            Estado.EN_CURSO -> reloj() - tInicioMs
            Estado.TERMINADO -> totalMs
        }

    /** Curva distancia-tiempo registrada hasta ahora (copia). */
    fun marcas(): List<MarcaTiempo> = marcas.toList()

    /** La pasada completa, o null si aún no se cruzó la meta. */
    fun pasada(): Pasada? = if (estado == Estado.TERMINADO) Pasada(tramo.id, marcas.toList(), totalMs) else null

    /** Incorpora una fijación (en orden temporal) y devuelve lo que ha ocurrido, en orden de distancia. */
    fun alFijar(f: Fijacion): List<EventoCrono> {
        val proyeccion = if (estado == Estado.TERMINADO) null else traza.proyectar(f.punto, ventana(f.tiempoMs))
        lateralM = proyeccion?.lateralM
        return if (proyeccion == null || proyeccion.lateralM > lateralMaxM) {
            emptyList()
        } else if (estado == Estado.EN_ESPERA) {
            esperar(f.tiempoMs, proyeccion.progresoM)
        } else {
            avanzar(f.tiempoMs, proyeccion.progresoM)
        }
    }

    private fun ventana(tMs: Long): ClosedFloatingPointRange<Double> {
        val previa = previa
        return if (estado == Estado.EN_ESPERA) {
            val alcance = if (previa == null) 0.0 else previa.progresoM + adelante(tMs - previa.tMs)
            val tope = maxOf(TOL_SALIDA_M, alcance)
            Double.NEGATIVE_INFINITY..tope
        } else {
            (ref.progresoM - ATRAS_M)..(ref.progresoM + adelante(tMs - tInicioMs - ref.tMs))
        }
    }

    private fun esperar(
        tMs: Long,
        progresoM: Double,
    ): List<EventoCrono> {
        val anterior = previa
        return if (progresoM < 0.0) {
            previa = Referencia(progresoM, tMs)
            emptyList()
        } else {
            val inicio =
                if (anterior == null) {
                    tMs
                } else {
                    val fraccion = -anterior.progresoM / (progresoM - anterior.progresoM)
                    Math.round(anterior.tMs + fraccion * (tMs - anterior.tMs))
                }
            comenzar(inicio, if (anterior == null) progresoM else 0.0, tMs - inicio)
            listOf<EventoCrono>(EventoCrono.Salida(inicio)) + avanzar(tMs, progresoM)
        }
    }

    private fun comenzar(
        inicioMs: Long,
        progresoM: Double,
        tRelMs: Long,
    ) {
        tInicioMs = inicioMs
        estado = Estado.EN_CURSO
        ref = Referencia(progresoM, if (progresoM > 0.0) tRelMs else 0L)
        marcas += MarcaTiempo(0.0, 0L)
        if (progresoM > 0.0) marcas += MarcaTiempo(progresoM, ref.tMs)
    }

    private fun avanzar(
        tMs: Long,
        progresoM: Double,
    ): List<EventoCrono> {
        val tRel = tMs - tInicioMs
        val eventos = mutableListOf<EventoCrono>()
        if (tRel < ref.tMs) return eventos
        if (progresoM <= ref.progresoM) {
            ref = Referencia(ref.progresoM, tRel)
        } else {
            anadirMarca(ref.progresoM, ref.tMs)
            val fin = minOf(progresoM, tramo.metaM)
            val interpolar = { d: Double ->
                Math.round(ref.tMs + (d - ref.progresoM) / (progresoM - ref.progresoM) * (tRel - ref.tMs))
            }
            for (c in cortes.filter { it.distanciaM > ref.progresoM && it.distanciaM <= fin }) {
                val t = interpolar(c.distanciaM)
                anadirMarca(c.distanciaM, t)
                eventos +=
                    if (c.esSector) {
                        EventoCrono.CorteSector(c.numero, c.distanciaM, t)
                    } else {
                        EventoCrono.CorteMicro(c.numero, c.distanciaM, t)
                    }
            }
            if (progresoM >= tramo.metaM - EPS_META_M) {
                terminar(interpolar(tramo.metaM), eventos)
            } else {
                anadirMarca(progresoM, tRel)
                ref = Referencia(progresoM, tRel)
            }
        }
        return eventos
    }

    private fun terminar(
        total: Long,
        eventos: MutableList<EventoCrono>,
    ) {
        anadirMarca(tramo.metaM, total)
        totalMs = total
        ref = Referencia(tramo.metaM, total)
        estado = Estado.TERMINADO
        eventos += EventoCrono.Meta(tramo.metaM, total)
    }

    /** Añade la marca; si repite la distancia de la última (parado) sustituye su tiempo por el más tardío. */
    private fun anadirMarca(
        distanciaM: Double,
        tMs: Long,
    ) {
        val ultima = marcas.lastOrNull()
        if (ultima == null || distanciaM > ultima.distanciaM) {
            marcas += MarcaTiempo(distanciaM, tMs)
        } else if (distanciaM == ultima.distanciaM) {
            marcas[marcas.lastIndex] = MarcaTiempo(distanciaM, maxOf(tMs, ultima.tMs))
        }
    }

    companion object {
        /** Distancia máxima a la traza para que una fijación cuente (también la usa el detector como «seguimiento»). */
        const val LATERAL_MAX_M = 50.0

        /** Lo máximo que puede avanzar un coche en [dtMs]: velocidad tope por el tiempo, con un mínimo de margen. */
        private fun adelante(dtMs: Long): Double =
            maxOf(ADELANTE_MIN_M, VELOCIDAD_MAX_MS * dtMs.coerceAtLeast(0) / MS_POR_S)

        private const val TOL_SALIDA_M = 35.0
        private const val ATRAS_M = 30.0
        private const val ADELANTE_MIN_M = 150.0
        private const val VELOCIDAD_MAX_MS = 70.0
        private const val EPS_META_M = 1e-3
    }
}
