package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.domain.edicion.EditorTramo
import app.trombito.rallycopilot.domain.edicion.NotaPorRevisar
import app.trombito.rallycopilot.domain.edicion.TramoEditable
import app.trombito.rallycopilot.domain.escucha.MarcaConPosicion
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ListaNotasPorRevisarImplTest {
    private class MarcasDeTramo(
        val marcas: List<Marca>,
    ) : RepositorioMarcas {
        override suspend fun guardar(
            marca: Marca,
            posicion: PuntoGeo?,
        ) = Unit

        override suspend fun deTramo(tramoId: String) = marcas.map { MarcaConPosicion(it, null) }
    }

    private class EditorConHoja(
        val hoja: HojaNotas,
    ) : EditorTramo {
        var observado = 0

        override fun observar(tramoId: String): Flow<TramoEditable?> {
            observado++
            val tramo = Tramo(tramoId, listOf(PuntoGeo(39.6, 2.9), PuntoGeo(39.61, 2.9)), 1_000.0)
            return flowOf(TramoEditable(tramo, hoja, emptyList(), 0))
        }

        override suspend fun editar(
            tramoId: String,
            posicionM: Double,
            accion: AccionEdicion,
        ) = Unit

        override suspend fun restaurar(tramoId: String) = Unit

        override suspend fun guardarCortes(
            tramoId: String,
            sectores: List<Double>,
            microsectores: List<Double>,
        ) = Unit
    }

    private val hoja =
        HojaNotas(
            listOf(
                ElementoHoja.DeNota(Nota(300.0, 340.0, Direccion.DERECHA, 3)),
                ElementoHoja.DeNota(Nota(800.0, 830.0, Direccion.IZQUIERDA, 4)),
            ),
        )

    private fun marca(
        posicionM: Double,
        etiqueta: String? = Marca.ETIQUETA_NOTA_MAL,
    ) = Marca(posicionM, 10L, "t1", etiqueta)

    @Test
    fun `solo cuentan las marcas de nota mal, por posicion y con el texto de la nota cercana`() =
        runBlocking {
            val editor = EditorConHoja(hoja)
            val lista =
                ListaNotasPorRevisarImpl(
                    MarcasDeTramo(
                        listOf(marca(810.0), marca(500.0, Marca.ETIQUETA_RADAR), marca(310.0), marca(5.0, null)),
                    ),
                    editor,
                )

            val revisar = lista.de("t1")

            assertEquals(listOf(310.0, 810.0), revisar.map { it.posicionM })
            assertEquals(listOf("derecha tres", "izquierda cuatro"), revisar.map { it.texto })
        }

    @Test
    fun `una marca sin nota cerca en la hoja solo trae el kilometro`() =
        runBlocking {
            val lista = ListaNotasPorRevisarImpl(MarcasDeTramo(listOf(marca(600.0))), EditorConHoja(hoja))

            assertEquals(listOf(NotaPorRevisar(600.0, 10L, null)), lista.de("t1"))
        }

    @Test
    fun `sin marcas de nota mal no genera la hoja del tramo`() =
        runBlocking {
            val editor = EditorConHoja(hoja)
            val lista = ListaNotasPorRevisarImpl(MarcasDeTramo(listOf(marca(5.0, Marca.ETIQUETA_RADAR))), editor)

            assertEquals(emptyList<NotaPorRevisar>(), lista.de("t1"))
            assertEquals(0, editor.observado)
        }
}
