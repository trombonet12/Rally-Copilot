package app.trombito.rallycopilot.domain.perfil

import app.trombito.rallycopilot.data.carreteras.viaFalsa
import app.trombito.rallycopilot.motor.analisis.UsoVia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarneCarreterasTest {
    private val ma10 = viaFalsa(1, "Ma-10", 0, 1_000)
    private val ma10Otra = viaFalsa(7, "ma-10", 0, 1_000)
    private val ma15 = viaFalsa(2, "Ma-15", 0, 1_000)
    private val calle = viaFalsa(3, null, 0, 1_000).copy(nombre = "Carrer Major")

    private fun uso(via: app.trombito.rallycopilot.motor.vias.Via) = UsoVia(via, 1_000.0, 0)

    @Test
    fun `sin viajes no hay carne`() {
        assertTrue(CarneCarreteras.de(emptyList()).isEmpty())
    }

    @Test
    fun `cuenta en cuantos viajes aparece cada carretera, las mas repetidas primero`() {
        val carne =
            CarneCarreteras.de(
                listOf(listOf(uso(ma15), uso(ma10)), listOf(uso(ma10)), listOf(uso(ma10Otra), uso(calle))),
            )
        assertEquals(
            listOf(CarreteraCarne("Ma-10", 3), CarreteraCarne("Carrer Major", 1), CarreteraCarne("Ma-15", 1)),
            carne,
        )
    }

    @Test
    fun `una carretera con varias vias en el mismo viaje cuenta una sola vez`() {
        val carne = CarneCarreteras.de(listOf(listOf(uso(ma10), uso(ma10Otra))))
        assertEquals(listOf(CarreteraCarne("Ma-10", 1)), carne)
    }

    @Test
    fun `una via sin referencia ni nombre no entra`() {
        val anonima = viaFalsa(9, null, 0, 1_000)
        assertTrue(CarneCarreteras.de(listOf(listOf(uso(anonima)))).isEmpty())
    }
}
