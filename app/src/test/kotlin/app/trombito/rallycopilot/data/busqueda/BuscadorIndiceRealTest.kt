package app.trombito.rallycopilot.data.busqueda

import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Criterios de H5B contra el índice REAL (`busqueda-mallorca.sqlite` de la release fijada en
 * `app/datos-incluidos.properties`), que el build baja a `.descargas/`. Sin ese archivo el test se salta.
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
        assumeTrue("Sin índice real en .descargas/", indice != null)
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

    private companion object {
        const val LIMITE = 10
        val PALMA = PuntoGeo(39.5696, 2.6502)
    }
}
