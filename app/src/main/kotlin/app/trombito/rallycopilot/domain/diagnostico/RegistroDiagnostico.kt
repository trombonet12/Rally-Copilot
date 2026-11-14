package app.trombito.rallycopilot.domain.diagnostico

/** Gravedad de una línea del registro de diagnóstico. */
enum class NivelRegistro { DEBUG, INFO, AVISO, ERROR }

/**
 * Registro de diagnóstico en un archivo del móvil (`registros/rally.log` en la carpeta externa de la app) para
 * sacarlo con `adb pull` sin abrir la app. Nunca lanza ni bloquea al llamante. **Nunca** se le pasan coordenadas
 * (lat/lon) ni secretos: velocidades, precisiones, distancias, tiempos y nombres de vía sí.
 */
interface RegistroDiagnostico {
    /** Encola una línea; se escribe en segundo plano. Sin efecto si la «Copia automática» está apagada. */
    fun registrar(
        nivel: NivelRegistro,
        etiqueta: String,
        mensaje: String,
    )

    /** Escribe ya, en el hilo que llama (para el cierre por excepción no controlada, que no da tiempo a una cola). */
    fun registrarSincrono(
        nivel: NivelRegistro,
        etiqueta: String,
        mensaje: String,
    )

    fun info(
        etiqueta: String,
        mensaje: String,
    ) = registrar(NivelRegistro.INFO, etiqueta, mensaje)

    fun aviso(
        etiqueta: String,
        mensaje: String,
    ) = registrar(NivelRegistro.AVISO, etiqueta, mensaje)

    fun error(
        etiqueta: String,
        mensaje: String,
    ) = registrar(NivelRegistro.ERROR, etiqueta, mensaje)

    /** Registro que no hace nada (valor por defecto de quien lo recibe opcionalmente y de las pruebas). */
    object Nulo : RegistroDiagnostico {
        override fun registrar(
            nivel: NivelRegistro,
            etiqueta: String,
            mensaje: String,
        ) = Unit

        override fun registrarSincrono(
            nivel: NivelRegistro,
            etiqueta: String,
            mensaje: String,
        ) = Unit
    }
}
