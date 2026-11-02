package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fusión de las notas editadas con las automáticas de un tramo. */
class FusionNotasTest {
    private val tramo = "tramo-1"

    @Suppress("LongParameterList")
    private fun nota(
        posicionM: Double,
        grado: Int? = 4,
        direccion: Direccion = Direccion.DERECHA,
        modificadores: Set<Modificador> = emptySet(),
        distanciaM: Int? = null,
        unida: Boolean = false,
        cuidado: Boolean = false,
        largoM: Double = 40.0,
    ) = Nota(posicionM, posicionM + largoM, direccion, grado, modificadores, distanciaM, unida, cuidado)

    private fun hoja(vararg elementos: ElementoHoja) = HojaNotas(elementos.toList())

    private fun deNota(
        nota: Nota,
        unidos: List<Aviso> = emptyList(),
    ) = ElementoHoja.DeNota(nota, unidos)

    /** Hoja automática de tres notas en 100, 400 y 800 m; huecos 260 → 250 y 360 → 300. */
    private fun automatica(desplazamientoM: Double = 0.0) =
        hoja(
            deNota(nota(100.0 + desplazamientoM, grado = 4, distanciaM = 250)),
            deNota(nota(400.0 + desplazamientoM, grado = 3, distanciaM = 300)),
            deNota(nota(800.0 + desplazamientoM, grado = 5)),
        )

    private fun edicion(
        posicionM: Double,
        accion: AccionEdicion,
        t: Long = 1,
    ) = NotasEditadas(tramo, posicionM, accion, t)

    private fun aplicar(
        base: HojaNotas,
        vararg ediciones: NotasEditadas,
        enTramo: (Double) -> Double = { it },
    ) = FusionNotas.aplicar(base, enTramo, ediciones.toList())

    private fun HojaNotas.posiciones() = notas.map { it.posicionM }

    // --- Sin ediciones ---------------------------------------------------------------------------------------------

    @Test
    fun `sin ediciones la hoja no cambia`() {
        val base = automatica()
        assertEquals(base, FusionNotas.aplicar(base, { it }, emptyList()))
    }

    // --- Sustituir -------------------------------------------------------------------------------------------------

    @Test
    fun `sustituir cambia la nota automatica mas cercana y conserva su posicion`() {
        val nueva = nota(410.0, grado = 2, direccion = Direccion.IZQUIERDA, modificadores = setOf(Modificador.LARGA))
        val resultado = aplicar(automatica(), edicion(410.0, AccionEdicion.Sustituir(nueva)))
        assertEquals(listOf(100.0, 400.0, 800.0), resultado.posiciones())
        val editada = resultado.notas[1]
        assertEquals(Direccion.IZQUIERDA, editada.direccion)
        assertEquals(2, editada.grado)
        assertEquals(setOf(Modificador.LARGA), editada.modificadores)
        assertEquals(440.0, editada.finM, 0.0)
        assertEquals(300, editada.distanciaSiguienteM)
        assertEquals(4, resultado.notas[0].grado)
        assertEquals(5, resultado.notas[2].grado)
    }

    @Test
    fun `sustituir conserva los avisos unidos de la nota sustituida`() {
        val puente = Aviso(385.0, TipoAviso.PUENTE)
        val base =
            hoja(
                deNota(nota(100.0, distanciaM = 250)),
                deNota(nota(400.0, grado = 3), unidos = listOf(puente)),
            )
        val resultado = aplicar(base, edicion(400.0, AccionEdicion.Sustituir(nota(400.0, grado = 1))))
        assertEquals(listOf(puente), (resultado.elementos[1] as ElementoHoja.DeNota).avisosUnidos)
        assertEquals(1, resultado.notas[1].grado)
    }

    @Test
    fun `sustituir lejos de toda nota anade la nota`() {
        val resultado = aplicar(automatica(), edicion(600.0, AccionEdicion.Sustituir(nota(600.0, grado = 2))))
        assertEquals(listOf(100.0, 400.0, 600.0, 800.0), resultado.posiciones())
        assertEquals(2, resultado.notas[2].grado)
    }

    @Test
    fun `el limite de 30 m cuenta desde el borde de la curva`() {
        // La curva de 400 acaba en 440: 469 queda a 29 m (sustituye) y 471 a 31 m (anade).
        val sustituye = aplicar(automatica(), edicion(469.0, AccionEdicion.Sustituir(nota(469.0, grado = 1))))
        assertEquals(3, sustituye.notas.size)
        assertEquals(1, sustituye.notas[1].grado)
        val anade = aplicar(automatica(), edicion(471.0, AccionEdicion.Sustituir(nota(471.0, grado = 1))))
        assertEquals(4, anade.notas.size)
    }

    // --- Quitar ----------------------------------------------------------------------------------------------------

    @Test
    fun `quitar elimina la nota mas cercana y recalcula la distancia de la anterior`() {
        val resultado = aplicar(automatica(), edicion(405.0, AccionEdicion.Quitar))
        assertEquals(listOf(100.0, 800.0), resultado.posiciones())
        // Hueco 800 - 140 = 660 -> 600.
        assertEquals(600, resultado.notas[0].distanciaSiguienteM)
        assertFalse(resultado.notas[0].unidaConSiguiente)
    }

    @Test
    fun `quitar la ultima deja a la anterior sin distancia`() {
        val resultado = aplicar(automatica(), edicion(800.0, AccionEdicion.Quitar))
        assertEquals(listOf(100.0, 400.0), resultado.posiciones())
        assertNull(resultado.notas[1].distanciaSiguienteM)
        assertFalse(resultado.notas[1].unidaConSiguiente)
    }

    @Test
    fun `quitar una nota con avisos unidos deja los avisos sueltos`() {
        val puente = Aviso(385.0, TipoAviso.PUENTE)
        val base = hoja(deNota(nota(100.0, distanciaM = 250)), deNota(nota(400.0), unidos = listOf(puente)))
        val resultado = aplicar(base, edicion(400.0, AccionEdicion.Quitar))
        assertEquals(1, resultado.notas.size)
        assertEquals(listOf(puente), resultado.avisos)
        assertTrue(resultado.elementos.last() is ElementoHoja.DeAviso)
    }

    @Test
    fun `quitar donde no hay nota no cambia nada`() {
        assertEquals(automatica(), aplicar(automatica(), edicion(600.0, AccionEdicion.Quitar)))
    }

    // --- Añadir ----------------------------------------------------------------------------------------------------

    @Test
    fun `anadir inserta en orden y recalcula distancias`() {
        val nueva = nota(0.0, grado = 2, direccion = Direccion.IZQUIERDA)
        val resultado = aplicar(automatica(), edicion(600.0, AccionEdicion.Anadir(nueva)))
        assertEquals(listOf(100.0, 400.0, 600.0, 800.0), resultado.posiciones())
        // 400 acaba en 440: hueco 160 -> 150. La nueva acaba en 640: hueco 160 -> 150.
        assertEquals(150, resultado.notas[1].distanciaSiguienteM)
        assertEquals(150, resultado.notas[2].distanciaSiguienteM)
        assertEquals(Direccion.IZQUIERDA, resultado.notas[2].direccion)
        assertEquals(2, resultado.notas[2].grado)
    }

    @Test
    fun `anadir pegada a la anterior las une`() {
        val resultado = aplicar(automatica(), edicion(450.0, AccionEdicion.Anadir(nota(0.0, grado = 5))))
        assertEquals(listOf(100.0, 400.0, 450.0, 800.0), resultado.posiciones())
        assertTrue(resultado.notas[1].unidaConSiguiente)
        assertNull(resultado.notas[1].distanciaSiguienteM)
    }

    @Test
    fun `anadir antes de la primera y despues de la ultima`() {
        val resultado =
            aplicar(
                automatica(),
                edicion(10.0, AccionEdicion.Anadir(nota(0.0, grado = 1)), t = 1),
                edicion(1200.0, AccionEdicion.Anadir(nota(0.0, grado = 6)), t = 2),
            )
        assertEquals(listOf(10.0, 100.0, 400.0, 800.0, 1200.0), resultado.posiciones())
        // 10 + 40 = 50; hueco hasta 100 = 50.
        assertEquals(50, resultado.notas[0].distanciaSiguienteM)
        // 800 + 40 = 840; hueco 360 -> 300.
        assertEquals(300, resultado.notas[3].distanciaSiguienteM)
        assertNull(resultado.notas[4].distanciaSiguienteM)
    }

    @Test
    fun `la distancia dictada en la nota anadida se respeta`() {
        val nueva = nota(0.0, grado = 4, distanciaM = 50)
        val resultado = aplicar(automatica(), edicion(600.0, AccionEdicion.Anadir(nueva)))
        assertEquals(50, resultado.notas[2].distanciaSiguienteM)
        assertFalse(resultado.notas[2].unidaConSiguiente)
    }

    @Test
    fun `anadir sobre hoja vacia`() {
        val resultado = aplicar(HojaNotas(emptyList()), edicion(50.0, AccionEdicion.Anadir(nota(0.0, grado = 3))))
        assertEquals(listOf(50.0), resultado.posiciones())
        assertNull(resultado.notas[0].distanciaSiguienteM)
    }

    // --- Cuidado ---------------------------------------------------------------------------------------------------

    @Test
    fun `cuidado marca la nota cercana y es idempotente`() {
        val una = aplicar(automatica(), edicion(402.0, AccionEdicion.Cuidado))
        assertTrue(una.notas[1].cuidado)
        assertFalse(una.notas[0].cuidado)
        val repetida = listOf(edicion(402.0, AccionEdicion.Cuidado, 1), edicion(402.0, AccionEdicion.Cuidado, 2))
        val dos = aplicar(automatica(), *repetida.toTypedArray())
        assertEquals(una, dos)
    }

    @Test
    fun `cuidado sin nota cerca se ignora`() {
        assertEquals(automatica(), aplicar(automatica(), edicion(600.0, AccionEdicion.Cuidado)))
    }

    // --- Varias ediciones ------------------------------------------------------------------------------------------

    @Test
    fun `las ediciones se aplican por marca de tiempo aunque lleguen desordenadas`() {
        val quitar = edicion(400.0, AccionEdicion.Quitar, t = 1)
        val poner = edicion(402.0, AccionEdicion.Anadir(nota(0.0, grado = 1)), t = 2)
        val resultado = aplicar(automatica(), poner, quitar)
        assertEquals(listOf(100.0, 402.0, 800.0), resultado.posiciones())
        assertEquals(1, resultado.notas[1].grado)
    }

    @Test
    fun `una edicion posterior sobre una nota anadida la quita`() {
        val poner = edicion(402.0, AccionEdicion.Anadir(nota(0.0, grado = 1)), t = 1)
        val quitar = edicion(402.0, AccionEdicion.Quitar, t = 2)
        val resultado = aplicar(automatica(), poner, quitar)
        assertEquals(listOf(100.0, 400.0, 800.0), resultado.posiciones())
        assertEquals(3, resultado.notas[1].grado)
    }

    @Test
    fun `sustituir dos veces la misma nota prevalece la ultima`() {
        val resultado =
            aplicar(
                automatica(),
                edicion(400.0, AccionEdicion.Sustituir(nota(400.0, grado = 2)), t = 1),
                edicion(401.0, AccionEdicion.Sustituir(nota(401.0, grado = 1)), t = 2),
            )
        assertEquals(3, resultado.notas.size)
        assertEquals(1, resultado.notas[1].grado)
    }

    @Test
    fun `varias ediciones en distintas notas`() {
        val resultado =
            aplicar(
                automatica(),
                edicion(100.0, AccionEdicion.Cuidado, t = 1),
                edicion(400.0, AccionEdicion.Quitar, t = 2),
                edicion(800.0, AccionEdicion.Sustituir(nota(800.0, grado = 1)), t = 3),
                edicion(600.0, AccionEdicion.Anadir(nota(0.0, grado = 2)), t = 4),
            )
        assertEquals(listOf(100.0, 600.0, 800.0), resultado.posiciones())
        assertTrue(resultado.notas[0].cuidado)
        assertEquals(2, resultado.notas[1].grado)
        assertEquals(1, resultado.notas[2].grado)
        // 100 acaba en 140: hueco 460 -> 400. 600 acaba en 640: hueco 160 -> 150.
        assertEquals(400, resultado.notas[0].distanciaSiguienteM)
        assertEquals(150, resultado.notas[1].distanciaSiguienteM)
    }

    @Test
    fun `los avisos sueltos se mantienen en su sitio`() {
        val aviso = ElementoHoja.DeAviso(Aviso(250.0, TipoAviso.TUNEL))
        val base = hoja(deNota(nota(100.0, distanciaM = 250)), aviso, deNota(nota(400.0)))
        val resultado = aplicar(base, edicion(400.0, AccionEdicion.Sustituir(nota(400.0, grado = 1))))
        assertEquals(aviso, resultado.elementos[1])
        assertEquals(1, resultado.notas[1].grado)
    }

    @Test
    fun `ediciones de otro tramo se rechazan`() {
        val ajena = NotasEditadas("otro", 400.0, AccionEdicion.Quitar, 1)
        val propia = edicion(100.0, AccionEdicion.Quitar)
        val error = runCatching { aplicar(automatica(), propia, ajena) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    // --- Sobrevive a regenerar -------------------------------------------------------------------------------------

    private val ediciones =
        listOf(
            edicion(405.0, AccionEdicion.Sustituir(nota(405.0, grado = 1, direccion = Direccion.IZQUIERDA)), t = 1),
            edicion(100.0, AccionEdicion.Quitar, t = 2),
            edicion(600.0, AccionEdicion.Anadir(nota(0.0, grado = 2)), t = 3),
        )

    @Test
    fun `una nota editada sobrevive a regenerar las automaticas con la geometria desplazada`() {
        val original = FusionNotas.aplicar(automatica(), { it }, ediciones)
        listOf(-12.0, -5.0, 3.0, 9.0, 20.0).forEach { desplazamiento ->
            val regenerada = FusionNotas.aplicar(automatica(desplazamiento), { it }, ediciones)
            assertEquals("desplazamiento $desplazamiento", 3, regenerada.notas.size)
            val sustituida = regenerada.notas[0]
            assertEquals(Direccion.IZQUIERDA, sustituida.direccion)
            assertEquals(1, sustituida.grado)
            assertEquals(400.0 + desplazamiento, sustituida.posicionM, 0.0)
            assertEquals(600.0, regenerada.notas[1].posicionM, 0.0)
            assertEquals(5, regenerada.notas[2].grado)
            assertEquals(original.notas.map { it.grado }, regenerada.notas.map { it.grado })
        }
    }

    @Test
    fun `regenerar con una nota automatica nueva no revive la quitada`() {
        val regenerada =
            hoja(
                deNota(nota(103.0, distanciaM = 250)),
                deNota(nota(396.0, grado = 3)),
            )
        val resultado = FusionNotas.aplicar(regenerada, { it }, ediciones)
        assertEquals(listOf(396.0, 600.0), resultado.posiciones())
    }

    @Test
    fun `la posicion en el tramo puede diferir de la de la hoja`() {
        // La hoja se mide desde otro origen (+1000 m): la funcion la devuelve a coordenadas del tramo.
        val desplazada = automatica(1000.0)
        val resultado =
            FusionNotas.aplicar(
                desplazada,
                { it - 1000.0 },
                listOf(edicion(400.0, AccionEdicion.Sustituir(nota(400.0, grado = 1)))),
            )
        assertEquals(listOf(1100.0, 1400.0, 1800.0), resultado.posiciones())
        assertEquals(1, resultado.notas[1].grado)
    }

    // --- Dictado ---------------------------------------------------------------------------------------------------

    @Test
    fun `dictar derecha tres larga crea esa nota en la posicion actual`() {
        val orden = InterpreteOrdenes.interpretar("derecha tres larga") as Orden.Nota
        val posicionActualM = 520.0
        val resultado =
            aplicar(automatica(), edicion(posicionActualM, AccionEdicion.Anadir(orden.aNota(posicionActualM))))
        val creada = resultado.notas.single { it.posicionM == posicionActualM }
        assertEquals(Direccion.DERECHA, creada.direccion)
        assertEquals(3, creada.grado)
        assertEquals(setOf(Modificador.LARGA), creada.modificadores)
        assertEquals(listOf(100.0, 400.0, 520.0, 800.0), resultado.posiciones())
    }
}
