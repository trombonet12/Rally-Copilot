package app.trombito.rallycopilot.motor.precarga

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeselasPrecargaTest {
    private val palma = PuntoGeo(39.5696, 2.6502)

    /** 1 km al este de [p] (grados de longitud a esa latitud). */
    private fun alEste(
        p: PuntoGeo,
        metros: Double,
    ) = PuntoGeo(p.lat, p.lon + metros / (111_320.0 * Math.cos(Math.toRadians(p.lat))))

    @Test
    fun `un punto da las teselas de su caja de 300 m sin duplicados`() {
        val teselas = TeselasPrecarga.corredor(listOf(palma), 18)
        // Una tesela de z18 mide ~118 m en Mallorca: 300 m de caja tocan 3 o 4 por eje.
        assertTrue(teselas.size in 9..16, "teselas: ${teselas.size}")
        assertTrue(teselas.all { it.z == 18 })
    }

    @Test
    fun `la tesela del punto siempre esta incluida`() {
        val n = 1 shl 18
        val x = ((palma.lon + 180.0) / 360.0 * n).toInt()
        val teselas = TeselasPrecarga.corredor(listOf(palma), 18)
        assertTrue(teselas.any { it.x == x }, "falta la columna $x")
        val centro = teselas.first { it.x == x && MercatorTesela.latitudNorte(it.y, 18) >= palma.lat }
        assertTrue(centro.y >= 0)
    }

    @Test
    fun `una recta de un kilometro da el numero esperado y ninguna fuera del corredor`() {
        val b = alEste(palma, 1_000.0)
        val teselas = TeselasPrecarga.corredor(listOf(palma, b), 18)
        val columnas = teselas.map { it.x }.toSet().size
        val filas = teselas.map { it.y }.toSet().size
        assertTrue(columnas in 10..14, "columnas: $columnas")
        assertTrue(filas in 3..5, "filas: $filas")
        assertEquals(columnas * filas, teselas.size, "recta horizontal: rectángulo completo")
    }

    @Test
    fun `muestrear evita huecos en segmentos largos`() {
        val b = alEste(palma, 5_000.0)
        val directo = TeselasPrecarga.corredor(listOf(palma, b), 18)
        val columnas = directo.map { it.x }.toSet().sorted()
        assertEquals(columnas.last() - columnas.first() + 1, columnas.size, "columnas contiguas")
    }

    @Test
    fun `puntos repetidos y rutas que se cruzan no duplican teselas`() {
        val b = alEste(palma, 500.0)
        val una = TeselasPrecarga.corredor(listOf(palma, b), 18)
        val ida = TeselasPrecarga.corredor(listOf(palma, b, palma, b), 18)
        assertEquals(una, ida)
    }

    @Test
    fun `un zoom menor da menos teselas`() {
        val b = alEste(palma, 2_000.0)
        val z17 = TeselasPrecarga.corredor(listOf(palma, b), 17).size
        val z18 = TeselasPrecarga.corredor(listOf(palma, b), 18).size
        assertTrue(z18 > z17)
    }

    @Test
    fun `fuera de Mallorca no hay teselas`() {
        val barcelona = PuntoGeo(41.3874, 2.1686)
        assertTrue(TeselasPrecarga.corredor(listOf(barcelona, alEste(barcelona, 800.0)), 18).isEmpty())
        assertTrue(TeselasPrecarga.corredor(emptyList(), 18).isEmpty())
    }

    @Test
    fun `en el borde de la caja solo entran las teselas que la tocan`() {
        val borde = PuntoGeo(39.5, TeselasPrecarga.MALLORCA.oeste)
        val teselas = TeselasPrecarga.corredor(listOf(borde), 18)
        assertTrue(teselas.isNotEmpty())
        val oesteMax = (TeselasPrecarga.MALLORCA.oeste + 180.0) / 360.0 * (1 shl 18)
        assertTrue(teselas.all { it.x >= oesteMax.toInt() })
    }

    @Test
    fun `un rectangulo a z18 y z19 cuenta igual que enumera`() {
        val r = Rectangulo(2.6500, 39.5600, 2.6600, 39.5700)
        for (z in 18..19) {
            assertEquals(TeselasPrecarga.rectangulo(r, z).size.toLong(), TeselasPrecarga.contarRectangulo(r, z))
        }
        val z18 = TeselasPrecarga.contarRectangulo(r, 18)
        val z19 = TeselasPrecarga.contarRectangulo(r, 19)
        assertTrue(abs(z19 - 4 * z18) < 4 * (z18 / 4 + 20), "z19 ≈ 4 × z18 ($z18, $z19)")
    }

    @Test
    fun `un rectangulo se recorta a Mallorca y fuera da cero`() {
        val grande = Rectangulo(2.0, 39.0, 4.0, 40.5)
        val recortado = TeselasPrecarga.contarRectangulo(grande, 18)
        val isla = TeselasPrecarga.contarRectangulo(TeselasPrecarga.MALLORCA, 18)
        assertEquals(isla, recortado)
        assertEquals(0, TeselasPrecarga.contarRectangulo(Rectangulo(0.0, 41.0, 1.0, 42.0), 18))
        assertTrue(TeselasPrecarga.rectangulo(Rectangulo(0.0, 41.0, 1.0, 42.0), 18).isEmpty())
    }

    @Test
    fun `las teselas del rectangulo cubren el rectangulo`() {
        val r = Rectangulo(2.6500, 39.5600, 2.6600, 39.5700)
        val teselas = TeselasPrecarga.rectangulo(r, 19)
        val minX = teselas.minOf { it.x }
        val maxY = teselas.maxOf { it.y }
        assertTrue(MercatorTesela.longitudOeste(minX, 19) <= r.oeste)
        assertTrue(MercatorTesela.latitudNorte(maxY + 1, 19) <= r.sur)
        assertEquals(teselas.size, teselas.toSet().size)
    }

    @Test
    fun `la fila TMS es el espejo de la fila XYZ`() {
        assertEquals(262_143, Tesela(18, 0, 0).filaTms)
        assertEquals(0, Tesela(18, 5, 262_143).filaTms)
    }

    @Test
    fun `el encuadre visible a z14 en un movil mide unos 1_3 km de ancho`() {
        val r = EncuadreVisible.rectangulo(39.57, 2.65, 14.0, 360.0, 760.0)
        val anchoM = (r.este - r.oeste) * 111_320.0 * Math.cos(Math.toRadians(39.57))
        val altoM = (r.norte - r.sur) * 111_320.0
        assertTrue(anchoM in 1_100.0..1_400.0, "ancho: $anchoM")
        assertTrue(altoM in 2_300.0..2_900.0, "alto: $altoM")
        assertTrue(r.oeste < 2.65 && r.este > 2.65 && r.sur < 39.57 && r.norte > 39.57)
    }

    @Test
    fun `la descarga automatica exige carga o mas del 30 por ciento`() {
        assertTrue(PoliticaPrecarga.permiteAutomatica(cargando = true, porcentajeBateria = 5))
        assertTrue(PoliticaPrecarga.permiteAutomatica(cargando = false, porcentajeBateria = 31))
        assertTrue(!PoliticaPrecarga.permiteAutomatica(cargando = false, porcentajeBateria = 30))
        assertTrue(!PoliticaPrecarga.permiteAutomatica(cargando = false, porcentajeBateria = 12))
    }

    @Test
    fun `la estimacion multiplica teselas por el medio y recorta a Mallorca`() {
        assertEquals(15_000_000L, EstimacionTamano.bytes(1_000L))
        val r = Rectangulo(2.6500, 39.5600, 2.6600, 39.5700)
        val e = EstimacionTamano.zona(r, 18)
        assertEquals(e.teselas * EstimacionTamano.BYTES_MEDIOS_POR_TESELA, e.bytes)
        assertEquals(0L, EstimacionTamano.zona(Rectangulo(0.0, 41.0, 1.0, 42.0), 18).bytes)
    }
}
