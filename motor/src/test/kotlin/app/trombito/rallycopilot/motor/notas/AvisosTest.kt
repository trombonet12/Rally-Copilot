package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.DatosNotasVias
import app.trombito.rallycopilot.motor.vias.EtiquetasVia
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.NodoAviso
import app.trombito.rallycopilot.motor.vias.PerfilVia
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Extracción de avisos sobre una recta hacia el norte (x = 0) con vías falsas y etiquetas a medida. */
class AvisosTest {
    private val ruta = TrazasDePrueba.recta(600.0)

    private fun p(
        este: Double,
        norte: Double,
    ): PuntoGeo = TrazasDePrueba.enPlano(listOf(este to norte)).single()

    /** Vía recta sobre la ruta entre [desde] y [hasta] metros (con vértices en [intermedios]). */
    private fun via(
        id: Long,
        desde: Double,
        hasta: Double,
        sentido: SentidoUnico = SentidoUnico.NO,
        intermedios: List<Double> = emptyList(),
    ): Via {
        val puntos = listOf(desde) + intermedios + hasta
        return Via(id, "secondary", null, null, null, sentido, null, null, puntos.map { p(0.0, it) })
    }

    private fun lateral(
        id: Long,
        clase: String,
        desde: PuntoGeo,
        hasta: PuntoGeo,
    ) = Via(id, clase, null, null, null, SentidoUnico.NO, null, null, listOf(desde, hasta))

    @Suppress("LongParameterList")
    private fun etiquetas(
        bridge: String? = null,
        tunnel: String? = null,
        junction: String? = null,
        lanes: Int? = null,
        width: Double? = null,
        hazard: String? = null,
    ) = EtiquetasVia(bridge, tunnel, junction, lanes, width, hazard, null)

    private fun contexto(
        vias: List<Via>,
        etiquetas: Map<Long, EtiquetasVia> = emptyMap(),
        nodos: List<NodoAviso> = emptyList(),
        perfiles: Map<Long, PerfilVia> = emptyMap(),
    ) = ContextoVias(IndiceVias(vias), GrafoVias(vias), DatosNotasVias(etiquetas, nodos, perfiles))

    private fun extraer(contexto: ContextoVias) = ExtractorAvisos(contexto).extraer(ruta)

    /** Tres vías consecutivas; la central (200–300 m) lleva [central]. */
    private fun conTramoCentral(
        central: EtiquetasVia,
        sentido: SentidoUnico = SentidoUnico.NO,
    ): ContextoVias {
        val vias = listOf(via(1, 0.0, 200.0), via(2, 200.0, 300.0, sentido), via(3, 300.0, 600.0))
        return contexto(vias, mapOf(2L to central))
    }

    private fun assertUno(
        esperado: TipoAviso,
        posicionM: Double,
        r: AvisosRuta,
    ) {
        assertEquals(listOf(esperado), r.avisos.map { it.tipo })
        assertEquals(posicionM, r.avisos.single().posicionM, 6.0)
    }

    @Test
    fun `una ruta sin etiquetas no da avisos`() {
        val r = extraer(contexto(listOf(via(1, 0.0, 600.0))))
        assertTrue(r.avisos.isEmpty())
        assertTrue(r.curvasPeligrosasM.isEmpty())
    }

    @Test
    fun `una ruta de menos de dos puntos no da avisos`() {
        val c = contexto(listOf(via(1, 0.0, 600.0)))
        assertTrue(ExtractorAvisos(c).extraer(emptyList()).avisos.isEmpty())
        assertTrue(ExtractorAvisos(c).extraer(listOf(p(0.0, 0.0))).avisos.isEmpty())
    }

    @Test
    fun `un puente avisa una vez al empezar`() {
        assertUno(TipoAviso.PUENTE, 200.0, extraer(conTramoCentral(etiquetas(bridge = "yes"))))
        assertUno(TipoAviso.PUENTE, 200.0, extraer(conTramoCentral(etiquetas(bridge = "viaduct"))))
    }

    @Test
    fun `bridge no o ausente no avisa`() {
        assertTrue(extraer(conTramoCentral(etiquetas(bridge = "no"))).avisos.isEmpty())
    }

    @Test
    fun `dos vias seguidas con puente son un solo aviso`() {
        val vias = listOf(via(1, 0.0, 200.0), via(2, 200.0, 250.0), via(3, 250.0, 300.0), via(4, 300.0, 600.0))
        val e = mapOf(2L to etiquetas(bridge = "yes"), 3L to etiquetas(bridge = "viaduct"))
        assertUno(TipoAviso.PUENTE, 200.0, extraer(contexto(vias, e)))
    }

    @Test
    fun `un tunel avisa al empezar y una alcantarilla no`() {
        assertUno(TipoAviso.TUNEL, 200.0, extraer(conTramoCentral(etiquetas(tunnel = "yes"))))
        assertTrue(extraer(conTramoCentral(etiquetas(tunnel = "culvert"))).avisos.isEmpty())
    }

    @Test
    fun `una rotonda avisa al entrar`() {
        assertUno(TipoAviso.ROTONDA, 200.0, extraer(conTramoCentral(etiquetas(junction = "roundabout"))))
    }

    @Test
    fun `los cruces dentro de una rotonda no se avisan`() {
        val vias =
            listOf(
                via(1, 0.0, 200.0),
                via(2, 200.0, 300.0, intermedios = listOf(250.0)),
                via(3, 300.0, 600.0),
                lateral(4, "tertiary", p(0.0, 250.0), p(100.0, 250.0)),
            )
        val r = extraer(contexto(vias, mapOf(2L to etiquetas(junction = "roundabout"))))
        assertUno(TipoAviso.ROTONDA, 200.0, r)
    }

    @Test
    fun `un carril sin sentido unico es estrecha`() {
        assertUno(TipoAviso.ESTRECHA, 200.0, extraer(conTramoCentral(etiquetas(lanes = 1))))
    }

    @Test
    fun `un carril en via de sentido unico no es estrecha`() {
        val r = extraer(conTramoCentral(etiquetas(lanes = 1), SentidoUnico.ADELANTE))
        assertTrue(r.avisos.isEmpty())
    }

    @Test
    fun `menos de cinco metros de ancho es estrecha y cinco o mas no`() {
        assertUno(TipoAviso.ESTRECHA, 200.0, extraer(conTramoCentral(etiquetas(width = 3.5))))
        assertTrue(extraer(conTramoCentral(etiquetas(width = 5.0))).avisos.isEmpty())
        assertTrue(extraer(conTramoCentral(etiquetas(lanes = 2, width = 6.5))).avisos.isEmpty())
    }

    @Test
    fun `un hueco sin via cercana no parte el tramo`() {
        // Entre las dos vías con puente hay 60 m sin vía a 25 m: sigue siendo el mismo puente.
        val vias = listOf(via(1, 0.0, 200.0), via(2, 200.0, 230.0), via(3, 290.0, 330.0), via(4, 330.0, 600.0))
        val e = mapOf(2L to etiquetas(bridge = "yes"), 3L to etiquetas(bridge = "yes"))
        assertUno(TipoAviso.PUENTE, 200.0, extraer(contexto(vias, e)))
    }

    private fun nodoA(
        tipo: String,
        norte: Double = 300.0,
        este: Double = 5.0,
    ) = NodoAviso(p(este, norte), tipo)

    @Test
    fun `cada tipo de nodo da su aviso`() {
        val esperado =
            mapOf(
                "bump" to TipoAviso.BACHE,
                "hump" to TipoAviso.BACHE,
                "table" to TipoAviso.BACHE,
                "cushion" to TipoAviso.BACHE,
                "rumble_strip" to TipoAviso.BACHE,
                "dip" to TipoAviso.BADEN,
                "ford" to TipoAviso.BADEN,
                "level_crossing" to TipoAviso.PASO_A_NIVEL,
                "stop" to TipoAviso.STOP,
                "give_way" to TipoAviso.CEDA,
                "traffic_signals" to TipoAviso.SEMAFORO,
                "hazard:animal_crossing" to TipoAviso.CUIDADO,
            )
        for ((tipo, aviso) in esperado) {
            val r = extraer(contexto(listOf(via(1, 0.0, 600.0)), nodos = listOf(nodoA(tipo))))
            assertUno(aviso, 300.0, r)
        }
    }

    @Test
    fun `un tipo de nodo desconocido se ignora`() {
        val r = extraer(contexto(listOf(via(1, 0.0, 600.0)), nodos = listOf(nodoA("street_lamp"))))
        assertTrue(r.avisos.isEmpty())
    }

    @Test
    fun `un nodo a mas de 12 m de la ruta no cuenta y a menos si`() {
        val vias = listOf(via(1, 0.0, 600.0))
        assertTrue(extraer(contexto(vias, nodos = listOf(nodoA("stop", este = 14.0)))).avisos.isEmpty())
        assertUno(TipoAviso.STOP, 300.0, extraer(contexto(vias, nodos = listOf(nodoA("stop", este = 11.0)))))
    }

    @Test
    fun `un nodo que solo es vertice de una calle lateral no cuenta`() {
        val vias = listOf(via(1, 0.0, 600.0), lateral(9, "residential", p(10.0, 300.0), p(100.0, 300.0)))
        val r = extraer(contexto(vias, nodos = listOf(nodoA("stop", este = 10.0))))
        assertTrue(r.avisos.none { it.tipo == TipoAviso.STOP })
    }

    @Test
    fun `dos nodos del mismo tipo a menos de 30 m son un aviso y a mas son dos`() {
        val vias = listOf(via(1, 0.0, 600.0))
        val cerca = listOf(nodoA("bump", 300.0), nodoA("bump", 320.0))
        val lejos = listOf(nodoA("bump", 300.0), nodoA("bump", 335.0))
        assertEquals(1, extraer(contexto(vias, nodos = cerca)).avisos.size)
        assertEquals(2, extraer(contexto(vias, nodos = lejos)).avisos.size)
    }

    @Test
    fun `tipos distintos en el mismo sitio se conservan`() {
        val nodos = listOf(nodoA("bump", 300.0), nodoA("stop", 300.0))
        val r = extraer(contexto(listOf(via(1, 0.0, 600.0)), nodos = nodos))
        assertEquals(setOf(TipoAviso.BACHE, TipoAviso.STOP), r.avisos.map { it.tipo }.toSet())
    }

    @Test
    fun `un nodo hazard curve se devuelve aparte y no como aviso`() {
        val r = extraer(contexto(listOf(via(1, 0.0, 600.0)), nodos = listOf(nodoA("hazard:curve", 250.0))))
        assertTrue(r.avisos.isEmpty())
        assertEquals(250.0, r.curvasPeligrosasM.single(), 3.0)
    }

    @Test
    fun `un hazard de via que no es curva avisa con cuidado al empezar`() {
        assertUno(TipoAviso.CUIDADO, 200.0, extraer(conTramoCentral(etiquetas(hazard = "school_zone"))))
    }

    @Test
    fun `un hazard curve de via se devuelve aparte`() {
        val r = extraer(conTramoCentral(etiquetas(hazard = "curve")))
        assertTrue(r.avisos.isEmpty())
        assertEquals(200.0, r.curvasPeligrosasM.single(), 6.0)
    }

    @Test
    fun `una interseccion sobre la ruta es un cruce`() {
        val vias =
            listOf(
                via(1, 0.0, 600.0, intermedios = listOf(300.0)),
                lateral(9, "tertiary", p(0.0, 300.0), p(100.0, 300.0)),
            )
        assertUno(TipoAviso.CRUCE, 300.0, extraer(contexto(vias)))
    }

    @Test
    fun `una cresta del perfil es una rasante`() {
        val alturas = (0..120).map { k -> 100.0 + if (k <= 60) 0.08 * k * 5 else 0.08 * (120 - k) * 5 }
        val r = extraer(contexto(listOf(via(1, 0.0, 600.0)), perfiles = mapOf(1L to PerfilVia(5.0, alturas))))
        assertUno(TipoAviso.RASANTE, 300.0, r)
    }

    @Test
    fun `los avisos salen ordenados por posicion`() {
        val nodos = listOf(nodoA("stop", 500.0), nodoA("bump", 100.0), nodoA("give_way", 300.0))
        val r = extraer(contexto(listOf(via(1, 0.0, 600.0)), nodos = nodos))
        assertEquals(listOf(TipoAviso.BACHE, TipoAviso.CEDA, TipoAviso.STOP), r.avisos.map { it.tipo })
    }
}
