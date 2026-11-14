package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.domain.tramos.TipoCorte
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.tramos.MarcaTiempo
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.ReferenciaSubtramo
import app.trombito.rallycopilot.motor.tramos.SubtramoEnCurso
import app.trombito.rallycopilot.motor.tramos.Tramo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El seguimiento del subtramo en marcha: fantasma de trozo, cortes de la referencia y parciales (recta de ~4 km). */
class SeguimientoSubtramoTest {
    private val traza = listOf(PuntoGeo(39.0, 2.9), PuntoGeo(39.036, 2.9))
    private val tramo =
        Tramo(
            "ref",
            traza,
            traza.longitudMetros(),
            sectores = listOf(1_000.0, 2_000.0, 3_000.0),
            microsectores = listOf(1_500.0, 3_500.0),
        )

    /** La referencia pasó a 20 m/s: una marca cada 10 m hasta la meta. */
    private val pasada =
        Pasada(
            "ref",
            (0..(tramo.metaM / 10.0).toInt()).map { MarcaTiempo(it * 10.0, it * 500L) },
            (tramo.metaM / 20.0 * 1000.0).toLong(),
        )

    private fun seguimiento(tramoDe: (String) -> Tramo? = { if (it == "ref") tramo else null }) =
        SeguimientoSubtramo(tramoDe)

    private fun en(
        recorridoM: Double,
        tMs: Long,
        pasada: Pasada? = this.pasada,
        desdeM: Double = 500.0,
    ) = SubtramoEnCurso(
        "ref",
        recorridoM,
        tMs,
        pasada?.let { tMs - 25_000L },
        pasada?.let { ReferenciaSubtramo("ref", it, desdeM, desdeM + recorridoM, 100_000L) },
    )

    @Test
    fun `el fantasma arranca con el cronometro del subtramo en el inicio del trozo de la referencia`() {
        val s = seguimiento()
        // El coche va a 40 m/s; a 10 s el fantasma (20 m/s) lleva 200 m desde el inicio del trozo (500 m): 700 m.
        val vivo = checkNotNull(s.actualizar(en(400.0, 10_000L)))
        assertEquals(700.0, checkNotNull(vivo.fantasma).distanciaM, 1.0)
        assertEquals(tramo.metaM - 500.0, vivo.longitudM, 0.001)
    }

    @Test
    fun `los cortes de la referencia dentro del trozo son relativos a su inicio y numerados desde uno`() {
        val vivo = checkNotNull(seguimiento().actualizar(en(400.0, 10_000L)))
        assertEquals(listOf(500.0, 1_500.0, 2_500.0), vivo.sectoresM)
        assertEquals(listOf(1_000.0, 3_000.0), vivo.microsectoresM)
    }

    @Test
    fun `al pasar un corte se cierra su parcial con el tiempo interpolado y la diferencia con la referencia`() {
        val s = seguimiento()
        assertTrue(checkNotNull(s.actualizar(en(320.0, 8_000L))).parciales.isEmpty())
        val vivo = checkNotNull(s.actualizar(en(520.0, 13_000L)))
        val s1 = vivo.parciales.single()
        assertEquals(TipoCorte.SECTOR, s1.tipo)
        assertEquals(1, s1.numero)
        assertEquals(500.0, s1.distanciaM, 0.0)
        // 500 m a 40 m/s: 12,5 s; la referencia tardó 25 s en esos 500 m (de 500 a 1000 m).
        assertEquals(12_500L, s1.tiempoMs)
        assertEquals(-12_500L, s1.diferenciaMs)
        assertEquals(-12_500L, s1.deltaAcumuladoMs)
        assertEquals(s1, vivo.ultimaMarca)
        // Una fijación más no repite el parcial.
        assertEquals(1, checkNotNull(s.actualizar(en(560.0, 14_000L))).parciales.size)
    }

    @Test
    fun `los parciales de un mismo tipo miden desde el corte anterior`() {
        val s = seguimiento()
        s.actualizar(en(320.0, 8_000L))
        s.actualizar(en(920.0, 23_000L))
        val vivo = checkNotNull(s.actualizar(en(1_520.0, 38_000L)))
        val sectores = vivo.parciales.filter { it.tipo == TipoCorte.SECTOR }
        assertEquals(listOf(1, 2), sectores.map { it.numero })
        assertEquals(25_000L, sectores[1].duracionMs)
        // Los 1000 m entre los dos cortes: 25 s a 40 m/s contra 50 s de la referencia a 20 m/s.
        assertEquals(-25_000L, sectores[1].diferenciaMs)
        assertEquals(listOf(1), vivo.parciales.filter { it.tipo == TipoCorte.MICROSECTOR }.map { it.numero })
    }

    @Test
    fun `al acabar el subtramo o empezar otro se olvida lo anterior`() {
        val s = seguimiento()
        s.actualizar(en(320.0, 8_000L))
        assertEquals(1, checkNotNull(s.actualizar(en(520.0, 13_000L))).parciales.size)
        assertNull(s.actualizar(null))
        val nuevo = checkNotNull(s.actualizar(en(320.0, 8_000L)))
        assertTrue(nuevo.parciales.isEmpty())
        // Aunque no haya habido un null entre medias, un recorrido que retrocede es otro subtramo.
        s.actualizar(en(520.0, 13_000L))
        assertTrue(checkNotNull(s.actualizar(en(310.0, 7_000L))).parciales.isEmpty())
    }

    @Test
    fun `sin referencia solo hay la barra simple sobre el tramo en que va`() {
        val vivo = checkNotNull(seguimiento().actualizar(en(400.0, 10_000L, pasada = null)))
        assertNull(vivo.fantasma)
        assertTrue(vivo.sectoresM.isEmpty() && vivo.microsectoresM.isEmpty() && vivo.parciales.isEmpty())
        assertEquals(tramo.metaM, vivo.longitudM, 0.001)
    }

    @Test
    fun `si la pasada no cubre el inicio del trozo no hay fantasma pero el cronometro sigue`() {
        val corta = pasada.copy(tiempos = pasada.tiempos.filter { it.distanciaM >= 1_000.0 })
        val vivo = checkNotNull(seguimiento().actualizar(en(400.0, 10_000L, pasada = corta)))
        assertNull(vivo.fantasma)
        assertEquals(10_000L, vivo.transcurridoMs)
    }

    @Test
    fun `con el tramo de la referencia desconocido no rompe y no hay fantasma ni cortes`() {
        val vivo = checkNotNull(seguimiento { null }.actualizar(en(400.0, 10_000L)))
        assertNull(vivo.fantasma)
        assertTrue(vivo.sectoresM.isEmpty())
        assertNotNull(vivo.deltaMs)
    }

    @Test
    fun `si cambia el inicio del trozo de la referencia se reinicia el subtramo`() {
        val s = seguimiento()
        s.actualizar(en(320.0, 8_000L))
        s.actualizar(en(520.0, 13_000L))
        // Cambia el inicio del trozo (1500 m): los cortes pasan a 500 y 1500 m y el tiempo parte de cero otra vez.
        val vivo = checkNotNull(s.actualizar(en(560.0, 20_000L, desdeM = 1_500.0)))
        // Reiniciado, el tiempo en los 500 m se interpola solo con las muestras nuevas: 500/560 de 20 s.
        assertEquals(17_857L, vivo.parciales.first { it.tipo == TipoCorte.SECTOR }.tiempoMs)
    }

    @Test
    fun `si cambia el tramo de la referencia se reinicia el subtramo`() {
        val otro = tramo.copy(id = "otro")
        val s = seguimiento { mapOf("ref" to tramo, "otro" to otro)[it] }
        s.actualizar(en(320.0, 8_000L))
        s.actualizar(en(520.0, 13_000L))
        val deOtro =
            SubtramoEnCurso(
                "otro",
                560.0,
                20_000L,
                0L,
                ReferenciaSubtramo("otro", pasada.copy(tramoId = "otro"), 500.0, 1_060.0, 100_000L),
            )
        val vivo = checkNotNull(s.actualizar(deOtro))
        assertEquals(17_857L, vivo.parciales.first { it.tipo == TipoCorte.SECTOR }.tiempoMs)
    }
}
