package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class DetectorSalidaRutaTest {
    @Test
    fun `dentro de la ruta nunca avisa`() {
        val d = DetectorSalidaRuta()
        for (t in 0..20) assertFalse(d.actualizar(5.0, t * 1000L))
    }

    @Test
    fun `no avisa hasta que pasa el tiempo continuado fuera del umbral`() {
        val d = DetectorSalidaRuta(umbralM = 40.0, tiempoMs = 5000)
        assertFalse(d.actualizar(60.0, 1000))
        assertFalse(d.actualizar(60.0, 3000))
        assertFalse(d.actualizar(60.0, 5999))
        assertTrue(d.actualizar(60.0, 6000))
        assertTrue(d.actualizar(60.0, 7000))
    }

    @Test
    fun `por defecto avisa a los 3 segundos continuados fuera de ruta`() {
        val d = DetectorSalidaRuta()
        assertFalse(d.actualizar(60.0, 1000))
        assertFalse(d.actualizar(60.0, 3999))
        assertTrue(d.actualizar(60.0, 4000))
    }

    @Test
    fun `el sentido contrario avisa a la persistencia corta aunque la distancia a la ruta sea cero`() {
        val d = DetectorSalidaRuta()
        assertFalse(d.actualizar(0.0, 0, sentidoContrario = true))
        assertFalse(d.actualizar(0.0, 999, sentidoContrario = true))
        assertTrue(d.actualizar(0.0, 1000, sentidoContrario = true))
    }

    @Test
    fun `el sentido contrario no confirmado reinicia la cuenta`() {
        val d = DetectorSalidaRuta()
        assertFalse(d.actualizar(0.0, 0, sentidoContrario = true))
        assertFalse(d.actualizar(0.0, 500, sentidoContrario = false))
        assertFalse(d.actualizar(0.0, 1200, sentidoContrario = true))
        assertTrue(d.actualizar(0.0, 2200, sentidoContrario = true))
    }

    @Test
    fun `seguidor y detector detectan la marcha atras a 60 kmh antes de 100 m de retroceso`() {
        val ruta =
            Ruta(
                puntos =
                    listOf(
                        PuntoGeo(39.6, 2.9),
                        PuntoGeo(
                            39.6,
                            2.9 + 3000.0 / (111_194.9 * cos(Math.toRadians(39.6))),
                        ),
                    ),
                maniobras = emptyList(),
                longitudM = 3000.0,
                tiempoS = 300.0,
            )
        val seguidor = SeguidorRuta(ruta)
        val d = DetectorSalidaRuta()

        fun en(m: Double) = PuntoGeo(39.6, 2.9 + m / (111_194.9 * cos(Math.toRadians(39.6))))
        for (i in 0..90) {
            assertFalse(
                d.actualizar(
                    seguidor.actualizar(en(i * 17.0), i * 1000L).run { distanciaALaRutaM },
                    i * 1000L,
                ),
            )
        }
        var detectadoA = -1.0
        for (paso in 1..20) {
            val t = (90 + paso) * 1000L
            val e = seguidor.actualizar(en(90 * 17.0 - paso * 17.0), t)
            if (d.actualizar(e.distanciaALaRutaM, t, e.sentidoContrario) && detectadoA < 0) detectadoA = paso * 17.0
        }
        assertTrue("detectado a $detectadoA m", detectadoA in 1.0..100.0)
    }

    @Test
    fun `volver dentro del umbral antes de tiempo reinicia la cuenta`() {
        val d = DetectorSalidaRuta()
        assertFalse(d.actualizar(60.0, 0))
        assertFalse(d.actualizar(60.0, 2500))
        assertFalse(d.actualizar(10.0, 2800))
        assertFalse(d.actualizar(60.0, 3000))
        assertFalse(d.actualizar(60.0, 5999))
        assertTrue(d.actualizar(60.0, 6000))
    }

    @Test
    fun `el umbral es estricto`() {
        val d = DetectorSalidaRuta(umbralM = 40.0, tiempoMs = 1000)
        assertFalse(d.actualizar(40.0, 0))
        assertFalse(d.actualizar(40.0, 5000))
    }

    @Test
    fun `histeresis la vuelta exige bajar de la mitad del umbral`() {
        val d = DetectorSalidaRuta(umbralM = 40.0, tiempoMs = 1000)
        d.actualizar(60.0, 0)
        assertTrue(d.actualizar(60.0, 1000))
        assertTrue(d.actualizar(30.0, 2000))
        assertTrue(d.actualizar(20.0, 3000))
        assertFalse(d.actualizar(19.9, 4000))
    }

    @Test
    fun `tras volver puede salir de nuevo`() {
        val d = DetectorSalidaRuta(umbralM = 40.0, tiempoMs = 1000)
        d.actualizar(60.0, 0)
        assertTrue(d.actualizar(60.0, 1000))
        assertFalse(d.actualizar(0.0, 2000))
        assertFalse(d.actualizar(60.0, 3000))
        assertTrue(d.actualizar(60.0, 4000))
    }
}
