package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecEdicionesTest {
    private val nota =
        Nota(
            0.0,
            35.0,
            Direccion.IZQUIERDA,
            3,
            setOf(Modificador.CIERRA, Modificador.LARGA),
            distanciaSiguienteM = 150,
            cuidado = true,
        )

    @Test
    fun `una acción con nota sobrevive a codificarla y decodificarla`() {
        for (accion in listOf(AccionEdicion.Sustituir(nota), AccionEdicion.Anadir(nota))) {
            val vuelta = CodecEdiciones.accion(CodecEdiciones.tipo(accion), CodecEdiciones.nota(accion))
            assertEquals(accion, vuelta)
        }
    }

    @Test
    fun `una horquilla sin distancia ni modificadores se conserva`() {
        val horquilla = Nota(0.0, 10.0, Direccion.DERECHA, null)
        val accion = AccionEdicion.Sustituir(horquilla)
        assertEquals(accion, CodecEdiciones.accion(CodecEdiciones.tipo(accion), CodecEdiciones.nota(accion)))
    }

    @Test
    fun `quitar y cuidado no llevan nota`() {
        for (accion in listOf(AccionEdicion.Quitar, AccionEdicion.Cuidado)) {
            assertNull(CodecEdiciones.nota(accion))
            assertEquals(accion, CodecEdiciones.accion(CodecEdiciones.tipo(accion), null))
        }
    }

    @Test
    fun `un texto dañado o un tipo desconocido da null`() {
        assertNull(CodecEdiciones.accion("SUSTITUIR", "ARRIBA;3;;;0;0"))
        assertNull(CodecEdiciones.accion("SUSTITUIR", "DERECHA;9;;;0;0"))
        assertNull(CodecEdiciones.accion("SUSTITUIR", "DERECHA;3;XX;;0;0"))
        assertNull(CodecEdiciones.accion("SUSTITUIR", "DERECHA;3"))
        assertNull(CodecEdiciones.accion("SUSTITUIR", null))
        assertNull(CodecEdiciones.accion("OTRA", null))
    }
}
