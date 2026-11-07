package app.trombito.rallycopilot.data.tramos

import android.net.Uri
import app.trombito.rallycopilot.data.escucha.EdicionesFalsas
import app.trombito.rallycopilot.data.importar.AbridorDocumentos
import app.trombito.rallycopilot.data.importar.DocumentoAbierto
import app.trombito.rallycopilot.data.importar.ImportadorTramos
import app.trombito.rallycopilot.domain.edicion.EditorTramo
import app.trombito.rallycopilot.domain.edicion.TramoEditable
import app.trombito.rallycopilot.domain.importar.ResultadoImportar
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.edicion.NotasEditadas
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.resumenFalso
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

/** Ida y vuelta: exportar un tramo con sus notas y volver a importar el archivo recupera tramo, cortes y notas. */
@RunWith(RobolectricTestRunner::class)
class ExportarImportarTramoTest {
    /** Unos 4,5 km con quiebros de menos de 5 m: una simplificación los comería, así que prueba que no se aplica. */
    private val traza = List(41) { PuntoGeo(39.6 + it * 0.001, 2.9 + (it % 3) * 0.00003, 50.0 + it) }
    private val tramo =
        Tramo(
            "origen",
            traza,
            traza.longitudMetros(),
            sectores = listOf(1_000.0, 2_500.0),
            microsectores = listOf(1_500.0, 3_000.0),
        )
    private val curva = Nota(300.0, 340.0, Direccion.DERECHA, 3, setOf(Modificador.LARGA))
    private val hoja =
        HojaNotas(listOf(ElementoHoja.DeNota(curva), ElementoHoja.DeAviso(Aviso(1_200.0, TipoAviso.PUENTE))))

    // El códec de ediciones no guarda las posiciones de la nota (las da la edición): quedan en 0 y el largo.
    private val curvaEditada = curva.copy(posicionM = 0.0, finM = 40.0)

    private val editadas =
        listOf(
            NotasEditadas(
                "origen",
                300.0,
                AccionEdicion.Sustituir(curvaEditada.copy(grado = 2, cuidado = true)),
                1_000L,
            ),
            NotasEditadas("origen", 900.0, AccionEdicion.Quitar, 2_000L),
            NotasEditadas("origen", 2_000.0, AccionEdicion.Anadir(curvaEditada.copy(grado = null)), 3_000L),
            NotasEditadas("origen", 3_000.0, AccionEdicion.Cuidado, 4_000L),
        )

    private val origen =
        FalsoRepositorioTramos(
            resumenes = listOf(resumenFalso("origen", "Palma → Valldemossa")),
            trazas = mapOf("origen" to tramo),
        )

    private val edicionesOrigen = EdicionesFalsas().also { f -> runBlocking { editadas.forEach { f.guardar(it) } } }

    private val editor =
        object : EditorTramo {
            override fun observar(tramoId: String): Flow<TramoEditable?> =
                MutableStateFlow(TramoEditable(tramo, hoja, emptyList(), editadas.size))

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

    private fun exportar() = runBlocking { ExportadorTramo(origen, editor, edicionesOrigen).exportar("origen") }

    private val destino = FalsoRepositorioTramos()
    private val edicionesDestino = EdicionesFalsas()

    private fun importar(contenido: ByteArray): ResultadoImportar {
        val abridor =
            AbridorDocumentos { _: Uri ->
                DocumentoAbierto(ByteArrayInputStream(contenido), "palma.gpx", contenido.size.toLong())
            }
        val importador =
            ImportadorTramos(
                abridor,
                GuardadorTramos(destino) { null },
                GuardadorTramoArchivo(destino, { "Ma-13 · Palma" }, edicionesDestino),
            )
        return runBlocking { importador.importar("content://docs/exportado") }
    }

    @Test
    fun `el archivo exportado lleva el nombre del tramo, su traza y sus notas como waypoints`() {
        val exportado = checkNotNull(exportar())

        assertEquals("palma-valldemossa.gpx", exportado.nombreArchivo)
        assertTrue("<name>Palma → Valldemossa</name>" in exportado.contenido)
        assertTrue("<name>${ElementoHoja.DeNota(curva).frase}</name>" in exportado.contenido)
        assertTrue("<name>puente</name>" in exportado.contenido)
        assertEquals(41, Regex("<trkpt ").findAll(exportado.contenido).count())
    }

    @Test
    fun `importar el archivo exportado recupera la traza, el nombre y los cortes tal cual`() {
        val r = importar(checkNotNull(exportar()).contenido.toByteArray())

        assertTrue(r.toString(), r is ResultadoImportar.Importado)
        val guardado = destino.sinPasadas.single()
        assertEquals("Palma → Valldemossa", guardado.nombre)
        assertEquals(traza, guardado.tramo.traza)
        assertEquals(tramo.sectores, guardado.tramo.sectores)
        assertEquals(tramo.microsectores, guardado.tramo.microsectores)
        assertEquals(tramo.longitudM, guardado.tramo.longitudM, 1e-6)
        assertNotEquals("Es un tramo nuevo, no el mismo id", "origen", guardado.tramo.id)
        assertEquals((r as ResultadoImportar.Importado).tramoId, guardado.tramo.id)
        assertTrue(destino.pasadasPorTramo[guardado.tramo.id].isNullOrEmpty())
    }

    @Test
    fun `importar el archivo exportado recupera las notas editadas ligadas al tramo nuevo`() {
        importar(checkNotNull(exportar()).contenido.toByteArray())

        val nuevoId =
            destino.sinPasadas
                .single()
                .tramo.id
        assertEquals(editadas.map { it.copy(tramoId = nuevoId) }, edicionesDestino.guardadas)
    }

    @Test
    fun `las ediciones fuera del tramo se descartan y unos cortes invalidos dejan los de por defecto`() {
        val manipulado =
            checkNotNull(exportar())
                .contenido
                .replace("<rc:sectorRally m=\"2500.0\"/>", "<rc:sectorRally m=\"99999.0\"/>")
                .replace("</rc:tramoRally>", "<rc:edicionRally m=\"99999.0\" t=\"5\" tipo=\"QUITAR\"/></rc:tramoRally>")
        importar(manipulado.toByteArray())

        val guardado = destino.sinPasadas.single().tramo
        assertEquals(Tramo.sectoresPorDefecto(guardado.longitudM), guardado.sectores)
        assertEquals(editadas.size, edicionesDestino.guardadas.size)
    }

    @Test
    fun `si la base falla al guardar las ediciones no queda un tramo a medias`() {
        val rota =
            object : app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones by edicionesDestino {
                override suspend fun guardar(edicion: NotasEditadas) = error("base caída")
            }
        val contenido = checkNotNull(exportar()).contenido.toByteArray()
        val abridor = AbridorDocumentos { _: Uri -> DocumentoAbierto(ByteArrayInputStream(contenido), "a.gpx", null) }
        val importador =
            ImportadorTramos(abridor, GuardadorTramos(destino) { null }, GuardadorTramoArchivo(destino, { null }, rota))

        val r = runBlocking { importador.importar("content://docs/x") }

        assertTrue(r.toString(), r is ResultadoImportar.Fallo)
        assertEquals(
            listOf(
                destino.sinPasadas
                    .single()
                    .tramo.id,
            ),
            destino.borrados,
        )
    }

    @Test
    fun `un GPX de otra app sigue entrando por el camino de siempre y sin ediciones`() {
        val strava = checkNotNull(javaClass.getResourceAsStream("/importar/strava.gpx")).use { it.readBytes() }
        val r = importar(strava)

        assertTrue(r.toString(), r is ResultadoImportar.Importado)
        assertNotNull(destino.sinPasadas.single())
        assertTrue(edicionesDestino.guardadas.isEmpty())
        assertNull(edicionesDestino.guardadas.firstOrNull())
    }
}
