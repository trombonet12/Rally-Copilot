package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo

/**
 * Firma geométrica de un trozo de vía: sus extremos, el rumbo medio (de [inicio] a [fin], que es el promedio de los
 * rumbos de todos sus segmentos ponderado por longitud) y la distancia en línea recta entre ellos ([cuerdaM]). Se
 * deriva siempre de la traza ([de]), así que no hace falta guardarla: sale de la traza ya guardada del tramo.
 * [rumboGrados] es null si los extremos están a menos de [MIN_CUERDA_M] (bucle o trozo casi quieto: sin rumbo fiable).
 */
data class FirmaTramoVia(
    val inicio: PuntoGeo,
    val fin: PuntoGeo,
    val rumboGrados: Double?,
    val cuerdaM: Double,
) {
    companion object {
        /** Por debajo de esta distancia entre extremos el rumbo no es fiable. */
        const val MIN_CUERDA_M = 50.0

        /** Firma de [trazado]; null si tiene menos de dos puntos. */
        fun de(trazado: List<PuntoGeo>): FirmaTramoVia? {
            if (trazado.size < 2) return null
            val inicio = trazado.first()
            val fin = trazado.last()
            val cuerda = inicio.distanciaA(fin)
            return FirmaTramoVia(inicio, fin, if (cuerda >= MIN_CUERDA_M) Rumbo.entre(inicio, fin) else null, cuerda)
        }
    }
}

/** Un tramo `VIA` ya guardado: su [id] y lo que se sabe de él como [TramoVia] (ref, sentido, PK y traza guardada). */
data class TramoViaGuardado(
    val id: String,
    val tramo: TramoVia,
)

/** Un tramo guardado que es el mismo que el trozo nuevo, con el [solape] (0..1 respecto al más corto) que lo prueba. */
data class CoincidenciaTramoVia(
    val id: String,
    val solape: Double,
)

/**
 * Resultado de [EmparejadorTramosVia.emparejar]: los tramos guardados que son el mismo que el trozo nuevo
 * ([coincidencias], de mayor a menor solape; vacío si es un tramo nuevo) y el [sentido] con el que se debe tratar el
 * trozo (el de los tramos guardados con los que coincide o, si es nuevo, el coherente con los que ya hay).
 */
data class ResultadoEmparejo(
    val coincidencias: List<CoincidenciaTramoVia>,
    val sentido: SentidoVia,
)

/**
 * Decide si un trozo de vía recién troceado es uno de los tramos ya guardados sin depender de cómo se oriente el eje de
 * la carretera ([EjeCarretera]), que cambia con los datos de OSM (geometría o hitos nuevos).
 *
 * - Se compara por **geometría**: mismo [TramoVia.idVia], solape de trazado de al menos [umbralSolape] (fracción de
 *   puntos a menos de 30 m del otro, respecto al más corto) y **rumbo compatible**: el rumbo medio de la parte común
 *   difiere como mucho [toleranciaRumboGrados] (la marcha en sentido contrario por la misma carretera, o por la otra
 *   calzada de una autovía, da ~180° y es otro tramo).
 * - El **sentido** no se recalcula con el eje: si coincide con tramos guardados, es el de ellos; si va justo al revés
 *   que uno guardado, es el contrario al suyo; solo sin ningún tramo geométricamente relacionado manda el del
 *   troceador. Los PK no intervienen, así que un eje desplazado o invertido no duplica tramos.
 * - Solo si el trozo o el guardado no tienen traza (menos de dos puntos) se recurre a la clave de siempre: misma
 *   vía y sentido y solape de PK de al menos [umbralSolape].
 *
 * Es pura y determinista: no toca disco ni Room. El llamador trae los tramos guardados de la misma carretera con
 * cualquier sentido.
 */
class EmparejadorTramosVia(
    private val umbralSolape: Double = UMBRAL_SOLAPE,
    private val toleranciaRumboGrados: Double = TOLERANCIA_RUMBO_GRADOS,
) {
    private enum class Relacion { MISMO_SENTIDO, SENTIDO_CONTRARIO, DISTINTO }

    private class Candidato(
        val guardado: TramoViaGuardado,
        val solape: Double,
        val relacion: Relacion,
    )

    /** Empareja [nuevo] con los tramos [guardados]; ver la descripción de la clase. */
    fun emparejar(
        nuevo: TramoVia,
        guardados: List<TramoViaGuardado>,
    ): ResultadoEmparejo {
        val idVia = nuevo.idVia
        val candidatos =
            guardados
                .filter { idVia != null && it.tramo.idVia == idVia }
                .map { evaluar(nuevo, it) }
                .filter { it.solape >= umbralSolape }
        val iguales = candidatos.filter { it.relacion == Relacion.MISMO_SENTIDO }.sortedByDescending { it.solape }
        return if (iguales.isNotEmpty()) {
            val conocido = iguales.map { it.guardado.tramo.sentido }.firstOrNull { it != SentidoVia.DESCONOCIDO }
            val coincidencias = iguales.map { CoincidenciaTramoVia(it.guardado.id, it.solape) }
            ResultadoEmparejo(coincidencias, conocido ?: nuevo.sentido)
        } else {
            val contrario =
                candidatos
                    .filter { it.relacion == Relacion.SENTIDO_CONTRARIO }
                    .sortedByDescending { it.solape }
                    .firstNotNullOfOrNull { opuesto(it.guardado.tramo.sentido) }
            ResultadoEmparejo(emptyList(), contrario ?: nuevo.sentido)
        }
    }

    private fun evaluar(
        nuevo: TramoVia,
        guardado: TramoViaGuardado,
    ): Candidato {
        val otro = guardado.tramo
        if (nuevo.trazado.size < 2 || otro.trazado.size < 2) {
            // Sin geometría que comparar: la clave de siempre (misma vía y sentido) y el solape de PK.
            val solape = if (nuevo.sentido == otro.sentido) solapeTramosVia(nuevo, otro) else 0.0
            return Candidato(guardado, solape, Relacion.MISMO_SENTIDO)
        }
        // Solo geometría: los PK (y con ellos el sentido) dependen del eje y pueden haber cambiado.
        val solape = solapeTramosVia(nuevo.copy(pkDesde = null, pkHasta = null), otro)
        return Candidato(guardado, solape, if (solape < umbralSolape) Relacion.DISTINTO else relacion(nuevo, otro))
    }

    /** Compara el rumbo medio de la parte del trazado de cada tramo que cae sobre el del otro. */
    private fun relacion(
        nuevo: TramoVia,
        otro: TramoVia,
    ): Relacion {
        val rumboNuevo = FirmaTramoVia.de(puntosCubiertos(nuevo.trazado, otro.trazado))?.rumboGrados
        val rumboOtro = FirmaTramoVia.de(puntosCubiertos(otro.trazado, nuevo.trazado))?.rumboGrados
        if (rumboNuevo == null || rumboOtro == null) return Relacion.DISTINTO
        val diferencia = Rumbo.diferencia(rumboNuevo, rumboOtro)
        return when {
            diferencia <= toleranciaRumboGrados -> Relacion.MISMO_SENTIDO
            diferencia >= LLANO - toleranciaRumboGrados -> Relacion.SENTIDO_CONTRARIO
            else -> Relacion.DISTINTO
        }
    }

    private fun opuesto(sentido: SentidoVia): SentidoVia? =
        when (sentido) {
            SentidoVia.CRECIENTE -> SentidoVia.DECRECIENTE
            SentidoVia.DECRECIENTE -> SentidoVia.CRECIENTE
            SentidoVia.DESCONOCIDO -> null
        }

    companion object {
        /** Solape a partir del cual dos trozos son el mismo tramo (la mitad del más corto). */
        const val UMBRAL_SOLAPE = 0.5

        /** Diferencia máxima de rumbo medio para tener el mismo sentido de marcha. */
        const val TOLERANCIA_RUMBO_GRADOS = 60.0
        private const val LLANO = 180.0
    }
}
