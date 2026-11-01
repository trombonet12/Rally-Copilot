package app.trombito.rallycopilot.motor.analisis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NombreTramoTest {
    private fun via(
        ref: String? = null,
        nombre: String? = null,
        municipio: String? = null,
    ) = PasoVia(ref, nombre, municipio)

    @Test
    fun `una ref repetida y un municipio`() {
        val pasos = List(6) { via("Ma-2141", "Carretera de Sa Calobra", "Escorca") }
        assertEquals("Ma-2141 · Escorca", NombreTramo.de(pasos))
    }

    @Test
    fun `cambio de municipio da origen y destino`() {
        val pasos =
            listOf(
                via("Ma-10", municipio = "Pollença"),
                via("Ma-10", municipio = "Pollença"),
                via("Ma-10", municipio = "Lluc"),
            )
        assertEquals("Ma-10 Pollença → Lluc", NombreTramo.de(pasos))
    }

    @Test
    fun `varias refs se comprimen en orden sin repetir`() {
        val pasos =
            listOf(
                via("Ma-10", municipio = "Escorca"),
                via("Ma-2141", municipio = "Escorca"),
                via("ma-10", municipio = "Escorca"),
                via("Ma-2141"),
            )
        assertEquals("Ma-10 / Ma-2141 · Escorca", NombreTramo.de(pasos))
    }

    @Test
    fun `sin refs usa la calle mas frecuente y el municipio`() {
        val pasos =
            listOf(
                via(nombre = "Carrer Major", municipio = "Sóller"),
                via(nombre = "Camí de Muleta", municipio = "Sóller"),
                via(nombre = "Camí de Muleta", municipio = "Sóller"),
            )
        assertEquals("Camí de Muleta · Sóller", NombreTramo.de(pasos))
    }

    @Test
    fun `sin refs ni calles usa solo los municipios`() {
        assertEquals("Sóller → Deià", NombreTramo.de(listOf(via(municipio = "Sóller"), via(municipio = "Deià"))))
        assertEquals("Sóller", NombreTramo.de(listOf(via(municipio = "Sóller"), via(municipio = "Sóller"))))
    }

    @Test
    fun `sin ninguna pista da un nombre generico`() {
        assertEquals("Tramo sin nombre", NombreTramo.de(emptyList()))
        assertEquals("Tramo sin nombre", NombreTramo.de(listOf(via(ref = "  ", nombre = "", municipio = null))))
    }

    @Test
    fun `nunca pasa de 40 caracteres`() {
        val largas =
            listOf(
                via("Ma-10", municipio = "Santa Margalida de Muro"),
                via("Ma-2141", municipio = "Santa Eugènia de Berga del Pla"),
            )
        val nombre = NombreTramo.de(largas)
        assertTrue(nombre, nombre.length <= 40)
        assertTrue(nombre, nombre.startsWith("Ma-10"))

        val muchasRefs =
            listOf("Ma-10", "Ma-2141", "Ma-2142", "Ma-2200", "Ma-13A", "Ma-3460").map {
                via(it, municipio = "Escorca")
            }
        val corto = NombreTramo.de(muchasRefs)
        assertTrue(corto, corto.length <= 40)
        assertTrue(corto, corto.startsWith("Ma-10 / Ma-2141"))

        val absurda =
            NombreTramo.de(
                listOf(via(nombre = "Un nombre de camino excesivamente largo para una lista", municipio = "X")),
            )
        assertEquals(40, absurda.length)
        assertTrue(absurda.endsWith("…"))
        assertEquals("Ma-10 · E…", NombreTramo.de(listOf(via("Ma-10", municipio = "Escorca")), maxCaracteres = 10))
    }
}
