package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.escucha.ContextoConduccion
import app.trombito.rallycopilot.domain.escucha.EdicionPendiente
import app.trombito.rallycopilot.domain.escucha.MarcaConPosicion
import app.trombito.rallycopilot.domain.escucha.ProveedorContextoConduccion
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.domain.escucha.ResultadoEscucha
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

private class LocutorGrabador : Locutor {
    override val hablando: StateFlow<Boolean> = MutableStateFlow(false)
    val dichas = mutableListOf<String>()

    override fun usarVoz(v: VozCopiloto) = Unit

    override fun preparar(textos: List<String>) = Unit

    override fun decir(texto: String) {
        dichas += texto
    }

    override fun duracionEstimadaS(texto: String): Double = 0.0

    override fun probar(v: VozCopiloto) = Unit

    override fun cerrar() = Unit
}

private class MarcasFalsas : RepositorioMarcas {
    val guardadas = mutableListOf<Pair<Marca, PuntoGeo?>>()

    override suspend fun guardar(
        marca: Marca,
        posicion: PuntoGeo?,
    ) {
        guardadas += marca to posicion
    }

    override suspend fun deTramo(tramoId: String) = emptyList<MarcaConPosicion>()
}

class EjecutorOrdenesTest {
    private val ajustes = FalsoRepositorioAjustesConduccion(AjustesConduccion(voz = VozCopiloto.DAVEFX))
    private val locutor = LocutorGrabador()
    private val memoria = MemoriaFrases()
    private val ediciones = EdicionesFalsas()
    private val marcas = MarcasFalsas()
    private var contexto: ContextoConduccion? = ContextoConduccion(1_250.0, "tramo-1", PuntoGeo(39.7, 2.9))
    private val ejecutor =
        EjecutorOrdenes(
            ajustes,
            locutor,
            memoria,
            ediciones,
            marcas,
            ProveedorContextoConduccion { contexto },
            { 777L },
        )

    private fun entendido(orden: Orden) = ResultadoEscucha.Entendido(orden, "texto")

    @Test
    fun `repite vuelve a decir la ultima frase y no confirma`() =
        runBlocking {
            memoria.recordar("derecha tres, cincuenta")

            ejecutor.ejecutar(entendido(Orden.Repite))

            assertEquals(listOf("derecha tres, cincuenta"), locutor.dichas)
        }

    @Test
    fun `repite sin nada dicho no hace nada`() =
        runBlocking {
            ejecutor.ejecutar(entendido(Orden.Repite))

            assertEquals(emptyList<String>(), locutor.dichas)
        }

    @Test
    fun `mas pronto y mas tarde mueven la anticipacion un nivel con tope en los extremos`() =
        runBlocking {
            ejecutor.ejecutar(entendido(Orden.MasPronto))
            assertEquals(NivelAnticipacion.PRONTO, ajustes.mutable.value.anticipacion)
            ejecutor.ejecutar(entendido(Orden.MasPronto))
            ejecutor.ejecutar(entendido(Orden.MasPronto))
            assertEquals(NivelAnticipacion.MUY_PRONTO, ajustes.mutable.value.anticipacion)

            repeat(2) { ejecutor.ejecutar(entendido(Orden.MasTarde)) }
            assertEquals(NivelAnticipacion.NORMAL, ajustes.mutable.value.anticipacion)
            repeat(5) { ejecutor.ejecutar(entendido(Orden.MasTarde)) }
            assertEquals(NivelAnticipacion.MUY_TARDE, ajustes.mutable.value.anticipacion)
            assertEquals(List(10) { "hecho" }, locutor.dichas)
        }

    @Test
    fun `silencio pasa al modo silencio sin decir nada y habla vuelve al modo anterior`() =
        runBlocking {
            ajustes.mutable.value = ajustes.mutable.value.copy(modoCopiloto = ModoCopiloto.NOTAS)

            ejecutor.ejecutar(entendido(Orden.Silencio))
            assertEquals(ModoCopiloto.SILENCIO, ajustes.mutable.value.modoCopiloto)
            assertEquals(emptyList<String>(), locutor.dichas)

            ejecutor.ejecutar(entendido(Orden.Habla))
            assertEquals(ModoCopiloto.NOTAS, ajustes.mutable.value.modoCopiloto)
            assertEquals(listOf("hecho"), locutor.dichas)
        }

    @Test
    fun `silencio dos veces no pierde el modo anterior`() =
        runBlocking {
            ajustes.mutable.value = ajustes.mutable.value.copy(modoCopiloto = ModoCopiloto.SOLO_ALERTAS)

            ejecutor.ejecutar(entendido(Orden.Silencio))
            ejecutor.ejecutar(entendido(Orden.Silencio))
            ejecutor.ejecutar(entendido(Orden.Habla))

            assertEquals(ModoCopiloto.SOLO_ALERTAS, ajustes.mutable.value.modoCopiloto)
        }

    @Test
    fun `habla sin silencio previo no toca el modo y silencio guardado en ajustes vuelve al por defecto`() =
        runBlocking {
            ejecutor.ejecutar(entendido(Orden.Habla))
            assertEquals(ModoCopiloto.COMPLETO, ajustes.mutable.value.modoCopiloto)

            ajustes.mutable.value = ajustes.mutable.value.copy(modoCopiloto = ModoCopiloto.SILENCIO)
            ejecutor.ejecutar(entendido(Orden.Habla))
            assertEquals(ModoCopiloto.POR_DEFECTO, ajustes.mutable.value.modoCopiloto)
        }

    @Test
    fun `marca aqui guarda la marca con la posicion y el tramo activo`() =
        runBlocking {
            ejecutor.ejecutar(entendido(Orden.MarcaAqui))

            assertEquals(listOf(Marca(1_250.0, 777L, "tramo-1") to PuntoGeo(39.7, 2.9)), marcas.guardadas)
            assertEquals(listOf("hecho"), locutor.dichas)
        }

    @Test
    fun `las notas quitar y cuidado se publican como ediciones pendientes`() =
        runBlocking {
            val nota = Orden.Nota(Direccion.IZQUIERDA, 2, distanciaSiguienteM = 80)

            ejecutor.ejecutar(entendido(nota))
            ejecutor.ejecutar(entendido(Orden.Quitar))
            ejecutor.ejecutar(entendido(Orden.Cuidado))

            val posicion = PuntoGeo(39.7, 2.9)
            assertEquals(
                listOf(
                    EdicionPendiente(nota, 1_250.0, "tramo-1", posicion, 777L),
                    EdicionPendiente(Orden.Quitar, 1_250.0, "tramo-1", posicion, 777L),
                    EdicionPendiente(Orden.Cuidado, 1_250.0, "tramo-1", posicion, 777L),
                ),
                ediciones.publicadas,
            )
            assertEquals(List(3) { "hecho" }, locutor.dichas)
        }

    @Test
    fun `sin conduccion marca y ediciones dicen no entendido y no guardan nada`() =
        runBlocking {
            contexto = null

            ejecutor.ejecutar(entendido(Orden.MarcaAqui))
            ejecutor.ejecutar(entendido(Orden.Quitar))

            assertEquals(emptyList<Marca>(), marcas.guardadas.map { it.first })
            assertEquals(emptyList<EdicionPendiente>(), ediciones.publicadas)
            assertEquals(listOf("no entendido", "no entendido"), locutor.dichas)
        }

    @Test
    fun `lo que no se entiende dice no entendido`() =
        runBlocking {
            ejecutor.ejecutar(ResultadoEscucha.NoEntendido("[unk]"))

            assertEquals(listOf("no entendido"), locutor.dichas)
        }

    @Test
    fun `sin permiso o sin escucha no dice nada`() =
        runBlocking {
            ejecutor.ejecutar(ResultadoEscucha.SinPermiso)
            ejecutor.ejecutar(ResultadoEscucha.NoDisponible)

            assertEquals(emptyList<String>(), locutor.dichas)
        }

    @Test
    fun `sin voz elegida no confirma por voz pero ejecuta la orden`() =
        runBlocking {
            ajustes.mutable.value = ajustes.mutable.value.copy(voz = null)

            ejecutor.ejecutar(entendido(Orden.MarcaAqui))

            assertEquals(1, marcas.guardadas.size)
            assertEquals(emptyList<String>(), locutor.dichas)
        }
}
