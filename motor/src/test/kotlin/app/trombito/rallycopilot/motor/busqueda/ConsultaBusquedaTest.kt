package app.trombito.rallycopilot.motor.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultaBusquedaTest {
    private fun escritas(texto: String) = ConsultaBusqueda.de(texto).palabras.map { it.escrita }

    private fun alternativas(texto: String) = ConsultaBusqueda.de(texto).palabras.map { it.alternativas }

    @Test
    fun `normaliza y separa en palabras`() {
        assertEquals(listOf("hospital", "son", "espases"), escritas("  Hospital SON Espases "))
        assertEquals(listOf("cala", "or"), escritas("Cala d'Or"))
    }

    @Test
    fun `descarta las palabras vacias`() {
        assertEquals(listOf("carrer", "colom"), escritas("Carrer de la Colom"))
        assertEquals(listOf("colom"), escritas("el colom"))
        assertTrue(ConsultaBusqueda.de("de la").vacia)
        assertTrue(ConsultaBusqueda.de("  ").vacia)
    }

    @Test
    fun `expande sinonimos castellano y catalan en los dos sentidos`() {
        assertEquals(listOf(listOf("calle", "carrer"), listOf("bellver")), alternativas("calle bellver"))
        assertEquals(listOf("carrer", "calle"), alternativas("carrer").single())
        assertEquals(listOf("castillo", "castell"), alternativas("castillo").single())
        assertEquals(listOf("plaza", "placa"), alternativas("plaza").single())
        assertEquals(listOf("placa", "plaza"), alternativas("Plaça").single())
        assertEquals(listOf("collegi", "colegio"), alternativas("Col·legi").single())
        assertEquals(listOf("estacion", "estacio"), alternativas("estación").single())
        assertEquals(listOf("esglesia", "iglesia"), alternativas("església").single())
        assertEquals(listOf("platja", "playa"), alternativas("platja").single())
    }

    @Test
    fun `una palabra sin sinonimo solo se tiene a si misma`() {
        assertEquals(listOf("bellver"), alternativas("Bellver").single())
    }

    @Test
    fun `una sola palabra de categoria da modo categoria`() {
        assertEquals("hospital", ConsultaBusqueda.de("hospital").categoria)
        assertEquals("hospital", ConsultaBusqueda.de("Hospitales").categoria)
        assertEquals("gasolinera", ConsultaBusqueda.de("gasolinera").categoria)
        assertEquals("farmacia", ConsultaBusqueda.de("farmacia").categoria)
        assertEquals("supermercado", ConsultaBusqueda.de("supermercado").categoria)
        assertEquals("playa", ConsultaBusqueda.de("platja").categoria)
        assertEquals("castillo", ConsultaBusqueda.de("castell").categoria)
        assertEquals("aparcamiento", ConsultaBusqueda.de("parking").categoria)
        assertEquals("aeropuerto", ConsultaBusqueda.de("aeropuerto").categoria)
        assertEquals("hospital", ConsultaBusqueda.de("el hospital").categoria)
    }

    @Test
    fun `varias palabras o una palabra normal no son categoria`() {
        assertNull(ConsultaBusqueda.de("hospital son espases").categoria)
        assertNull(ConsultaBusqueda.de("castillo bellver").categoria)
        assertNull(ConsultaBusqueda.de("colom").categoria)
        assertNull(ConsultaBusqueda.de("calle").categoria)
        assertNull(ConsultaBusqueda.de("").categoria)
    }

    @Test
    fun `detecta cuando se pide una calle`() {
        assertTrue(ConsultaBusqueda.de("calle colom palma").pideCalle)
        assertTrue(ConsultaBusqueda.de("carrer major").pideCalle)
        assertTrue(ConsultaBusqueda.de("Avenida Argentina").pideCalle)
        assertTrue(ConsultaBusqueda.de("plaça major").pideCalle)
        assertTrue(ConsultaBusqueda.de("paseo del born").pideCalle)
        assertTrue(ConsultaBusqueda.de("camino de muro").pideCalle)
        assertTrue(ConsultaBusqueda.de("carretera valldemossa").pideCalle)
        assertFalse(ConsultaBusqueda.de("hospital son espases").pideCalle)
        assertFalse(ConsultaBusqueda.de("colom palma").pideCalle)
    }

    @Test
    fun `quita el numero de portal y el codigo postal finales`() {
        assertEquals(listOf("jaume", "iii"), escritas("Jaume III 12"))
        assertEquals(listOf("calle", "jaume", "iii"), escritas("c/ Jaume III 12"))
        assertEquals(listOf("carrer", "colom"), escritas("Carrer Colom 5-7"))
        assertEquals(listOf("carrer", "colom"), escritas("Carrer Colom 12B"))
        assertEquals(listOf("carrer", "colom"), escritas("Carrer Colom nº 5"))
        assertEquals(listOf("carrer", "colom"), escritas("Carrer Colom 5 3-B"))
        assertEquals(listOf("colom", "palma"), escritas("Colom 5 Palma"))
        assertEquals(listOf("colom", "palma"), escritas("Colom 07002 Palma"))
        assertEquals(listOf("colom", "palma"), escritas("Colom 07002 Palma de Mallorca"))
    }

    @Test
    fun `quita piso y puerta finales`() {
        assertEquals(listOf("carrer", "colom"), escritas("Carrer Colom 5 bajos"))
        assertEquals(listOf("carrer", "colom"), escritas("Carrer Colom 5 2ºA"))
        assertEquals(listOf("carrer", "colom"), escritas("Carrer Colom 5 entlo izda"))
        assertEquals(listOf("carrer", "colom"), escritas("Carrer Colom 5 pta 3"))
    }

    @Test
    fun `no quita numeros que forman parte del nombre`() {
        assertEquals(listOf("ma", "13"), escritas("Ma-13"))
        assertEquals(listOf("carretera", "ma", "1032"), escritas("carretera Ma-1032"))
        assertEquals(listOf("carrer", "31", "desembre"), escritas("Carrer 31 de Desembre"))
        assertEquals(listOf("carrer", "5"), escritas("Carrer 5"))
        assertEquals(listOf("carrer", "principal"), escritas("Carrer Principal"))
        assertEquals(listOf("07002"), escritas("07002"))
    }

    @Test
    fun `expande abreviaturas de via`() {
        assertEquals(listOf("calle", "colom"), escritas("c/Colom"))
        assertEquals(listOf("calle", "colom"), escritas("C. Colom"))
        assertEquals(listOf("calle", "colom"), escritas("cl Colom"))
        assertEquals(listOf("calle", "colom"), escritas("cr Colom"))
        assertEquals(listOf("avenida", "argentina"), escritas("avda. Argentina"))
        assertEquals(listOf("avenida", "argentina"), escritas("av Argentina"))
        assertEquals(listOf("avenida", "argentina"), escritas("avd Argentina"))
        assertEquals(listOf("plaza", "major"), escritas("pza Major"))
        assertEquals(listOf("plaza", "major"), escritas("pl. Major"))
        assertEquals(listOf("plaza", "major"), escritas("pç Major"))
        assertEquals(listOf("carretera", "valldemossa"), escritas("ctra Valldemossa"))
        assertEquals(listOf("carretera", "valldemossa"), escritas("cra. Valldemossa"))
        assertEquals(listOf("paseo", "born"), escritas("pso Born"))
        assertEquals(listOf("paseo", "born"), escritas("pg Born"))
        assertEquals(listOf("camino", "muro"), escritas("cno Muro"))
        assertEquals(listOf("camino", "muro"), escritas("cmno Muro"))
    }

    @Test
    fun `una abreviatura de via cuenta como pedir calle y mantiene sinonimos`() {
        val c = ConsultaBusqueda.de("avda Argentina")

        assertTrue(c.pideCalle)
        assertEquals(listOf("avenida", "avinguda"), c.palabras.first().alternativas)
        assertTrue(ConsultaBusqueda.de("c/Colom").pideCalle)
    }

    @Test
    fun `equivalencias de nombre propio castellano y catalan en los dos sentidos`() {
        assertEquals(listOf("jaime", "jaume"), alternativas("Jaime").single())
        assertEquals(listOf("jaume", "jaime"), alternativas("Jaume").single())
        assertEquals(listOf("colon", "colom"), alternativas("Colón").single())
        assertEquals(listOf("colom", "colon"), alternativas("Colom").single())
        for ((a, b) in PARES_NOMBRES) {
            assertTrue("$a -> $b", b in alternativas(a).single())
            assertTrue("$b -> $a", a in alternativas(b).single())
        }
        assertTrue("san" in alternativas("sant").single())
        assertTrue("santa" in alternativas("sant").single())
        assertTrue("sant" in alternativas("san").single())
    }

    @Test
    fun `intentos sin comas es un solo intento`() {
        val intentos = ConsultaBusqueda.intentos("c/ Jaume III 12")

        assertEquals(1, intentos.size)
        assertEquals(listOf("calle", "jaume", "iii"), intentos.single().palabras.map { it.escrita })
        assertEquals(ConsultaBusqueda.de("c/ Jaume III 12"), intentos.single())
    }

    @Test
    fun `intentos de una direccion son calle y municipio y despues solo la calle`() {
        val texto = "Carrer de Ramon Berenguer III, 5, Bajos, Norte, 07002 Palma, Balearic Islands"
        val intentos = ConsultaBusqueda.intentos(texto)

        assertEquals(
            listOf(
                listOf("carrer", "ramon", "berenguer", "iii", "palma"),
                listOf("carrer", "ramon", "berenguer", "iii"),
            ),
            intentos.map { c -> c.palabras.map { it.escrita } },
        )
        assertEquals(intentos.first(), ConsultaBusqueda.de(texto))
    }

    @Test
    fun `intentos de una direccion sin municipio es solo la calle`() {
        val intentos = ConsultaBusqueda.intentos("Carrer Colom, 5, Balearic Islands")

        assertEquals(listOf(listOf("carrer", "colom")), intentos.map { c -> c.palabras.map { it.escrita } })
    }

    @Test
    fun `intentos de texto vacio es una consulta vacia`() {
        assertTrue(ConsultaBusqueda.intentos("  ").single().vacia)
        assertTrue(ConsultaBusqueda.intentos(", Palma").all { !it.vacia })
    }

    @Test
    fun `sinPalabra quita una palabra y recalcula`() {
        val c = ConsultaBusqueda.de("calle ramon berenguer iii")

        val sin = c.sinPalabra(0)

        assertEquals(listOf("ramon", "berenguer", "iii"), sin.palabras.map { it.escrita })
        assertFalse(sin.pideCalle)
        assertNull(sin.categoria)
        assertEquals(listOf("calle", "berenguer", "iii"), c.sinPalabra(1).palabras.map { it.escrita })
        assertTrue(c.sinPalabra(1).pideCalle)
    }

    private companion object {
        val PARES_NOMBRES =
            listOf(
                "jaume" to "jaime",
                "colom" to "colon",
                "joan" to "juan",
                "miquel" to "miguel",
                "pere" to "pedro",
                "antoni" to "antonio",
                "josep" to "jose",
                "francesc" to "francisco",
                "rei" to "rey",
                "bartomeu" to "bartolome",
                "jordi" to "jorge",
                "llorenc" to "lorenzo",
                "mateu" to "mateo",
                "andreu" to "andres",
                "nicolau" to "nicolas",
                "sebastia" to "sebastian",
                "pau" to "pablo",
                "esteve" to "esteban",
                "maria" to "mariano",
                "catalina" to "caterina",
            )
    }
}
