package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.alertas.GeoPruebas.punto
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorPasoRadarTest {
    private fun radar(
        id: String = "r1",
        tipo: TipoRadar = TipoRadar.FIJO,
        rumbo: Double? = 0.0,
        limite: Int? = 80,
        esteM: Double = 5.0,
    ) = Radar(id, tipo, punto(500.0, esteM), rumbo, limite, null, "test")

    /** Conduce hacia el norte desde [desdeM] hasta [hastaM] cada [pasoM], con [velocidad] (km/h) en cada fijación. */
    @Suppress("LongParameterList")
    private fun conducir(
        detector: DetectorPasoRadar,
        desdeM: Double,
        hastaM: Double,
        pasoM: Double = 25.0,
        rumbo: Double? = 0.0,
        velocidad: (Double) -> Double = { 90.0 },
    ): List<PasoRadar> {
        val pasos = ArrayList<PasoRadar>()
        var m = desdeM
        while (m <= hastaM) {
            detector.actualizar(punto(m), rumbo, velocidad(m))?.let { pasos += it }
            m += pasoM
        }
        return pasos
    }

    @Test
    fun `al cruzar un radar fijo da la velocidad de la fijacion mas cercana y su limite`() {
        val d = DetectorPasoRadar(listOf(radar()))
        // Fijaciones en 375, 400, ... ; la más cercana a 500 es la de 500 (a 5 m): 87 km/h.
        val pasos = conducir(d, 300.0, 700.0) { m -> if (m == 500.0) 87.0 else 60.0 }
        assertEquals(listOf(PasoRadar("r1", 87.0, 80)), pasos)
    }

    @Test
    fun `solo un evento por pasada`() {
        val d = DetectorPasoRadar(listOf(radar()))
        assertEquals(1, conducir(d, 300.0, 900.0).size)
    }

    @Test
    fun `una segunda pasada despues de alejarse vuelve a dar evento`() {
        val d = DetectorPasoRadar(listOf(radar(rumbo = null)))
        assertEquals(1, conducir(d, 300.0, 700.0).size)
        // Vuelta atrás, sin rumbo de receptor: se deduce del movimiento (sur).
        val vuelta = ArrayList<PasoRadar>()
        var m = 700.0
        while (m >= 300.0) {
            d.actualizar(punto(m), null, 70.0)?.let { vuelta += it }
            m -= 25.0
        }
        assertEquals(1, vuelta.size)
    }

    @Test
    fun `sin limite el evento lo lleva a null`() {
        val d = DetectorPasoRadar(listOf(radar(limite = null)))
        assertNull(conducir(d, 300.0, 700.0).single().limiteKmh)
    }

    @Test
    fun `los radares del otro sentido no se registran`() {
        val d = DetectorPasoRadar(listOf(radar(rumbo = 180.0)))
        assertEquals(emptyList<PasoRadar>(), conducir(d, 300.0, 700.0))
    }

    @Test
    fun `sin rumbo de receptor se usa el del movimiento`() {
        val bien = DetectorPasoRadar(listOf(radar(rumbo = 0.0)))
        assertEquals(1, conducir(bien, 300.0, 700.0, rumbo = null).size)
        val mal = DetectorPasoRadar(listOf(radar(rumbo = 180.0)))
        assertEquals(0, conducir(mal, 300.0, 700.0, rumbo = null).size)
    }

    @Test
    fun `un radar sin rumbo cuenta en los dos sentidos`() {
        val d = DetectorPasoRadar(listOf(radar(rumbo = null)))
        assertEquals(1, conducir(d, 300.0, 700.0, rumbo = 0.0).size)
    }

    @Test
    fun `a mas de 30 m del radar no es un paso`() {
        val d = DetectorPasoRadar(listOf(radar(esteM = 45.0)))
        assertEquals(emptyList<PasoRadar>(), conducir(d, 300.0, 700.0))
    }

    @Test
    fun `a 30 m o menos si lo es aunque la fijacion mas cercana no caiga encima`() {
        // Fijaciones cada 25 m en 487,5 y 512,5: a 12,5 m en el eje, 28,3 m con 25 m de lado.
        val d = DetectorPasoRadar(listOf(radar(esteM = 25.0)))
        assertEquals(1, conducir(d, 287.5, 700.0).size)
    }

    @Test
    fun `solo cuentan los fijos, no los tramos ni las zonas moviles`() {
        val d =
            DetectorPasoRadar(
                listOf(
                    radar("t", TipoRadar.TRAMO_INICIO),
                    radar("z", TipoRadar.ZONA_MOVIL),
                    radar("f2", TipoRadar.TRAMO_FIN),
                ),
            )
        assertEquals(emptyList<PasoRadar>(), conducir(d, 300.0, 700.0))
    }

    @Test
    fun `empezar ya pegado al radar y alejarse no es un paso`() {
        val d = DetectorPasoRadar(listOf(radar()))
        assertEquals(emptyList<PasoRadar>(), conducir(d, 500.0, 700.0))
    }

    @Test
    fun `parado junto al radar con ruido del GPS no repite el evento`() {
        val d = DetectorPasoRadar(listOf(radar()))
        val pasos = ArrayList<PasoRadar>()
        // Se acerca, se para a 10 m y el GPS baila 8–14 m.
        conducir(d, 300.0, 475.0).also { pasos += it }
        listOf(490.0, 492.0, 488.0, 493.0, 490.0, 491.0).forEach { m ->
            d.actualizar(punto(m), 0.0, 0.0)?.let { pasos += it }
        }
        assertEquals(1, pasos.size)
    }

    @Test
    fun `a alta velocidad con fijaciones muy separadas tambien se detecta`() {
        // 250 km/h: 70 m entre fijaciones; las dos del paso quedan a 20 y 50 m.
        val d = DetectorPasoRadar(listOf(radar(esteM = 0.0)))
        val pasos = conducir(d, 100.0, 800.0, pasoM = 70.0, rumbo = 0.0) { 250.0 }
        assertEquals(1, pasos.size)
        assertEquals(250.0, pasos.single().velocidadKmh, 0.0)
    }

    @Test
    fun `varios radares a la vez cada uno con su paso`() {
        val a = radar("a")
        val b = a.copy(id = "b", punto = punto(900.0, 3.0), limiteKmh = 50)
        val d = DetectorPasoRadar(listOf(a, b))
        val pasos = conducir(d, 300.0, 1100.0)
        assertEquals(listOf("a", "b"), pasos.map { it.radarId })
        assertEquals(listOf(80, 50), pasos.map { it.limiteKmh })
    }

    @Test
    fun `sin radares no hay nada`() {
        assertNull(DetectorPasoRadar(emptyList()).actualizar(PuntoGeo(39.6, 2.9), 0.0, 90.0))
    }
}
