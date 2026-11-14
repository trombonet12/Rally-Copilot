package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.domain.tramos.ParcialCrono
import app.trombito.rallycopilot.domain.tramos.SubtramoVivo
import app.trombito.rallycopilot.domain.tramos.TipoCorte
import app.trombito.rallycopilot.motor.tramos.Fantasma
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.ReferenciaSubtramo
import app.trombito.rallycopilot.motor.tramos.SubtramoEnCurso
import app.trombito.rallycopilot.motor.tramos.Tramo

/**
 * Lo que el HUD necesita de un subtramo en marcha además de lo que da el detector: el fantasma de la pasada de
 * referencia (su cronómetro marca 0 al pasar por el inicio del trozo) y los parciales de los sectores y microsectores
 * del tramo de la referencia que caen dentro del trozo. [tramo] da un tramo guardado por id. Un subtramo nuevo se
 * reconoce porque el recorrido o el tiempo retroceden, porque cambia el tramo o el inicio del trozo de la referencia
 * (o porque no hubo subtramo en la fijación anterior). No es concurrente: se usa desde la corrutina de la sesión.
 */
internal class SeguimientoSubtramo(
    private val tramo: (String) -> Tramo?,
) {
    /** Un instante del subtramo: metros recorridos y tiempo desde su inicio. */
    private class Muestra(
        val recorridoM: Double,
        val tiempoMs: Long,
    )

    /** Un corte del tramo de la referencia dentro del trozo: [distanciaM] relativa al inicio del trozo. */
    private class Corte(
        val tipo: TipoCorte,
        val numero: Int,
        val distanciaM: Double,
    )

    /** La referencia con la que se calcularon fantasma y cortes: la misma pasada, tramo e inicio del trozo. */
    private class Base(
        val referencia: ReferenciaSubtramo,
        val tramo: Tramo,
        val fantasma: Fantasma?,
        val cortes: List<Corte>,
    ) {
        fun esLa(otra: ReferenciaSubtramo) =
            referencia.tramoId == otra.tramoId &&
                referencia.pasada === otra.pasada &&
                referencia.desdeM == otra.desdeM
    }

    private val muestras = ArrayList<Muestra>()
    private var base: Base? = null
    private val parciales = ArrayList<ParcialCrono>()

    /** La última referencia vista en este subtramo (otro tramo o inicio de trozo distinto es otro subtramo). */
    private var ultimaReferencia: ReferenciaSubtramo? = null

    /** Actualiza el seguimiento con el subtramo [en] de esta fijación (null si no hay) y da lo que se publica. */
    fun actualizar(en: SubtramoEnCurso?): SubtramoVivo? {
        if (en == null) {
            reiniciar()
            return null
        }
        val ultima = muestras.lastOrNull()
        val referencia = en.referencia
        val retrocede = ultima != null && (en.recorridoM < ultima.recorridoM || en.transcurridoMs < ultima.tiempoMs)
        if (retrocede || cambiaLaReferencia(ultimaReferencia, referencia)) reiniciar()
        if (referencia != null) ultimaReferencia = referencia
        if (muestras.isEmpty()) muestras += Muestra(0.0, 0L)
        muestras += Muestra(en.recorridoM, en.transcurridoMs)
        val actual = referencia?.let { elegirBase(it) }
        if (actual == null) {
            base = null
            parciales.clear()
        } else {
            cerrarCortes(actual, en.recorridoM)
        }
        val cortes = actual?.cortes.orEmpty()
        return SubtramoVivo(
            recorridoM = en.recorridoM,
            deltaMs = en.deltaMs,
            transcurridoMs = en.transcurridoMs,
            longitudM = longitudTotal(actual, en),
            sectoresM = cortes.filter { it.tipo == TipoCorte.SECTOR }.map { it.distanciaM },
            microsectoresM = cortes.filter { it.tipo == TipoCorte.MICROSECTOR }.map { it.distanciaM },
            parciales = parciales.toList(),
            ultimaMarca = parciales.lastOrNull(),
            fantasma = actual?.fantasma?.posicionEn(en.transcurridoMs),
        )
    }

    /**
     * Hasta dónde llega la barra: con referencia, lo que queda del tramo de la referencia desde el inicio del trozo;
     * sin ella, el tramo en que va (el final del subtramo no se conoce hasta que acaba).
     */
    private fun longitudTotal(
        actual: Base?,
        en: SubtramoEnCurso,
    ): Double =
        if (actual != null) {
            (actual.tramo.metaM - actual.referencia.desdeM).coerceAtLeast(en.recorridoM)
        } else {
            tramo(en.tramoRefId)?.metaM ?: 0.0
        }

    private fun reiniciar() {
        ultimaReferencia = null
        muestras.clear()
        parciales.clear()
        base = null
    }

    /** La base de la referencia: la anterior si es la misma pasada y trozo, o una nueva (se rehacen los parciales). */
    private fun elegirBase(referencia: ReferenciaSubtramo): Base? {
        val previa = base?.takeIf { it.esLa(referencia) }
        if (previa != null) return previa
        parciales.clear()
        val nueva = tramo(referencia.tramoId)?.let { crearBase(it, referencia) }
        base = nueva
        return nueva
    }

    private fun crearBase(
        tramoRef: Tramo,
        referencia: ReferenciaSubtramo,
    ) = Base(referencia, tramoRef, fantasmaDe(tramoRef, referencia), cortesDe(tramoRef, referencia.desdeM))

    /** Sin marcas válidas o sin cubrir el inicio del trozo no hay fantasma, pero el cronómetro sigue. */
    private fun fantasmaDe(
        tramoRef: Tramo,
        referencia: ReferenciaSubtramo,
    ): Fantasma? =
        try {
            Fantasma.deTrozo(tramoRef, referencia.pasada, referencia.desdeM)
        } catch (ignored: IllegalArgumentException) {
            null
        }

    private fun cortesDe(
        tramoRef: Tramo,
        desdeM: Double,
    ): List<Corte> {
        fun cortes(
            lista: List<Double>,
            tipo: TipoCorte,
        ): List<Corte> {
            val dentro = lista.filter { it > desdeM && it < tramoRef.metaM }
            return dentro.mapIndexed { i, d -> Corte(tipo, i + 1, d - desdeM) }
        }
        return (cortes(tramoRef.sectores, TipoCorte.SECTOR) + cortes(tramoRef.microsectores, TipoCorte.MICROSECTOR))
            .sortedBy { it.distanciaM }
    }

    /** Cierra los cortes que ya quedan atrás ([recorridoM] o menos) y aún no tienen parcial. */
    private fun cerrarCortes(
        base: Base,
        recorridoM: Double,
    ) {
        base.cortes
            .filter { it.distanciaM <= recorridoM }
            .filter { corte -> parciales.none { it.tipo == corte.tipo && it.numero == corte.numero } }
            .forEach { parciales += parcial(base, it) }
    }

    private fun parcial(
        base: Base,
        corte: Corte,
    ): ParcialCrono {
        val previo = parciales.lastOrNull { it.tipo == corte.tipo }
        val desdeM = previo?.distanciaM ?: 0.0
        val tDesdeMs = previo?.tiempoMs ?: 0L
        val tMs = tiempoEnSubtramo(corte.distanciaM)
        val inicioM = base.referencia.desdeM
        val pasada = base.referencia.pasada
        val mejorInicio = tiempoEnPasada(pasada, inicioM)
        val mejorHasta = tiempoEnPasada(pasada, inicioM + corte.distanciaM)
        val mejorDesde = tiempoEnPasada(pasada, inicioM + desdeM)
        val diferencia =
            if (mejorHasta != null && mejorDesde != null) {
                Math.round((tMs - tDesdeMs) - (mejorHasta - mejorDesde))
            } else {
                null
            }
        val acumulado =
            if (mejorHasta != null && mejorInicio != null) Math.round(tMs - (mejorHasta - mejorInicio)) else null
        return ParcialCrono(corte.tipo, corte.numero, corte.distanciaM, tMs, tMs - tDesdeMs, diferencia, acumulado)
    }

    /** Tiempo (ms desde el inicio del subtramo) en que se pasó por [recorridoM], interpolado entre muestras. */
    private fun tiempoEnSubtramo(recorridoM: Double): Long {
        val i = muestras.indexOfFirst { it.recorridoM >= recorridoM }
        if (i <= 0) return muestras.getOrNull(i.coerceAtLeast(0))?.tiempoMs ?: 0L
        val a = muestras[i - 1]
        val b = muestras[i]
        val ancho = b.recorridoM - a.recorridoM
        val fraccion = if (ancho <= 0.0) 1.0 else (recorridoM - a.recorridoM) / ancho
        return Math.round(a.tiempoMs + fraccion * (b.tiempoMs - a.tiempoMs))
    }
}

/** Tiempo de [pasada] en [distanciaM], acotado a lo que cubre (las marcas guardadas redondean al milímetro). */
private fun tiempoEnPasada(
    pasada: Pasada,
    distanciaM: Double,
): Double? {
    val primera = pasada.tiempos.firstOrNull() ?: return null
    val ultima = pasada.tiempos.last()
    return pasada.tiempoEn(distanciaM.coerceIn(primera.distanciaM, ultima.distanciaM))
}

/** Otro tramo o otro inicio del trozo respecto a la referencia anterior: es otro subtramo. */
private fun cambiaLaReferencia(
    previa: ReferenciaSubtramo?,
    nueva: ReferenciaSubtramo?,
): Boolean = previa != null && nueva != null && (previa.tramoId != nueva.tramoId || previa.desdeM != nueva.desdeM)
