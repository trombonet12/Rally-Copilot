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
    ) = Candidata(
        Ruta(
            puntos = listOf(PuntoGeo(39.5, 2.6), PuntoGeo(39.6, 2.7)),
            maniobras = emptyList(),
            longitudM = 10_000.0 + marca,
            tiempoS = tiempoMin * 60.0,
        ),
        curvasKm * 1_000.0,
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
}
