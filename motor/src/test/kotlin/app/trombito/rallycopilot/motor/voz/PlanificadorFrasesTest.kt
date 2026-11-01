package app.trombito.rallycopilot.motor.voz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanificadorFrasesTest {
    private val duraciones = mutableMapOf<String, Double>()
    private val planificador = PlanificadorFrases { duraciones[it] ?: 1.0 }

    private fun frase(
        id: String,
        tipo: TipoFrase = TipoFrase.NOTA,
        puntoM: Double? = 500.0,
        texto: String = "texto $id",
    ) = Frase(id, texto, tipo, puntoM)

    private fun siguiente(
        candidatas: List<Frase>,
        posicionM: Double = 0.0,
        vMs: Double? = 20.0,
        modo: ModoCopiloto = ModoCopiloto.COMPLETO,
    ) = planificador.siguiente(candidatas, posicionM, vMs, modo)

    @Test
    fun `sin candidatas no hay frase`() {
        assertNull(siguiente(emptyList()))
    }

    @Test
    fun `en silencio no habla nada ni siquiera una alerta`() {
        assertNull(siguiente(listOf(frase("a", TipoFrase.ALERTA)), modo = ModoCopiloto.SILENCIO))
    }

    @Test
    fun `filtra por modo y deja pasar lo permitido`() {
        val candidatas = listOf(frase("n", TipoFrase.NOTA, 100.0), frase("m", TipoFrase.MANIOBRA, 200.0))
        assertEquals("m", siguiente(candidatas, modo = ModoCopiloto.SOLO_ALERTAS)?.id)
    }

    @Test
    fun `una frase callada por el modo no se descarta y suena al cambiar a un modo que la permite`() {
        val nota = frase("n", TipoFrase.NOTA)
        assertNull(siguiente(listOf(nota), modo = ModoCopiloto.SOLO_ALERTAS))
        assertEquals("n", siguiente(listOf(nota), modo = ModoCopiloto.NOTAS)?.id)
    }

    @Test
    fun `una frase que ya paso no suena y no vuelve nunca`() {
        val nota = frase("n", puntoM = 100.0)
        assertNull(siguiente(listOf(nota), posicionM = 100.0))
        assertNull(siguiente(listOf(nota), posicionM = 50.0))
    }

    @Test
    fun `una frase que no acabaria antes de llegar se descarta para siempre`() {
        duraciones["texto n"] = 2.0
        val nota = frase("n", puntoM = 100.0)
        // 10 m a 10 m/s = 1 s < 2 s de voz.
        assertNull(siguiente(listOf(nota), posicionM = 90.0, vMs = 10.0))
        // Aunque luego hubiera tiempo de sobra (p. ej. se frena), no se recupera.
        assertNull(siguiente(listOf(nota), posicionM = 0.0, vMs = 10.0))
    }

    @Test
    fun `justo a tiempo si suena`() {
        duraciones["texto n"] = 2.0
        // 20 m a 10 m/s = 2 s: no es menor que la duracion.
        assertEquals("n", siguiente(listOf(frase("n", puntoM = 100.0)), posicionM = 80.0, vMs = 10.0)?.id)
    }

    @Test
    fun `parado o sin velocidad no se aplica el corte por tiempo`() {
        duraciones["texto n"] = 5.0
        assertEquals("n", siguiente(listOf(frase("n", puntoM = 100.0)), posicionM = 99.0, vMs = 0.5)?.id)
        val otra = frase("o", puntoM = 100.0)
        duraciones["texto o"] = 5.0
        assertEquals("o", siguiente(listOf(otra), posicionM = 99.0, vMs = null)?.id)
    }

    @Test
    fun `sin punto no caduca por posicion ni por tiempo`() {
        duraciones["texto x"] = 30.0
        val alerta = frase("x", TipoFrase.ALERTA, puntoM = null)
        assertEquals("x", siguiente(listOf(alerta), posicionM = 10_000.0, vMs = 50.0)?.id)
    }

    @Test
    fun `la mayor prioridad gana aunque la otra este mas cerca`() {
        val candidatas =
            listOf(
                frase("nota", TipoFrase.NOTA, 100.0),
                frase("crono", TipoFrase.CRONO, 90.0),
                frase("maniobra", TipoFrase.MANIOBRA, 300.0),
                frase("alerta", TipoFrase.ALERTA, 400.0),
            )
        assertEquals("alerta", siguiente(candidatas)?.id)
        assertEquals("maniobra", siguiente(candidatas)?.id)
        assertEquals("nota", siguiente(candidatas)?.id)
        assertEquals("crono", siguiente(candidatas)?.id)
        assertNull(siguiente(candidatas))
    }

    @Test
    fun `a igual prioridad gana el punto mas cercano`() {
        val candidatas = listOf(frase("lejos", puntoM = 300.0), frase("cerca", puntoM = 150.0))
        assertEquals("cerca", siguiente(candidatas)?.id)
        assertEquals("lejos", siguiente(candidatas)?.id)
    }

    @Test
    fun `sin punto cuenta como la mas urgente a igual prioridad`() {
        val candidatas = listOf(frase("con", TipoFrase.ALERTA, 150.0), frase("sin", TipoFrase.ALERTA, null))
        assertEquals("sin", siguiente(candidatas)?.id)
    }

    @Test
    fun `una frase dicha no se repite aunque siga siendo candidata`() {
        val candidatas = listOf(frase("a", puntoM = 100.0), frase("b", puntoM = 200.0))
        assertEquals("a", siguiente(candidatas)?.id)
        assertEquals("b", siguiente(candidatas)?.id)
        assertNull(siguiente(candidatas))
    }

    @Test
    fun `al recalcular las mismas ids no se repiten aunque cambie el texto y las nuevas si suenan`() {
        assertEquals("x", siguiente(listOf(frase("x", texto = "a quinientos")))?.id)
        assertNull(siguiente(listOf(frase("x", texto = "a cuatrocientos"))))
        assertEquals("y", siguiente(listOf(frase("x", texto = "a cuatrocientos"), frase("y")))?.id)
    }

    @Test
    fun `una frase descartada por la mas urgente que la tapa sigue pendiente`() {
        val candidatas = listOf(frase("nota", TipoFrase.NOTA, 100.0), frase("alerta", TipoFrase.ALERTA, 200.0))
        assertEquals("alerta", siguiente(candidatas)?.id)
        assertEquals("nota", siguiente(candidatas)?.id)
    }

    @Test
    fun `proximas devuelve las n siguientes sin marcarlas como dichas`() {
        val candidatas = listOf(frase("c", puntoM = 300.0), frase("a", puntoM = 100.0), frase("b", puntoM = 200.0))
        assertEquals(listOf("a", "b"), planificador.proximas(candidatas, 0.0, ModoCopiloto.COMPLETO, 2).map { it.id })
        assertEquals(listOf("a", "b"), planificador.proximas(candidatas, 0.0, ModoCopiloto.COMPLETO, 2).map { it.id })
    }

    @Test
    fun `proximas omite lo dicho lo pasado y lo que el modo calla`() {
        val candidatas =
            listOf(
                frase("pasada", puntoM = 50.0),
                frase("dicha", puntoM = 120.0),
                frase("crono", TipoFrase.CRONO, 130.0),
                frase("ok1", puntoM = 140.0),
                frase("ok2", puntoM = 150.0),
            )
        assertEquals("dicha", siguiente(candidatas.drop(1), posicionM = 60.0)?.id)
        val proximas = planificador.proximas(candidatas, 60.0, ModoCopiloto.NOTAS, 5)
        assertEquals(listOf("ok1", "ok2"), proximas.map { it.id })
    }

    @Test
    fun `proximas coloca primero las frases sin punto y con n cero no devuelve nada`() {
        val candidatas = listOf(frase("con", puntoM = 100.0), frase("sin", TipoFrase.ALERTA, null))
        val proximas = planificador.proximas(candidatas, 0.0, ModoCopiloto.COMPLETO, 5)
        assertEquals(listOf("sin", "con"), proximas.map { it.id })
        assertEquals(emptyList<Frase>(), planificador.proximas(candidatas, 0.0, ModoCopiloto.COMPLETO, 0))
    }
}
