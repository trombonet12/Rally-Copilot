package app.trombito.rallycopilot.ui.comun

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guarda de aislamiento: toda clase de test que monte un banco Compose declara la regla [LimpiezaCompose]. */
class LimpiezaComposeTest {
    @Test
    fun `toda clase que usa un banco Compose declara la regla de limpieza`() {
        val raiz = File("src/test/kotlin")
        assertTrue("No se encuentra ${raiz.absolutePath}", raiz.isDirectory)
        val montan = Regex("""\b(BancoCompose)\(\)""")
        val sinRegla =
            raiz
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "BancoCompose.kt" }
                .filter { montan.containsMatchIn(it.readText()) }
                .filterNot { "LimpiezaCompose()" in it.readText() }
                .map { it.name }
                .toList()
        assertTrue("Falta @get:Rule val limpieza = LimpiezaCompose() en: $sinRegla", sinRegla.isEmpty())
    }
}
