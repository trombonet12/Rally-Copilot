package app.trombito.rallycopilot.data.diagnostico

import app.trombito.rallycopilot.domain.diagnostico.NivelRegistro
import app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Manejador de excepciones no controladas: escribe la traza **síncronamente** en el registro (el proceso va a morir) y
 * delega en el [anterior] (el del sistema, que muestra el cierre inesperado). Nada de lo que falle aquí puede impedir
 * esa delegación.
 */
class ManejadorCrashes(
    private val registro: RegistroDiagnostico,
    private val anterior: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    @Suppress("TooGenericExceptionCaught") // el registro no puede impedir que el sistema cierre la app como siempre
    override fun uncaughtException(
        hilo: Thread,
        error: Throwable,
    ) {
        try {
            val traza = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            registro.registrarSincrono(
                NivelRegistro.ERROR,
                ETIQUETA,
                "crash hilo=${hilo.name} ${error.javaClass.name}\n$traza",
            )
        } catch (_: Exception) {
            // Sin registro: se sigue con el manejador anterior.
        }
        anterior?.uncaughtException(hilo, error)
    }

    companion object {
        private const val ETIQUETA = "Crash"

        fun instalar(registro: RegistroDiagnostico) {
            Thread.setDefaultUncaughtExceptionHandler(
                ManejadorCrashes(registro, Thread.getDefaultUncaughtExceptionHandler()),
            )
        }
    }
}
