package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.Modificador

/** Orden de voz ya interpretada (ver [InterpreteOrdenes]). */
sealed interface Orden {
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
    data object MasPronto : Orden

    /** Avisar más tarde las próximas notas. */
    data object MasTarde : Orden

    /** Callar al copiloto. */
    data object Silencio : Orden

    /** Que el copiloto vuelva a hablar. */
    data object Habla : Orden

    /** Dejar una [Marca] en la posición actual. */
    data object MarcaAqui : Orden
}

/** Marca dejada con «marca aquí»: [posicionM] sobre la traza del tramo (si hay), [tiempoMs] del reloj del sistema. */
data class Marca(
    val posicionM: Double,
    val tiempoMs: Long,
    val tramoId: String? = null,
)
