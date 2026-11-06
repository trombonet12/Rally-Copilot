package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.Modificador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Gramática de las órdenes de voz: lo que devuelve un reconocedor (ASR) y la orden que se entiende. */
class InterpreteOrdenesTest {
    private fun nota(
        direccion: Direccion,
        grado: Int?,
        modificadores: Set<Modificador> = emptySet(),
        distanciaM: Int? = null,
        cuidado: Boolean = false,
    ) = Orden.Nota(direccion, grado, modificadores, distanciaM, cuidado)

    private val izq = Direccion.IZQUIERDA
    private val der = Direccion.DERECHA

    private fun comprobar(
        texto: String,
        esperada: Orden?,
    ) = assertEquals("«$texto»", esperada, InterpreteOrdenes.interpretar(texto))

    // --- Notas ----------------------------------------------------------------------------------------------------

    @Test
    fun `notas con grado en palabra y modificadores`() {
        val casos =
            mapOf(
                "derecha tres larga" to nota(der, 3, setOf(Modificador.LARGA)),
                "izquierda uno cierra" to nota(izq, 1, setOf(Modificador.CIERRA)),
                "izquierda cuatro abre" to nota(izq, 4, setOf(Modificador.ABRE)),
                "izquierda cinco muy larga" to nota(izq, 5, setOf(Modificador.MUY_LARGA)),
                "derecha dos muy larga cierra" to nota(der, 2, setOf(Modificador.MUY_LARGA, Modificador.CIERRA)),
                "izquierda seis" to nota(izq, 6),
                "derecha dos" to nota(der, 2),
            )
        casos.forEach { (texto, orden) -> comprobar(texto, orden) }
    }

    @Test
    fun `notas con cifras y mayusculas`() {
        comprobar("Derecha 3 larga", nota(der, 3, setOf(Modificador.LARGA)))
        comprobar("IZQUIERDA 4 abre", nota(izq, 4, setOf(Modificador.ABRE)))
        comprobar("izquierda 1", nota(izq, 1))
    }

    @Test
    fun `tildes y signos de puntuacion no importan`() {
        comprobar("Izquierda, cinco, muy larga.", nota(izq, 5, setOf(Modificador.MUY_LARGA)))
        comprobar("derecha cuatro, cincuenta", nota(der, 4, distanciaM = 50))
        comprobar("izquierdá tres", nota(izq, 3))
    }

    @Test
    fun `horquillas`() {
        comprobar("horquilla derecha", nota(der, null))
        comprobar("izquierda horquilla", nota(izq, null))
        comprobar("horquilla izquierda muy larga", nota(izq, null, setOf(Modificador.MUY_LARGA)))
        comprobar("cuidado horquilla derecha", nota(der, null, cuidado = true))
        comprobar("horquilla derecha cincuenta", nota(der, null, distanciaM = 50))
    }

    @Test
    fun `escuadra es grado tres con el modificador escuadra`() {
        val esc = setOf(Modificador.ESCUADRA)
        comprobar("derecha escuadra", nota(der, 3, esc))
        comprobar("Izquierda, escuadra.", nota(izq, 3, esc))
        comprobar("escuadra izquierda", nota(izq, 3, esc))
        comprobar("cuidado derecha escuadra", nota(der, 3, esc, cuidado = true))
        comprobar("derecha escuadra cincuenta", nota(der, 3, esc, distanciaM = 50))
    }

    @Test
    fun `la escuadra no admite modificadores ni se entiende sin direccion`() {
        comprobar("derecha escuadra larga", null)
        comprobar("escuadra", null)
    }

    @Test
    fun `la horquilla nunca lleva cierra ni abre`() {
        comprobar("horquilla derecha cierra", nota(der, null))
        comprobar("horquilla izquierda larga abre", nota(izq, null, setOf(Modificador.LARGA)))
    }

    @Test
    fun `cuidado en la misma frase de la nota`() {
        comprobar("cuidado izquierda dos", nota(izq, 2, cuidado = true))
        comprobar("derecha tres, cuidado", nota(der, 3, cuidado = true))
        comprobar("cuidado derecha uno cierra", nota(der, 1, setOf(Modificador.CIERRA), cuidado = true))
    }

    @Test
    fun `distancia a la siguiente en palabras y cifras se ajusta a la lista`() {
        comprobar("derecha cuatro cincuenta", nota(der, 4, distanciaM = 50))
        comprobar("derecha tres larga ciento veinte", nota(der, 3, setOf(Modificador.LARGA), 120))
        comprobar("izquierda 2 150", nota(izq, 2, distanciaM = 150))
        comprobar("derecha cuatro doscientos cincuenta", nota(der, 4, distanciaM = 250))
        comprobar("derecha cuatro cien", nota(der, 4, distanciaM = 100))
        comprobar("derecha cuatro cincuenta metros", nota(der, 4, distanciaM = 50))
        comprobar("derecha cuatro treinta y cinco", nota(der, 4, distanciaM = 30))
        comprobar("derecha cuatro seiscientos", nota(der, 4, distanciaM = 600))
        comprobar("derecha tres 9", nota(der, 3))
    }

    @Test
    fun `notas simplificadas dan un grado representativo`() {
        comprobar("derecha media", nota(der, 3))
        comprobar("izquierda muy cerrada", nota(izq, 1))
        comprobar("izquierda cerrada", nota(izq, 2))
        comprobar("derecha suave", nota(der, 5))
        comprobar("derecha media abre cincuenta", nota(der, 3, setOf(Modificador.ABRE), 50))
    }

    @Test
    fun `relleno y ruido tipico del reconocedor`() {
        val casos =
            mapOf(
                "la derecha tres" to nota(der, 3),
                "a la izquierda dos" to nota(izq, 2),
                "eh derecha tres larga" to nota(der, 3, setOf(Modificador.LARGA)),
                "copiloto derecha tres" to nota(der, 3),
                "oye copiloto pon izquierda dos" to nota(izq, 2),
                "izquierda tres larga por favor" to nota(izq, 3, setOf(Modificador.LARGA)),
                "vale la izquierda cuatro" to nota(izq, 4),
                "es una derecha tres" to nota(der, 3),
                "derecha tres la nota" to nota(der, 3),
            )
        casos.forEach { (texto, orden) -> comprobar(texto, orden) }
    }

    // --- Órdenes sueltas ------------------------------------------------------------------------------------------

    @Test
    fun `quitar la nota`() {
        listOf("quita", "borra", "quítala", "borra esa nota", "elimina la nota", "Quita esa").forEach {
            comprobar(it, Orden.Quitar)
        }
    }

    @Test
    fun `cuidado suelto marca la nota cercana`() {
        listOf("cuidado", "marca cuidado", "con cuidado", "pon cuidado", "ojo").forEach {
            comprobar(it, Orden.Cuidado)
        }
    }

    @Test
    fun `repite`() {
        listOf("repite", "repítelo", "otra vez", "repite la última nota").forEach { comprobar(it, Orden.Repite) }
    }

    @Test
    fun `mas pronto y mas tarde`() {
        listOf("más pronto", "mas pronto", "avisa antes", "adelanta").forEach { comprobar(it, Orden.MasPronto) }
        listOf("más tarde", "avisa más tarde", "retrasa").forEach { comprobar(it, Orden.MasTarde) }
    }

    @Test
    fun `silencio y habla`() {
        listOf("silencio", "cállate", "no hables", "calla").forEach { comprobar(it, Orden.Silencio) }
        listOf("habla", "vuelve a hablar", "ya puedes hablar").forEach { comprobar(it, Orden.Habla) }
    }

    @Test
    fun `marca aqui`() {
        listOf("marca aquí", "marca", "marca este punto", "marcar aqui").forEach { comprobar(it, Orden.MarcaAqui) }
    }

    // --- Lo que no se entiende ------------------------------------------------------------------------------------

    @Test
    fun `vacio o sin sentido devuelve null`() {
        listOf("", "   ", "hola qué tal", "quiero un café", "tal cual").forEach { comprobar(it, null) }
    }

    @Test
    fun `nota incompleta devuelve null`() {
        comprobar("derecha", null)
        comprobar("tres larga", null)
        comprobar("larga", null)
        comprobar("derecha siete", null)
        comprobar("derecha cincuenta", null)
    }

    @Test
    fun `ordenes contradictorias devuelven null`() {
        comprobar("izquierda derecha tres", null)
        comprobar("izquierda cierra abre tres", null)
        comprobar("quita derecha tres", null)
        comprobar("repite silencio", null)
        comprobar("más pronto más tarde", null)
        comprobar("silencio habla", null)
    }

    @Test
    fun `demasiado ruido desconocido devuelve null`() {
        comprobar("el coche de la derecha tiene cuatro ruedas grandes", null)
    }

    // --- Conversión a nota ----------------------------------------------------------------------------------------

    @Test
    fun `una orden de nota se convierte en nota en la posicion dada`() {
        val orden = InterpreteOrdenes.interpretar("cuidado derecha tres larga ciento veinte") as Orden.Nota
        val nota = orden.aNota(520.0)
        assertEquals(520.0, nota.posicionM, 0.0)
        assertEquals(520.0, nota.finM, 0.0)
        assertEquals(der, nota.direccion)
        assertEquals(3, nota.grado)
        assertEquals(setOf(Modificador.LARGA), nota.modificadores)
        assertEquals(120, nota.distanciaSiguienteM)
        assertEquals(true, nota.cuidado)
        assertNull(InterpreteOrdenes.interpretar("derecha"))
    }
}
