package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Hoja completa (curvas, rasantes, avisos y precaución) de rutas reales de la Serra con el recorte de vías de test. */
class HojaNotasRealTest {
    private val contexto get() = FixturesVias.contexto
    private val ma2141 by lazy { TrazasDePrueba.cargarGpx("ma-2141-sa-calobra.gpx") }
    private val ma10 by lazy { TrazasDePrueba.cargarGpx("ma-10-soller-lluc.gpx") }
    private val hoja2141 by lazy { GeneradorHoja(contexto = contexto).generar(ma2141) }
    private val hoja10 by lazy { GeneradorHoja(contexto = contexto).generar(ma10) }

    private fun texto(hoja: HojaNotas): String =
        hoja.elementos.joinToString("") {
            val unida = if (it is ElementoHoja.DeNota && it.nota.unidaConSiguiente) " y" else ""
            "${it.posicionM.roundToInt().toString().padStart(5)} m  ${it.frase}$unida\n"
        }

    private fun comparar(
        hoja: HojaNotas,
        nombre: String,
    ) {
        val golden = File("src/test/resources/notas/$nombre")
        val actual = texto(hoja)
        if (!golden.exists()) {
            golden.parentFile.mkdirs()
            golden.writeText(actual)
            fail(
                "No existía ${golden.path}: generado (${hoja.elementos.size} elementos). Revísalo y reejecuta.",
            )
        }
        assertEquals(golden.readText().replace("\r\n", "\n"), actual)
    }

    @Test
    fun `la hoja de la Ma-2141 coincide con el golden file`() = comparar(hoja2141, "ma-2141-hoja.txt")

    @Test
    fun `la hoja de la Ma-10 coincide con el golden file`() = comparar(hoja10, "ma-10-hoja.txt")

    @Test
    fun `las notas de la hoja son las del generador de notas con la precaucion añadida`() {
        val curvas = DetectorCurvas().detectar(ma2141)
        val notas = GeneradorNotas().generar(curvas)
        assertEquals(notas, hoja2141.notas.map { it.copy(cuidado = false) })
    }

    @Test
    fun `la corbata de la Ma-2141 es horquilla con cuidado y hay rasantes y varios tipos de aviso`() {
        val horquilla = hoja2141.notas.filter { it.grado == null && it.posicionM in 2900.0..3500.0 }
        val tipos = hoja2141.avisos.groupingBy { it.tipo }.eachCount()
        println(
            "Ma-2141: horquillas en ${hoja2141.notas.filter { it.grado == null }.map { it.posicionM.roundToInt() }}",
        )
        println(
            "Ma-2141: ${hoja2141.notas.size} notas, ${hoja2141.notas.count { it.cuidado }} con cuidado, avisos $tipos",
        )
        println("Ma-2141: primeras frases " + hoja2141.elementos.take(15).map { it.frase })
        assertTrue("sin horquilla hacia los 3,2 km", horquilla.isNotEmpty())
        assertTrue(horquilla.all { it.cuidado })
        val rasantes = tipos.filterKeys { it in RASANTES }.values.sum()
        assertTrue("sin rasantes", rasantes > 0)
        assertTrue(
            "avisos de ${tipos.keys}",
            tipos.keys.containsAll(listOf(TipoAviso.PUENTE, TipoAviso.TUNEL, TipoAviso.STOP)),
        )
    }

    @Test
    fun `la Ma-10 tiene avisos de nodos y de tramos`() {
        val tipos = hoja10.avisos.groupingBy { it.tipo }.eachCount()
        println("Ma-10: ${hoja10.notas.size} notas, ${hoja10.notas.count { it.cuidado }} con cuidado, avisos $tipos")
        assertTrue(
            "avisos de ${tipos.keys}",
            tipos.keys.containsAll(listOf(TipoAviso.CEDA, TipoAviso.PASO_A_NIVEL, TipoAviso.BACHE)),
        )
    }

    @Test
    fun `los elementos estan ordenados y los avisos no se repiten a menos de 30 m`() {
        for (hoja in listOf(hoja2141, hoja10)) {
            assertTrue(hoja.elementos.zipWithNext().all { (a, b) -> a.posicionM <= b.posicionM })
            val avisos = hoja.avisos
            assertTrue(avisos.zipWithNext().all { (a, b) -> a.posicionM <= b.posicionM })
            for (tipo in TipoAviso.entries) {
                val posiciones = avisos.filter { it.tipo == tipo }.map { it.posicionM }
                assertTrue("$tipo repetido", posiciones.zipWithNext().all { (a, b) -> abs(b - a) >= 30.0 })
            }
        }
    }

    @Test
    fun `los avisos caen dentro de la ruta`() {
        val largo = ma2141.longitudMetros()
        assertTrue(hoja2141.avisos.all { it.posicionM in 0.0..largo })
    }

    @Test
    fun `dos ejecuciones dan la misma hoja`() {
        assertEquals(hoja10, GeneradorHoja(contexto = contexto).generar(ma10))
        assertEquals(hoja2141, GeneradorHoja(contexto = contexto).generar(ma2141))
    }

    @Test
    fun `15 km de la Ma-10 con contexto se generan en menos de un segundo tras calentar`() {
        val ruta = primerosMetros(ma10, 15_000.0)
        val generador = GeneradorHoja(contexto = contexto)
        val calentamiento = generador.generar(ruta)
        val inicio = System.nanoTime()
        val hoja = generador.generar(ruta)
        val ms = (System.nanoTime() - inicio) / 1_000_000
        println("Ma-10 15 km: ${hoja.elementos.size} elementos en $ms ms")
        assertEquals(calentamiento, hoja)
        assertTrue("$ms ms", ms < 1000)
    }

    /** Puntos de [ruta] hasta que la distancia acumulada alcanza [metros]. */
    private fun primerosMetros(
        ruta: List<PuntoGeo>,
        metros: Double,
    ): List<PuntoGeo> {
        var acumulado = 0.0
        val salida = arrayListOf(ruta.first())
        for (i in 1 until ruta.size) {
            if (acumulado >= metros) break
            acumulado += ruta[i - 1].distanciaA(ruta[i])
            salida.add(ruta[i])
        }
        return salida
    }

    private companion object {
        val RASANTES = setOf(TipoAviso.RASANTE, TipoAviso.CAMBIO_RASANTE, TipoAviso.SALTO)
    }
}
