package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EjesCarreterasTest {
    private val base = PuntoGeo(39.70, 2.70)

    private fun punto(
        esteM: Double,
        norteM: Double = 0.0,
    ): PuntoGeo {
        val dLon = Math.toDegrees(esteM / (PuntoGeo.RADIO_TIERRA_M * Math.cos(Math.toRadians(base.lat))))
        val dLat = Math.toDegrees(norteM / PuntoGeo.RADIO_TIERRA_M)
        return PuntoGeo(base.lat + dLat, base.lon + dLon)
    }

    /** Vía recta de [desdeM] a [hastaM] (este) a [norteM] de la base, con un punto cada ~500 m. */
    @Suppress("LongParameterList") // un parámetro por etiqueta de OSM que importa en cada prueba
    private fun via(
        id: Long,
        ref: String?,
        desdeM: Double,
        hastaM: Double,
        norteM: Double = 0.0,
        sentidoUnico: SentidoUnico = SentidoUnico.NO,
        clase: String = "secondary",
    ): Via {
        val pasos = maxOf(2, (Math.abs(hastaM - desdeM) / 500).toInt())
        val puntos = (0..pasos).map { punto(desdeM + (hastaM - desdeM) * it / pasos, norteM) }
        return Via(id, clase, null, null, null, sentidoUnico, ref, null, puntos)
    }

    private fun hitos(
        ref: String,
        km0: Double,
        desdeM: Int,
        hastaM: Int,
        sentido: Double = 1.0,
    ) = (desdeM..hastaM step 500).map { HitoKm(ref, km0 + sentido * (it - desdeM) / 1000.0, punto(it.toDouble())) }

    /** Autovía de 10 km: calzada al este (norte 0) y calzada al oeste (30 m al norte), dos `way` cada una. */
    private fun autovia(ref: String = "Ma-15") =
        listOf(
            via(1, ref, 0.0, 5_000.0, 0.0, SentidoUnico.ADELANTE),
            via(2, ref, 5_000.0, 10_000.0, 0.0, SentidoUnico.ADELANTE),
            via(3, ref, 10_000.0, 5_000.0, 30.0, SentidoUnico.ADELANTE),
            via(4, ref, 5_000.0, 0.0, 30.0, SentidoUnico.ADELANTE),
        )

    @Test
    fun `encadena todos los ways de la carretera en un eje`() {
        val eje = EjesCarreteras(autovia()).de("Ma-15")!!

        assertEquals(10_000.0, eje.longitudM, 150.0)
    }

    @Test
    fun `con dos calzadas el eje sigue solo una y no suma las dos`() {
        val eje = EjesCarreteras(autovia()).de("Ma-15")!!

        assertTrue(eje.longitudM < 11_000.0)
        // La proyeccion de ambas calzadas da el mismo PK.
        assertEquals(eje.pk(punto(4_000.0, 0.0))!!, eje.pk(punto(4_000.0, 30.0))!!, 0.02)
    }

    @Test
    fun `sin hitos el origen es el extremo mas cercano a Palma`() {
        // Palma queda al suroeste de la base: el extremo oeste es el origen aunque la via se dibuje hacia el oeste.
        val eje = EjesCarreteras(listOf(via(1, "Ma-15", 8_000.0, 0.0))).de("Ma-15")!!

        assertFalse(eje.deHitos)
        assertEquals(0.0, eje.pk(punto(0.0))!!, 0.01)
        assertEquals(8.0, eje.pk(punto(8_000.0))!!, 0.02)
        assertEquals(3.0, eje.pk(punto(3_000.0))!!, 0.02)
        assertTrue(
            eje.puntos.first().distanciaA(EjesCarreteras.PALMA) < eje.puntos.last().distanciaA(EjesCarreteras.PALMA),
        )
    }

    @Test
    fun `con hitos el origen es el extremo de km menor aunque no sea el de Palma`() {
        val vias = listOf(via(1, "Ma-15", 0.0, 8_000.0))
        val hitos = IndicePuntosKm(hitos("Ma-15", 20.0, 0, 8_000, sentido = -1.0))
        val eje = EjesCarreteras(vias, hitos).de("Ma-15")!!

        // El km 12 queda en el extremo este (Palma está al oeste): el eje empieza allí y el PK crece hacia el oeste.
        assertTrue(eje.deHitos)
        assertEquals(12.0, eje.pk(punto(8_000.0))!!, 0.02)
        assertEquals(20.0, eje.pk(punto(0.0))!!, 0.02)
        assertEquals(16.0, eje.pk(punto(4_000.0))!!, 0.02)
    }

    @Test
    fun `calibra el PK entre dos hitos con desplazamiento y escala`() {
        val vias = listOf(via(1, "Ma-15", 0.0, 6_000.0))
        val hitos =
            IndicePuntosKm(
                listOf(
                    HitoKm("Ma-15", 10.0, punto(1_000.0)),
                    HitoKm("Ma-15", 14.0, punto(3_000.0)),
                ),
            )
        val eje = EjesCarreteras(vias, hitos).de("Ma-15")!!

        assertTrue(eje.deHitos)
        assertEquals(10.0, eje.pk(punto(1_000.0))!!, 0.02)
        assertEquals(14.0, eje.pk(punto(3_000.0))!!, 0.02)
        // Entre hitos: dos km de PK por cada km recorrido.
        assertEquals(12.0, eje.pk(punto(2_000.0))!!, 0.02)
        // Fuera: un km por km desde el hito mas cercano.
        assertEquals(9.0, eje.pk(punto(0.0))!!, 0.02)
        assertEquals(17.0, eje.pk(punto(6_000.0))!!, 0.02)
    }

    @Test
    fun `descarta un hito mal ubicado que rompe el orden`() {
        val vias = listOf(via(1, "Ma-15", 0.0, 6_000.0))
        val hitos =
            IndicePuntosKm(
                listOf(
                    HitoKm("Ma-15", 1.0, punto(1_000.0)),
                    HitoKm("Ma-15", 40.0, punto(2_000.0)),
                    HitoKm("Ma-15", 3.0, punto(3_000.0)),
                    HitoKm("Ma-15", 5.0, punto(5_000.0)),
                ),
            )
        val eje = EjesCarreteras(vias, hitos).de("Ma-15")!!

        assertEquals(2.0, eje.pk(punto(2_000.0))!!, 0.02)
        assertEquals(4.0, eje.pk(punto(4_000.0))!!, 0.02)
    }

    @Test
    fun `con un solo hito no calibra y estima desde Palma`() {
        val vias = listOf(via(1, "Ma-15", 0.0, 6_000.0))
        val hitos = IndicePuntosKm(listOf(HitoKm("Ma-15", 50.0, punto(3_000.0))))
        val eje = EjesCarreteras(vias, hitos).de("Ma-15")!!

        assertFalse(eje.deHitos)
        assertEquals(3.0, eje.pk(punto(3_000.0))!!, 0.02)
    }

    @Test
    fun `los hitos fuera del eje no cuentan`() {
        val vias = listOf(via(1, "Ma-15", 0.0, 6_000.0))
        val hitos =
            IndicePuntosKm(
                listOf(HitoKm("Ma-15", 1.0, punto(1_000.0, 2_000.0)), HitoKm("Ma-15", 2.0, punto(3_000.0, 2_000.0))),
            )

        assertFalse(EjesCarreteras(vias, hitos).de("Ma-15")!!.deHitos)
    }

    @Test
    fun `la ref se normaliza, usa la primera y Ma-13A no es Ma-13`() {
        val ejes = EjesCarreteras(listOf(via(1, "Ma-13;Ma-13A", 0.0, 3_000.0), via(2, "Ma-13A", 5_000.0, 9_000.0)))

        assertNotNull(ejes.de("ma 13"))
        assertEquals(3_000.0, ejes.de("Ma-13")!!.longitudM, 60.0)
        assertEquals(4_000.0, ejes.de("Ma-13A")!!.longitudM, 60.0)
        assertNull(ejes.de("Ma-14"))
        assertNull(ejes.de(null))
        assertNull(ejes.de(" "))
    }

    @Test
    fun `ignora rotondas, enlaces y vias sin ref`() {
        val vias =
            listOf(
                via(1, "Ma-15", 0.0, 3_000.0),
                via(2, "Ma-15", 3_000.0, 3_200.0).copy(junction = "roundabout"),
                via(3, "Ma-15", 3_000.0, 6_000.0, clase = "motorway_link"),
                via(4, null, 3_000.0, 7_000.0),
            )

        assertEquals(3_000.0, EjesCarreteras(vias).de("Ma-15")!!.longitudM, 60.0)
    }

    @Test
    fun `une tramos separados por un hueco pequeno y descarta los lejanos`() {
        val vias =
            listOf(
                via(1, "Ma-13", 0.0, 4_000.0),
                via(2, "Ma-13", 5_000.0, 9_000.0),
                via(3, "Ma-13", 40_000.0, 41_000.0),
            )
        val eje = EjesCarreteras(vias).de("Ma-13")!!

        // 4 km + 1 km de hueco + 4 km; el trozo lejano no cuenta.
        assertEquals(9_000.0, eje.longitudM, 100.0)
        assertNull(eje.pk(punto(40_500.0)))
        assertEquals(6.0, eje.pk(punto(6_000.0))!!, 0.05)
    }

    @Test
    fun `une un tramo que queda antes del principal`() {
        val vias = listOf(via(1, "Ma-13", 5_000.0, 9_000.0), via(2, "Ma-13", 0.0, 3_000.0))
        val eje = EjesCarreteras(vias).de("Ma-13")!!

        assertEquals(9_000.0, eje.longitudM, 100.0)
        assertEquals(2.0, eje.pk(punto(2_000.0))!!, 0.05)
    }

    @Test
    fun `una posicion lejos del eje no tiene PK`() {
        val eje = EjesCarreteras(listOf(via(1, "Ma-15", 0.0, 5_000.0))).de("Ma-15")!!

        assertNull(eje.pk(punto(2_000.0, 500.0)))
        assertNotNull(eje.pk(punto(2_000.0, 500.0), maxDistanciaM = 600.0))
    }

    @Test
    fun `una cadena larga de ways pequenos da un solo eje`() {
        val vias = (0 until 400).map { via(it.toLong(), "Ma-15", it * 100.0, (it + 1) * 100.0) }
        val ejes =
            EjesCarreteras(vias + (0 until 300).map { via(1_000L + it, "Ma-${it + 100}", 0.0, 500.0, it * 10.0) })
        val eje = ejes.de("Ma-15")!!

        assertEquals(40_000.0, eje.longitudM, 400.0)
        assertEquals(20.0, eje.pk(punto(20_000.0))!!, 0.05)
        assertNotNull(ejes.de("Ma-399"))
    }

    @Test
    fun `una carretera sin vias utilizables no tiene eje`() {
        val vias = listOf(via(1, "Ma-15", 0.0, 3_000.0).copy(puntos = listOf(punto(0.0))))

        assertNull(EjesCarreteras(vias).de("Ma-15"))
    }
}
