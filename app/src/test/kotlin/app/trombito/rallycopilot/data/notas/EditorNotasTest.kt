package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.data.escucha.EdicionesFalsas
import app.trombito.rallycopilot.domain.escucha.MarcaConPosicion
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.tramos.TramoCronometrado
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorNotasTest {
    private val traza = recta(0.0, 0.0, haciaEste = true, largoM = 1_000.0)
    private val tramo = Tramo("t1", traza, 1_000.0)
    private val tramos = FalsoRepositorioTramos(trazas = mapOf("t1" to tramo))

    private fun hojaCon(vararg curvas: Pair<Double, Int>) =
        HojaNotas(curvas.map { (m, g) -> ElementoHoja.DeNota(Nota(m, m + 30.0, Direccion.DERECHA, g)) })

    private class Marcas(
        val lista: List<MarcaConPosicion> = emptyList(),
    ) : RepositorioMarcas {
        override suspend fun guardar(
            marca: Marca,
            posicion: PuntoGeo?,
        ) = Unit

        override suspend fun deTramo(tramoId: String) = lista
    }

    @Test
    fun `una nota editada del tramo sobrevive a regenerar las automaticas`() =
        runBlocking {
            val ediciones = EdicionesFalsas()
            var generacion = 0
            val generador =
                GeneradorHojaTramo {
                    // La segunda vez las automáticas salen desplazadas 8 m y con otro grado.
                    if (++generacion == 1) hojaCon(300.0 to 4, 700.0 to 5) else hojaCon(308.0 to 2, 708.0 to 5)
                }
            val editor = EditorTramoImpl(tramos, ediciones, Marcas(), generador) { 99L }

            editor.editar("t1", 310.0, AccionEdicion.Sustituir(Nota(0.0, 0.0, Direccion.IZQUIERDA, 1)))
            val primera = checkNotNull(editor.observar("t1").first())
            val segunda = checkNotNull(editor.observar("t1").first())

            for (visto in listOf(primera, segunda)) {
                val nota = visto.hoja.notas.first()
                assertEquals(Direccion.IZQUIERDA, nota.direccion)
                assertEquals(1, nota.grado)
                assertEquals(2, visto.hoja.notas.size)
                assertEquals(1, visto.editadas)
            }
            assertEquals(2, generacion)
            editor.restaurar("t1")
            assertEquals(
                Direccion.DERECHA,
                editor
                    .observar("t1")
                    .first()
                    ?.hoja
                    ?.notas
                    ?.first()
                    ?.direccion,
            )
        }

    @Test
    fun `un tramo que no existe no es editable y las marcas salen en el detalle`() =
        runBlocking {
            val marca = MarcaConPosicion(Marca(120.0, 5L, "t1"), PuntoGeo(39.6, 2.9))
            val editor =
                EditorTramoImpl(tramos, EdicionesFalsas(), Marcas(listOf(marca)), { hojaCon() }) { 1L }

            assertNull(editor.observar("otro").first())
            assertEquals(listOf(marca), editor.observar("t1").first()?.marcas)
        }

    private fun enCurso(tramoReconocimiento: String? = null) =
        NotasEnCurso(
            hojaCon(),
            posicionM = 100.0,
            vMs = 0.0,
            origen = OrigenNotas.LIBRE,
            puntoEn = { p(it, 0.0) },
            tramoReconocimiento = tramoReconocimiento,
        )

    private fun crono(tramoId: String?) =
        object : ProveedorCrono {
            override val crono =
                MutableStateFlow<CronoEnCurso?>(
                    CronoEnCurso(
                        tramoId?.let { TramoCronometrado(it, null, 1_000.0, 990.0, emptyList(), emptyList()) },
                        0L,
                        0L,
                    ),
                )
        }

    private fun notas(valor: NotasEnCurso?) =
        object : ProveedorNotas {
            override val notas = MutableStateFlow(valor)
        }

    @Test
    fun `desde el mapa con tramo activo la edicion se ancla a los metros del tramo`() =
        runBlocking {
            val ediciones = EdicionesFalsas()
            val enVivo = EdicionNotasEnVivoImpl(notas(enCurso()), crono("t1"), tramos, ediciones) { 7L }

            assertTrue(enVivo.editar(450.0, AccionEdicion.Quitar))

            val guardada = ediciones.guardadas.single()
            assertEquals("t1", guardada.tramoId)
            assertEquals(450.0, guardada.posicionM, 3.0)
            assertTrue(ediciones.sinTramo.isEmpty())
        }

    @Test
    fun `desde el mapa en reconocimiento usa el tramo reconocido aunque no haya crono`() =
        runBlocking {
            val ediciones = EdicionesFalsas()
            val enVivo = EdicionNotasEnVivoImpl(notas(enCurso("t1")), crono(null), tramos, ediciones) { 7L }

            enVivo.editar(200.0, AccionEdicion.Cuidado)

            assertEquals("t1", ediciones.guardadas.single().tramoId)
        }

    @Test
    fun `desde el mapa sin tramo la edicion queda pendiente con su coordenada y sin notas no hace nada`() =
        runBlocking {
            val ediciones = EdicionesFalsas()
            val enVivo = EdicionNotasEnVivoImpl(notas(enCurso()), crono(null), tramos, ediciones) { 7L }

            enVivo.editar(300.0, AccionEdicion.Quitar)

            assertTrue(ediciones.guardadas.isEmpty())
            assertEquals(300.0, ediciones.sinTramo.single().posicionM, 0.0)
            assertTrue(ediciones.sinTramo.single().posicion != null)
            val sinNotas = EdicionNotasEnVivoImpl(notas(null), crono(null), tramos, ediciones) { 7L }
            assertFalse(sinNotas.editar(1.0, AccionEdicion.Quitar))
        }
}
