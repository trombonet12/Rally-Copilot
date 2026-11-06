package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.escucha.ResultadoEscucha
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.OrdenParadas
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.edicion.TipoLugar
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.voz.RespuestasVoz
import app.trombito.rallycopilot.ui.navegacion.PALMA
import app.trombito.rallycopilot.ui.navegacion.SOLLER
import app.trombito.rallycopilot.ui.navegacion.rutaDePrueba
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las órdenes de navegación por voz de H14d: ir a un lugar, casa, paradas, cancelar la ruta y lo más cercano. */
class NavegacionPorVozTest {
    private val banco = BancoOrdenes(CoroutineScope(Dispatchers.Default))
    private val inca = PuntoGeo(39.72, 2.91)

    private val soller = ResultadoBusqueda("Sóller", "pueblo", "Sóller", SOLLER)
    private val portSoller = ResultadoBusqueda("Port de Sóller", "aldea", "Sóller", PuntoGeo(39.795, 2.695))

    init {
        banco.control.publicar(EstadoNavegacion.Libre(0L, 0.0))
        banco.enrutador.respuestas[PerfilRuta.MAS_RAPIDA] = ResultadoRuta.Ok(rutaDePrueba(tiempoS = 1_500.0), 5L)
        banco.buscador.resultados = listOf(soller, portSoller)
    }

    private suspend fun decir(orden: Orden) = banco.ejecutor.ejecutar(ResultadoEscucha.Entendido(orden, "texto"))

    private fun dichas() = banco.locutor.dichas.toList()

    private fun guiando(paradas: List<Destino> = emptyList()) =
        banco.control.publicar(
            EstadoNavegacion.Guiando(
                Destino("Palma", PALMA),
                PerfilRuta.MAS_RAPIDA,
                rutaDePrueba(),
                progreso = null,
                recalculando = false,
                paradas = paradas,
            ),
        )

    // --- Llévame a ------------------------------------------------------------------------------------------------

    @Test
    fun `llevame a un lugar arranca el guiado al primer resultado y lo dice con el tiempo`() =
        runBlocking {
            decir(Orden.IrA("soller"))

            assertEquals(listOf("llevándote a Sóller, veinticinco minutos"), dichas())
            assertEquals(
                Destino("Sóller", SOLLER),
                banco.control.empezadas
                    .single()
                    .first,
            )
            assertEquals(listOf("soller" to PuntoGeo(39.7, 2.9)), banco.buscador.consultas)
        }

    @Test
    fun `un lugar que no existe se dice y no se arranca nada`() =
        runBlocking {
            banco.buscador.resultados = emptyList()

            decir(Orden.IrA("xyz"))

            assertEquals(listOf("no encuentro xyz"), dichas())
            assertTrue(banco.control.empezadas.isEmpty())
        }

    @Test
    fun `sin posicion no busca ni arranca`() =
        runBlocking {
            banco.ubicacion.fijacion = null

            decir(Orden.IrA("soller"))

            assertEquals(listOf(RespuestasVoz.SIN_POSICION), dichas())
            assertTrue(banco.buscador.consultas.isEmpty())
        }

    @Test
    fun `sin sesion de conduccion dice que no hay conduccion`() =
        runBlocking {
            banco.control.terminar()

            decir(Orden.IrA("soller"))

            assertEquals(listOf("no hay conducción"), dichas())
        }

    @Test
    fun `guiando cambia el destino en vez de empezar otra ruta`() =
        runBlocking {
            guiando()

            decir(Orden.IrA("soller"))

            assertEquals(listOf(OrdenParadas.CambiarDestino(Destino("Sóller", SOLLER))), banco.control.ordenes)
            assertTrue(banco.control.empezadas.isEmpty())
            assertEquals(listOf("llevándote a Sóller, veinticinco minutos"), dichas())
        }

    // --- Otro -----------------------------------------------------------------------------------------------------

    @Test
    fun `otro dentro de diez segundos pasa al siguiente resultado`() =
        runBlocking {
            decir(Orden.IrA("soller"))
            banco.ahoraMs += 9_000L

            decir(Orden.Otro)

            assertEquals("llevándote a Port de Sóller, veinticinco minutos", dichas().last())
            assertEquals(
                OrdenParadas.CambiarDestino(Destino("Port de Sóller", portSoller.punto)),
                banco.control.ordenes.last(),
            )
        }

    @Test
    fun `otro pasado el plazo, sin busqueda o sin mas resultados no hay otra opcion`() =
        runBlocking {
            decir(Orden.Otro)
            decir(Orden.IrA("soller"))
            banco.ahoraMs += 11_000L
            decir(Orden.Otro)
            decir(Orden.IrA("soller"))
            decir(Orden.Otro)
            decir(Orden.Otro)

            assertEquals(
                listOf(
                    RespuestasVoz.SIN_OTRA_OPCION,
                    "llevándote a Sóller, veinticinco minutos",
                    RespuestasVoz.SIN_OTRA_OPCION,
                    "llevándote a Sóller, veinticinco minutos",
                    "llevándote a Port de Sóller, veinticinco minutos",
                    RespuestasVoz.SIN_OTRA_OPCION,
                ),
                dichas(),
            )
        }

    // --- Casa -----------------------------------------------------------------------------------------------------

    @Test
    fun `volver a casa sin casa dice como fijarla`() =
        runBlocking {
            decir(Orden.VolverACasa)

            assertEquals(listOf(RespuestasVoz.SIN_CASA), dichas())
            assertTrue(banco.control.empezadas.isEmpty())
        }

    @Test
    fun `volver a casa lleva al punto guardado`() =
        runBlocking {
            banco.ajustes.fijarCasa(inca)

            decir(Orden.VolverACasa)

            assertEquals(listOf("llevándote a casa, veinticinco minutos"), dichas())
            assertEquals(
                Destino("casa", inca),
                banco.control.empezadas
                    .single()
                    .first,
            )
            assertEquals(inca, banco.ajustes.mutable.value.casa)
        }

    // --- Cancelar la ruta -----------------------------------------------------------------------------------------

    @Test
    fun `cancelar la ruta pide confirmacion y con si vuelve al modo libre`() =
        runBlocking {
            guiando()

            decir(Orden.CancelarRuta)
            assertEquals(listOf("di sí para cancelar la ruta"), dichas())
            assertTrue(banco.ejecutor.esperaConfirmacion)
            assertEquals(0, banco.control.libres)

            decir(Orden.Confirma(true))
            assertEquals("ruta cancelada", dichas().last())
            assertEquals(1, banco.control.libres)
            assertFalse(banco.ejecutor.esperaConfirmacion)
        }

    @Test
    fun `cancelar la ruta con no la deja como estaba`() =
        runBlocking {
            guiando()

            decir(Orden.CancelarRuta)
            decir(Orden.Confirma(false))

            assertEquals("cancelado", dichas().last())
            assertEquals(0, banco.control.libres)
            assertFalse(banco.ejecutor.esperaConfirmacion)
        }

    @Test
    fun `sin ruta no hay nada que cancelar`() =
        runBlocking {
            decir(Orden.CancelarRuta)

            assertEquals(listOf(RespuestasVoz.SIN_RUTA), dichas())
            assertFalse(banco.ejecutor.esperaConfirmacion)
        }

    @Test
    fun `otra orden en vez de la respuesta anula la pregunta y un si suelto no hace nada`() =
        runBlocking {
            guiando()

            decir(Orden.CancelarRuta)
            decir(Orden.Repite)
            decir(Orden.Confirma(true))

            assertEquals(0, banco.control.libres)
            assertFalse(banco.ejecutor.esperaConfirmacion)
        }

    // --- Lo más cercano -------------------------------------------------------------------------------------------

    @Test
    fun `la gasolinera mas cercana dice distancia y municipio y con si arranca la ruta`() =
        runBlocking {
            val repsol = ResultadoBusqueda("Repsol", "gasolinera", "Sóller", SOLLER)
            banco.buscador.resultados = listOf(repsol)
            val distancia = PuntoGeo(39.7, 2.9).distanciaA(SOLLER)

            decir(Orden.MasCercano(TipoLugar.GASOLINERA))
            assertEquals(listOf(RespuestasVoz.masCercano(TipoLugar.GASOLINERA, distancia, "Sóller")), dichas())
            assertEquals(listOf("gasolinera" to PuntoGeo(39.7, 2.9)), banco.buscador.consultas)
            assertTrue(banco.ejecutor.esperaConfirmacion)
            assertTrue(banco.control.empezadas.isEmpty())

            decir(Orden.Confirma(true))
            assertEquals("llevándote a Repsol, veinticinco minutos", dichas().last())
            assertEquals(
                Destino("Repsol", SOLLER),
                banco.control.empezadas
                    .single()
                    .first,
            )
        }

    @Test
    fun `con no no se arranca nada y sin ninguno de la categoria se dice`() =
        runBlocking {
            banco.buscador.resultados = listOf(ResultadoBusqueda("Repsol", "gasolinera", "Sóller", SOLLER))
            decir(Orden.MasCercano(TipoLugar.GASOLINERA))
            decir(Orden.Confirma(false))

            assertEquals("cancelado", dichas().last())
            assertTrue(banco.control.empezadas.isEmpty())

            banco.buscador.resultados = listOf(ResultadoBusqueda("Carrer de la Farmàcia", "calle", "Palma", PALMA))
            decir(Orden.MasCercano(TipoLugar.FARMACIA))
            assertEquals("no encuentro ninguna farmacia cerca", dichas().last())
            assertFalse(banco.ejecutor.esperaConfirmacion)
        }

    // --- Parada ---------------------------------------------------------------------------------------------------

    @Test
    fun `anade parada a la ruta en curso`() =
        runBlocking {
            guiando()
            banco.buscador.resultados = listOf(ResultadoBusqueda("Inca", "pueblo", "Inca", inca))

            decir(Orden.ParadaEn("inca"))

            assertEquals(listOf(OrdenParadas.Anadir(Destino("Inca", inca))), banco.control.ordenes)
            assertEquals(listOf("parada añadida: Inca"), dichas())
        }

    @Test
    fun `sin guiado, con demasiadas paradas o sin el lugar no se anade`() =
        runBlocking {
            decir(Orden.ParadaEn("inca"))
            assertEquals(RespuestasVoz.SIN_RUTA_PARA_PARADA, dichas().last())

            guiando(List(18) { Destino("p$it", inca) })
            decir(Orden.ParadaEn("inca"))
            assertEquals(RespuestasVoz.DEMASIADAS_PARADAS, dichas().last())

            guiando()
            banco.buscador.resultados = emptyList()
            decir(Orden.ParadaEn("xyz"))
            assertEquals("no encuentro xyz", dichas().last())
            assertTrue(banco.control.ordenes.isEmpty())
        }

    @Test
    fun `sin voz elegida no habla pero ejecuta`() =
        runBlocking {
            val mudo = BancoOrdenes(CoroutineScope(Dispatchers.Default), inicial = AjustesConduccion(voz = null))
            mudo.control.publicar(EstadoNavegacion.Libre(0L, 0.0))
            mudo.enrutador.respuestas[PerfilRuta.MAS_RAPIDA] = ResultadoRuta.Ok(rutaDePrueba(), 5L)
            mudo.buscador.resultados = listOf(soller)

            mudo.ejecutor.ejecutar(ResultadoEscucha.Entendido(Orden.IrA("soller"), "texto"))

            assertTrue(mudo.locutor.dichas.isEmpty())
            assertEquals(1, mudo.control.empezadas.size)
        }
}
