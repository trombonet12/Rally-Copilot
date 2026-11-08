package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.escucha.ResultadoEscucha
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.edicion.Indicacion
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.edicion.TipoPregunta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Las órdenes de voz de la parte 1 de H14d: preguntas, control del copiloto, mapa y cronómetro, ayuda. */
class EjecutorOrdenesNuevasTest {
    private val banco = BancoOrdenes(CoroutineScope(Dispatchers.Default))

    private suspend fun BancoOrdenes.decir(orden: Orden) = ejecutor.ejecutar(ResultadoEscucha.Entendido(orden, "texto"))

    private fun pregunta(tipo: TipoPregunta) = Orden.Pregunta(tipo)

    private fun llegada(
        hora: Int,
        minuto: Int,
    ) = LocalDateTime.of(2026, 11, 6, hora, minuto).toInstant(ZoneOffset.UTC).toEpochMilli()

    // --- Preguntas ------------------------------------------------------------------------------------------------

    @Test
    fun `cuanto falta dice distancia y minutos al destino`() =
        runBlocking {
            banco.estado = guiandoCon(12_400.0, 900.0, llegada(16, 10))

            banco.decir(pregunta(TipoPregunta.CUANTO_FALTA))

            assertEquals(listOf("faltan doce kilómetros, quince minutos"), banco.locutor.dichas.toList())
        }

    @Test
    fun `sin destino cuanto falta y a que hora llego dicen no hay destino`() =
        runBlocking {
            banco.decir(pregunta(TipoPregunta.CUANTO_FALTA))
            banco.estado = EstadoNavegacion.Libre(0L, 0.0)
            banco.decir(pregunta(TipoPregunta.HORA_LLEGADA))

            assertEquals(List(2) { "no hay destino" }, banco.locutor.dichas.toList())
        }

    @Test
    fun `a que hora llego usa la llegada prevista de la ruta`() =
        runBlocking {
            banco.estado = guiandoCon(12_400.0, 900.0, llegada(16, 10))

            banco.decir(pregunta(TipoPregunta.HORA_LLEGADA))

            assertEquals(listOf("llegas a las cuatro y diez"), banco.locutor.dichas.toList())
        }

    @Test
    fun `guiando sin progreso aun no sabe cuanto falta`() =
        runBlocking {
            banco.estado = guiandoCon(1.0, 1.0, 0L).copy(progreso = null)

            banco.decir(pregunta(TipoPregunta.CUANTO_FALTA))

            assertEquals(listOf("no lo sé"), banco.locutor.dichas.toList())
        }

    @Test
    fun `que limite hay lo saca de la via de la posicion actual`() =
        runBlocking {
            banco.vias.via = ViaActual("carretera de Sóller", "Ma-11", 90)
            banco.decir(pregunta(TipoPregunta.LIMITE))
            banco.vias.via = ViaActual("camino", null, null)
            banco.decir(pregunta(TipoPregunta.LIMITE))
            banco.vias.via = null
            banco.decir(pregunta(TipoPregunta.LIMITE))
            banco.vias.via = ViaActual(null, "Ma-11", 90)
            banco.ubicacion.fijacion = null
            banco.decir(pregunta(TipoPregunta.LIMITE))

            assertEquals(
                listOf("el límite es noventa", "no lo sé", "no lo sé", "no lo sé"),
                banco.locutor.dichas.toList(),
            )
        }

    @Test
    fun `donde estoy dice la carretera y su nombre`() =
        runBlocking {
            banco.vias.via = ViaActual("carretera de Sóller", "Ma-11", 90)
            banco.decir(pregunta(TipoPregunta.DONDE_ESTOY))
            banco.vias.via = null
            banco.decir(pregunta(TipoPregunta.DONDE_ESTOY))

            assertEquals(
                listOf("estás en la Ma once, carretera de Sóller", "no lo sé"),
                banco.locutor.dichas.toList(),
            )
        }

    @Test
    fun `como voy dice el tiempo del tramo y la diferencia con el mejor`() =
        runBlocking {
            banco.ahoraMs = 20_000L
            banco.crono.estado.value =
                CronoEnCurso(null, 70_000L, 10_000L, deltaMs = -3_000L, hayMejorPasada = true)
            banco.decir(pregunta(TipoPregunta.COMO_VOY))
            banco.crono.estado.value = CronoEnCurso(null, 70_000L, 10_000L, deltaMs = -3_000L, hayMejorPasada = false)
            banco.decir(pregunta(TipoPregunta.COMO_VOY))
            banco.crono.estado.value = CronoEnCurso(null, 0L, 10_000L, enMarcha = false)
            banco.decir(pregunta(TipoPregunta.COMO_VOY))
            banco.crono.estado.value =
                CronoEnCurso(null, 80_000L, 10_000L, resultado = ResultadoCrono(80_000L, 2_000L, false, 78_000L))
            banco.decir(pregunta(TipoPregunta.COMO_VOY))
            banco.crono.estado.value = null
            banco.decir(pregunta(TipoPregunta.COMO_VOY))

            assertEquals(
                listOf(
                    "uno veinte, tres segundos por delante",
                    "uno veinte",
                    "aún no has salido",
                    "uno veinte, dos segundos por detrás",
                    "no hay crono",
                ),
                banco.locutor.dichas.toList(),
            )
        }

    @Test
    fun `sin voz elegida las preguntas no hablan`() =
        runBlocking {
            banco.ajustes.mutable.value =
                banco.ajustes.mutable.value
                    .copy(voz = null)
            banco.estado = guiandoCon(12_400.0, 900.0, llegada(16, 10))

            banco.decir(pregunta(TipoPregunta.CUANTO_FALTA))

            assertEquals(emptyList<String>(), banco.locutor.dichas.toList())
        }

    // --- Control del copiloto -------------------------------------------------------------------------------------

    @Test
    fun `el modo se cambia con confirmacion hablada y silencio no dice nada`() =
        runBlocking {
            banco.decir(Orden.Modo(ModoCopiloto.NOTAS))
            assertEquals(ModoCopiloto.NOTAS, banco.ajustes.mutable.value.modoCopiloto)
            banco.decir(Orden.Modo(ModoCopiloto.SOLO_ALERTAS))
            assertEquals(ModoCopiloto.SOLO_ALERTAS, banco.ajustes.mutable.value.modoCopiloto)
            banco.decir(Orden.Modo(ModoCopiloto.COMPLETO))
            assertEquals(ModoCopiloto.COMPLETO, banco.ajustes.mutable.value.modoCopiloto)
            banco.decir(Orden.Modo(ModoCopiloto.SILENCIO))
            assertEquals(ModoCopiloto.SILENCIO, banco.ajustes.mutable.value.modoCopiloto)

            assertEquals(listOf("modo notas", "solo avisos", "modo completo"), banco.locutor.dichas.toList())
        }

    @Test
    fun `salir del silencio con un modo olvida el modo anterior`() =
        runBlocking {
            banco.ajustes.mutable.value =
                banco.ajustes.mutable.value
                    .copy(modoCopiloto = ModoCopiloto.SOLO_ALERTAS)
            banco.decir(Orden.Silencio)
            banco.decir(Orden.Modo(ModoCopiloto.NOTAS))
            banco.decir(Orden.Silencio)
            banco.decir(Orden.Habla)

            assertEquals(ModoCopiloto.NOTAS, banco.ajustes.mutable.value.modoCopiloto)
        }

    @Test
    fun `el volumen sube y baja de diez en diez con tope en los extremos`() =
        runBlocking {
            banco.decir(Orden.Volumen(subir = true))
            assertEquals(110, banco.ajustes.mutable.value.volumenVoz)
            banco.ajustes.mutable.value =
                banco.ajustes.mutable.value
                    .copy(volumenVoz = 140)
            banco.decir(Orden.Volumen(subir = true))
            banco.decir(Orden.Volumen(subir = true))
            assertEquals(AjustesConduccion.VOLUMEN_VOZ_MAX, banco.ajustes.mutable.value.volumenVoz)
            banco.ajustes.mutable.value =
                banco.ajustes.mutable.value
                    .copy(volumenVoz = 60)
            banco.decir(Orden.Volumen(subir = false))
            banco.decir(Orden.Volumen(subir = false))
            assertEquals(AjustesConduccion.VOLUMEN_VOZ_MIN, banco.ajustes.mutable.value.volumenVoz)

            assertEquals(
                listOf(
                    "volumen ciento diez",
                    "volumen ciento cincuenta",
                    "volumen máximo",
                    "volumen cincuenta",
                    "volumen mínimo",
                ),
                banco.locutor.dichas.toList(),
            )
        }

    @Test
    fun `notas simplificadas o completas`() =
        runBlocking {
            banco.decir(Orden.NotasSimplificadas(true))
            assertTrue(banco.ajustes.mutable.value.notasSimplificadas)
            banco.decir(Orden.NotasSimplificadas(false))
            assertFalse(banco.ajustes.mutable.value.notasSimplificadas)

            assertEquals(listOf("notas simples", "notas completas"), banco.locutor.dichas.toList())
        }

    @Test
    fun `los filtros de cruces puentes rotondas y salidas se activan y desactivan por separado`() =
        runBlocking {
            banco.decir(Orden.Avisar(Indicacion.CRUCES, false))
            banco.decir(Orden.Avisar(Indicacion.PUENTES, false))
            banco.decir(Orden.Avisar(Indicacion.ROTONDAS, false))
            banco.decir(Orden.Avisar(Indicacion.SALIDAS, false))
            val apagados = banco.ajustes.mutable.value
            assertFalse(apagados.avisarCruces)
            assertFalse(apagados.avisarPuentes)
            assertFalse(apagados.avisarRotondas)
            assertFalse(apagados.avisarSalidasAutopista)

            banco.decir(Orden.Avisar(Indicacion.PUENTES, true))
            banco.decir(Orden.Avisar(Indicacion.ROTONDAS, true))
            val parcial = banco.ajustes.mutable.value
            assertFalse(parcial.avisarCruces)
            assertTrue(parcial.avisarPuentes)
            assertTrue(parcial.avisarRotondas)
            assertFalse(parcial.avisarSalidasAutopista)

            assertEquals(
                listOf("sin cruces", "sin puentes", "sin rotondas", "sin salidas", "con puentes", "con rotondas"),
                banco.locutor.dichas.toList(),
            )
        }

    // --- Mapa y cronómetro ----------------------------------------------------------------------------------------

    @Test
    fun `satelite mapa norte y rumbo cambian la capa y la orientacion`() =
        runBlocking {
            banco.decir(Orden.Satelite(true))
            assertEquals(CapaMapa.SATELITE, banco.ajustes.mutable.value.capa)
            banco.decir(Orden.Satelite(false))
            assertEquals(CapaMapa.VECTORIAL, banco.ajustes.mutable.value.capa)
            banco.decir(Orden.NorteArriba(true))
            assertEquals(Orientacion.NORTE, banco.ajustes.mutable.value.orientacion)
            banco.decir(Orden.NorteArriba(false))
            assertEquals(Orientacion.RUMBO, banco.ajustes.mutable.value.orientacion)

            assertEquals(listOf("satélite", "mapa", "norte arriba", "rumbo arriba"), banco.locutor.dichas.toList())
        }

    @Test
    fun `acerca y aleja mueven el zoom un nivel con tope`() =
        runBlocking {
            banco.decir(Orden.Zoom(acercar = true))
            banco.decir(Orden.Zoom(acercar = true))
            banco.decir(Orden.Zoom(acercar = true))
            assertEquals(AjustesConduccion.NIVEL_MAX, banco.ajustes.mutable.value.nivelZoom)
            repeat(5) { banco.decir(Orden.Zoom(acercar = false)) }
            assertEquals(AjustesConduccion.NIVEL_MIN, banco.ajustes.mutable.value.nivelZoom)

            assertEquals(
                listOf(
                    "zoom más cerca",
                    "zoom más cerca",
                    "zoom máximo",
                    "zoom más lejos",
                    "zoom más lejos",
                    "zoom más lejos",
                    "zoom más lejos",
                    "zoom mínimo",
                ),
                banco.locutor.dichas.toList(),
            )
        }

    @Test
    fun `salida automatica lo dice y no da la salida`() =
        runBlocking {
            banco.crono.estado.value = CronoEnCurso(null, 0L, 0L, enMarcha = false, esperaSalidaManual = true)

            banco.decir(Orden.Salida)

            assertEquals(listOf("la salida es automática"), banco.locutor.dichas.toList())
            assertEquals(0, banco.salida.salidas)
        }

    @Test
    fun `salida manual sin conduccion lista dice que no hay salida pendiente`() =
        runBlocking {
            banco.ajustes.mutable.value =
                banco.ajustes.mutable.value
                    .copy(salidaManual = true)

            banco.decir(Orden.Salida)

            assertEquals(listOf("no hay salida pendiente"), banco.locutor.dichas.toList())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `salida manual hace la cuenta atras por voz y da la salida al final`() =
        runTest {
            val b = BancoOrdenes(backgroundScope, pasoCuentaAtrasMs = 1_000L)
            b.ajustes.mutable.value =
                b.ajustes.mutable.value
                    .copy(salidaManual = true)
            b.crono.estado.value = CronoEnCurso(null, 0L, 0L, enMarcha = false, esperaSalidaManual = true)

            b.ejecutor.ejecutar(ResultadoEscucha.Entendido(Orden.Salida, "salida"))
            // Una segunda petición mientras cuenta no la duplica.
            b.ejecutor.ejecutar(ResultadoEscucha.Entendido(Orden.Salida, "salida"))
            runCurrent()
            assertEquals(listOf("cinco"), b.locutor.dichas.toList())
            advanceTimeBy(4_500L)
            assertEquals(listOf("cinco", "cuatro", "tres", "dos", "uno"), b.locutor.dichas.toList())
            assertEquals(0, b.salida.salidas)
            advanceTimeBy(1_000L)
            runCurrent()

            assertEquals(listOf("cinco", "cuatro", "tres", "dos", "uno", "ya"), b.locutor.dichas.toList())
            assertEquals(1, b.salida.salidas)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `si el coche sale durante la cuenta atras no se da otra salida`() =
        runTest {
            val b = BancoOrdenes(backgroundScope, pasoCuentaAtrasMs = 1_000L)
            b.ajustes.mutable.value =
                b.ajustes.mutable.value
                    .copy(salidaManual = true)
            b.crono.estado.value = CronoEnCurso(null, 0L, 0L, enMarcha = false, esperaSalidaManual = true)

            b.ejecutor.ejecutar(ResultadoEscucha.Entendido(Orden.Salida, "salida"))
            runCurrent()
            b.crono.estado.value = CronoEnCurso(null, 0L, 0L, enMarcha = true)
            advanceTimeBy(6_000L)
            runCurrent()

            assertEquals(0, b.salida.salidas)
            assertEquals(listOf("cinco", "cuatro", "tres", "dos", "uno"), b.locutor.dichas.toList())
        }

    @Test
    fun `para el crono termina la conduccion libre sin preguntar`() =
        runBlocking {
            banco.estado = EstadoNavegacion.Libre(0L, 0.0)

            banco.decir(Orden.ParaCrono)

            assertEquals(1, banco.terminadas)
            assertEquals(listOf("conducción terminada"), banco.locutor.dichas.toList())
            assertFalse(banco.ejecutor.esperaConfirmacion)
        }

    @Test
    fun `para el crono guiando pide si y solo termina con la confirmacion`() =
        runBlocking {
            banco.estado = guiandoCon(1_000.0, 60.0, 0L)

            banco.decir(Orden.ParaCrono)
            assertTrue(banco.ejecutor.esperaConfirmacion)
            assertEquals(0, banco.terminadas)

            banco.decir(Orden.Confirma(true))
            assertEquals(1, banco.terminadas)
            assertFalse(banco.ejecutor.esperaConfirmacion)
            assertEquals(listOf("di sí para terminar", "conducción terminada"), banco.locutor.dichas.toList())
        }

    @Test
    fun `decir no a la pregunta cancela y otra orden tambien la anula`() =
        runBlocking {
            banco.estado = guiandoCon(1_000.0, 60.0, 0L)

            banco.decir(Orden.ParaCrono)
            banco.decir(Orden.Confirma(false))
            assertEquals(0, banco.terminadas)
            assertFalse(banco.ejecutor.esperaConfirmacion)

            banco.decir(Orden.ParaCrono)
            banco.decir(Orden.Repite)
            assertFalse(banco.ejecutor.esperaConfirmacion)
            banco.decir(Orden.Confirma(true))

            assertEquals(0, banco.terminadas)
            assertEquals(
                listOf("di sí para terminar", "cancelado", "di sí para terminar"),
                banco.locutor.dichas.toList(),
            )
        }

    @Test
    fun `un si suelto sin pregunta pendiente se ignora en silencio`() =
        runBlocking {
            banco.estado = guiandoCon(1_000.0, 60.0, 0L)

            banco.decir(Orden.Confirma(true))

            assertEquals(0, banco.terminadas)
            assertEquals(emptyList<String>(), banco.locutor.dichas.toList())
        }

    @Test
    fun `para el crono sin conduccion dice que no hay conduccion`() =
        runBlocking {
            banco.decir(Orden.ParaCrono)

            assertEquals(listOf("no hay conducción"), banco.locutor.dichas.toList())
        }

    @Test
    fun `avisa radar aqui guarda una marca con la etiqueta radar`() =
        runBlocking {
            banco.decir(Orden.RadarAqui)

            assertEquals(
                listOf(Marca(1_250.0, 777L, "tramo-1", Marca.ETIQUETA_RADAR) to PuntoGeo(39.7, 2.9)),
                banco.marcas.guardadas,
            )
            assertEquals(listOf("radar marcado"), banco.locutor.dichas.toList())
        }

    @Test
    fun `radar sin conduccion no guarda nada`() =
        runBlocking {
            banco.contexto = null

            banco.decir(Orden.RadarAqui)

            assertEquals(emptyList<Marca>(), banco.marcas.guardadas.map { it.first })
            assertEquals(listOf("no hay conducción"), banco.locutor.dichas.toList())
        }

    @Test
    fun `ayuda lee las cinco ordenes mas utiles`() =
        runBlocking {
            banco.decir(Orden.Ayuda)

            assertEquals(
                listOf("puedes decir: cuánto falta, qué límite hay, cómo voy, repite o silencio"),
                banco.locutor.dichas.toList(),
            )
        }

    @Test
    fun `lo no entendido invita a decir ayuda`() =
        runBlocking {
            banco.ejecutor.ejecutar(ResultadoEscucha.NoEntendido("[unk]"))

            assertEquals(listOf("no te he entendido, di ayuda"), banco.locutor.dichas.toList())
        }
}
