package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.motor.notas.GeneradorHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.TablaGrados

/**
 * Hoja de [medida]. Con [factorCalibrado] la tabla escala la velocidad de curva con la del piloto. Con vías, el
 * límite de la vía donde cae cada curva es siempre su velocidad de referencia (tope), también con factor calibrado.
 * Sin ruta, la hoja sale vacía. Pesado: llamar fuera del hilo de las fijaciones.
 */
internal fun generarHoja(
    medida: RutaMedida?,
    datos: ContextoNotas?,
    factorCalibrado: Double?,
): HojaNotas {
    if (medida == null) return HojaNotas(emptyList())
    val tabla = if (factorCalibrado != null) TablaGrados(factor = factorCalibrado) else TablaGrados()
    val vRef: (Double) -> Double? =
        if (datos != null) {
            { posicion -> medida.limiteKmhEn(datos.indice, posicion) }
        } else {
            { null }
        }
    return GeneradorHoja(tabla, datos?.contextoVias).generar(medida.puntos, vRef)
}

/** Cuándo hay que regenerar la predicción del modo libre. */
internal object ReglasPrediccion {
    /** Metros de predicción recorridos tras los que se regenera la hoja. */
    const val RECORRIDO_REGENERAR_M = 500.0

    /** Distancia a la predicción a partir de la cual se considera que se ha salido de ella. */
    const val FUERA_MAX_M = 30.0

    /** Lo mismo, si la predicción acabó en un cruce dudoso y ya se ha llegado a su final. */
    const val FUERA_CRUCE_M = 20.0

    private const val EXTREMO_M = 2.0

    /** Fuera de la predicción: más lejos de [FUERA_MAX_M] (o de [FUERA_CRUCE_M] si acabó en un cruce dudoso). */
    fun fuera(
        actual: RutaMedida,
        proyeccion: RutaMedida.Proyeccion,
        cortadaEnCruceDudoso: Boolean,
    ): Boolean {
        val alFinal = cortadaEnCruceDudoso && proyeccion.posicionM >= actual.longitudM - EXTREMO_M
        return proyeccion.distanciaM > if (alFinal) FUERA_CRUCE_M else FUERA_MAX_M
    }

    fun debeRegenerar(
        actual: RutaMedida,
        proyeccion: RutaMedida.Proyeccion,
        cortadaEnCruceDudoso: Boolean,
    ): Boolean = proyeccion.posicionM >= RECORRIDO_REGENERAR_M || fuera(actual, proyeccion, cortadaEnCruceDudoso)
}
