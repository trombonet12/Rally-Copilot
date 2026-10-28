package app.trombito.rallycopilot.motor.busqueda

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/** Los 19 casos de `CASOS_NOMBRE_NORM` de `scripts/test_scripts.py` (repo de datos): deben coincidir con Python. */
@RunWith(Parameterized::class)
class NormalizadorNombreTest(
    private val entrada: String,
    private val esperado: String,
) {
    @Test
    fun `normaliza como el script de Python`() {
        assertEquals(esperado, NormalizadorNombre.normalizar(entrada), "entrada: '$entrada'")
    }

    @Test
    fun `es idempotente`() {
        assertEquals(esperado, NormalizadorNombre.normalizar(esperado))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun casos(): List<Array<String>> =
            listOf(
                arrayOf("Sóller", "soller"),
                arrayOf("Cala d'Or", "cala d or"),
                arrayOf("Cala d’Or", "cala d or"),
                arrayOf("Coll de sa Batalla", "coll de sa batalla"),
                arrayOf("Ses Salines", "ses salines"),
                arrayOf("Port d'Andratx", "port d andratx"),
                arrayOf("Sa Pobla", "sa pobla"),
                arrayOf("Col·legi", "collegi"),
                arrayOf("COL·LEGI Públic", "collegi public"),
                arrayOf("Paŀlada", "pallada"),
                arrayOf("Sant-Jordi", "sant jordi"),
                arrayOf("S'Arenal", "s arenal"),
                arrayOf("Pollença", "pollenca"),
                arrayOf("Alcúdia", "alcudia"),
                arrayOf("Caló des Moro", "calo des moro"),
                arrayOf("  Carrer   de\tla  Lluna ", "carrer de la lluna"),
                arrayOf("Ma-13", "ma 13"),
                arrayOf("Es Pil·larí", "es pillari"),
                arrayOf("", ""),
            )
    }
}
