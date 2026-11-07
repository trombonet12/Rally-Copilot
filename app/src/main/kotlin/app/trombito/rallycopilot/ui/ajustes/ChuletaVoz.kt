package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.motor.edicion.FrasesOrdenes
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.edicion.TipoPregunta

/**
 * La chuleta de voz generada desde la fuente real: `FrasesOrdenes.porOrden` (la primera variante de cada orden), los
 * prefijos de las órdenes con lugar y las órdenes sueltas de [grupo] (control y edición), que llegan de recursos
 * en [sueltas].
 */
internal object ChuletaVoz {
    /** Las frases de [grupo], tal como las reconoce el copiloto (sin comillas ni mayúscula inicial). */
    fun frases(
        grupo: GrupoVoz,
        sueltas: List<String>,
        lugarIr: String,
        lugarParada: String,
    ): List<Pair<String, Boolean>> {
        val delGrupo =
            FrasesOrdenes.porOrden
                .filter { (orden, frases) -> frases.isNotEmpty() && GrupoVoz.de(orden) == grupo }
                .map { (orden, frases) -> frases.first() to (orden is Orden.Pregunta) }
        val conLugar =
            if (grupo == GrupoVoz.NAVEGACION) {
                listOf(
                    "${FrasesOrdenes.PREFIJOS_IR.first()} $lugarIr" to false,
                    "${FrasesOrdenes.PREFIJO_PARADA} $lugarParada" to false,
                )
            } else {
                emptyList()
            }
        return conLugar + delGrupo + sueltas.map { it to false }
    }

    /** Cómo se enseña una [frase]: con mayúscula inicial, entre comillas y con signos si es una pregunta. */
    fun formato(
        frase: String,
        pregunta: Boolean,
    ): String {
        val inicial = frase.replaceFirstChar { it.uppercase() }
        return if (pregunta) "«¿$inicial?»" else "«$inicial»"
    }

    /** El ejemplo de una orden concreta de `FrasesOrdenes`, formateado: la primera variante de la orden. */
    fun ejemplo(orden: Orden): String {
        val frase =
            FrasesOrdenes.porOrden
                .first { it.first == orden }
                .second
                .first()
        return formato(frase, orden is Orden.Pregunta)
    }
}
