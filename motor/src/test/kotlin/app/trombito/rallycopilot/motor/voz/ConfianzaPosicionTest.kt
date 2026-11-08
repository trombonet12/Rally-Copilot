package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.Anticipacion
import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.motor.velocidad.PrecisionGps
import app.trombito.rallycopilot.motor.voz.ConfianzaPosicion.Estado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfianzaPosicionTest {
    private val c = ConfianzaPosicion()

    private fun buena(
        t: Long,
        pos: Double = 100.0,
    ) = c.evaluar(t, precisionM = 5.0, lateralM = 3.0, posicionM = pos)

    private fun pausar() = c.evaluar(0, precisionM = PrecisionGps.FIJACION_UTIL_M + 1, lateralM = 0.0, posicionM = 10.0)

    @Test
    fun `empieza fiable y una fijacion buena no cambia nada`() {
        assertEquals(ConfianzaPosicion.Evaluacion(Estado.FIABLE, null), buena(0))
    }

    @Test
    fun `la precision en el limite util es fiable y por encima pausa al instante`() {
        assertEquals(Estado.FIABLE, c.evaluar(0, PrecisionGps.FIJACION_UTIL_M, 0.0, 10.0).estado)
        assertEquals(Estado.PAUSA, c.evaluar(1000, PrecisionGps.FIJACION_UTIL_M + 0.1, 0.0, 10.0).estado)
    }

    @Test
    fun `sin precision ni lateral conocidos se da por buena`() {
        assertEquals(Estado.FIABLE, c.evaluar(0, null, null, 10.0).estado)
    }

    @Test
    fun `sin posicion sobre la hoja pausa al instante`() {
        assertEquals(Estado.PAUSA, c.evaluar(0, 5.0, 0.0, null).estado)
    }

    @Test
    fun `lateral alto no pausa hasta pasar de tres segundos`() {
        assertEquals(Estado.FIABLE, c.evaluar(0, 5.0, 41.0, 10.0).estado)
        assertEquals(Estado.FIABLE, c.evaluar(1500, 5.0, 60.0, 10.0).estado)
        assertEquals(Estado.FIABLE, c.evaluar(3000, 5.0, 60.0, 10.0).estado)
        assertEquals(Estado.PAUSA, c.evaluar(3001, 5.0, 60.0, 10.0).estado)
    }

    @Test
    fun `lateral de exactamente cuarenta metros no cuenta como alto`() {
        assertEquals(Estado.FIABLE, c.evaluar(0, 5.0, 40.0, 10.0).estado)
        assertEquals(Estado.FIABLE, c.evaluar(10_000, 5.0, 40.0, 10.0).estado)
    }

    @Test
    fun `volver a la ruta antes de tres segundos reinicia el plazo del lateral`() {
        c.evaluar(0, 5.0, 50.0, 10.0)
        c.evaluar(2000, 5.0, 10.0, 10.0)
        c.evaluar(3000, 5.0, 50.0, 10.0)
        assertEquals(Estado.FIABLE, c.evaluar(5500, 5.0, 50.0, 10.0).estado)
        assertEquals(Estado.PAUSA, c.evaluar(6001, 5.0, 50.0, 10.0).estado)
    }

    @Test
    fun `tres evaluaciones buenas seguidas devuelven a fiable con la resincronizacion`() {
        pausar()
        assertEquals(ConfianzaPosicion.Evaluacion(Estado.PAUSA, null), buena(1000, 110.0))
        assertEquals(ConfianzaPosicion.Evaluacion(Estado.PAUSA, null), buena(2000, 120.0))
        assertEquals(ConfianzaPosicion.Evaluacion(Estado.FIABLE, 130.0), buena(3000, 130.0))
        assertEquals(ConfianzaPosicion.Evaluacion(Estado.FIABLE, null), buena(4000, 140.0))
    }

    @Test
    fun `una mala en mitad reinicia la cuenta de buenas`() {
        pausar()
        buena(1000)
        buena(2000)
        c.evaluar(3000, 80.0, 0.0, 100.0)
        assertEquals(Estado.PAUSA, buena(4000).estado)
        assertEquals(Estado.PAUSA, buena(5000).estado)
        assertEquals(Estado.FIABLE, buena(6000).estado)
    }

    @Test
    fun `con el lateral alto en pausa la fijacion no cuenta como buena`() {
        pausar()
        buena(1000)
        buena(2000)
        assertEquals(Estado.PAUSA, c.evaluar(3000, 5.0, 50.0, 100.0).estado)
        assertEquals(Estado.PAUSA, buena(4000).estado)
        assertEquals(Estado.PAUSA, buena(5000).estado)
        assertEquals(Estado.FIABLE, buena(6000).estado)
    }

    @Test
    fun `tras volver a fiable una mala vuelve a pausar y pide otra resincronizacion`() {
        pausar()
        repeat(3) { buena(1000L * (it + 1)) }
        assertEquals(Estado.PAUSA, c.evaluar(5000, null, null, null).estado)
        buena(6000)
        buena(7000)
        assertEquals(200.0, buena(8000, 200.0).resincronizar)
    }

    private val hoja =
        HojaNotas(
            listOf(
                ElementoHoja.DeNota(Nota(100.0, 120.0, Direccion.DERECHA, 3)),
                ElementoHoja.DeAviso(Aviso(300.0, TipoAviso.PUENTE)),
                ElementoHoja.DeNota(Nota(900.0, 930.0, Direccion.IZQUIERDA, 2)),
            ),
        )
    private val anticipacion = Anticipacion()

    @Test
    fun `relee la siguiente nota si esta dentro de la anticipacion`() {
        // 20 m/s con NORMAL: 20 * (5 + 1) = 120 m
        val e = pendientesTrasResincronizar(hoja, 200.0, 20.0, NivelAnticipacion.NORMAL, anticipacion)
        assertEquals(hoja.elementos[1], e)
    }

    @Test
    fun `no relee si la siguiente aun queda lejos`() {
        assertNull(pendientesTrasResincronizar(hoja, 400.0, 20.0, NivelAnticipacion.NORMAL, anticipacion))
    }

    @Test
    fun `salta las notas que quedaron atras`() {
        val e = pendientesTrasResincronizar(hoja, 150.0, 40.0, NivelAnticipacion.NORMAL, anticipacion)
        assertEquals(hoja.elementos[1], e)
    }

    @Test
    fun `una nota justo en la posicion cuenta como por delante`() {
        val e = pendientesTrasResincronizar(hoja, 300.0, 5.0, NivelAnticipacion.NORMAL, anticipacion)
        assertEquals(hoja.elementos[1], e)
    }

    @Test
    fun `sin nada por delante devuelve null`() {
        assertNull(pendientesTrasResincronizar(hoja, 1000.0, 30.0, NivelAnticipacion.MUY_PRONTO, anticipacion))
        assertNull(
            pendientesTrasResincronizar(HojaNotas(emptyList()), 0.0, 30.0, NivelAnticipacion.NORMAL, anticipacion),
        )
    }

    @Test
    fun `el nivel cambia la distancia a la que se relee`() {
        // 150 m por delante a 20 m/s: MUY_PRONTO 180 m si, TARDE 90 m no
        val pronto = pendientesTrasResincronizar(hoja, 150.0, 20.0, NivelAnticipacion.MUY_PRONTO, anticipacion)
        assertEquals(hoja.elementos[1], pronto)
        assertNull(pendientesTrasResincronizar(hoja, 150.0, 20.0, NivelAnticipacion.TARDE, anticipacion))
    }
}
