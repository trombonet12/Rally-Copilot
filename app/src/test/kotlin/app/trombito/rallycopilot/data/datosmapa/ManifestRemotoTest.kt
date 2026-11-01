package app.trombito.rallycopilot.data.datosmapa

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManifestRemotoTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa

    @Before
    fun preparar() {
        entorno = EntornoDatosMapa(carpeta.newFolder("entorno"))
        entorno.publicar("v1")
    }

    @After
    fun cerrar() = entorno.close()

    private fun idsConExtra(contenidos: Map<String, ByteArray>): List<String> {
        entorno.publicarExtra("v1", contenidos)
        val remoto = ClienteHttp(entorno.config).leerManifestRemoto(entorno.config)
        return remoto.manifest.archivos.map { it.id }
    }

    private fun bytes() = PaqueteDePrueba.aleatorio(5, 1_000)

    @Test
    fun `el z17 con su nombre pnoa-mallorca se acepta`() {
        assertTrue("satelite-z17" in idsConExtra(mapOf("satelite-z17" to bytes())))
    }

    @Test
    fun `un id de satelite con un nombre que no es pnoa-mallorca pmtiles se descarta`() {
        // El entorno de pruebas nombra "satelite-malo" como "satelite-malo.bin".
        val ids = idsConExtra(mapOf("satelite-z17" to bytes(), "satelite-malo" to bytes()))
        assertTrue("satelite-z17" in ids)
        assertEquals(false, "satelite-malo" in ids)
    }
}
