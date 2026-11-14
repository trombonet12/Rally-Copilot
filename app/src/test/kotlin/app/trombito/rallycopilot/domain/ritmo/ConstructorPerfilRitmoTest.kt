package app.trombito.rallycopilot.domain.ritmo

import app.trombito.rallycopilot.motor.analisis.Marcha
import app.trombito.rallycopilot.motor.analisis.VelocidadVia
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.IdVia
import app.trombito.rallycopilot.motor.ruta.PerfilRitmo
import app.trombito.rallycopilot.motor.ruta.ViajeHistorico
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructorPerfilRitmoTest {
    private fun via(
        id: Long,
        ref: String?,
        nombre: String? = null,
    ) = Via(
        id,
        "secondary",
        null,
        null,
        null,
        SentidoUnico.NO,
        ref,
        nombre,
        listOf(PuntoGeo(39.6, 2.9), PuntoGeo(39.61, 2.9)),
    )

    /** [km] recorridos a [kmh] en una vía, en [pasadas] pasadas. */
    private fun velocidad(
        via: Via,
        kmh: Double,
        km: Double = 4.0,
        pasadas: Int = 1,
    ) = VelocidadVia(via, pasadas, Marcha(km * 1_000.0, (km / kmh * 3_600_000.0).toLong()), Marcha())

    @Test
    fun `sin datos el perfil es el de partida`() {
        val perfil = ConstructorPerfilRitmo.construir(emptyList(), emptyList())

        assertEquals(PerfilRitmo(), perfil)
        assertEquals(PerfilRitmo.FACTOR_PERSONAL_DEFECTO, perfil.factorPersonal, 0.0)
    }

    @Test
    fun `la clave es la ref si hay y si no el nombre`() {
        val perfil =
            ConstructorPerfilRitmo.construir(
                listOf(velocidad(via(1, "Ma-13"), 50.0), velocidad(via(2, null, "Carrer de la Mar"), 30.0)),
                emptyList(),
            )

        assertEquals(50.0, perfil.vias.getValue(IdVia("Ma-13")).velocidadMediaKmh, 0.1)
        assertEquals(30.0, perfil.vias.getValue(IdVia("Carrer de la Mar")).velocidadMediaKmh, 0.1)
    }

    @Test
    fun `de una ref con varias ref usa la primera`() {
        val perfil = ConstructorPerfilRitmo.construir(listOf(velocidad(via(1, " Ma-13 ; Ma-1130"), 55.0)), emptyList())

        assertNotNull(perfil.vias[IdVia("Ma-13")])
        assertNull(perfil.vias[IdVia("Ma-1130")])
    }

    @Test
    fun `las vias de una misma carretera se suman por metros y tiempo, no por medias`() {
        // 6 km a 60 km/h (6 min) y 2 km a 30 km/h (4 min): 8 km en 10 min = 48 km/h; la media de medias daria 45.
        val perfil =
            ConstructorPerfilRitmo.construir(
                listOf(
                    velocidad(via(1, "Ma-10"), 60.0, km = 6.0),
                    velocidad(via(2, "Ma-10"), 30.0, km = 2.0),
                ),
                emptyList(),
            )

        val ritmo = perfil.vias.getValue(IdVia("Ma-10"))
        assertEquals(48.0, ritmo.velocidadMediaKmh, 0.1)
        assertEquals(8.0, ritmo.km, 0.01)
    }

    @Test
    fun `las pasadas de una carretera son las de su via mas recorrida, no la suma de sus vias`() {
        val perfil =
            ConstructorPerfilRitmo.construir(
                listOf(
                    velocidad(via(1, "Ma-10"), 50.0, km = 1.0, pasadas = 1),
                    velocidad(via(2, "Ma-10"), 50.0, km = 1.0, pasadas = 3),
                    velocidad(via(3, "Ma-10"), 50.0, km = 1.0, pasadas = 2),
                ),
                emptyList(),
            )

        assertEquals(3, perfil.vias.getValue(IdVia("Ma-10")).pasadas)
    }

    @Test
    fun `una carretera con una pasada y pocos km no es suficiente y con dos pasadas si`() {
        val una = ConstructorPerfilRitmo.construir(listOf(velocidad(via(1, "Ma-10"), 50.0, km = 1.0)), emptyList())
        val dosPasadas = listOf(velocidad(via(1, "Ma-10"), 50.0, km = 1.0, pasadas = 2))
        val dos = ConstructorPerfilRitmo.construir(dosPasadas, emptyList())

        assertFalse(una.vias.getValue(IdVia("Ma-10")).suficiente)
        assertTrue(dos.vias.getValue(IdVia("Ma-10")).suficiente)
    }

    @Test
    fun `el nombre de una via con ref tambien casa si ese nombre es siempre de la misma ref`() {
        val perfil =
            ConstructorPerfilRitmo.construir(
                listOf(
                    velocidad(via(1, "Ma-11", "Carretera de Sóller"), 45.0),
                    velocidad(via(2, "Ma-11", "Carretera de Sóller"), 45.0),
                ),
                emptyList(),
            )

        assertEquals(45.0, perfil.vias.getValue(IdVia("Carretera de Sóller")).velocidadMediaKmh, 0.1)
    }

    @Test
    fun `un nombre repetido en refs distintas no se registra por nombre`() {
        val perfil =
            ConstructorPerfilRitmo.construir(
                listOf(
                    velocidad(via(1, "Ma-11", "Carrer Major"), 45.0),
                    velocidad(via(2, "Ma-13", "Carrer Major"), 70.0),
                    velocidad(via(3, null, "Carrer Major"), 20.0),
                ),
                emptyList(),
            )

        assertNotNull(perfil.vias[IdVia("Ma-11")])
        assertNotNull(perfil.vias[IdVia("Ma-13")])
        assertNull(perfil.vias[IdVia("Carrer Major")])
    }

    @Test
    fun `las vias sin velocidad o sin ref ni nombre se ignoran`() {
        val sinTiempo = VelocidadVia(via(1, "Ma-10"), 1, Marcha(1_000.0, 0L), Marcha())
        val anonima = velocidad(via(2, null, null), 50.0)
        val vacia = velocidad(via(3, "  ", " "), 50.0)

        val perfil = ConstructorPerfilRitmo.construir(listOf(sinTiempo, anonima, vacia), emptyList())

        assertTrue(perfil.vias.isEmpty())
    }

    @Test
    fun `el factor personal sale de los recorridos con tiempo previsto`() {
        val viajes =
            listOf(
                ViajeHistorico(realS = 1_300.0, previstoS = 1_000.0),
                ViajeHistorico(realS = 1_200.0, previstoS = 1_000.0),
                ViajeHistorico(realS = 1_250.0, previstoS = 1_000.0),
                ViajeHistorico(realS = 900.0, previstoS = null),
            )

        val perfil = ConstructorPerfilRitmo.construir(emptyList(), viajes)

        assertEquals(1.25, perfil.factorPersonal, 1e-9)
    }

    @Test
    fun `con menos de tres viajes con previsto el factor es el de partida`() {
        val viajes = listOf(ViajeHistorico(1_300.0, 1_000.0), ViajeHistorico(1_300.0, 1_000.0))

        assertEquals(
            PerfilRitmo.FACTOR_PERSONAL_DEFECTO,
            ConstructorPerfilRitmo.construir(emptyList(), viajes).factorPersonal,
            0.0,
        )
    }
}
