package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinoTramoViaTest {
    private val base = PuntoGeo(39.70, 2.70)

    private fun alNorte(metros: Double) =
        PuntoGeo(base.lat + Math.toDegrees(metros / PuntoGeo.RADIO_TIERRA_M), base.lon)

    private fun alEste(metros: Double) =
        PuntoGeo(
            base.lat,
            base.lon + Math.toDegrees(metros / (PuntoGeo.RADIO_TIERRA_M * Math.cos(Math.toRadians(base.lat)))),
        )

    private fun lugar(
        nombre: String,
        punto: PuntoGeo,
        tipo: TipoLugar = TipoLugar.PUEBLO,
    ) = LugarNombrado(nombre, punto, tipo)

    @Test
    fun `elige el pueblo mas cercano a la meta`() {
        val lugares = listOf(lugar("Lejos", alEste(2_500.0)), lugar("Cerca", alEste(600.0)))

        assertEquals("Cerca", DestinoTramoVia.de(alEste(-8_000.0), base, lugares))
    }

    @Test
    fun `un pueblo mas alla de tres kilometros no cuenta y sale el rumbo`() {
        val lugares = listOf(lugar("Fuera", alEste(3_200.0)))

        assertEquals("el norte", DestinoTramoVia.de(alNorte(-5_000.0), base, lugares))
    }

    @Test
    fun `sin lugares el rumbo va de la salida a la meta en ocho sectores`() {
        val sinLugares = emptyList<LugarNombrado>()

        assertEquals("el este", DestinoTramoVia.de(alEste(-5_000.0), base, sinLugares))
        assertEquals("el sur", DestinoTramoVia.de(alNorte(5_000.0), base, sinLugares))
        assertEquals("el oeste", DestinoTramoVia.de(alEste(5_000.0), base, sinLugares))
        val sureste = PuntoGeo(base.lat + 0.05, base.lon - 0.05 / Math.cos(Math.toRadians(base.lat)))
        assertEquals("el sureste", DestinoTramoVia.de(sureste, base, sinLugares))
        val suroeste = PuntoGeo(base.lat - 0.05, base.lon - 0.05 / Math.cos(Math.toRadians(base.lat)))
        assertEquals("el noreste", DestinoTramoVia.de(suroeste, base, sinLugares))
    }

    @Test
    fun `los sectores cambian a 22 grados y medio`() {
        assertEquals("el norte", DestinoTramoVia.rumbo(22.0))
        assertEquals("el noreste", DestinoTramoVia.rumbo(23.0))
        assertEquals("el norte", DestinoTramoVia.rumbo(338.0))
        assertEquals("el noroeste", DestinoTramoVia.rumbo(337.0))
        assertEquals("el suroeste", DestinoTramoVia.rumbo(225.0))
    }

    @Test
    fun `una aldea justo al lado no le gana a un pueblo casi igual de cerca`() {
        val lugares =
            listOf(
                lugar("Aldea", alEste(300.0), TipoLugar.ALDEA),
                lugar("Pueblo", alEste(1_400.0), TipoLugar.PUEBLO),
            )

        assertEquals("Pueblo", DestinoTramoVia.de(alEste(-8_000.0), base, lugares))
    }

    @Test
    fun `una aldea mucho mas cerca que el pueblo gana`() {
        val lugares =
            listOf(
                lugar("Aldea", alEste(100.0), TipoLugar.ALDEA),
                lugar("Pueblo", alEste(2_800.0), TipoLugar.PUEBLO),
            )

        assertEquals("Aldea", DestinoTramoVia.de(alEste(-8_000.0), base, lugares))
    }

    @Test
    fun `un municipio y un pueblo a igual distancia no se penalizan entre si`() {
        val lugares =
            listOf(
                lugar("Pueblo", alEste(900.0), TipoLugar.PUEBLO),
                lugar("Municipio", alEste(-800.0), TipoLugar.MUNICIPIO),
            )

        assertEquals("Municipio", DestinoTramoVia.de(alEste(-8_000.0), base, lugares))
    }

    @Test
    fun `los nombres vacios se ignoran`() {
        val lugares = listOf(lugar("  ", alEste(50.0)), lugar("Real", alEste(500.0)))

        assertEquals("Real", DestinoTramoVia.de(alEste(-8_000.0), base, lugares))
    }

    @Test
    fun `el nombre con destino se anade una sola vez`() {
        assertEquals(
            "Ma-15 · km 20,1→32,4 · hacia Manacor",
            nombreTramoViaConDestino("Ma-15 · km 20,1→32,4", "Manacor"),
        )
        assertEquals(
            "Ma-15 · km 20,1→32,4 · hacia Manacor",
            nombreTramoViaConDestino("Ma-15 · km 20,1→32,4 · hacia Manacor", "Inca"),
        )
    }
}
