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
        vararg puntos: PuntoGeo,
    ) = Via(id, clase, null, null, null, SentidoUnico.NO, null, null, puntos.toList())

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
    fun `un cruce de la via de debajo bajo el puente de la ruta no es un cruce de la ruta`() {
        // La ruta va por un puente (vía 1) sobre una carretera E-O con un ramal; el nodo cae justo bajo la ruta.
        val debajo = lateral(5, "secondary", p(-100.0, 300.0), p(0.0, 300.0), p(100.0, 300.0))
        val vias =
            listOf(
                via(1, 0.0, 600.0),
                debajo,
                lateral(9, "tertiary", p(0.0, 300.0), p(50.0, 360.0)),
            )
        assertTrue(extraer(contexto(vias)).avisos.none { it.tipo == TipoAviso.CRUCE })
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

    @Test
    fun `los avisos de bache y ceda usan las frases nuevas`() {
        assertEquals("cuidado, resalto", TipoAviso.BACHE.frase)
        assertEquals("ceda el paso", TipoAviso.CEDA.frase)
    }

    @Test
    fun `un cruce con solo una calle de servicio y la ruta recta no es un cruce`() {
        val vias =
            listOf(
                via(1, 0.0, 600.0, intermedios = listOf(300.0)),
                lateral(9, "service", p(0.0, 300.0), p(100.0, 300.0)),
            )
        assertTrue(extraer(contexto(vias)).avisos.none { it.tipo == TipoAviso.CRUCE })
    }

    private fun autoviaConEnlace(
        claseRuta: String,
        claseEnlace: String,
    ) = listOf(
        Via(1, claseRuta, null, null, null, SentidoUnico.NO, null, null, listOf(0.0, 300.0, 600.0).map { p(0.0, it) }),
        lateral(9, claseEnlace, p(0.0, 300.0), p(100.0, 400.0)),
    )

    @Test
    fun `en autovia no se canta cruce en la entrada ni en la salida`() {
        assertTrue(
            extraer(contexto(autoviaConEnlace("motorway", "motorway_link"))).avisos.none {
                it.tipo ==
                    TipoAviso.CRUCE
            },
        )
        assertTrue(extraer(contexto(autoviaConEnlace("trunk", "tertiary"))).avisos.none { it.tipo == TipoAviso.CRUCE })
        assertTrue(
            extraer(contexto(autoviaConEnlace("primary_link", "tertiary"))).avisos.none {
                it.tipo ==
                    TipoAviso.CRUCE
            },
        )
    }

    @Test
    fun `si todas las demas ramas son enlaces no se canta cruce`() {
        val vias = autoviaConEnlace("secondary", "primary_link")
        assertTrue(extraer(contexto(vias)).avisos.none { it.tipo == TipoAviso.CRUCE })
    }

    @Test
    fun `en carretera convencional el cruce con una calle sigue avisando`() {
        assertUno(TipoAviso.CRUCE, 300.0, extraer(contexto(autoviaConEnlace("secondary", "tertiary"))))
    }

    // --- STOP y CEDA de la otra via ---------------------------------------------------------------------------------

    private fun viaConRef(
        id: Long,
        clase: String,
        ref: String?,
        nortes: List<Double>,
    ) = Via(id, clase, null, null, null, SentidoUnico.NO, ref, null, nortes.map { p(0.0, it) })

    private fun avisosDelNodo(
        tipo: String,
        vias: List<Via>,
    ) = extraer(contexto(vias, nodos = listOf(nodoA(tipo, 300.0, 0.0)))).avisos.map { it.tipo }

    @Test
    fun `un stop o ceda en un cruce al que la ruta llega por la via de mayor rango no se avisa`() {
        val vias =
            listOf(
                viaConRef(1, "secondary", "Ma-1", listOf(0.0, 300.0, 600.0)),
                lateral(9, "residential", p(0.0, 300.0), p(100.0, 300.0)),
            )
        assertTrue(avisosDelNodo("stop", vias).none { it == TipoAviso.STOP })
        assertTrue(avisosDelNodo("give_way", vias).none { it == TipoAviso.CEDA })
    }

    @Test
    fun `un stop en un cruce al que la ruta llega por la via de menor rango si se avisa`() {
        val vias =
            listOf(
                viaConRef(1, "residential", null, listOf(0.0, 300.0, 600.0)),
                lateral(9, "secondary", p(0.0, 300.0), p(100.0, 300.0)),
            )
        assertTrue(TipoAviso.STOP in avisosDelNodo("stop", vias))
        assertTrue(TipoAviso.CEDA in avisosDelNodo("give_way", vias))
    }

    @Test
    fun `con rangos iguales y sin ref el stop de un cruce se avisa y con ref no`() {
        fun vias(ref: String?) =
            listOf(
                viaConRef(1, "residential", ref, listOf(0.0, 300.0, 600.0)),
                lateral(9, "residential", p(0.0, 300.0), p(100.0, 300.0)),
            )
        assertTrue(TipoAviso.STOP in avisosDelNodo("stop", vias(null)))
        assertTrue(TipoAviso.STOP !in avisosDelNodo("stop", vias("Ma-1")))
    }

    @Test
    fun `un stop en un cruce donde la ruta sigue por la misma ref no se avisa`() {
        fun vias(ref: String?) =
            listOf(
                viaConRef(1, "residential", ref, listOf(0.0, 300.0)),
                viaConRef(2, "residential", ref, listOf(300.0, 600.0)),
                lateral(9, "residential", p(0.0, 300.0), p(100.0, 300.0)),
            )
        assertTrue(avisosDelNodo("stop", vias("Ma-10")).none { it == TipoAviso.STOP })
        assertTrue(TipoAviso.STOP in avisosDelNodo("stop", vias(null)))
    }

    @Test
    fun `un stop sin cruce se avisa siempre`() {
        val vias = listOf(viaConRef(1, "secondary", "Ma-1", listOf(0.0, 600.0)))
        assertTrue(TipoAviso.STOP in avisosDelNodo("stop", vias))
    }

    // --- Rasantes junto a puentes y tuneles --------------------------------------------------------------------------

    /** Vias 0-200 (rampa del 8 %), 200-400 con [central] y 400-600; la central sube hasta el metro [cimaM] y baja. */
    private fun contextoConCima(
        central: EtiquetasVia?,
        cimaM: Double,
    ): ContextoVias {
        val vias = listOf(via(1, 0.0, 200.0), via(2, 200.0, 400.0), via(3, 400.0, 600.0))
        val rampa = PerfilVia(5.0, (0..40).map { 100.0 + 0.4 * it })
        val k0 = (cimaM / 5.0).toInt()
        val cima = PerfilVia(5.0, (0..40).map { 116.0 + 0.4 * (if (it <= k0) it else 2 * k0 - it) })
        val etiquetas = if (central == null) emptyMap() else mapOf(2L to central)
        return contexto(vias, etiquetas, perfiles = mapOf(1L to rampa, 2L to cima))
    }

    private fun rasantes(r: AvisosRuta) = r.avisos.filter { it.tipo == TipoAviso.RASANTE }

    @Test
    fun `una cresta junto a la entrada de un puente o tunel se descarta`() {
        assertEquals(1, rasantes(extraer(contextoConCima(null, 25.0))).size)
        assertTrue(rasantes(extraer(contextoConCima(etiquetas(bridge = "yes"), 25.0))).isEmpty())
        assertTrue(rasantes(extraer(contextoConCima(etiquetas(tunnel = "yes"), 25.0))).isEmpty())
    }

    @Test
    fun `una cresta junto a la salida de un puente se descarta`() {
        assertTrue(rasantes(extraer(contextoConCima(etiquetas(bridge = "yes"), 175.0))).isEmpty())
    }

    @Test
    fun `una cresta en mitad de un puente se conserva`() {
        val r = rasantes(extraer(contextoConCima(etiquetas(bridge = "yes"), 100.0)))
        assertEquals(1, r.size)
        assertEquals(300.0, r.single().posicionM, 10.0)
    }
}
