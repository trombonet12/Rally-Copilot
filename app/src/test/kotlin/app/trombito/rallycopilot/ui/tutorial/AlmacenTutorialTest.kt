package app.trombito.rallycopilot.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Lo ya enseñado se guarda en preferencias simples y sobrevive a un nuevo arranque. */
@RunWith(RobolectricTestRunner::class)
class AlmacenTutorialTest {
    private fun almacen() = AlmacenTutorial(RuntimeEnvironment.getApplication())

    @Test
    fun `al principio no se ha visto nada`() {
        val almacen = almacen()
        assertFalse(almacen.bienvenidaVista())
        assertTrue(almacen.pistasVistas().isEmpty())
    }

    @Test
    fun `completar la bienvenida la marca vista y no vuelve a salir al arrancar de nuevo`() {
        TutorialViewModel(almacen()).onBienvenidaVista()
        // Otro arranque: un ViewModel y un almacén nuevos sobre las mismas preferencias.
        assertTrue(TutorialViewModel(almacen()).bienvenidaVista)
    }

    @Test
    fun `cada pista se marca por separado y persiste`() {
        val modelo = TutorialViewModel(almacen())
        modelo.onPistaEntendida(Pista.MAPA)
        assertEquals(setOf(Pista.MAPA), modelo.pistasVistas.value)
        assertEquals(setOf(Pista.MAPA), almacen().pistasVistas())
        modelo.onPistaEntendida(Pista.ANALISIS)
        assertEquals(setOf(Pista.MAPA, Pista.ANALISIS), TutorialViewModel(almacen()).pistasVistas.value)
    }

    @Test
    fun `ver el tutorial otra vez devuelve la bienvenida y las pistas`() {
        val modelo = TutorialViewModel(almacen())
        modelo.onBienvenidaVista()
        Pista.entries.forEach(modelo::onPistaEntendida)
        modelo.onReiniciar()
        assertTrue(modelo.pistasVistas.value.isEmpty())
        assertFalse(TutorialViewModel(almacen()).bienvenidaVista)
    }
}
