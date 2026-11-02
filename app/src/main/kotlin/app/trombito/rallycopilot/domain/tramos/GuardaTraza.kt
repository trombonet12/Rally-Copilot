package app.trombito.rallycopilot.domain.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.TramoDesdeRecorrido

/** Resultado de guardar una traza como tramo. */
sealed interface ResultadoGuardado {
    /** Tramo guardado, sin pasadas; [nombre] es el efectivo (el dado o el automático; null si no hay ninguno). */
    data class Guardado(
        val tramoId: String,
        val nombre: String?,
    ) : ResultadoGuardado

    /** La traza mide menos de lo que pide un tramo ([TramoDesdeRecorrido.LONGITUD_MIN_M]). */
    data object Corto : ResultadoGuardado

    /** La base de datos no pudo guardarlo. */
    data object Fallo : ResultadoGuardado
}

/** Guarda una traza que no viene de conducir (importada o dibujada) como tramo sin pasadas. */
fun interface GuardaTraza {
    /** [nombre] es el del usuario (null: queda el automático). */
    suspend fun guardar(
        traza: List<PuntoGeo>,
        nombre: String?,
    ): ResultadoGuardado
}
