package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Diferencias entre la hoja de notas de antes y la de después de una pasada. */
class DiferenciasNotasTest {
    private fun nota(
        posicionM: Double,
        grado: Int = 4,
        direccion: Direccion = Direccion.DERECHA,
    ) = ElementoHoja.DeNota(Nota(posicionM, posicionM + 40.0, direccion, grado))

    private fun aviso(
        posicionM: Double,
        tipo: TipoAviso = TipoAviso.PUENTE,
    ) = ElementoHoja.DeAviso(Aviso(posicionM, tipo))

    private fun hoja(vararg elementos: ElementoHoja) = HojaNotas(elementos.toList())

    @Test
    fun `hojas iguales no tienen cambios`() {
        val h = hoja(nota(100.0), nota(400.0, grado = 2), aviso(600.0))
        assertTrue(DiferenciasNotas.entre(h, h).isEmpty())
    }

    @Test
    fun `una nota con otro grado es un cambio con la frase de antes y la de despues`() {
        val antes = hoja(nota(100.0, grado = 4), nota(400.0))
        val despues = hoja(nota(100.0, grado = 3), nota(400.0))

        val cambios = DiferenciasNotas.entre(antes, despues)

        assertEquals(1, cambios.size)
        val c = cambios.single() as CambioNota.Cambiada
        assertEquals(antes.elementos[0].frase, c.antes)
        assertEquals(despues.elementos[0].frase, c.despues)
        assertEquals(100.0, c.posicionM, 0.0)
    }

    @Test
    fun `una nota nueva es anadida y una que falta es quitada`() {
        val antes = hoja(nota(100.0), nota(400.0, direccion = Direccion.IZQUIERDA))
        val despues = hoja(nota(100.0), nota(250.0, grado = 2))

        val cambios = DiferenciasNotas.entre(antes, despues)

        assertEquals(
            listOf(
                CambioNota.Anadida(250.0, despues.elementos[1].frase),
                CambioNota.Quitada(400.0, antes.elementos[1].frase),
            ),
            cambios,
        )
    }

    @Test
    fun `una nota desplazada dentro de la tolerancia se empareja y fuera es quitar y anadir`() {
        val antes = hoja(nota(100.0, grado = 4))
        val cerca = DiferenciasNotas.entre(antes, hoja(nota(120.0, grado = 3)))
        assertTrue(cerca.single() is CambioNota.Cambiada)

        val lejos = DiferenciasNotas.entre(antes, hoja(nota(130.0, grado = 3)))
        assertEquals(2, lejos.size)
        assertTrue(lejos.any { it is CambioNota.Quitada })
        assertTrue(lejos.any { it is CambioNota.Anadida })
    }

    @Test
    fun `cada elemento se empareja una sola vez con el mas cercano`() {
        val antes = hoja(nota(100.0, grado = 4), nota(110.0, grado = 5))
        val despues = hoja(nota(112.0, grado = 5))

        val cambios = DiferenciasNotas.entre(antes, despues)

        // La de 110 es la pareja de 112 (sin cambios); la de 100 se queda sin pareja.
        assertEquals(listOf(CambioNota.Quitada(100.0, antes.elementos[0].frase)), cambios)
    }

    @Test
    fun `una nota y un aviso cercanos no se emparejan entre si`() {
        val cambios = DiferenciasNotas.entre(hoja(nota(100.0)), hoja(aviso(105.0, TipoAviso.TUNEL)))

        assertEquals(2, cambios.size)
        assertTrue(cambios[0] is CambioNota.Quitada)
        assertTrue(cambios[1] is CambioNota.Anadida)
    }

    @Test
    fun `los cambios salen por posicion`() {
        val antes = hoja(nota(800.0), nota(100.0))
        val despues = hoja(nota(500.0), nota(100.0, grado = 2))

        val posiciones = DiferenciasNotas.entre(antes.ordenada(), despues.ordenada()).map { it.posicionM }

        assertEquals(posiciones.sorted(), posiciones)
    }

    private fun HojaNotas.ordenada() = HojaNotas(elementos.sortedBy { it.posicionM })
}
