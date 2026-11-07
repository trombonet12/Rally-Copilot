package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.motor.notas.GeneradorHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.RutaMedida
import app.trombito.rallycopilot.motor.notas.TablaGrados
import app.trombito.rallycopilot.motor.vias.LimiteVelocidad
import app.trombito.rallycopilot.motor.vias.Via

/**
 * Velocidad de referencia (km/h) de una vía para calibrar los grados: su límite explícito de OSM y, si no lo tiene,
 * el genérico de su clase en España (Reglamento General de Circulación, orientativo): autopista y autovía 120,
 * carretera convencional (trunk, primary, secondary, tertiary) 90, `unclassified` 70, residencial 50 y calle
 * residencial de coexistencia (`living_street`) 20. Las clases `*_link` heredan la de su vía. Clase desconocida
 * (track, service, path…): null, sin referencia.
 */
internal object VelocidadReferencia {
    private const val AUTOVIA_KMH = 120.0
    private const val CONVENCIONAL_KMH = 90.0
    private const val SIN_CLASIFICAR_KMH = 70.0
    private const val RESIDENCIAL_KMH = 50.0
    private const val COEXISTENCIA_KMH = 20.0

    /** Límite genérico de [clase] (etiqueta `highway` de OSM), o null. */
    fun porClase(clase: String): Double? =
        when (clase.trim().lowercase().removeSuffix("_link")) {
            "motorway" -> AUTOVIA_KMH
            "trunk", "primary", "secondary", "tertiary" -> CONVENCIONAL_KMH
            "unclassified" -> SIN_CLASIFICAR_KMH
            "residential" -> RESIDENCIAL_KMH
            "living_street" -> COEXISTENCIA_KMH
            else -> null
        }

    /** Límite explícito de [via] en el sentido de la marcha o, sin él, el de su clase. */
    fun de(
        via: Via,
        adelante: Boolean,
    ): Double? = LimiteVelocidad.aplicable(via, adelante)?.toDouble() ?: porClase(via.clase)
}

/**
 * Hoja de [medida]. Con [factorCalibrado] la tabla escala la velocidad de curva con la del piloto. Con vías, la
 * velocidad de referencia de cada curva (a la que se relativizan los grados) es el límite de la vía donde cae o,
 * si no tiene `maxspeed`, el de su clase ([VelocidadReferencia]), también con factor calibrado. Sin vías, no hay
 * referencia y valen los umbrales absolutos. Sin ruta, la hoja sale vacía. Pesado: llamar fuera del hilo de las
 * fijaciones.
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
            { posicion -> medida.viaEn(datos.indice, posicion)?.let { VelocidadReferencia.de(it.via, it.adelante) } }
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
