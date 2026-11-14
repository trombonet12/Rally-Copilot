package app.trombito.rallycopilot.data.ruta

import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Ruta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SelectorPerfilesTest {
    private fun cand(
        tiempoMin: Double,
        curvasKm: Double,
        marca: Double = tiempoMin,
        conocidosKm: Double = 0.0,
    ) = Candidata(
        Ruta(
            puntos = listOf(PuntoGeo(39.5, 2.6), PuntoGeo(39.6, 2.7)),
            maniobras = emptyList(),
            longitudM = 10_000.0 + marca,
            tiempoS = tiempoMin * 60.0,
        ),
        curvasKm * 1_000.0,
        conocidosKm * 1_000.0,
    )

    private val rapida = cand(tiempoMin = 20.0, curvasKm = 2.0)

    private fun elegir(
        perfil: PerfilRuta,
        vararg otras: Candidata,
    ) = SelectorPerfiles.elegir(perfil, rapida, otras.toList())

    @Test
    fun `la rapida es siempre la de referencia`() {
        assertSame(rapida, elegir(PerfilRuta.MAS_RAPIDA, cand(21.0, 9.0)))
    }

    @Test
    fun `curvas coge la de mas km de curvas dentro de 1,6 veces la duracion de la rapida`() {
        val dentro = cand(31.0, 8.0)
        val justo = cand(32.0, 9.0) // 1,6 × 20 = 32 min: entra
        val fuera = cand(32.1, 15.0)
        assertSame(justo, elegir(PerfilRuta.SINUOSA, dentro, justo, fuera))
    }

    @Test
    fun `curvas a igualdad de curvas prefiere la mas rapida`() {
        val lenta = cand(30.0, 8.0, marca = 1.0)
        val veloz = cand(24.0, 8.0, marca = 2.0)
        assertSame(veloz, elegir(PerfilRuta.SINUOSA, lenta, veloz))
    }

    @Test
    fun `curvas sin ninguna candidata mejor devuelve la rapida`() {
        assertSame(rapida, elegir(PerfilRuta.SINUOSA, cand(40.0, 20.0), cand(22.0, 1.0)))
        assertSame(rapida, elegir(PerfilRuta.SINUOSA))
    }

    @Test
    fun `equilibrada coge la mejor relacion de curvas ganadas por minuto extra`() {
        val modesta = cand(21.0, 3.0) // +1 km en +1 min: 1,0 km/min
        val grande = cand(24.0, 8.0) // +6 km en +4 min: 1,5 km/min
        val muyLenta = cand(26.0, 20.0) // pasa de 1,25 × 20 = 25 min
        assertSame(grande, elegir(PerfilRuta.EQUILIBRADA, modesta, grande, muyLenta))
    }

    @Test
    fun `equilibrada prefiere una pequena ganancia barata a una grande cara`() {
        val barata = cand(20.5, 3.0) // +1 km en +0,5 min: 2 km/min
        val cara = cand(25.0, 9.0) // +7 km en +5 min: 1,4 km/min
        assertSame(barata, elegir(PerfilRuta.EQUILIBRADA, barata, cara))
    }

    @Test
    fun `equilibrada con una candidata igual de rapida y mas curvosa no divide por cero`() {
        val gratis = cand(20.0, 4.0, marca = 7.0)
        val otra = cand(22.0, 5.0)
        assertSame(gratis, elegir(PerfilRuta.EQUILIBRADA, gratis, otra))
    }

    @Test
    fun `equilibrada sin ninguna que gane curvas devuelve la rapida`() {
        assertSame(rapida, elegir(PerfilRuta.EQUILIBRADA, cand(21.0, 2.0), cand(22.0, 1.0)))
        assertSame(rapida, elegir(PerfilRuta.EQUILIBRADA))
    }

    @Test
    fun `curvas nunca tiene menos curvas que equilibrada y esta que la rapida`() {
        val otras = listOf(cand(21.0, 3.0), cand(24.0, 8.0), cand(30.0, 12.0), cand(35.0, 18.0))
        val r = SelectorPerfiles.elegir(PerfilRuta.MAS_RAPIDA, rapida, otras).curvasM
        val e = SelectorPerfiles.elegir(PerfilRuta.EQUILIBRADA, rapida, otras).curvasM
        val c = SelectorPerfiles.elegir(PerfilRuta.SINUOSA, rapida, otras).curvasM
        assertEquals(true, c >= e && e >= r)
    }

    // --- Preferencia suave por vías conocidas (H24) ---

    @Test
    fun `sin vias conocidas elige exactamente como antes en los tres perfiles`() {
        val otras = listOf(cand(21.0, 3.0), cand(24.0, 8.0), cand(30.0, 12.0), cand(35.0, 18.0))
        assertSame(rapida, SelectorPerfiles.elegir(PerfilRuta.MAS_RAPIDA, rapida, otras))
        assertSame(otras[1], SelectorPerfiles.elegir(PerfilRuta.EQUILIBRADA, rapida, otras))
        assertSame(otras[2], SelectorPerfiles.elegir(PerfilRuta.SINUOSA, rapida, otras))
    }

    @Test
    fun `equilibrada prefiere la conocida si cuesta un 8 por ciento mas de tiempo`() {
        val hoy = cand(21.0, 6.0, marca = 1.0) // la que elegiría hoy
        val conocida = cand(22.68, 4.0, marca = 2.0, conocidosKm = 5.0) // +8 % sobre 21 min y ≤ 25 min
        assertSame(hoy, elegir(PerfilRuta.EQUILIBRADA, hoy))
        assertSame(conocida, elegir(PerfilRuta.EQUILIBRADA, hoy, conocida))
    }

    @Test
    fun `curvas prefiere la conocida si cuesta un 8 por ciento mas de tiempo`() {
        val hoy = cand(25.0, 9.0, marca = 1.0)
        val conocida = cand(27.0, 9.0, marca = 2.0, conocidosKm = 3.0) // +8 %, mismas curvas
        assertSame(conocida, elegir(PerfilRuta.SINUOSA, hoy, conocida))
    }

    @Test
    fun `no gana la conocida si cuesta un 15 por ciento mas de tiempo`() {
        val hoy = cand(21.0, 6.0, marca = 1.0)
        val lenta = cand(24.15, 4.0, marca = 2.0, conocidosKm = 9.0) // +15 % sobre 21 min, aún dentro de 1,25 × 20
        assertSame(hoy, elegir(PerfilRuta.EQUILIBRADA, hoy, lenta))
        val hoyC = cand(25.0, 9.0, marca = 3.0)
        val lentaC = cand(28.75, 8.0, marca = 4.0, conocidosKm = 9.0) // +15 %
        assertSame(hoyC, elegir(PerfilRuta.SINUOSA, hoyC, lentaC))
    }

    @Test
    fun `el limite es un 10 por ciento sobre la elegida hoy`() {
        val hoy = cand(21.0, 6.0, marca = 1.0)
        val justo = cand(23.1, 4.0, marca = 2.0, conocidosKm = 2.0)
        val pasado = cand(23.2, 4.0, marca = 3.0, conocidosKm = 9.0)
        assertSame(justo, elegir(PerfilRuta.EQUILIBRADA, hoy, justo, pasado))
    }

    @Test
    fun `la conocida respeta el tope del chip aunque este dentro del 10 por ciento`() {
        // Curvas: la elegida hoy dura 31,9 min; la conocida 34,9 (+9,4 %) pasa de 1,6 × 20 = 32 min.
        val hoy = cand(31.9, 9.0, marca = 1.0)
        val fuera = cand(34.9, 8.0, marca = 2.0, conocidosKm = 9.0)
        assertSame(hoy, elegir(PerfilRuta.SINUOSA, hoy, fuera))
        // Equilibrada: la elegida hoy dura 24 min; la conocida 26 (+8 %) pasa de 1,25 × 20 = 25 min.
        val hoyE = cand(24.0, 8.0, marca = 3.0)
        val fueraE = cand(26.0, 9.0, marca = 4.0, conocidosKm = 9.0)
        assertSame(hoyE, elegir(PerfilRuta.EQUILIBRADA, hoyE, fueraE))
    }

    @Test
    fun `equilibrada no cambia por una conocida que no gana curvas a la rapida`() {
        val hoy = cand(21.0, 6.0, marca = 1.0)
        val sinCurvas = cand(21.5, 1.0, marca = 2.0, conocidosKm = 9.0)
        assertSame(hoy, elegir(PerfilRuta.EQUILIBRADA, hoy, sinCurvas))
    }

    @Test
    fun `gana la de mas metros conocidos y a empate la de hoy`() {
        val hoy = cand(21.0, 6.0, marca = 1.0, conocidosKm = 4.0)
        val menos = cand(21.5, 4.0, marca = 2.0, conocidosKm = 3.0)
        val mas = cand(22.0, 4.0, marca = 3.0, conocidosKm = 6.0)
        val empate = cand(21.2, 4.0, marca = 4.0, conocidosKm = 4.0)
        assertSame(mas, elegir(PerfilRuta.EQUILIBRADA, hoy, menos, mas, empate))
        assertSame(hoy, elegir(PerfilRuta.EQUILIBRADA, hoy, menos, empate))
    }

    @Test
    fun `rapida prefiere una conocida dentro del 10 por ciento y no una que pasa`() {
        val dentro = cand(21.8, 5.0, marca = 1.0, conocidosKm = 3.0) // +9 % sobre 20 min
        val fuera = cand(22.5, 5.0, marca = 2.0, conocidosKm = 9.0) // +12,5 %
        assertSame(dentro, elegir(PerfilRuta.MAS_RAPIDA, dentro, fuera))
        assertSame(rapida, elegir(PerfilRuta.MAS_RAPIDA, fuera))
    }

    @Test
    fun `rapida sin vias conocidas devuelve la rapida`() {
        assertSame(rapida, elegir(PerfilRuta.MAS_RAPIDA, cand(21.0, 9.0), cand(20.5, 1.0)))
    }

    @Test
    fun `curvas no cambia a una conocida con menos curvas que la de siempre`() {
        val hoy = cand(25.0, 9.0, marca = 1.0)
        val menosCurvas = cand(26.0, 7.0, marca = 2.0, conocidosKm = 5.0) // +4 %, pero con menos curvas
        val igualCurvas = cand(26.5, 9.0, marca = 3.0, conocidosKm = 2.0)
        assertSame(hoy, elegir(PerfilRuta.SINUOSA, hoy, menosCurvas))
        assertSame(igualCurvas, elegir(PerfilRuta.SINUOSA, hoy, menosCurvas, igualCurvas))
    }

    @Test
    fun `curvas no cambia a la rapida conocida porque tiene menos curvas`() {
        val hoy = cand(25.0, 9.0, marca = 1.0)
        val conocidaRapida = cand(22.0, 8.0, marca = 2.0, conocidosKm = 5.0)
        val rapidaConocida = Candidata(rapida.ruta, rapida.curvasM, 9_000.0)
        assertSame(hoy, SelectorPerfiles.elegir(PerfilRuta.SINUOSA, rapidaConocida, listOf(hoy, conocidaRapida)))
    }
}
