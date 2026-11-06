package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * La cadena de la salida manual tal como la monta [FabricaCronoSesion]: cada conducción crea su [SesionCrono] y el
 * cronómetro en vivo debe dar la salida a la vigente, aunque haya otras sesiones creadas o cerradas alrededor (el
 * servicio crea una sesión solo para cerrar huérfanos y la anterior cierra mientras arranca la nueva).
 */
class CronoEnVivoImplTest {
    private val enVivo = CronoEnVivoImpl()
    private var ahora = 10_000L

    /** Sin tramos guardados: `cronometrables` es lo único que usa una sesión al arrancar; el resto es un error. */
    private val repositorio: RepositorioTramos =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(RepositorioTramos::class.java)) { _, metodo, _ ->
            if (metodo.name == "cronometrables") emptyList<Any>() else error("repositorio inesperado: ${metodo.name}")
        } as RepositorioTramos

    private fun crearSesion(): SesionCrono =
        enVivo.enlazar { publicar ->
            SesionCrono(repositorio, publicar, { ahora }, salidaManual = flowOf(true))
        }

    private fun fijar(sesion: SesionCrono) {
        sesion.alFijar(Fijacion(PuntoGeo(39.7, 2.9), ahora, 0.0, null, 5.0))
    }

    @Test
    fun `dar la salida desde el control publica en marcha sin esperar a otra fijacion y el tiempo corre`() =
        runTest {
            val sesion = crearSesion()
            sesion.arrancar(this)
            testScheduler.runCurrent()
            fijar(sesion)
            assertTrue(checkNotNull(enVivo.crono.value).esperaSalidaManual)

            ahora = 14_000L
            enVivo.darSalida()

            val en = checkNotNull(enVivo.crono.value)
            assertTrue(en.enMarcha)
            assertFalse(en.esperaSalidaManual)
            assertEquals(0L, en.transcurridoMs)
            assertEquals(14_000L, en.actualizadoEnMs)
        }

    @Test
    fun `una sesion creada despues sin publicar no le quita la salida a la vigente`() =
        runTest {
            val vigente = crearSesion()
            vigente.arrancar(this)
            testScheduler.runCurrent()
            fijar(vigente)
            crearSesion() // la de limpiar huérfanos: nunca conduce
            ahora = 12_000L

            enVivo.darSalida()

            assertTrue(checkNotNull(enVivo.crono.value).enMarcha)
        }

    @Test
    fun `el cierre de la sesion anterior no borra la salida de la nueva ni su cronometro`() =
        runTest {
            val anterior = crearSesion()
            anterior.arrancar(this)
            testScheduler.runCurrent()
            fijar(anterior)
            val nueva = crearSesion() // el servicio la crea antes de que la anterior acabe de cerrar
            anterior.cerrar(null) { emptyList() }
            assertNull(enVivo.crono.value)

            nueva.arrancar(this)
            testScheduler.runCurrent()
            fijar(nueva)
            ahora = 12_000L
            enVivo.darSalida()

            assertTrue(checkNotNull(enVivo.crono.value).enMarcha)
        }
}
