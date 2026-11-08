package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.LONGITUD_MAX_TEXTO_LIBRE
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.motor.notas.limpiarTextoLibre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editor completo de notas (H18): fusionar, dividir, texto libre y avisos añadidos. */
class EditorCompletoNotasTest {
    private val tramo = "tramo-1"

    @Suppress("LongParameterList")
    private fun nota(
        posicionM: Double,
        grado: Int? = 4,
        direccion: Direccion = Direccion.DERECHA,
        modificadores: Set<Modificador> = emptySet(),
        distanciaM: Int? = null,
        largoM: Double = 40.0,
        texto: String? = null,
    ) = Nota(posicionM, posicionM + largoM, direccion, grado, modificadores, distanciaM, textoLibre = texto)

    private fun hoja(vararg elementos: ElementoHoja) = HojaNotas(elementos.toList())

    private fun deNota(nota: Nota) = ElementoHoja.DeNota(nota)

    /** Notas en 100, 400 y 800 m (40 m de largo cada una). */
    private fun automatica() =
        hoja(
            deNota(nota(100.0, grado = 4, distanciaM = 250)),
            deNota(nota(400.0, grado = 3, distanciaM = 300, direccion = Direccion.IZQUIERDA)),
            deNota(nota(800.0, grado = 5)),
        )

    private fun aplicar(
        base: HojaNotas,
        posicionM: Double,
        accion: AccionEdicion,
        vararg mas: NotasEditadas,
    ) = FusionNotas.aplicar(base, { it }, listOf(NotasEditadas(tramo, posicionM, accion, 1)) + mas)

    // --- Fusionar --------------------------------------------------------------------------------------------------

    @Test
    fun `fusionar une la nota con la siguiente`() {
        val resultado = aplicar(automatica(), 100.0, AccionEdicion.Fusionar)
        assertEquals(listOf(100.0, 800.0), resultado.notas.map { it.posicionM })
        val unida = resultado.notas[0]
        assertEquals(Direccion.DERECHA, unida.direccion)
        assertEquals(3, unida.grado)
        assertEquals(440.0, unida.finM, 0.0)
    }

    @Test
    fun `la nota fusionada hereda la distancia de la segunda y recalcula la de la anterior`() {
        val resultado = aplicar(automatica(), 100.0, AccionEdicion.Fusionar)
        // Hueco de 440 a 800 = 360 m → 300 m.
        assertEquals(300, resultado.notas[0].distanciaSiguienteM)
        assertNull(resultado.notas[1].distanciaSiguienteM)
    }

    @Test
    fun `fusionar la segunda y la tercera recalcula la distancia de la primera`() {
        val resultado = aplicar(automatica(), 400.0, AccionEdicion.Fusionar)
        assertEquals(listOf(100.0, 400.0), resultado.notas.map { it.posicionM })
        // La primera acaba en 140 y la fusionada empieza en 400: hueco 260 → 250.
        assertEquals(250, resultado.notas[0].distanciaSiguienteM)
        assertEquals(Direccion.IZQUIERDA, resultado.notas[1].direccion)
        assertEquals(3, resultado.notas[1].grado)
        assertEquals(840.0, resultado.notas[1].finM, 0.0)
    }

    @Test
    fun `fusionar la ultima nota no hace nada`() {
        val base = automatica()
        assertEquals(base.notas.size, aplicar(base, 800.0, AccionEdicion.Fusionar).notas.size)
    }

    @Test
    fun `fusionar toma el grado mas cerrado y la horquilla gana`() {
        val base =
            hoja(
                deNota(nota(100.0, grado = 5)),
                deNota(nota(200.0, grado = null)),
                deNota(nota(300.0, grado = 2)),
            )
        assertNull(aplicar(base, 100.0, AccionEdicion.Fusionar).notas[0].grado)
        assertNull(aplicar(base, 200.0, AccionEdicion.Fusionar).notas[1].grado)
        val numeradas = hoja(deNota(nota(100.0, grado = 5)), deNota(nota(200.0, grado = 2)))
        assertEquals(2, aplicar(numeradas, 100.0, AccionEdicion.Fusionar).notas.single().grado)
    }

    @Test
    fun `fusionar combina los matices`() {
        val base =
            hoja(
                deNota(nota(100.0, modificadores = setOf(Modificador.LARGA, Modificador.CIERRA))),
                deNota(nota(200.0, modificadores = setOf(Modificador.MUY_LARGA, Modificador.ABRE))),
            )
        val unida = aplicar(base, 100.0, AccionEdicion.Fusionar).notas.single()
        assertEquals(setOf(Modificador.MUY_LARGA), unida.modificadores)
    }

    @Test
    fun `fusionar dos escuadras deja una escuadra y una sola la descarta`() {
        val dos =
            hoja(
                deNota(nota(100.0, grado = 3, modificadores = setOf(Modificador.ESCUADRA))),
                deNota(nota(200.0, grado = 3, modificadores = setOf(Modificador.ESCUADRA))),
            )
        assertEquals(
            setOf(Modificador.ESCUADRA),
            aplicar(dos, 100.0, AccionEdicion.Fusionar).notas.single().modificadores,
        )
        val una =
            hoja(
                deNota(nota(100.0, grado = 3, modificadores = setOf(Modificador.ESCUADRA))),
                deNota(nota(200.0, grado = 4)),
            )
        val unida = aplicar(una, 100.0, AccionEdicion.Fusionar).notas.single()
        assertTrue(unida.modificadores.isEmpty())
    }

    @Test
    fun `la nota fusionada se puede editar despues`() {
        val editada = nota(0.0, grado = 1, direccion = Direccion.IZQUIERDA, largoM = 0.0)
        val resultado =
            aplicar(
                automatica(),
                100.0,
                AccionEdicion.Fusionar,
                NotasEditadas(tramo, 110.0, AccionEdicion.Sustituir(editada), 2),
            )
        assertEquals(Direccion.IZQUIERDA, resultado.notas[0].direccion)
        assertEquals(1, resultado.notas[0].grado)
        assertEquals(440.0, resultado.notas[0].finM, 0.0)
    }

    @Test
    fun `fusionar conserva los avisos unidos de las dos notas`() {
        val a = Aviso(90.0, TipoAviso.RASANTE)
        val b = Aviso(390.0, TipoAviso.CRUCE)
        val base =
            hoja(
                ElementoHoja.DeNota(nota(100.0), avisosUnidos = listOf(a)),
                ElementoHoja.DeNota(nota(400.0), avisosUnidos = listOf(b)),
            )
        val unida = aplicar(base, 100.0, AccionEdicion.Fusionar).elementos.single() as ElementoHoja.DeNota
        assertEquals(listOf(a, b), unida.avisosUnidos)
    }

    @Test
    fun `fusionar con texto libre une las dos frases`() {
        val base =
            hoja(
                deNota(nota(100.0, grado = 3, texto = "gravilla")),
                deNota(nota(200.0, grado = 4, direccion = Direccion.IZQUIERDA)),
            )
        val unida = aplicar(base, 100.0, AccionEdicion.Fusionar).notas.single()
        assertEquals("gravilla, izquierda cuatro", unida.textoLibre)
    }

    // --- Dividir ---------------------------------------------------------------------------------------------------

    @Test
    fun `dividir por la mitad da dos notas contiguas iguales`() {
        val resultado = aplicar(automatica(), 100.0, AccionEdicion.Dividir) // 100 = inicio: fuera de la curva → mitad
        assertEquals(listOf(100.0, 120.0, 400.0, 800.0), resultado.notas.map { it.posicionM })
        val (primera, segunda) = resultado.notas
        assertEquals(120.0, primera.finM, 0.0)
        assertEquals(140.0, segunda.finM, 0.0)
        assertEquals(primera.grado, segunda.grado)
        assertEquals(primera.direccion, segunda.direccion)
        assertTrue(primera.unidaConSiguiente)
        assertNull(primera.distanciaSiguienteM)
        // La segunda conserva la distancia a la siguiente nota (hueco 140 → 400 = 260 → 250).
        assertEquals(250, segunda.distanciaSiguienteM)
    }

    @Test
    fun `dividir corta donde se toca si cae dentro de la curva`() {
        val resultado = aplicar(automatica(), 110.0, AccionEdicion.Dividir)
        assertEquals(listOf(100.0, 110.0, 400.0, 800.0), resultado.notas.map { it.posicionM })
        assertEquals(110.0, resultado.notas[0].finM, 0.0)
        assertEquals(140.0, resultado.notas[1].finM, 0.0)
    }

    @Test
    fun `dividir no deja una mitad vacia aunque se toque en el borde`() {
        val resultado = aplicar(automatica(), 100.2, AccionEdicion.Dividir)
        assertEquals(101.0, resultado.notas[1].posicionM, 1e-9)
    }

    @Test
    fun `dividir una curva demasiado corta no hace nada`() {
        val base = hoja(deNota(nota(100.0, largoM = 1.0)))
        assertEquals(1, aplicar(base, 100.0, AccionEdicion.Dividir).notas.size)
    }

    @Test
    fun `cada mitad se puede editar por separado`() {
        val derecha = nota(0.0, grado = 2, largoM = 0.0)
        val izquierda = nota(0.0, grado = 5, direccion = Direccion.IZQUIERDA, largoM = 0.0)
        val resultado =
            aplicar(
                automatica(),
                110.0,
                AccionEdicion.Dividir,
                NotasEditadas(tramo, 105.0, AccionEdicion.Sustituir(derecha), 2),
                NotasEditadas(tramo, 125.0, AccionEdicion.Sustituir(izquierda), 3),
            )
        assertEquals(2, resultado.notas[0].grado)
        assertEquals(Direccion.DERECHA, resultado.notas[0].direccion)
        assertEquals(5, resultado.notas[1].grado)
        assertEquals(Direccion.IZQUIERDA, resultado.notas[1].direccion)
    }

    @Test
    fun `dividir y volver a fusionar da una nota con el mismo recorrido`() {
        val resultado =
            aplicar(
                automatica(),
                110.0,
                AccionEdicion.Dividir,
                NotasEditadas(tramo, 100.0, AccionEdicion.Fusionar, 2),
            )
        assertEquals(listOf(100.0, 400.0, 800.0), resultado.notas.map { it.posicionM })
        assertEquals(140.0, resultado.notas[0].finM, 0.0)
    }

    @Test
    fun `dividir reparte el puente de la curva segun donde caiga`() {
        val puente = Aviso(135.0, TipoAviso.PUENTE)
        val base = hoja(ElementoHoja.DeNota(nota(100.0), sobrePuente = puente))
        val resultado = aplicar(base, 110.0, AccionEdicion.Dividir).elementos.filterIsInstance<ElementoHoja.DeNota>()
        assertNull(resultado[0].sobrePuente)
        assertEquals(puente, resultado[1].sobrePuente)
    }

    // --- Texto libre -----------------------------------------------------------------------------------------------

    @Test
    fun `el texto libre prevalece sobre la frase generada`() {
        val conTexto = nota(100.0, grado = 3, texto = "cuidado gravilla, derecha tres")
        assertEquals("cuidado gravilla, derecha tres", FrasesNota.frase(conTexto))
        assertEquals("cuidado gravilla, derecha tres", FrasesNota.frase(conTexto, simplificada = true))
    }

    @Test
    fun `el texto libre anula el cuidado generado pero la distancia se sigue cantando`() {
        val conTexto = nota(100.0, grado = 3, distanciaM = 150, texto = "gravilla").copy(cuidado = true)
        assertEquals("gravilla, ciento cincuenta", FrasesNota.frase(conTexto))
        val elemento = ElementoHoja.DeNota(conTexto)
        assertEquals("gravilla, ciento cincuenta", elemento.frase)
    }

    @Test
    fun `un texto en blanco es como no tenerlo`() {
        val sinTexto = nota(100.0, grado = 3, texto = "   ")
        assertEquals("derecha tres", FrasesNota.frase(sinTexto))
    }

    @Test
    fun `sustituir con texto libre lo deja en la hoja`() {
        val nueva = nota(0.0, grado = 4, texto = "cuidado gravilla")
        val resultado = aplicar(automatica(), 100.0, AccionEdicion.Sustituir(nueva))
        assertEquals("cuidado gravilla", resultado.notas[0].textoLibre)
        assertEquals("cuidado gravilla, doscientos cincuenta", (resultado.elementos[0] as ElementoHoja.DeNota).frase)
    }

    @Test
    fun `el texto libre se limpia y se recorta`() {
        assertEquals("a b c", limpiarTextoLibre("  a \n b\t c  "))
        assertNull(limpiarTextoLibre("   "))
        assertNull(limpiarTextoLibre(null))
        assertEquals(LONGITUD_MAX_TEXTO_LIBRE, limpiarTextoLibre("x".repeat(LONGITUD_MAX_TEXTO_LIBRE + 50))?.length)
    }

    // --- Avisos ----------------------------------------------------------------------------------------------------

    @Test
    fun `anadir un aviso lo inserta en su posicion y en orden`() {
        val resultado = aplicar(automatica(), 250.0, AccionEdicion.AnadirAviso(TipoAviso.BADEN))
        assertEquals(listOf(100.0, 250.0, 400.0, 800.0), resultado.elementos.map { it.posicionM })
        val aviso = resultado.elementos[1] as ElementoHoja.DeAviso
        assertEquals(Aviso(250.0, TipoAviso.BADEN), aviso.aviso)
        assertEquals(TipoAviso.BADEN.frase, aviso.frase)
        assertEquals(3, resultado.notas.size)
        assertEquals(250, resultado.notas[0].distanciaSiguienteM)
    }

    @Test
    fun `el aviso anadido se puede quitar despues`() {
        val resultado =
            aplicar(
                automatica(),
                250.0,
                AccionEdicion.AnadirAviso(TipoAviso.CRUCE),
                NotasEditadas(tramo, 255.0, AccionEdicion.QuitarAviso, 2),
            )
        assertTrue(resultado.elementos.none { it is ElementoHoja.DeAviso })
    }

    @Test
    fun `quitar aviso solo afecta a un aviso cercano`() {
        val base = hoja(ElementoHoja.DeAviso(Aviso(100.0, TipoAviso.STOP)), deNota(nota(300.0)))
        assertEquals(2, aplicar(base, 200.0, AccionEdicion.QuitarAviso).elementos.size)
        val sin = aplicar(base, 105.0, AccionEdicion.QuitarAviso)
        assertEquals(1, sin.elementos.size)
        assertFalse(sin.elementos.single() is ElementoHoja.DeAviso)
    }

    // --- Codec -----------------------------------------------------------------------------------------------------

    private fun ida(accion: AccionEdicion) =
        CodecEdiciones.accion(CodecEdiciones.tipo(accion), CodecEdiciones.nota(accion))

    @Test
    fun `las acciones nuevas sobreviven al codec`() {
        val acciones =
            listOf(
                AccionEdicion.Fusionar,
                AccionEdicion.Dividir,
                AccionEdicion.QuitarAviso,
            ) + TipoAviso.entries.map { AccionEdicion.AnadirAviso(it) }
        for (accion in acciones) assertEquals(accion, ida(accion))
    }

    @Test
    fun `un aviso desconocido o sin tipo da null`() {
        assertNull(CodecEdiciones.accion(CodecEdiciones.ANADIR_AVISO, "NO_EXISTE"))
        assertNull(CodecEdiciones.accion(CodecEdiciones.ANADIR_AVISO, null))
    }

    @Test
    fun `el texto libre sobrevive al codec con separadores y porcentajes`() {
        val texto = "cuidado; gravilla 100% al 50%25 %3B fin"
        val accion = AccionEdicion.Sustituir(nota(0.0, grado = 3, texto = texto))
        val vuelta = ida(accion) as AccionEdicion.Sustituir
        assertEquals(texto, vuelta.nota.textoLibre)
        assertEquals(accion, vuelta)
    }

    @Test
    fun `una nota sin texto se guarda igual que antes`() {
        val nota = nota(0.0, grado = 3, direccion = Direccion.IZQUIERDA)
        assertEquals("IZQUIERDA;3;;;0;40.0", CodecEdiciones.codificarNota(nota))
    }

    @Test
    fun `el codec lee lo guardado antes del texto libre`() {
        val vieja = CodecEdiciones.decodificarNota("IZQUIERDA;2;LARGA;100;1;40.0")
        assertEquals(Direccion.IZQUIERDA, vieja?.direccion)
        assertEquals(2, vieja?.grado)
        assertEquals(100, vieja?.distanciaSiguienteM)
        assertNull(vieja?.textoLibre)
        val esperada = Nota(0.0, 40.0, Direccion.IZQUIERDA, 2, setOf(Modificador.LARGA), 100, cuidado = true)
        assertEquals(
            AccionEdicion.Sustituir(esperada),
            CodecEdiciones.accion(CodecEdiciones.SUSTITUIR, "IZQUIERDA;2;LARGA;100;1;40.0"),
        )
    }

    @Test
    fun `un campo extra de mas de siete campos da null`() {
        assertNull(CodecEdiciones.decodificarNota("IZQUIERDA;2;LARGA;100;1;40.0;a;b"))
    }
}
