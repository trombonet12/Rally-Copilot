package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.LectorVias
import app.trombito.rallycopilot.motor.vias.LectorViasNotas
import java.util.zip.GZIPInputStream

/** Recorte real de la Serra de Tramuntana (`src/test/resources/vias/`) cargado una sola vez para todos los tests. */
object FixturesVias {
    val contexto: ContextoVias by lazy {
        val vias = LectorVias.leer(lineas("/vias/vias-serra.tsv.gz")).vias
        ContextoVias(IndiceVias(vias), GrafoVias(vias), LectorViasNotas.leer(lineas("/vias/vias-notas-serra.tsv.gz")))
    }

    private fun lineas(recurso: String): Sequence<String> {
        val flujo = checkNotNull(FixturesVias::class.java.getResourceAsStream(recurso)) { "Falta $recurso" }
        return GZIPInputStream(flujo).bufferedReader(Charsets.UTF_8).readLines().asSequence()
    }
}
