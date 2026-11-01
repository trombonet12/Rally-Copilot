package app.trombito.rallycopilot.data.datosmapa

import app.trombito.rallycopilot.data.db.AjustesMapasEntity
import app.trombito.rallycopilot.data.db.ArchivoInstaladoEntity
import app.trombito.rallycopilot.data.db.EstadoDatosEntity
import app.trombito.rallycopilot.motor.datos.LIMITE_POR_DEFECTO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatosMapaMapperTest {
    private val gb = 1024L * 1024 * 1024

    private fun archivo(
        id: String,
        bytes: Long = 100,
    ) = ArchivoInstaladoEntity(id, "$id.bin", "a".repeat(64), bytes, "v1")

    private val sembrados = listOf(archivo("vectorial"), archivo("estilo"), archivo("vias"))
    private val estado = EstadoDatosEntity(versionInstalada = "datos-2026.11.01-1")

    @Test
    fun `lo sembrado sin satelite cuenta como instalado y sin satelite`() {
        val instalados = requireNotNull(construirEstado(sembrados, estado, null).instalados)

        assertFalse(instalados.conSatelite)
        assertEquals(300L, instalados.bytes)
        assertEquals("datos-2026.11.01-1", instalados.version)
    }

    @Test
    fun `cualquier id que empiece por satelite cuenta como satelite`() {
        listOf("satelite", "satelite-z17").forEach { id ->
            val instalados = requireNotNull(construirEstado(sembrados + archivo(id, 900), estado, null).instalados)

            assertTrue(id, instalados.conSatelite)
            assertEquals(1_200L, instalados.bytes)
        }
    }

    @Test
    fun `solo el satelite o sin nada no es tener los datos instalados`() {
        assertNull(construirEstado(emptyList(), null, null).instalados)
        assertNull(construirEstado(listOf(archivo("satelite")), estado, null).instalados)
        assertNull(construirEstado(sembrados.dropLast(1), estado, null).instalados)
    }

    @Test
    fun `una fila de ajustes con 1 GB se lee como 2 GB`() {
        assertEquals(2 * gb, AjustesMapasEntity(soloWifi = true, limiteBytes = gb).aDominio().limiteBytes)
    }

    @Test
    fun `los demas limites guardados se respetan y el de por defecto son 4 GB`() {
        assertEquals(2 * gb, AjustesMapasEntity(limiteBytes = 2 * gb).aDominio().limiteBytes)
        assertEquals(8 * gb, AjustesMapasEntity(limiteBytes = 8 * gb).aDominio().limiteBytes)
        assertEquals(100_000L, AjustesMapasEntity(limiteBytes = 100_000).aDominio().limiteBytes)
        assertEquals(4 * gb, AjustesMapasEntity().aDominio().limiteBytes)
        assertEquals(4 * gb, LIMITE_POR_DEFECTO)
    }
}
