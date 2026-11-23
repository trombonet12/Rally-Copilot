package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmparejadorTramosViaTest {
    private val base = PuntoGeo(39.70, 2.70)
    private val emparejador = EmparejadorTramosVia()

    /** Punto [metros] al este y [norteM] al norte de la base. */
    private fun en(
        metros: Double,
        norteM: Double = 0.0,
    ): PuntoGeo {
        val grados = Math.toDegrees(metros / (PuntoGeo.RADIO_TIERRA_M * Math.cos(Math.toRadians(base.lat))))
        return PuntoGeo(base.lat + Math.toDegrees(norteM / PuntoGeo.RADIO_TIERRA_M), base.lon + grados)
    }

    /** Puntos cada 100 m entre [desdeM] y [hastaM] hacia el este (o hacia el oeste si `desdeM > hastaM`). */
    private fun recta(
        desdeM: Double,
        hastaM: Double,
        norteM: Double = 0.0,
    ): List<PuntoGeo> {
        val pasos = (kotlin.math.abs(hastaM - desdeM) / 100.0).toInt()
        return (0..pasos).map { en(desdeM + (hastaM - desdeM) * it / pasos, norteM) }
    }

    @Suppress("LongParameterList") // una etiqueta por dato que importa en cada prueba
    private fun tramo(
        trazado: List<PuntoGeo>,
        sentido: SentidoVia,
        ref: String? = "Ma-15",
        pkDesde: Double? = 10.0,
        pkHasta: Double? = 15.0,
    ) = TramoVia(ref, null, 0.0, 5_000.0, pkDesde, pkHasta, sentido, null, trazado)

    private fun guardado(
        id: String,
        tramo: TramoVia,
    ) = TramoViaGuardado(id, tramo)

    private val existente = tramo(recta(0.0, 5_000.0), SentidoVia.CRECIENTE)

    @Test
    fun `el mismo trozo con el eje invertido es el mismo tramo y conserva el sentido guardado`() {
        // El eje nuevo está del revés: mismos puntos, pero el PK baja y el troceador dice DECRECIENTE.
        val nuevo = tramo(recta(0.0, 5_000.0), SentidoVia.DECRECIENTE, pkDesde = 15.0, pkHasta = 10.0)

        val resultado = emparejador.emparejar(nuevo, listOf(guardado("a", existente)))

        assertEquals(listOf("a"), resultado.coincidencias.map { it.id })
        assertEquals(SentidoVia.CRECIENTE, resultado.sentido)
    }

    @Test
    fun `los PK desplazados por un eje nuevo no crean otro tramo`() {
        val nuevo = tramo(recta(0.0, 5_000.0), SentidoVia.CRECIENTE, pkDesde = 25.3, pkHasta = 30.3)

        val resultado = emparejador.emparejar(nuevo, listOf(guardado("a", existente)))

        assertEquals(listOf("a"), resultado.coincidencias.map { it.id })
        assertEquals(SentidoVia.CRECIENTE, resultado.sentido)
    }

    @Test
    fun `la misma carretera en sentido contrario de verdad es otro tramo con el sentido contrario`() {
        // Va al oeste; el eje invertido le pone también CRECIENTE y los mismos PK que al guardado.
        val nuevo = tramo(recta(5_000.0, 0.0), SentidoVia.CRECIENTE)

        val resultado = emparejador.emparejar(nuevo, listOf(guardado("a", existente)))

        assertTrue(resultado.coincidencias.isEmpty())
        assertEquals(SentidoVia.DECRECIENTE, resultado.sentido)
    }

    @Test
    fun `el sentido contrario con PK que el eje da como si fuese el mismo tampoco coincide`() {
        val nuevo = tramo(recta(5_000.0, 0.0), SentidoVia.DECRECIENTE, pkDesde = 15.0, pkHasta = 10.0)

        val resultado = emparejador.emparejar(nuevo, listOf(guardado("a", existente)))

        assertTrue(resultado.coincidencias.isEmpty())
        assertEquals(SentidoVia.DECRECIENTE, resultado.sentido)
    }

    @Test
    fun `la otra calzada de una autovia a 20 m en sentido contrario es otro tramo`() {
        val nuevo = tramo(recta(5_000.0, 0.0, norteM = 20.0), SentidoVia.DECRECIENTE)

        val resultado = emparejador.emparejar(nuevo, listOf(guardado("a", existente)))

        assertTrue(resultado.coincidencias.isEmpty())
    }

    @Test
    fun `un trozo que solapa un 60 por ciento es el mismo tramo y uno que solapa un 30 no`() {
        val sesenta = tramo(recta(2_000.0, 7_000.0), SentidoVia.CRECIENTE) // 3 km comunes de 5
        val treinta = tramo(recta(3_500.0, 8_500.0), SentidoVia.CRECIENTE) // 1,5 km comunes de 5

        val guardados = listOf(guardado("a", existente))
        assertEquals(listOf("a"), emparejador.emparejar(sesenta, guardados).coincidencias.map { it.id })
        assertTrue(emparejador.emparejar(treinta, guardados).coincidencias.isEmpty())
    }

    @Test
    fun `una carretera distinta no coincide aunque la geometria sea la misma`() {
        val nuevo = tramo(recta(0.0, 5_000.0), SentidoVia.CRECIENTE, ref = "Ma-13")

        assertTrue(emparejador.emparejar(nuevo, listOf(guardado("a", existente))).coincidencias.isEmpty())
    }

    @Test
    fun `una carretera con una curva recorrida en el mismo sentido coincide pese a otro eje`() {
        val curva = recta(0.0, 2_000.0) + (1..20).map { en(2_000.0, it * 100.0) }
        val guardada = tramo(curva, SentidoVia.DECRECIENTE, pkDesde = 8.0, pkHasta = 4.0)
        val nuevo = tramo(curva, SentidoVia.CRECIENTE, pkDesde = 3.0, pkHasta = 7.0)

        val resultado = emparejador.emparejar(nuevo, listOf(guardado("c", guardada)))

        assertEquals(listOf("c"), resultado.coincidencias.map { it.id })
        assertEquals(SentidoVia.DECRECIENTE, resultado.sentido)
    }

    @Test
    fun `un guardado simplificado a dos vertices coincide con un trozo que cubre solo una parte`() {
        val simplificado = tramo(listOf(en(0.0), en(5_000.0)), SentidoVia.CRECIENTE)
        val parte = tramo(recta(1_000.0, 3_000.0), SentidoVia.DECRECIENTE, pkDesde = 3.0, pkHasta = 1.0)
        val contrario = tramo(recta(3_000.0, 1_000.0), SentidoVia.CRECIENTE)

        val mismo = emparejador.emparejar(parte, listOf(guardado("a", simplificado)))
        val otro = emparejador.emparejar(contrario, listOf(guardado("a", simplificado)))

        assertEquals(listOf("a"), mismo.coincidencias.map { it.id })
        assertEquals(SentidoVia.CRECIENTE, mismo.sentido)
        assertTrue(otro.coincidencias.isEmpty())
        assertEquals(SentidoVia.DECRECIENTE, otro.sentido)
    }

    @Test
    fun `con varios tramos guardados que solapan devuelve todos ordenados por solape`() {
        val parcial = tramo(recta(0.0, 3_000.0), SentidoVia.CRECIENTE, pkDesde = 10.0, pkHasta = 13.0)

        val resultado =
            emparejador.emparejar(
                existente,
                listOf(guardado("parcial", parcial), guardado("entero", existente)),
            )

        assertEquals(listOf("parcial", "entero").toSet(), resultado.coincidencias.map { it.id }.toSet())
        assertTrue(resultado.coincidencias[0].solape >= resultado.coincidencias[1].solape)
    }

    @Test
    fun `sin tramos guardados es nuevo y mantiene el sentido del troceador`() {
        val resultado = emparejador.emparejar(existente, emptyList())

        assertTrue(resultado.coincidencias.isEmpty())
        assertEquals(SentidoVia.CRECIENTE, resultado.sentido)
    }

    @Test
    fun `si el guardado tiene sentido desconocido el trozo nuevo conserva el suyo`() {
        val desconocido = tramo(recta(0.0, 5_000.0), SentidoVia.DESCONOCIDO)
        val nuevo = tramo(recta(0.0, 5_000.0), SentidoVia.CRECIENTE)

        val resultado = emparejador.emparejar(nuevo, listOf(guardado("a", desconocido)))

        assertEquals(listOf("a"), resultado.coincidencias.map { it.id })
        assertEquals(SentidoVia.CRECIENTE, resultado.sentido)
    }

    @Test
    fun `sin traza se empareja por clave y solape de PK como antes`() {
        val sinTraza = tramo(emptyList(), SentidoVia.CRECIENTE)
        val mismo = tramo(emptyList(), SentidoVia.CRECIENTE, pkDesde = 11.0, pkHasta = 16.0)
        val contrario = tramo(emptyList(), SentidoVia.DECRECIENTE, pkDesde = 15.0, pkHasta = 10.0)
        val lejos = tramo(emptyList(), SentidoVia.CRECIENTE, pkDesde = 40.0, pkHasta = 45.0)

        val guardados = listOf(guardado("a", sinTraza))
        assertEquals(listOf("a"), emparejador.emparejar(mismo, guardados).coincidencias.map { it.id })
        assertTrue(emparejador.emparejar(contrario, guardados).coincidencias.isEmpty())
        assertTrue(emparejador.emparejar(lejos, guardados).coincidencias.isEmpty())
    }

    @Test
    fun `un trozo sin ref ni nombre no se empareja`() {
        val nuevo = tramo(recta(0.0, 5_000.0), SentidoVia.CRECIENTE, ref = null)

        val resultado = emparejador.emparejar(nuevo, listOf(guardado("a", existente)))

        assertTrue(resultado.coincidencias.isEmpty())
        assertEquals(SentidoVia.CRECIENTE, resultado.sentido)
    }

    @Test
    fun `la firma da el rumbo medio y los extremos de la traza`() {
        val este = FirmaTramoVia.de(recta(0.0, 5_000.0))
        val oeste = FirmaTramoVia.de(recta(5_000.0, 0.0))

        assertNotNull(este)
        assertEquals(90.0, este!!.rumboGrados!!, 0.5)
        assertEquals(270.0, oeste!!.rumboGrados!!, 0.5)
        assertEquals(5_000.0, este.cuerdaM, 10.0)
        assertEquals(recta(0.0, 5_000.0).first(), este.inicio)
        assertEquals(recta(0.0, 5_000.0).last(), este.fin)
    }

    @Test
    fun `la firma de una traza corta o de un bucle no tiene rumbo y sin dos puntos no hay firma`() {
        assertNull(FirmaTramoVia.de(listOf(en(0.0))))
        assertNull(FirmaTramoVia.de(listOf(en(0.0), en(10.0)))?.rumboGrados)
        assertNull(FirmaTramoVia.de(recta(0.0, 1_000.0) + recta(1_000.0, 0.0))?.rumboGrados)
    }
}
