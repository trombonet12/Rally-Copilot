package app.trombito.rallycopilot.domain.enlaces

/** Resultado de seguir por red un enlace corto de Google Maps. */
sealed interface ResultadoResolucion {
    /** URL a la que se llegó (la primera redirección con coordenadas, o la última alcanzada). */
    data class Url(
        val final: String,
    ) : ResultadoResolucion

    /** No hay conexión a internet. */
    data object SinRed : ResultadoResolucion

    /** El enlace (o un salto de la redirección) no está en la lista cerrada de destinos. */
    data object NoPermitido : ResultadoResolucion

    /** Fallo de red, respuesta inesperada o demasiadas redirecciones. */
    data object Error : ResultadoResolucion
}

/** Resuelve enlaces cortos de Google Maps siguiendo su redirección. */
interface ResolvedorEnlaces {
    suspend fun resolver(url: String): ResultadoResolucion
}
