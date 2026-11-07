package app.trombito.rallycopilot.data.voz

import app.trombito.rallycopilot.domain.voz.VozCopiloto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VocesInstaladasTest {
    @get:Rule val carpeta = TemporaryFolder()

    @Test
    fun `dave siempre esta disponible`() {
        val voces = VocesInstaladas(carpeta.root)

        assertTrue(voces.estaInstalada(VozCopiloto.DAVEFX))
        assertEquals(VozCopiloto.DAVEFX, voces.efectiva(VozCopiloto.DAVEFX))
    }

    @Test
    fun `sin los archivos de sharvard habla dave`() {
        val voces = VocesInstaladas(carpeta.root)

        assertFalse(voces.estaInstalada(VozCopiloto.SHARVARD))
        assertEquals(VozCopiloto.DAVEFX, voces.efectiva(VozCopiloto.SHARVARD))
    }

    @Test
    fun `con el modelo pero sin los tokens sharvard no esta lista`() {
        carpeta.newFile(NOMBRE_MODELO_SHARVARD).writeText("modelo")

        assertEquals(VozCopiloto.DAVEFX, VocesInstaladas(carpeta.root).efectiva(VozCopiloto.SHARVARD))
    }

    @Test
    fun `con modelo y tokens habla sharvard y al borrarlos vuelve dave`() {
        val modelo = carpeta.newFile(NOMBRE_MODELO_SHARVARD).apply { writeText("modelo") }
        carpeta.newFile(NOMBRE_TOKENS_SHARVARD).writeText("tokens")
        val voces = VocesInstaladas(carpeta.root)

        assertEquals(VozCopiloto.SHARVARD, voces.efectiva(VozCopiloto.SHARVARD))
        modelo.delete()
        assertEquals(VozCopiloto.DAVEFX, voces.efectiva(VozCopiloto.SHARVARD))
    }
}
