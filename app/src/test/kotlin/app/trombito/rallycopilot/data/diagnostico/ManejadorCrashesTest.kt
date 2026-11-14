package app.trombito.rallycopilot.data.diagnostico

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManejadorCrashesTest {
    @Test
    fun `escribe la traza y delega en el manejador anterior`() {
        val registro = RegistroEnMemoria()
        var delegado: Throwable? = null
        val manejador = ManejadorCrashes(registro) { _, error -> delegado = error }
        val fallo = IllegalStateException("boom")
        manejador.uncaughtException(Thread.currentThread(), fallo)
        assertTrue(registro.lineas.single().startsWith("ERROR Crash crash hilo="))
        assertTrue(registro.lineas.single().contains("IllegalStateException"))
        assertEquals(fallo, delegado)
    }

    @Test
    fun `delega aunque el registro falle`() {
        val registro =
            object : app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico by RegistroEnMemoria() {
                override fun registrarSincrono(
                    nivel: app.trombito.rallycopilot.domain.diagnostico.NivelRegistro,
                    etiqueta: String,
                    mensaje: String,
                ) = error("disco")
            }
        var delegado = false
        ManejadorCrashes(registro) { _, _ -> delegado = true }
            .uncaughtException(Thread.currentThread(), RuntimeException())
        assertTrue(delegado)
    }
}
