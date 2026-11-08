package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class AgregadoCarreterasTest {
    private val latBase = 39.6
    private val lonBase = 2.9
    private val metrosPorGradoLat = 111_195.0
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(latBase))

    /** Punto a [x] metros al este y [y] al norte del origen. */
    private fun punto(
        x: Double,
        y: Double = 0.0,
    ) = PuntoGeo(latBase + y / metrosPorGradoLat, lonBase + x / metrosPorGradoLon)

    /** Línea recta de [desdeX] a [hastaX] metros sobre y = 0, con un punto cada [pasoM]. */
    private fun recta(
        desdeX: Double,
        hastaX: Double,
        pasoM: Double = 10.0,
    ): List<PuntoGeo> {
        val n = ((hastaX - desdeX) / pasoM).toInt()
        return (0..n).map { punto(desdeX + it * pasoM) }
    }

    private fun via(
        id: Long,
        ref: String?,
        nombre: String?,
        puntos: List<PuntoGeo>,
    ) = Via(id, "secondary", null, null, null, SentidoUnico.NO, ref, nombre, puntos)

    /** Dos vías seguidas hacia el este: A de 0 a 1 000 m (Ma-10) y B de 1 000 a 2 000 m (Ma-11). */
    private val viaA = via(1, "Ma-10", "Carretera de Valldemossa", recta(0.0, 1_000.0, 100.0))
    private val viaB = via(2, "Ma-11", null, recta(1_000.0, 2_000.0, 100.0))
    private val indice = IndiceVias(listOf(viaA, viaB))
    private val sinVias = AgregadoCarreteras.ViaDe { _, _ -> null }
    private val viaDe = AgregadoCarreteras.ViaDe { p, rumbo -> indice.cercana(p, rumbo)?.via }

    @Test
    fun `sin recorridos todo es cero`() {
        val resumen = AgregadoCarreteras.resumir(emptyList(), viaDe)
        assertEquals(0, resumen.recorridos)
        assertEquals(0.0, resumen.metrosTotales, 0.0)
        assertEquals(0, resumen.curvas.total)
        assertTrue(resumen.vias.isEmpty())
        assertTrue(resumen.carreteras.isEmpty())
    }

    @Test
    fun `una pasada por recorrido y via aunque tenga muchos puntos`() {
        // Puntos cada metro: cientos de muestras en la misma vía, pero una sola pasada.
        val densa = recta(0.0, 900.0, 1.0)
        val resumen = AgregadoCarreteras.resumir(listOf(densa), viaDe)
        assertEquals(1, resumen.vias.size)
        assertEquals(1, resumen.vias.single().pasadas)
        assertEquals(900.0, resumen.vias.single().metros, 30.0)
    }

    @Test
    fun `las pasadas se cuentan por recorrido en cada via`() {
        val recorridos =
            listOf(
                recta(50.0, 1_950.0),
                recta(100.0, 1_900.0),
                recta(100.0, 800.0),
            )
        val resumen = AgregadoCarreteras.resumir(recorridos, viaDe)
        val a = resumen.vias.first { it.via.id == 1L }
        val b = resumen.vias.first { it.via.id == 2L }
        assertEquals(3, a.pasadas)
        assertEquals(2, b.pasadas)
        val masRepetida = resumen.vias.first()
        assertEquals(1L, masRepetida.via.id)
        assertEquals(3, resumen.recorridos)
    }

    @Test
    fun `los kilometros por via y los totales suman la polilinea`() {
        val resumen = AgregadoCarreteras.resumir(listOf(recta(0.0, 2_000.0), recta(0.0, 500.0)), viaDe)
        assertEquals(2_500.0, resumen.metrosTotales, 5.0)
        assertEquals(1_500.0, resumen.vias.first { it.via.id == 1L }.metros, 40.0)
        assertEquals(1_000.0, resumen.vias.first { it.via.id == 2L }.metros, 40.0)
    }

    @Test
    fun `cruzar una calle suelta no cuenta como pasada`() {
        val transversal = via(3, null, "Carrer curt", listOf(punto(500.0, -100.0), punto(500.0, 100.0)))
        val con = IndiceVias(listOf(viaA, viaB, transversal))
        // Un recorrido por la transversal que solo toca la vía A al cruzarla.
        val cruce = (0..20).map { punto(500.0, -100.0 + it * 10.0) }
        val resumen =
            AgregadoCarreteras.resumir(listOf(cruce)) { p, r -> con.cercana(p, r)?.via }
        assertEquals(listOf(3L), resumen.vias.map { it.via.id })
    }

    @Test
    fun `un salto de GPS no suma kilometros ni une los trozos`() {
        val trozo1 = recta(0.0, 400.0)
        val trozo2 = recta(1_400.0, 1_800.0)
        val resumen = AgregadoCarreteras.resumir(listOf(trozo1 + trozo2), viaDe)
        assertEquals(800.0, resumen.metrosTotales, 5.0)
    }

    @Test
    fun `sin vias hay kilometros y curvas pero no pasadas`() {
        val resumen = AgregadoCarreteras.resumir(listOf(recta(0.0, 1_000.0))) { _, _ -> null }
        assertEquals(1, resumen.recorridos)
        assertEquals(1_000.0, resumen.metrosTotales, 5.0)
        assertTrue(resumen.vias.isEmpty())
    }

    /** Recta de 400 m al este, un arco de [grados] con radio [radioM] hacia la izquierda y otra recta de 400 m. */
    private fun conCurva(
        grados: Double,
        radioM: Double,
    ): List<PuntoGeo> {
        val puntos = ArrayList<PuntoGeo>()
        for (x in 0..400 step 10) puntos += punto(x.toDouble())
        val pasos = (grados / 3.0).toInt()
        // Centro a la izquierda (norte) del final de la recta.
        for (i in 1..pasos) {
            val ang = Math.toRadians(i * 3.0)
            puntos += punto(400.0 + radioM * sin(ang), radioM - radioM * cos(ang))
        }
        val fin = Math.toRadians(pasos * 3.0)
        val x0 = 400.0 + radioM * sin(fin)
        val y0 = radioM - radioM * cos(fin)
        for (d in 10..400 step 10) puntos += punto(x0 + d * cos(fin), y0 + d * sin(fin))
        return puntos
    }

    @Test
    fun `una curva abierta cuenta en su grado`() {
        val analisis = AgregadoCarreteras.analizar(conCurva(45.0, 120.0), sinVias)
        assertEquals(1, analisis.curvas.total)
        assertEquals(1, analisis.curvas.deGrado(5))
    }

    @Test
    fun `una curva de mas de 180 grados es una horquilla`() {
        val analisis = AgregadoCarreteras.analizar(conCurva(240.0, 40.0), sinVias)
        assertEquals(1, analisis.curvas.horquillas)
        assertEquals(0, analisis.curvas.porGrado.sum())
    }

    @Test
    fun `las curvas se asignan a la via donde caen y a su carretera`() {
        // El mismo trazo de la curva, puesto sobre la vía A (Ma-10): arranca en x = 0 y gira en x = 400.
        val resumen = AgregadoCarreteras.resumir(listOf(conCurva(45.0, 120.0), conCurva(45.0, 120.0)), viaDe)
        assertEquals(2, resumen.curvas.total)
        assertEquals(2, resumen.curvas.deGrado(5))
        val carretera = resumen.carreteras.single()
        assertEquals("Ma-10", carretera.ref)
        assertEquals("Carretera de Valldemossa", carretera.nombre)
        assertEquals(2, carretera.pasadas)
        assertEquals(2, carretera.curvas)
    }

    @Test
    fun `las carreteras se ordenan por curvas y se agrupan por ref`() {
        val b1 = via(10, "Ma-11", null, recta(0.0, 500.0, 100.0))
        val b2 = via(11, "Ma-11", null, recta(500.0, 1_000.0, 100.0))
        val a = via(12, "Ma-10", null, listOf(punto(0.0, 5_000.0), punto(1_000.0, 5_000.0)))
        val resumen =
            AgregadoCarreteras.agregar(
                listOf(
                    AnalisisRecorrido(
                        1_000.0,
                        listOf(UsoVia(b1, 500.0, 2), UsoVia(b2, 500.0, 3), UsoVia(a, 100.0, 1)),
                        CurvasPorGrado.VACIO,
                    ),
                ),
            )
        assertEquals(listOf("Ma-11", "Ma-10"), resumen.carreteras.map { it.ref })
        assertEquals(5, resumen.carreteras.first().curvas)
        assertEquals(1, resumen.carreteras.first().pasadas)
        assertEquals(1_000.0, resumen.carreteras.first().metros, 0.0)
    }

    @Test
    fun `las curvas por grado se suman`() {
        val a = CurvasPorGrado(listOf(1, 0, 2, 0, 0, 0), escuadras = 1, horquillas = 0)
        val b = CurvasPorGrado(listOf(0, 3, 0, 0, 0, 1), escuadras = 0, horquillas = 2)
        val suma = a + b
        assertEquals(listOf(1, 3, 2, 0, 0, 1), suma.porGrado)
        assertEquals(1, suma.escuadras)
        assertEquals(2, suma.horquillas)
        assertEquals(10, suma.total)
    }
}
