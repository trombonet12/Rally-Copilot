package app.trombito.rallycopilot.ui

import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import org.junit.Assert.assertEquals
import org.junit.Test

class ActualizacionDatosViewModelTest {
    @Test
    fun `comprueba la actualizacion una sola vez aunque se llame al girar la pantalla`() {
        val repositorio = FalsoRepositorioDatosMapa()
        val vm = ActualizacionDatosViewModel(repositorio)
        assertEquals(0, repositorio.comprobaciones)
        vm.comprobarActualizacion()
        vm.comprobarActualizacion()
        assertEquals(1, repositorio.comprobaciones)
    }
}
