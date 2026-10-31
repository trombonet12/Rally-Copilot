package app.trombito.rallycopilot.motor.notas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/** Detector + generador sobre la Ma-2141 (Sa Calobra) comparados con un golden file. */
class GeneradorNotasRealTest {
    private val notas by lazy {
        val curvas = DetectorCurvas().detectar(TrazasDePrueba.cargarGpx("ma-2141-sa-calobra.gpx"))
        GeneradorNotas().generar(curvas)
    }

    @Test
    fun `la corbata de los 3,2 km sale como horquilla`() {
        val horquillas = notas.filter { it.grado == null && it.posicionM in 2900.0..3500.0 }
        val posiciones = notas.filter { it.grado == null }.map { it.posicionM.roundToInt() }
        println("Ma-2141: ${notas.size} notas; horquillas en $posiciones")
        assertTrue("sin horquilla hacia los 3,2 km", horquillas.isNotEmpty())
    }

    @Test
    fun `las notas de la Ma-2141 estan ordenadas y no se solapan`() {
        println("Reparto por grado: " + notas.groupingBy { it.grado }.eachCount().toSortedMap(compareBy { it ?: 0 }))
        assertTrue(notas.size in 40..200)
        assertTrue(notas.zipWithNext().all { (a, b) -> a.finM <= b.posicionM })
        assertTrue(notas.dropLast(1).all { it.unidaConSiguiente || it.distanciaSiguienteM != null })
    }

    @Test
    fun `la Ma-2141 tiene al menos 5 notas encadenadas y notas de grado 6`() {
        val encadenadas = notas.count { it.unidaConSiguiente }
        val seis = notas.count { it.grado == 6 }
        val mods = notas.flatMap { it.modificadores }.groupingBy { it }.eachCount()
        println("Ma-2141: $encadenadas encadenadas, $seis de grado 6, modificadores $mods")
        assertTrue("$encadenadas encadenadas", encadenadas >= 5)
        assertTrue("sin notas de grado 6", seis > 0)
    }

    @Test
    fun `las notas de la Ma-2141 coinciden con el golden file`() {
        val actual =
            notas.joinToString("") {
                val unida = if (it.unidaConSiguiente) " y" else ""
                "${it.posicionM.roundToInt().toString().padStart(5)} m  ${FrasesNota.frase(it)}$unida\n"
            }
        val golden = File("src/test/resources/notas/ma-2141.txt")
        if (!golden.exists()) {
            golden.parentFile.mkdirs()
            golden.writeText(actual)
            fail("No existía ${golden.path}: generado con ${notas.size} notas. Revísalo y vuelve a ejecutar.")
        }
        assertEquals(golden.readText().replace("\r\n", "\n"), actual)
    }
}
