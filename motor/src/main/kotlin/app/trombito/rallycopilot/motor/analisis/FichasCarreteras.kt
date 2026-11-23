package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.vias.EjesCarreteras
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.motor.vias.Via
import app.trombito.rallycopilot.motor.vias.idDeVia
import app.trombito.rallycopilot.motor.vias.primeraRef
import kotlin.math.abs

private const val M_POR_KM = 1_000.0

/**
 * Hacia dónde crece el punto kilométrico de una carretera al recorrer una vía en el sentido en el que está dibujada
 * ([Via.puntos]): true si crece, false si decrece y null si no se sabe. Una carretera son muchas vías dibujadas
 * cada una en su sentido: esto las pone a todas en el mismo.
 */
fun interface OrientacionVia {
    fun adelanteCreciente(via: Via): Boolean?
}

/**
 * [OrientacionVia] según los [ejes] de las carreteras, los mismos que dan el PK y el sentido a los tramos de vía
 * guardados: compara el PK del primer y el último punto de la vía. Sin eje (la vía no tiene ref), en una rotonda o con
 * una diferencia menor de [MIN_DIFERENCIA_KM] (vía casi sin recorrido sobre el eje), no se sabe.
 */
class OrientacionPorEjes(
    private val ejes: EjesCarreteras,
) : OrientacionVia {
    override fun adelanteCreciente(via: Via): Boolean? {
        val eje = if (via.esRotonda) null else ejes.de(via.ref)
        val desde = via.puntos.firstOrNull()?.let { eje?.pk(it) }
        val hasta = via.puntos.lastOrNull()?.let { eje?.pk(it) }
        val diferencia = if (desde != null && hasta != null) hasta - desde else null
        return diferencia?.takeIf { abs(it) >= MIN_DIFERENCIA_KM }?.let { it > 0.0 }
    }

    companion object {
        const val MIN_DIFERENCIA_KM = 0.05
    }
}

/** Un viaje analizado con el instante en que empezó ([inicioMs], epoch), para las fichas por carretera. */
class ViajeParaFichas(
    val inicioMs: Long,
    val carreteras: AnalisisRecorrido,
    val marcha: AnalisisMarcha,
)

/** Lo hecho en una carretera en un sentido: en cuántos viajes se circuló así y la marcha acumulada. */
data class SentidoCarretera(
    val viajes: Int = 0,
    val marcha: Marcha = Marcha(),
) {
    /** Velocidad media en marcha (km/h); null si no hay tiempos. */
    val velMediaKmh: Double? get() = marcha.velMediaKmh
}

/**
 * Todo lo conducido en una carretera ([id] es la identidad estable de [FichasCarreteras.idCarretera], la misma que
 * `DatosVia.viaRef` de sus tramos de vía): [viajes] que pasaron por ella, [metros] conducidos y reparto de sus
 * [curvas] por grado, el instante del último viaje ([ultimaVezMs]) y el ritmo en cada sentido (km crecientes,
 * decrecientes o sin sentido conocido).
 */
data class FichaCarretera(
    val id: String,
    val ref: String?,
    val nombre: String?,
    val viajes: Int,
    val metros: Double,
    val curvas: CurvasPorGrado,
    val ultimaVezMs: Long?,
    val creciente: SentidoCarretera = SentidoCarretera(),
    val decreciente: SentidoCarretera = SentidoCarretera(),
    val sinSentido: SentidoCarretera = SentidoCarretera(),
) {
    val km: Double get() = metros / M_POR_KM

    /** Marcha de todos los sentidos juntos. */
    val marcha: Marcha get() = creciente.marcha + decreciente.marcha + sinSentido.marcha

    /** Velocidad media en marcha (km/h) en toda la carretera; null si no hay tiempos. */
    val velMediaKmh: Double? get() = marcha.velMediaKmh

    /** Curvas por kilómetro conducido; null sin kilómetros. */
    val curvasPorKm: Double? get() = if (metros > 0.0) curvas.total / km else null

    /** Lo hecho en el [sentido]; el desconocido es el de las vías que no se pudieron orientar. */
    fun deSentido(sentido: SentidoVia): SentidoCarretera =
        when (sentido) {
            SentidoVia.CRECIENTE -> creciente
            SentidoVia.DECRECIENTE -> decreciente
            SentidoVia.DESCONOCIDO -> sinSentido
        }
}

/** Cómo se ordena la lista de carreteras. */
enum class OrdenCarreteras { USO, CURVAS, RECIENTE }

/**
 * Fichas por carretera a partir de los viajes ya analizados: lo que [AgregadoCarreteras] (kilómetros, veces, curvas por
 * grado) y [VelocidadVias] (marcha por vía) calculan por separado, juntado por carretera y orientado por sentido.
 * Puro.
 */
object FichasCarreteras {
    /** Longitud mínima de una carretera para que sus curvas por kilómetro cuenten al ordenar por curvas. */
    const val MIN_METROS_ORDEN_CURVAS = AgregadoCarreteras.MIN_METROS_REVIRADA

    /** Identidad estable de la carretera de una vía: su primera ref normalizada o su nombre; null si es anónima. */
    fun idCarretera(via: Via): String? = idDeVia(via.ref, via.nombre)

    private class Acumulado(
        val id: String,
        val ref: String?,
        val nombre: String?,
    ) {
        var viajes = 0
        var metros = 0.0
        var curvas = CurvasPorGrado.VACIO
        var ultimaVezMs: Long? = null
        var creciente = SentidoCarretera()
        var decreciente = SentidoCarretera()
        var sinSentido = SentidoCarretera()

        fun aFicha() =
            FichaCarretera(id, ref, nombre, viajes, metros, curvas, ultimaVezMs, creciente, decreciente, sinSentido)
    }

    /** Marcha de un viaje en una carretera, repartida por sentido. */
    private class MarchaDelViaje {
        var creciente = Marcha()
        var decreciente = Marcha()
        var sinSentido = Marcha()
    }

    /** Las fichas de [viajes], las de más kilómetros primero. [orientacion] decide el sentido de cada vía. */
    fun agregar(
        viajes: List<ViajeParaFichas>,
        orientacion: OrientacionVia = OrientacionVia { null },
    ): List<FichaCarretera> {
        val porId = LinkedHashMap<String, Acumulado>()
        val memoria = HashMap<Long, Boolean?>()
        val orientar = { via: Via -> memoria.getOrPut(via.id) { orientacion.adelanteCreciente(via) } }
        for (viaje in viajes) {
            sumarUsos(viaje, porId)
            sumarMarcha(viaje, porId, orientar)
        }
        return porId.values.map { it.aFicha() }.ordenadasPor(OrdenCarreteras.USO)
    }

    private fun sumarUsos(
        viaje: ViajeParaFichas,
        destino: MutableMap<String, Acumulado>,
    ) {
        val deEsteViaje =
            viaje.carreteras.usos
                .mapNotNull { uso -> idCarretera(uso.via)?.let { it to uso } }
                .groupBy({ it.first }, { it.second })
        for ((id, grupo) in deEsteViaje) {
            val principal = grupo.maxBy { it.metros }.via
            val acumulado =
                destino.getOrPut(id) {
                    Acumulado(id, primeraRef(principal.ref), principal.nombre?.trim()?.takeIf { it.isNotEmpty() })
                }
            acumulado.viajes++
            acumulado.metros += grupo.sumOf { it.metros }
            acumulado.curvas = grupo.fold(acumulado.curvas) { suma, uso -> suma + uso.grados }
            acumulado.ultimaVezMs = maxOf(acumulado.ultimaVezMs ?: Long.MIN_VALUE, viaje.inicioMs)
        }
    }

    private fun sumarMarcha(
        viaje: ViajeParaFichas,
        destino: Map<String, Acumulado>,
        orientar: (Via) -> Boolean?,
    ) {
        val deEsteViaje = HashMap<String, MarchaDelViaje>()
        for (uso in viaje.marcha.usos) {
            val id = idCarretera(uso.via)?.takeIf { it in destino } ?: continue
            val marcha = deEsteViaje.getOrPut(id) { MarchaDelViaje() }
            when (orientar(uso.via)) {
                true -> {
                    marcha.creciente += uso.adelante
                    marcha.decreciente += uso.atras
                }

                false -> {
                    marcha.creciente += uso.atras
                    marcha.decreciente += uso.adelante
                }

                null -> {
                    marcha.sinSentido += uso.total
                }
            }
        }
        for ((id, marcha) in deEsteViaje) {
            val acumulado = destino.getValue(id)
            acumulado.creciente = acumulado.creciente.sumar(marcha.creciente)
            acumulado.decreciente = acumulado.decreciente.sumar(marcha.decreciente)
            acumulado.sinSentido = acumulado.sinSentido.sumar(marcha.sinSentido)
        }
    }

    /** Suma la marcha de un viaje; cuenta como viaje en este sentido solo si hubo al menos una pasada de marcha. */
    private fun SentidoCarretera.sumar(delViaje: Marcha): SentidoCarretera =
        if (delViaje.metros >= VelocidadVias.MIN_PASADA_M) {
            SentidoCarretera(viajes + 1, marcha + delViaje)
        } else {
            this
        }
}

/** Las fichas en el [orden] pedido (siempre con el mismo desempate: más kilómetros y, si no, la ref). */
fun List<FichaCarretera>.ordenadasPor(orden: OrdenCarreteras): List<FichaCarretera> =
    when (orden) {
        OrdenCarreteras.USO -> {
            sortedWith(
                compareByDescending<FichaCarretera> { it.metros }
                    .thenByDescending { it.viajes }
                    .thenBy { it.id },
            )
        }

        OrdenCarreteras.CURVAS -> {
            sortedWith(
                compareByDescending<FichaCarretera> { it.metros >= FichasCarreteras.MIN_METROS_ORDEN_CURVAS }
                    .thenByDescending { it.curvasPorKm ?: 0.0 }
                    .thenByDescending { it.metros }
                    .thenBy { it.id },
            )
        }

        OrdenCarreteras.RECIENTE -> {
            sortedWith(
                compareByDescending<FichaCarretera> { it.ultimaVezMs ?: Long.MIN_VALUE }
                    .thenByDescending { it.metros }
                    .thenBy { it.id },
            )
        }
    }
