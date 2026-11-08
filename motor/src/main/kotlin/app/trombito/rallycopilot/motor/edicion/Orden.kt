package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.voz.ModoCopiloto

/** Orden de voz ya interpretada (ver [InterpreteOrdenes]). */
sealed interface Orden {
    /** Órdenes que cambian un ajuste de la conducción (el ejecutor las aplica juntas, sin tocar la hoja de notas). */
    sealed interface Ajuste : Orden

    /**
     * Dictado de una nota («cuidado, derecha tres larga, cincuenta»). [grado] `null` es una horquilla; en las notas
     * simplificadas el grado es el representativo (muy cerrada 1, cerrada 2, media 3, suave 5). [distanciaSiguienteM]
     * ya viene ajustada a la lista de distancias del motor.
     */
    data class Nota(
        val direccion: Direccion,
        val grado: Int?,
        val modificadores: Set<Modificador> = emptySet(),
        val distanciaSiguienteM: Int? = null,
        val cuidado: Boolean = false,
    ) : Orden {
        /** La nota dictada en [posicionM] (metros sobre la traza); su inicio y su fin coinciden. */
        fun aNota(posicionM: Double): app.trombito.rallycopilot.motor.notas.Nota =
            app.trombito.rallycopilot.motor.notas.Nota(
                posicionM = posicionM,
                finM = posicionM,
                direccion = direccion,
                grado = grado,
                modificadores = modificadores,
                distanciaSiguienteM = distanciaSiguienteM,
                cuidado = cuidado,
            )
    }

    /** Quitar la nota cercana. */
    data object Quitar : Orden

    /** Marcar con «cuidado» la nota cercana. */
    data object Cuidado : Orden

    /** Repetir la última nota cantada. */
    data object Repite : Orden

    /** Avisar antes las próximas notas. */
    data object MasPronto : Ajuste

    /** Avisar más tarde las próximas notas. */
    data object MasTarde : Ajuste

    /** Callar al copiloto. */
    data object Silencio : Ajuste

    /** Que el copiloto vuelva a hablar. */
    data object Habla : Ajuste

    /** Dejar una [Marca] en la posición actual. */
    data object MarcaAqui : Orden

    /** «Esta nota estaba mal»: dejar una [Marca] [Marca.ETIQUETA_NOTA_MAL] en la última nota dicha. */
    data object NotaMal : Orden

    // --- Preguntas: el copiloto responde por voz (ver `RespuestasVoz`) ------------------------------------------

    /** Una pregunta sobre la ruta o la conducción. */
    data class Pregunta(
        val tipo: TipoPregunta,
    ) : Orden

    // --- Control del copiloto -----------------------------------------------------------------------------------

    /** Cambiar cuánto habla el copiloto («modo notas», «solo avisos», «completo»). */
    data class Modo(
        val modo: ModoCopiloto,
    ) : Ajuste

    /** Subir o bajar el volumen propio del copiloto (pasos de 10 %). */
    data class Volumen(
        val subir: Boolean,
    ) : Ajuste

    /** Notas simplificadas (activar) o completas (no activar). */
    data class NotasSimplificadas(
        val activar: Boolean,
    ) : Ajuste

    /** Decir (activar) o no decir [indicacion] («con cruces», «sin rotondas»). */
    data class Avisar(
        val indicacion: Indicacion,
        val activar: Boolean,
    ) : Ajuste

    // --- Mapa y cronómetro --------------------------------------------------------------------------------------

    /** Capa de satélite (activar) o de mapa. */
    data class Satelite(
        val activar: Boolean,
    ) : Ajuste

    /** Mapa con el norte arriba (activar) o con el rumbo arriba. */
    data class NorteArriba(
        val activar: Boolean,
    ) : Ajuste

    /** Un paso más cerca (acercar) o más lejos del zoom del mapa. */
    data class Zoom(
        val acercar: Boolean,
    ) : Ajuste

    /** Dar la salida del cronómetro (cuenta atrás si la salida es manual). */
    data object Salida : Orden

    /** Parar el cronómetro: termina la conducción (con confirmación si hay un destino). */
    data object ParaCrono : Orden

    /** Dejar una [Marca] de radar en la posición actual. */
    data object RadarAqui : Orden

    // --- Navegación ---------------------------------------------------------------------------------------------

    /** Ir a [lugar] («llévame a Sóller»): el nombre, normalizado (sin tildes), tal como lo oyó el reconocedor. */
    data class IrA(
        val lugar: String,
    ) : Orden

    /** Añadir [lugar] como parada intermedia de la ruta en curso («añade parada en Inca»). */
    data class ParadaEn(
        val lugar: String,
    ) : Orden

    /** Ir a la casa guardada en los ajustes («volver a casa»). */
    data object VolverACasa : Orden

    /** Dejar la ruta en curso y seguir en modo libre (pide confirmación). */
    data object CancelarRuta : Orden

    /** El [tipo] de sitio más cercano a la posición actual («gasolinera más cercana»); pregunta si ir. */
    data class MasCercano(
        val tipo: TipoLugar,
    ) : Orden

    /** El siguiente resultado de la última búsqueda por voz («otro»). */
    data object Otro : Orden

    // --- Diálogo ------------------------------------------------------------------------------------------------

    /** «Sí» ([si] verdadero) o «no» a una pregunta del copiloto («di sí para terminar»). */
    data class Confirma(
        val si: Boolean,
    ) : Orden

    /** Pedir las órdenes más útiles. */
    data object Ayuda : Orden
}

/** Lo que se puede preguntar al copiloto. */
enum class TipoPregunta { CUANTO_FALTA, HORA_LLEGADA, LIMITE, DONDE_ESTOY, COMO_VOY }

/**
 * Sitios que se piden por categoría («gasolinera más cercana»). [categoria] es la del índice de búsqueda y [consulta]
 * la palabra con la que el buscador devuelve los más cercanos de ella; [nombre] es cómo se dice.
 */
enum class TipoLugar(
    val categoria: String,
    val consulta: String,
    val nombre: String,
    val femenino: Boolean,
) {
    GASOLINERA("gasolinera", "gasolinera", "gasolinera", true),
    APARCAMIENTO("aparcamiento", "aparcamiento", "aparcamiento", false),
    FARMACIA("farmacia", "farmacia", "farmacia", true),
    HOSPITAL("hospital", "hospital", "hospital", false),
    CAFETERIA("cafeteria", "cafeteria", "cafetería", true),
    SUPERMERCADO("supermercado", "supermercado", "supermercado", false),
    ;

    /** «cercana» o «cercano» según el género de [nombre]. */
    val cercano: String get() = if (femenino) "cercana" else "cercano"

    /** Las frases para pedirlo: «gasolinera más cercana», «gasolinera más próxima», «gasolinera cercana». */
    fun frases(): List<String> {
        val proximo = if (femenino) "próxima" else "próximo"
        return listOf("$nombre más $cercano", "$nombre más $proximo", "$nombre $cercano")
    }
}

/** Indicaciones que se pueden quitar o poner por voz (los filtros de `FiltroIndicaciones`). */
enum class Indicacion { CRUCES, PUENTES, ROTONDAS, SALIDAS }

/**
 * Marca dejada con «marca aquí» o «avisa radar aquí»: [posicionM] sobre la traza del tramo (si hay), [tiempoMs] del
 * reloj del sistema. [etiqueta] dice qué es (`null` = marca sin más; [ETIQUETA_RADAR] = radar propio;
 * [ETIQUETA_NOTA_MAL] = nota que Joan marcó como mal cantada, con [posicionM] la de esa nota).
 */
data class Marca(
    val posicionM: Double,
    val tiempoMs: Long,
    val tramoId: String? = null,
    val etiqueta: String? = null,
) {
    companion object {
        const val ETIQUETA_RADAR = "radar"
        const val ETIQUETA_NOTA_MAL = "nota-mal"
    }
}
