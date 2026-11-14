package app.trombito.rallycopilot.motor.ruta

import org.junit.Assert.assertEquals
import org.junit.Test

class EstimadorLlegadaTest {
    private val ma13 = IdVia("Ma-13")
    private val ma10 = IdVia("Ma-10")

    // 10 km en 600 s: Valhalla va a 60 km/h.
    private fun tramo(via: IdVia?) = TramoEstimable(via, 10_000.0, 600.0)

    private fun perfil(
        ritmo: RitmoVia,
        factor: Double = PerfilRitmo.FACTOR_PERSONAL_DEFECTO,
    ) = PerfilRitmo(mapOf(ma13 to ritmo), factor)

    @Test
    fun `via conocida usa su velocidad media real`() {
        val e = EstimadorLlegada(perfil(RitmoVia(72.0, pasadas = 5, km = 50.0)))
        assertEquals(500.0, e.tiempoPrevistoS(tramo(ma13)), 1e-6)
    }

    @Test
    fun `una via mas lenta que Valhalla alarga el tiempo`() {
        val e = EstimadorLlegada(perfil(RitmoVia(50.0, pasadas = 3, km = 10.0)))
        assertEquals(720.0, e.tiempoPrevistoS(tramo(ma13)), 1e-6)
    }

    @Test
    fun `un dato demasiado rapido se acota a 1,6 veces la velocidad de Valhalla`() {
        val e = EstimadorLlegada(perfil(RitmoVia(100.0, pasadas = 4, km = 40.0)))
        assertEquals(375.0, e.tiempoPrevistoS(tramo(ma13)), 1e-6)
    }

    @Test
    fun `un dato demasiado lento se acota a 0,6 veces la velocidad de Valhalla`() {
        val e = EstimadorLlegada(perfil(RitmoVia(30.0, pasadas = 4, km = 40.0)))
        assertEquals(1000.0, e.tiempoPrevistoS(tramo(ma13)), 1e-6)
    }

    @Test
    fun `via desconocida o sin identidad usa Valhalla por el factor personal`() {
        val e = EstimadorLlegada(perfil(RitmoVia(72.0, 5, 50.0)))
        assertEquals(672.0, e.tiempoPrevistoS(tramo(ma10)), 1e-6)
        assertEquals(672.0, e.tiempoPrevistoS(tramo(null)), 1e-6)
    }

    @Test
    fun `sin perfil se aplica el factor por defecto 1,12`() {
        assertEquals(672.0, EstimadorLlegada().tiempoPrevistoS(tramo(ma13)), 1e-6)
    }

    @Test
    fun `historico insuficiente no se usa, suficiente por pasadas o por kilometros si`() {
        val pocas = EstimadorLlegada(perfil(RitmoVia(72.0, pasadas = 1, km = 2.9)))
        assertEquals(672.0, pocas.tiempoPrevistoS(tramo(ma13)), 1e-6)
        val dosPasadas = EstimadorLlegada(perfil(RitmoVia(72.0, pasadas = 2, km = 1.0)))
        assertEquals(500.0, dosPasadas.tiempoPrevistoS(tramo(ma13)), 1e-6)
        val tresKm = EstimadorLlegada(perfil(RitmoVia(72.0, pasadas = 1, km = 3.0)))
        assertEquals(500.0, tresKm.tiempoPrevistoS(tramo(ma13)), 1e-6)
    }

    @Test
    fun `una velocidad media nula no cuenta como historico`() {
        val e = EstimadorLlegada(perfil(RitmoVia(0.0, pasadas = 9, km = 99.0)))
        assertEquals(672.0, e.tiempoPrevistoS(tramo(ma13)), 1e-6)
    }

    @Test
    fun `el factor personal se acota a 1,0 y 1,4`() {
        val alto = EstimadorLlegada(PerfilRitmo(factorPersonal = 2.0))
        assertEquals(840.0, alto.tiempoPrevistoS(tramo(null)), 1e-6)
        val bajo = EstimadorLlegada(PerfilRitmo(factorPersonal = 0.5))
        assertEquals(600.0, bajo.tiempoPrevistoS(tramo(null)), 1e-6)
    }

    @Test
    fun `tramos degenerados no desmadran`() {
        val e = EstimadorLlegada(perfil(RitmoVia(72.0, 5, 50.0)))
        assertEquals(0.0, e.tiempoPrevistoS(TramoEstimable(ma13, 0.0, 0.0)), 1e-6)
        assertEquals(67.2, e.tiempoPrevistoS(TramoEstimable(ma13, 0.0, 60.0)), 1e-6)
        assertEquals(0.0, e.tiempoPrevistoS(TramoEstimable(ma13, 500.0, 0.0)), 1e-6)
    }

    @Test
    fun `mezcla de vias conocidas y desconocidas suma por tramos`() {
        val e = EstimadorLlegada(perfil(RitmoVia(72.0, 5, 50.0)))
        assertEquals(500.0 + 672.0, e.tiempoPrevistoS(listOf(tramo(ma13), tramo(ma10))), 1e-6)
        assertEquals(0.0, e.tiempoPrevistoS(emptyList<TramoEstimable>()), 1e-6)
    }

    @Test
    fun `la correccion en vivo escala lo que queda`() {
        val e = EstimadorLlegada(PerfilRitmo())
        val ritmo = RitmoViaje()
        ritmo.registrar(5000.0, 400.0, 300.0) // r = 4/3, w(5 km) = 0,5
        assertEquals(672.0 * (1 + 0.5 / 3), e.tiempoRestanteS(listOf(tramo(null)), ritmo), 1e-6)
        assertEquals(672.0, e.tiempoRestanteS(listOf(tramo(null))), 1e-6)
    }

    @Test
    fun `como ajuste reparte por fraccion y usa la primera via de la maniobra`() {
        val e = EstimadorLlegada(perfil(RitmoVia(72.0, 5, 50.0)))
        val maniobra =
            Maniobra(TipoManiobra.DERECHA, "", listOf("Ma-13", "Carrer"), 0, 1, 10_000.0, 600.0, null)
        assertEquals(250.0, e.comoAjuste().tiempoS(maniobra, 0.5), 1e-6)
        val otra = maniobra.copy(vias = listOf("Ma-10"))
        assertEquals(336.0, e.comoAjuste().tiempoS(otra, 0.5), 1e-6)
        val sinVia = maniobra.copy(vias = emptyList())
        assertEquals(336.0, e.comoAjuste().tiempoS(sinVia, 0.5), 1e-6)
    }

    @Test
    fun `como ajuste aplica la correccion en vivo`() {
        val ritmo = RitmoViaje()
        ritmo.registrar(5000.0, 400.0, 300.0)
        val maniobra = Maniobra(TipoManiobra.DERECHA, "", emptyList(), 0, 1, 10_000.0, 600.0, null)
        assertEquals(672.0 * (1 + 0.5 / 3), EstimadorLlegada().comoAjuste(ritmo).tiempoS(maniobra, 1.0), 1e-6)
    }

    @Test
    fun `el ajuste de Valhalla es el tiempo repartido por fraccion`() {
        val maniobra = Maniobra(TipoManiobra.DERECHA, "", emptyList(), 0, 1, 10_000.0, 600.0, null)
        assertEquals(150.0, AjusteTiempo.VALHALLA.tiempoS(maniobra, 0.25), 1e-6)
    }
}
