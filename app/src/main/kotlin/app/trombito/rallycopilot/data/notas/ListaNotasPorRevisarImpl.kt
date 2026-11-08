package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.domain.edicion.EditorTramo
import app.trombito.rallycopilot.domain.edicion.ListaNotasPorRevisar
import app.trombito.rallycopilot.domain.edicion.NotaPorRevisar
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.motor.edicion.Marca
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.abs

/**
 * Las marcas `nota-mal` de un tramo con el texto de su nota: la hoja del tramo (con las ediciones aplicadas) solo se
 * genera si hay alguna marca, y el texto es el del elemento a menos de [TOLERANCIA_M] de la marca.
 */
class ListaNotasPorRevisarImpl(
    private val marcas: RepositorioMarcas,
    private val editor: EditorTramo,
) : ListaNotasPorRevisar {
    override suspend fun de(tramoId: String): List<NotaPorRevisar> {
        val malas = marcas.deTramo(tramoId).map { it.marca }.filter { it.etiqueta == Marca.ETIQUETA_NOTA_MAL }
        if (malas.isEmpty()) return emptyList()
        val editable = editor.observar(tramoId).firstOrNull()
        val hoja = editable?.hoja?.elementos.orEmpty()
        return malas.sortedBy { it.posicionM }.map { marca ->
            val cercano =
                hoja
                    .filter { abs(it.posicionM - marca.posicionM) <= TOLERANCIA_M }
                    .minByOrNull { abs(it.posicionM - marca.posicionM) }
            NotaPorRevisar(marca.posicionM, marca.tiempoMs, cercano?.frase)
        }
    }

    companion object {
        /** Distancia máxima (m) entre la marca y la nota de la hoja para darlas por la misma. */
        const val TOLERANCIA_M = 25.0
    }
}
