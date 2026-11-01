package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.DatosNotasVias
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.NodoAviso
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ensamblado de la hoja (unión de avisos con notas), reglas de precaución y generador con vías sintéticas. */
class HojaNotasTest {
    @Suppress("LongParameterList")
    private fun nota(
        posicionM: Double,
        grado: Int? = 4,
        modificadores: Set<Modificador> = emptySet(),
        unida: Boolean = false,
        finM: Double = posicionM + 40.0,
        direccion: Direccion = Direccion.DERECHA,
    ) = Nota(posicionM, finM, direccion, grado, modificadores, null, unida)

    private fun aviso(
        posicionM: Double,
        tipo: TipoAviso,
    ) = Aviso(posicionM, tipo)

    private fun marcar(
        notas: List<Nota>,
        avisos: List<Aviso> = emptyList(),
        peligros: List<Double> = emptyList(),
    ) = Precaucion.marcar(notas, avisos, peligros).map { it.cuidado }

    // --- Ensamblado ---------------------------------------------------------------------------------------------

    @Test
    fun `un aviso a menos de 20 m antes de una nota se canta con ella`() {
        val notas = listOf(nota(100.0, grado = 3), nota(300.0, grado = 4, direccion = Direccion.IZQUIERDA))
        val avisos =
            listOf(
                aviso(85.0, TipoAviso.RASANTE),
                aviso(150.0, TipoAviso.STOP),
                aviso(290.0, TipoAviso.PUENTE),
                aviso(310.0, TipoAviso.BACHE),
            )
        val frases = Ensamblado.unir(notas, avisos).map { it.frase }
        assertEquals(
            listOf("rasante, derecha tres", "stop", "puente, izquierda cuatro", "cuidado, resalto"),
            frases,
        )
    }

    @Test
    fun `un aviso a 20 m o mas antes de la nota va suelto`() {
        val elementos = Ensamblado.unir(listOf(nota(100.0)), listOf(aviso(80.0, TipoAviso.RASANTE)))
        assertEquals(listOf("rasante", "derecha cuatro"), elementos.map { it.frase })
        assertTrue(elementos[0] is ElementoHoja.DeAviso)
    }

    @Test
    fun `un aviso en la misma posicion que la nota se une a ella`() {
        val elementos = Ensamblado.unir(listOf(nota(100.0)), listOf(aviso(100.0, TipoAviso.SALTO)))
        assertEquals(listOf("salto, derecha cuatro"), elementos.map { it.frase })
    }

    @Test
    fun `varios avisos se unen en orden`() {
        val avisos = listOf(aviso(90.0, TipoAviso.PUENTE), aviso(95.0, TipoAviso.BACHE))
        val elementos = Ensamblado.unir(listOf(nota(100.0)), avisos)
        assertEquals(listOf("cuidado, puente, resalto, derecha cuatro"), elementos.map { it.frase })
    }

    @Test
    fun `un aviso a menos de 20 m de una nota que continua una cadena se mete dentro de la cadena`() {
        val notas = listOf(nota(100.0, unida = true), nota(150.0, direccion = Direccion.IZQUIERDA))
        val elementos = Ensamblado.unir(notas, listOf(aviso(140.0, TipoAviso.RASANTE)))
        assertEquals(listOf("derecha cuatro", "rasante, izquierda cuatro"), elementos.map { it.frase })
    }

    @Test
    fun `un cambio de rasante dentro de una cadena se descarta`() {
        val notas = listOf(nota(100.0, unida = true), nota(150.0, direccion = Direccion.IZQUIERDA))
        val elementos = Ensamblado.unir(notas, listOf(aviso(140.0, TipoAviso.CAMBIO_RASANTE)))
        assertEquals(listOf("derecha cuatro", "izquierda cuatro"), elementos.map { it.frase })
    }

    @Test
    fun `un aviso a 20 m o mas de una nota que continua una cadena va suelto`() {
        val notas = listOf(nota(100.0, unida = true), nota(150.0, direccion = Direccion.IZQUIERDA))
        val elementos = Ensamblado.unir(notas, listOf(aviso(125.0, TipoAviso.CAMBIO_RASANTE)))
        assertEquals(listOf("derecha cuatro", "cambio de rasante", "izquierda cuatro"), elementos.map { it.frase })
    }

    @Test
    fun `una compresion unida a una nota se canta antes de ella`() {
        val elementos = Ensamblado.unir(listOf(nota(100.0)), listOf(aviso(90.0, TipoAviso.COMPRESION)))
        assertEquals(listOf("compresión, derecha cuatro"), elementos.map { it.frase })
        val suelta = Ensamblado.unir(listOf(nota(100.0)), listOf(aviso(60.0, TipoAviso.COMPRESION)))
        assertEquals(listOf("compresión", "derecha cuatro"), suelta.map { it.frase })
    }

    @Test
    fun `el cuidado va siempre primero en la frase`() {
        val n = nota(100.0, grado = null).copy(cuidado = true)
        val unida = Ensamblado.unir(listOf(n), listOf(aviso(90.0, TipoAviso.RASANTE)))
        assertEquals(listOf("cuidado, rasante, horquilla derecha"), unida.map { it.frase })
        val conBache =
            Ensamblado.unir(
                listOf(nota(100.0)),
                listOf(aviso(90.0, TipoAviso.RASANTE), aviso(95.0, TipoAviso.BACHE)),
            )
        assertEquals(listOf("cuidado, rasante, resalto, derecha cuatro"), conBache.map { it.frase })
        val ambos = Ensamblado.unir(listOf(n), listOf(aviso(95.0, TipoAviso.BACHE)))
        assertEquals(listOf("cuidado, resalto, horquilla derecha"), ambos.map { it.frase })
    }

    @Test
    fun `un puente dentro de una curva se dice sobre puente al final de la nota`() {
        val n = nota(100.0, finM = 160.0).copy(distanciaSiguienteM = 50)
        val elementos = Ensamblado.unir(listOf(n), listOf(aviso(130.0, TipoAviso.PUENTE)))
        assertEquals(listOf("derecha cuatro sobre puente, cincuenta"), elementos.map { it.frase })
        assertTrue(elementos.single() is ElementoHoja.DeNota)
        assertEquals(TipoAviso.PUENTE, HojaNotas(elementos).avisos.single().tipo)
    }

    @Test
    fun `un puente fuera de la curva sigue sus reglas normales`() {
        val n = nota(100.0, finM = 160.0)
        val elementos =
            Ensamblado.unir(
                listOf(n),
                listOf(aviso(60.0, TipoAviso.PUENTE), aviso(200.0, TipoAviso.PUENTE)),
            )
        assertEquals(listOf("puente", "derecha cuatro", "puente"), elementos.map { it.frase })
        val unido = Ensamblado.unir(listOf(n), listOf(aviso(90.0, TipoAviso.PUENTE)))
        assertEquals(listOf("puente, derecha cuatro"), unido.map { it.frase })
    }

    @Test
    fun `otros avisos dentro de una curva no se tratan como puente`() {
        val n = nota(100.0, finM = 160.0)
        val elementos = Ensamblado.unir(listOf(n), listOf(aviso(130.0, TipoAviso.TUNEL)))
        assertEquals(listOf("derecha cuatro", "túnel"), elementos.map { it.frase })
    }

    @Test
    fun `un aviso despues de la ultima nota va suelto`() {
        val elementos = Ensamblado.unir(listOf(nota(100.0)), listOf(aviso(500.0, TipoAviso.STOP)))
        assertEquals(listOf("derecha cuatro", "stop"), elementos.map { it.frase })
    }

    @Test
    fun `sin notas todos los avisos van sueltos y ordenados`() {
        val avisos = listOf(aviso(10.0, TipoAviso.CEDA), aviso(20.0, TipoAviso.CRUCE))
        val elementos = Ensamblado.unir(emptyList(), avisos)
        assertEquals(listOf("ceda el paso", "cruce"), elementos.map { it.frase })
        assertTrue(elementos.all { it is ElementoHoja.DeAviso })
    }

    @Test
    fun `un aviso de cuidado unido a una nota con cuidado no repite la palabra`() {
        val n = nota(100.0).copy(cuidado = true)
        val elementos = Ensamblado.unir(listOf(n), listOf(aviso(95.0, TipoAviso.CUIDADO)))
        assertEquals(listOf("cuidado, derecha cuatro"), elementos.map { it.frase })
    }

    @Test
    fun `las frases de aviso`() {
        val esperado =
            mapOf(
                TipoAviso.PUENTE to "puente",
                TipoAviso.TUNEL to "túnel",
                TipoAviso.ROTONDA to "rotonda",
                TipoAviso.ESTRECHA to "estrecha",
                TipoAviso.BACHE to "cuidado, resalto",
                TipoAviso.BADEN to "cuidado badén",
                TipoAviso.PASO_A_NIVEL to "paso a nivel",
                TipoAviso.STOP to "stop",
                TipoAviso.CEDA to "ceda el paso",
                TipoAviso.SEMAFORO to "semáforo",
                TipoAviso.CRUCE to "cruce",
                TipoAviso.RASANTE to "rasante",
                TipoAviso.CAMBIO_RASANTE to "cambio de rasante",
                TipoAviso.SALTO to "salto",
                TipoAviso.COMPRESION to "compresión",
                TipoAviso.CUIDADO to "cuidado",
            )
        assertEquals(TipoAviso.entries.toSet(), esperado.keys)
        esperado.forEach { (tipo, frase) -> assertEquals(frase, ElementoHoja.DeAviso(aviso(0.0, tipo)).frase) }
    }

    // --- Precaución ---------------------------------------------------------------------------------------------

    @Test
    fun `una nota de grado 2 o menos que cierra lleva cuidado y de grado 3 no`() {
        val cierra = setOf(Modificador.CIERRA)
        assertEquals(listOf(true), marcar(listOf(nota(100.0, grado = 2, modificadores = cierra))))
        assertEquals(listOf(true), marcar(listOf(nota(100.0, grado = 1, modificadores = cierra))))
        assertEquals(listOf(false), marcar(listOf(nota(100.0, grado = 3, modificadores = cierra))))
        assertEquals(listOf(false), marcar(listOf(nota(100.0, grado = 2, modificadores = setOf(Modificador.ABRE)))))
    }

    @Test
    fun `una curva de grado 3 o menos con rasante o salto en los 60 m anteriores lleva cuidado`() {
        val n = listOf(nota(500.0, grado = 3))
        assertEquals(listOf(true), marcar(n, listOf(aviso(450.0, TipoAviso.RASANTE))))
        assertEquals(listOf(true), marcar(n, listOf(aviso(440.0, TipoAviso.SALTO))))
        assertEquals(listOf(false), marcar(n, listOf(aviso(430.0, TipoAviso.RASANTE))))
        assertEquals(listOf(true), marcar(n, listOf(aviso(450.0, TipoAviso.CAMBIO_RASANTE))))
        assertEquals(listOf(false), marcar(n, listOf(aviso(520.0, TipoAviso.RASANTE))))
        assertEquals(listOf(false), marcar(listOf(nota(500.0, grado = 4)), listOf(aviso(450.0, TipoAviso.RASANTE))))
    }

    @Test
    fun `una horquilla con rasante antes lleva cuidado`() {
        assertEquals(listOf(true), marcar(listOf(nota(500.0, grado = null)), listOf(aviso(480.0, TipoAviso.RASANTE))))
    }

    @Test
    fun `un salto de dos grados o mas a mas cerrada a menos de 150 m lleva cuidado`() {
        fun segunda(
            gradoAntes: Int?,
            gradoAhora: Int?,
            inicio: Double = 200.0,
        ) = marcar(listOf(nota(100.0, gradoAntes, finM = 140.0), nota(inicio, gradoAhora)))[1]
        assertTrue(segunda(5, 3))
        assertTrue(segunda(6, 2))
        assertTrue(segunda(3, null, inicio = 200.0))
        assertFalse(segunda(5, 4))
        assertFalse(segunda(3, 5))
        assertFalse(segunda(5, 3, inicio = 290.0))
        assertTrue(segunda(5, 3, inicio = 289.0))
        assertFalse(segunda(null, 3))
    }

    @Test
    fun `el salto de grado no marca si hubo otra horquilla a menos de 400 m`() {
        fun tercera(inicio: Double) =
            marcar(
                listOf(
                    nota(100.0, null, finM = 140.0),
                    nota(200.0, 5, finM = 240.0),
                    nota(inicio, 3),
                ),
            )[2]
        assertFalse(tercera(300.0))
        // Sin la horquilla, el mismo salto de grado si marca.
        assertTrue(marcar(listOf(nota(200.0, 5, finM = 240.0), nota(300.0, 3)))[1])
    }

    @Test
    fun `el salto de grado marca si la horquilla anterior queda a 400 m o mas`() {
        val notas = listOf(nota(100.0, null, finM = 140.0), nota(600.0, 5, finM = 640.0), nota(700.0, 3))
        assertTrue(marcar(notas)[2])
    }

    @Test
    fun `una horquilla tras mas de 300 m de recta lleva cuidado`() {
        fun segunda(inicio: Double) = marcar(listOf(nota(100.0, 2, finM = 140.0), nota(inicio, null)))[1]
        assertTrue(segunda(441.0))
        assertFalse(segunda(440.0))
        assertFalse(segunda(300.0))
    }

    @Test
    fun `una horquilla que abre la hoja cuenta la recta desde el inicio`() {
        assertEquals(listOf(true), marcar(listOf(nota(400.0, null))))
        assertEquals(listOf(false), marcar(listOf(nota(200.0, null))))
    }

    @Test
    fun `un hazard curve a 30 m o menos de la curva lleva cuidado`() {
        val n = listOf(nota(100.0, finM = 150.0))
        assertEquals(listOf(true), marcar(n, peligros = listOf(125.0)))
        assertEquals(listOf(true), marcar(n, peligros = listOf(70.0)))
        assertEquals(listOf(true), marcar(n, peligros = listOf(180.0)))
        assertEquals(listOf(false), marcar(n, peligros = listOf(69.0)))
        assertEquals(listOf(false), marcar(n, peligros = listOf(181.0)))
    }

    @Test
    fun `los hazard curve sin curva cerca se devuelven para cantarlos sueltos`() {
        val n = listOf(nota(100.0, finM = 150.0))
        assertEquals(listOf(400.0), Precaucion.curvasSinNota(n, listOf(125.0, 400.0)))
    }

    @Test
    fun `una nota tranquila no lleva cuidado`() {
        assertEquals(listOf(false, false), marcar(listOf(nota(100.0, 4, finM = 140.0), nota(300.0, 4))))
    }

    // --- Generador ----------------------------------------------------------------------------------------------

    private val arco =
        TrazasDePrueba.enPlano(
            TrazasDePrueba.arcoEnMetros(60.0, 90.0, entradaM = 300.0, salidaM = 300.0),
        )

    private fun viaDe(
        id: Long,
        puntos: List<PuntoGeo>,
    ) = Via(id, "secondary", null, null, null, SentidoUnico.NO, null, null, puntos)

    private fun contextoSobre(
        vias: List<Via>,
        nodos: List<NodoAviso> = emptyList(),
    ) = ContextoVias(IndiceVias(vias), GrafoVias(vias), DatosNotasVias(emptyMap(), nodos, emptyMap()))

    @Test
    fun `sin contexto la hoja solo tiene notas de curva`() {
        val hoja = GeneradorHoja().generar(arco)
        val notas = GeneradorNotas().generar(DetectorCurvas().detectar(arco))
        assertTrue(hoja.elementos.all { it is ElementoHoja.DeNota })
        assertEquals(notas.size, hoja.elementos.size)
        assertEquals(notas, hoja.notas.map { it.copy(cuidado = false) })
        assertTrue(hoja.avisos.isEmpty())
    }

    @Test
    fun `una ruta vacia da una hoja vacia`() {
        assertTrue(GeneradorHoja().generar(emptyList()).elementos.isEmpty())
        assertTrue(
            GeneradorHoja(contexto = contextoSobre(listOf(viaDe(1, arco)))).generar(emptyList()).elementos.isEmpty(),
        )
    }

    @Test
    fun `con contexto un bache justo antes de la curva se une a su nota`() {
        val contexto0 = contextoSobre(listOf(viaDe(1, arco)))
        val inicioCurva =
            GeneradorHoja(contexto = contexto0)
                .generar(arco)
                .notas
                .single()
                .posicionM
        val bache = NodoAviso(TrazasDePrueba.enPlano(listOf(2.0 to inicioCurva - 10.0)).single(), "bump")
        val hoja = GeneradorHoja(contexto = contextoSobre(listOf(viaDe(1, arco)), listOf(bache))).generar(arco)
        val elemento = hoja.elementos.single() as ElementoHoja.DeNota
        assertEquals(listOf(TipoAviso.BACHE), elemento.avisosUnidos.map { it.tipo })
        assertTrue(elemento.frase, elemento.frase.startsWith("cuidado, resalto, "))
        assertEquals(listOf(TipoAviso.BACHE), hoja.avisos.map { it.tipo })
    }

    @Test
    fun `con contexto un stop lejos de la curva va suelto y antes de ella`() {
        val nodo = NodoAviso(TrazasDePrueba.enPlano(listOf(2.0 to 50.0)).single(), "stop")
        val hoja = GeneradorHoja(contexto = contextoSobre(listOf(viaDe(1, arco)), listOf(nodo))).generar(arco)
        assertEquals(2, hoja.elementos.size)
        assertEquals("stop", hoja.elementos[0].frase)
        assertTrue(hoja.elementos[1] is ElementoHoja.DeNota)
    }

    @Test
    fun `un hazard curve cerca de la curva marca la nota y lejos se canta como cuidado`() {
        val inicio =
            GeneradorHoja()
                .generar(arco)
                .notas
                .single()
                .posicionM
        val cerca = NodoAviso(TrazasDePrueba.enPlano(listOf(2.0 to inicio + 5.0)).single(), "hazard:curve")
        val lejos = NodoAviso(TrazasDePrueba.enPlano(listOf(2.0 to 20.0)).single(), "hazard:curve")
        val hoja = GeneradorHoja(contexto = contextoSobre(listOf(viaDe(1, arco)), listOf(cerca, lejos))).generar(arco)
        assertEquals(
            listOf(
                "cuidado",
                "cuidado, " + FrasesNota.frase(hoja.notas.single().copy(cuidado = false)),
            ),
            hoja.elementos.map {
                it.frase
            },
        )
    }

    @Test
    fun `el generador de la hoja es determinista`() {
        val contexto = contextoSobre(listOf(viaDe(1, arco)), listOf(NodoAviso(arco[10], "stop")))
        val a = GeneradorHoja(contexto = contexto).generar(arco)
        val b = GeneradorHoja(contexto = contexto).generar(arco)
        assertEquals(a, b)
    }

    // --- Sin curvas en calles ------------------------------------------------------------------------------------

    /** Arco a la izquierda, 300 m de recta y arco a la derecha, en una sola polilínea. */
    private val dosArcos: List<PuntoGeo> =
        run {
            val primero = TrazasDePrueba.arcoEnMetros(60.0, 90.0, entradaM = 150.0, salidaM = 300.0)
            val segundo = TrazasDePrueba.arcoEnMetros(60.0, 90.0, izquierda = false, entradaM = 0.0, salidaM = 200.0)
            val (x0, y0) = primero.last()
            // El segundo arco, girado 90 grados a la izquierda (x, y) -> (-y, x) y trasladado al final del primero.
            TrazasDePrueba.enPlano(primero + segundo.drop(1).map { (x, y) -> (x0 - y) to (y0 + x) })
        }

    @Test
    fun `una curva con referencia de 30 km por hora o menos no genera nota y el resto si`() {
        val curvas = DetectorCurvas().detectar(dosArcos)
        assertEquals(2, curvas.size)
        val segundaM = curvas[1].inicioM
        val hoja = GeneradorHoja().generar(dosArcos, { if (it >= segundaM) 30.0 else 90.0 })
        assertEquals(1, hoja.notas.size)
        assertEquals(curvas[0].inicioM, hoja.notas.single().posicionM, 0.0)
    }

    @Test
    fun `la referencia justo por encima de 30 conserva la curva y el umbral es parametro`() {
        assertEquals(2, GeneradorHoja().generar(dosArcos, { 30.1 }).notas.size)
        assertTrue(GeneradorHoja().generar(dosArcos, { 30.0 }).notas.isEmpty())
        assertTrue(GeneradorHoja().generar(dosArcos, { 50.0 }, sinCurvasHastaKmh = 50.0).notas.isEmpty())
        assertEquals(2, GeneradorHoja().generar(dosArcos, { 30.0 }, sinCurvasHastaKmh = 20.0).notas.size)
    }

    @Test
    fun `sin referencia de velocidad no se descarta ninguna curva`() {
        assertEquals(2, GeneradorHoja().generar(dosArcos).notas.size)
    }

    @Test
    fun `si la curva filtrada era la siguiente la nota anterior no deja y ni distancia colgando`() {
        val segundaM = DetectorCurvas().detectar(dosArcos)[1].inicioM
        val con = GeneradorHoja().generar(dosArcos, { 90.0 }).notas.first()
        val sin = GeneradorHoja().generar(dosArcos, { if (it >= segundaM) 20.0 else 90.0 }).notas.single()
        assertTrue(con.distanciaSiguienteM != null || con.unidaConSiguiente)
        assertEquals(null, sin.distanciaSiguienteM)
        assertFalse(sin.unidaConSiguiente)
    }

    @Test
    fun `una curva de calle no genera nota pero si siguen los avisos y el cuidado`() {
        val stop = NodoAviso(dosArcos[10], "stop")
        val peligro = NodoAviso(dosArcos[60], "hazard:curve")
        val contexto = contextoSobre(listOf(viaDe(1, dosArcos)), listOf(stop, peligro))
        val hoja = GeneradorHoja(contexto = contexto).generar(dosArcos, { 20.0 })
        assertTrue(hoja.notas.isEmpty())
        assertTrue(hoja.avisos.any { it.tipo == TipoAviso.STOP })
        assertTrue(hoja.avisos.any { it.tipo == TipoAviso.CUIDADO })
    }
}
