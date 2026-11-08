package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PuntoKilometricoTest {
    private val base = PuntoGeo(39.70, 2.70)

    /** Punto [metros] al este de [base] (la latitud no cambia). */
    private fun alEste(metros: Double): PuntoGeo {
        val grados = Math.toDegrees(metros / (PuntoGeo.RADIO_TIERRA_M * Math.cos(Math.toRadians(base.lat))))
        return PuntoGeo(base.lat, base.lon + grados)
    }

    private fun hito(
        ref: String,
        km: Double,
        metros: Double,
    ) = HitoKm(ref, km, alEste(metros))

    @Test
    fun `da el kilometro del hito mas cercano de la misma carretera`() {
        val indice = IndicePuntosKm(listOf(hito("Ma-10", 23.0, 0.0), hito("Ma-13", 5.0, 50.0)))

        val pk = indice.buscar("Ma-10", alEste(40.0))

        assertEquals(PuntoKilometrico("Ma-10", 23.0), pk)
    }

    @Test
    fun `ignora los hitos de otra carretera aunque esten mas cerca`() {
        val indice = IndicePuntosKm(listOf(hito("Ma-13", 5.0, 0.0), hito("Ma-10", 23.0, 900.0)))

        assertEquals(23.0, indice.buscar("Ma-10", alEste(10.0))!!.km, 1e-9)
        assertNull(IndicePuntosKm(listOf(hito("Ma-13", 5.0, 0.0))).buscar("Ma-10", alEste(10.0)))
    }

    @Test
    fun `no confunde Ma-13 con Ma-13A y compara sin mayusculas ni guiones`() {
        val indice = IndicePuntosKm(listOf(hito("Ma-13A", 7.0, 0.0), hito("Ma-13", 40.0, 600.0)))

        assertEquals(40.0, indice.buscar("ma 13", alEste(0.0))!!.km, 1e-9)
        assertEquals(7.0, indice.buscar("MA13A", alEste(0.0))!!.km, 1e-9)
    }

    @Test
    fun `con varias carreteras en la via mira la primera`() {
        val indice = IndicePuntosKm(listOf(hito("Ma-13", 40.0, 0.0), hito("Ma-13A", 7.0, 0.0)))

        assertEquals(40.0, indice.buscar("Ma-13;Ma-13A", alEste(0.0))!!.km, 1e-9)
    }

    @Test
    fun `interpola entre dos hitos consecutivos`() {
        val indice = IndicePuntosKm(listOf(hito("Ma-10", 23.0, 0.0), hito("Ma-10", 24.0, 1000.0)))

        assertEquals(23.25, indice.buscar("Ma-10", alEste(250.0))!!.km, 0.01)
        assertEquals(23.9, indice.buscar("Ma-10", alEste(900.0))!!.km, 0.01)
    }

    @Test
    fun `interpola tambien en sentido decreciente`() {
        val indice = IndicePuntosKm(listOf(hito("Ma-10", 24.0, 0.0), hito("Ma-10", 23.0, 1000.0)))

        assertEquals(23.5, indice.buscar("Ma-10", alEste(500.0))!!.km, 0.01)
    }

    @Test
    fun `no interpola si el punto esta fuera del tramo entre los hitos`() {
        val indice = IndicePuntosKm(listOf(hito("Ma-10", 23.0, 0.0), hito("Ma-10", 24.0, 1000.0)))

        assertEquals(23.0, indice.buscar("Ma-10", alEste(-200.0))!!.km, 1e-9)
        assertEquals(24.0, indice.buscar("Ma-10", alEste(1_300.0))!!.km, 1e-9)
    }

    @Test
    fun `no interpola con un hito demasiado lejano en km`() {
        val indice = IndicePuntosKm(listOf(hito("Ma-10", 23.0, 0.0), hito("Ma-10", 30.0, 1000.0)))

        assertEquals(23.0, indice.buscar("Ma-10", alEste(400.0))!!.km, 1e-9)
    }

    @Test
    fun `dos mojones del mismo km (uno por lado) no estropean la interpolacion`() {
        val indice =
            IndicePuntosKm(
                listOf(hito("Ma-10", 23.0, 0.0), hito("Ma-10", 23.0, 10.0), hito("Ma-10", 24.0, 1000.0)),
            )

        assertEquals(23.5, indice.buscar("Ma-10", alEste(505.0))!!.km, 0.02)
    }

    @Test
    fun `sin hito a menos de 1,5 km no hay punto kilometrico`() {
        val indice = IndicePuntosKm(listOf(hito("Ma-10", 23.0, 0.0)))

        assertNotNull(indice.buscar("Ma-10", alEste(1_400.0)))
        assertNull(indice.buscar("Ma-10", alEste(1_600.0)))
    }

    @Test
    fun `sin carretera o sin hitos no hay punto kilometrico`() {
        val indice = IndicePuntosKm(listOf(hito("Ma-10", 23.0, 0.0)))

        assertNull(indice.buscar(null, base))
        assertNull(indice.buscar("  ", base))
        assertNull(IndicePuntosKm(emptyList()).buscar("Ma-10", base))
    }
}
