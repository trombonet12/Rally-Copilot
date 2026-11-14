package app.trombito.rallycopilot.data.busqueda

import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Criterios de H5B contra el índice REAL (`busqueda-mallorca.sqlite` de la release fijada en
 * `app/datos-incluidos.properties`), que el build baja a `.descargas/`. Sin ese archivo el test se salta en local y
 * falla en CI (variable `CI`).
 */
@RunWith(RobolectricTestRunner::class)
class BuscadorIndiceRealTest {
    private lateinit var buscador: BuscadorSqlite

    @Before
    fun preparar() {
        val indice =
            File("../.descargas")
                .listFiles { f -> f.name.endsWith("-busqueda-mallorca.sqlite") }
                ?.maxByOrNull { it.lastModified() }
        // En CI un índice ausente es un fallo (el gate no puede pasar con estos criterios saltados); en local se salta.
        if (System.getenv("CI") != null) {
            assertNotNull("Sin índice real en .descargas/ (en CI es obligatorio)", indice)
        } else {
            assumeTrue("Sin índice real en .descargas/", indice != null)
        }
        buscador = BuscadorSqlite(checkNotNull(indice), Dispatchers.Unconfined)
    }

    private fun buscar(
        texto: String,
        cerca: PuntoGeo? = null,
    ): List<ResultadoBusqueda> = runBlocking { buscador.buscar(texto, cerca, LIMITE) }

    private fun List<ResultadoBusqueda>.entreLosPrimeros(
        n: Int,
        condicion: (ResultadoBusqueda) -> Boolean,
    ): Boolean = take(n).any(condicion)

    @Test
    fun `hospital son espases`() {
        val r = buscar("hospital son espases", PALMA)
        assertTrue(r.joinToString { it.nombre }, r.entreLosPrimeros(3) { "espases" in it.nombre.lowercase() })
    }

    @Test
    fun `castillo bellver`() {
        val r = buscar("castillo bellver", PALMA)
        assertTrue(r.joinToString { it.nombre }, r.entreLosPrimeros(3) { "bellver" in it.nombre.lowercase() })
    }

    @Test
    fun `calle colom palma`() {
        val r = buscar("calle colom palma", PALMA)
        assertTrue(
            r.joinToString { "${it.nombre} (${it.categoria})" },
            r.entreLosPrimeros(3) { it.categoria == "calle" && "colom" in it.nombre.lowercase() },
        )
    }

    @Test
    fun `hospital desde palma da hospitales cercanos`() {
        val r = buscar("hospital", PALMA)
        assertTrue(r.joinToString { "${it.nombre} (${it.categoria})" }, r.take(3).all { it.categoria == "hospital" })
    }

    private fun List<ResultadoBusqueda>.descripcion() =
        joinToString { "${it.nombre} (${it.categoria}, ${it.municipio})" }

    private fun List<ResultadoBusqueda>.hayCalle(
        palabra: String,
        municipio: String? = "Palma",
        primeros: Int = 3,
    ) = entreLosPrimeros(primeros) {
        it.categoria == "calle" && palabra in it.nombre.lowercase() && (municipio == null || it.municipio == municipio)
    }

    @Test
    fun `direccion pegada de google encuentra la calle`() {
        val r = buscar("Carrer de Ramon Berenguer III, 5, Bajos, Norte, 07002 Palma, Balearic Islands", PALMA)
        assertTrue(r.descripcion(), r.firstOrNull()?.nombre == "Carrer de Ramon Berenguer III")
    }

    @Test
    fun `calle con numero de portal sin comas`() {
        val r = buscar("c/ Jaume III 12", PALMA)
        // Hay «Carrer de Jaume III» en varios pueblos (en Palma es una avenida): vale cualquiera, pero sin el «12».
        assertTrue(r.descripcion(), r.take(3).all { it.categoria == "calle" && "jaume iii" in it.nombre.lowercase() })
    }

    @Test
    fun `calle con nombre castellano`() {
        val r = buscar("calle Jaime III", PALMA)
        assertTrue(r.descripcion(), r.take(3).all { it.categoria == "calle" && "jaume iii" in it.nombre.lowercase() })
    }

    @Test
    fun `jaume iii con portal y municipio es la avenida de palma`() {
        val r = buscar("Jaume III 12 Palma", PALMA)
        assertTrue(r.descripcion(), r.hayCalle("jaume iii", "Palma", primeros = LIMITE))
    }

    @Test
    fun `avenida abreviada con municipio`() {
        val r = buscar("avda Argentina Palma", PALMA)
        assertTrue(r.descripcion(), r.hayCalle("argentina"))
    }

    @Test
    fun `colon palma encuentra la calle colom`() {
        val r = buscar("Colón Palma", PALMA)
        assertTrue(r.descripcion(), r.hayCalle("colom", primeros = LIMITE))
    }

    @Test
    fun `c barra colom pegado`() {
        val r = buscar("C/Colom", PALMA)
        assertTrue(r.descripcion(), r.hayCalle("colom", primeros = LIMITE))
    }

    @Test
    fun `una palabra de mas se relaja`() {
        val r = buscar("calle Ramon Berenguer III Norte", PALMA)
        assertTrue(r.descripcion(), r.firstOrNull()?.nombre == "Carrer de Ramon Berenguer III")
    }

    private companion object {
        const val LIMITE = 10
        val PALMA = PuntoGeo(39.5696, 2.6502)
    }
}
