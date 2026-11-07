package app.trombito.rallycopilot.motor.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplementoTest {
    @Test
    fun `cada id de un complemento se asigna a su complemento`() {
        assertEquals(Complemento.SATELITE, Complemento.de("satelite"))
        assertEquals(Complemento.SATELITE, Complemento.de("satelite-z17"))
        assertEquals(Complemento.RELIEVE, Complemento.de("relieve"))
        assertEquals(Complemento.RELIEVE, Complemento.de("curvas"))
        assertEquals(Complemento.VOZ_SHARVARD, Complemento.de("voz-sharvard"))
        assertEquals(Complemento.VOZ_SHARVARD, Complemento.de("voz-sharvard-tokens"))
    }

    @Test
    fun `lo que va en el APK no es de ningun complemento`() {
        listOf("vectorial", "estilo", "vias", "vias-notas", "valhalla", "busqueda", "radares").forEach {
            assertNull(it, Complemento.de(it))
            assertFalse(it, Complemento.esBajoDemanda(it))
        }
    }

    @Test
    fun `los prefijos del APK son los de todos los complementos`() {
        assertEquals(listOf("satelite", "relieve", "curvas", "voz-sharvard"), Complemento.PREFIJOS_BAJO_DEMANDA)
        assertTrue(Complemento.esBajoDemanda("curvas"))
    }

    @Test
    fun `un complemento esta instalado con todos sus ids requeridos`() {
        assertFalse(Complemento.RELIEVE.estaCompleto(setOf("relieve")))
        assertTrue(Complemento.RELIEVE.estaCompleto(setOf("relieve", "curvas", "vias")))
        assertFalse(Complemento.VOZ_SHARVARD.estaCompleto(setOf("voz-sharvard")))
        assertTrue(Complemento.VOZ_SHARVARD.estaCompleto(setOf("voz-sharvard", "voz-sharvard-tokens")))
        assertTrue(Complemento.SATELITE.estaCompleto(setOf("satelite")))
    }
}
