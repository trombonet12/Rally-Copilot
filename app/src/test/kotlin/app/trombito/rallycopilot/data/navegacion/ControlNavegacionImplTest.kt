package app.trombito.rallycopilot.data.navegacion

import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.GuiadoPendiente
import app.trombito.rallycopilot.domain.navegacion.RepositorioGuiadoPendiente
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ControlNavegacionImplTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private val fuente =
        object : FuenteUbicacion {
            override fun fijaciones(): Flow<Fijacion> = emptyFlow()

            override fun tienePermiso() = true
        }

    private val guiadoPendiente = GuiadoPendienteEnMemoria()
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val control = ControlNavegacionImpl(app, fuente, guiadoPendiente, alcance)
    private val destinoPrueba = Destino("Sóller", PuntoGeo(39.7667, 2.7154))
    private val rutaPrueba =
        Ruta(
            puntos = listOf(PuntoGeo(39.5696, 2.6502), PuntoGeo(39.7667, 2.7154)),
            maniobras = emptyList(),
            longitudM = 18_400.0,
            tiempoS = 1_380.0,
        )

    @Test
    fun `una sesion terminada no puede volver a publicar`() {
        control.empezarLibre()
        val id = checkNotNull(control.tomarSolicitud()).id

        control.terminar()
        control.publicar(id, EstadoNavegacion.Libre(0L, 10.0))

        assertEquals(EstadoNavegacion.Inactiva, control.estado.value)
    }

    @Test
    fun `la sesion vigente publica y una anterior ya no`() {
        control.empezarLibre()
        val primero = checkNotNull(control.tomarSolicitud()).id
        control.terminar()
        control.empezarLibre()
        val segundo = checkNotNull(control.tomarSolicitud()).id
        assertNotEquals(primero, segundo)

        control.publicar(primero, EstadoNavegacion.Error("vieja"))
        assertTrue(control.estado.value is EstadoNavegacion.Libre)

        control.publicar(segundo, EstadoNavegacion.Error("nueva"))
        assertEquals(EstadoNavegacion.Error("nueva"), control.estado.value)
    }

    @Test
    fun `si el sistema deniega el servicio en primer plano publica Error en vez de caerse`() {
        val denegado =
            object : ContextWrapper(app) {
                override fun startForegroundService(service: Intent?): ComponentName =
                    throw SecurityException("foreground not allowed")
            }

        ControlNavegacionImpl(denegado, fuente, guiadoPendiente, alcance).also { it.empezarLibre() }.let {
            assertTrue(it.estado.value is EstadoNavegacion.Error)
            assertNull(it.tomarSolicitud())
        }
    }

    @Test
    fun `empezar un destino estando en libre cambia la sesion sin parar el servicio`() {
        control.empezarLibre()
        val libre = checkNotNull(control.tomarSolicitud()).id

        control.empezar(destinoPrueba, PerfilRuta.MAS_RAPIDA, rutaPrueba)

        val guiado = checkNotNull(control.tomarSolicitud())
        assertTrue(control.estado.value is EstadoNavegacion.Guiando)
        assertTrue(guiado.solicitud is SolicitudNavegacion.Guiada)
        assertNotEquals(libre, guiado.id)
        // Mismo servicio, dos arranques: no se ha parado entre medias.
        assertNotNull(shadowOf(app).nextStartedService)
        assertNotNull(shadowOf(app).nextStartedService)
        assertNull(shadowOf(app).nextStoppedService)
    }

    @Test
    fun `tras sustituir la libre por el guiado la sesion libre ya no puede publicar`() {
        control.empezarLibre()
        val libre = checkNotNull(control.tomarSolicitud()).id
        control.empezar(destinoPrueba, PerfilRuta.MAS_RAPIDA, rutaPrueba)
        val guiado = checkNotNull(control.tomarSolicitud()).id

        control.publicar(libre, EstadoNavegacion.Libre(0L, 99.0))
        assertTrue(control.estado.value is EstadoNavegacion.Guiando)

        control.publicar(guiado, EstadoNavegacion.Llegada(destinoPrueba))
        assertEquals(EstadoNavegacion.Llegada(destinoPrueba), control.estado.value)
    }

    @Test
    fun `volver del guiado al libre tambien sustituye la sesion`() {
        control.empezar(destinoPrueba, PerfilRuta.MAS_RAPIDA, rutaPrueba)
        val guiado = checkNotNull(control.tomarSolicitud()).id

        control.empezarLibre()

        assertTrue(control.estado.value is EstadoNavegacion.Libre)
        val libre = checkNotNull(control.tomarSolicitud())
        assertEquals(SolicitudNavegacion.Libre, libre.solicitud)
        assertNotEquals(guiado, libre.id)
        control.publicar(guiado, EstadoNavegacion.Error("vieja"))
        assertTrue(control.estado.value is EstadoNavegacion.Libre)
    }

    @Test
    fun `repetir el mismo tipo de sesion no hace nada`() {
        control.empezarLibre()
        checkNotNull(control.tomarSolicitud())
        control.empezarLibre()
        assertNull(control.tomarSolicitud())

        control.empezar(destinoPrueba, PerfilRuta.MAS_RAPIDA, rutaPrueba)
        val guiado = checkNotNull(control.tomarSolicitud()).id
        control.empezar(destinoPrueba, PerfilRuta.SINUOSA, rutaPrueba)
        assertNull(control.tomarSolicitud())
        control.publicar(guiado, EstadoNavegacion.Llegada(destinoPrueba))
        assertTrue(control.estado.value is EstadoNavegacion.Llegada)
    }

    @Test
    fun `si falla el arranque al sustituir se publica Error y se para el servicio`() {
        var paradas = 0
        var arranques = 0
        val contexto =
            object : ContextWrapper(app) {
                override fun startForegroundService(service: Intent?): ComponentName? {
                    if (++arranques > 1) throw SecurityException("foreground not allowed")
                    return super.startForegroundService(service)
                }

                override fun stopService(name: Intent?): Boolean {
                    paradas++
                    return super.stopService(name)
                }
            }
        val control = ControlNavegacionImpl(contexto, fuente, guiadoPendiente, alcance)
        control.empezarLibre()

        control.empezar(destinoPrueba, PerfilRuta.MAS_RAPIDA, rutaPrueba)

        assertTrue(control.estado.value is EstadoNavegacion.Error)
        assertEquals(1, paradas)
    }

    @Test
    fun `tomarSolicitud entrega la solicitud una sola vez`() {
        control.empezarLibre()

        assertEquals(SolicitudNavegacion.Libre, checkNotNull(control.tomarSolicitud()).solicitud)
        assertNull(control.tomarSolicitud())
    }

    @Test
    fun `empezar un guiado guarda el destino y el modo libre no guarda nada`() {
        control.empezarLibre()
        assertNull(guiadoPendiente.valor.value)

        control.empezar(destinoPrueba, PerfilRuta.SINUOSA, rutaPrueba)

        assertEquals(destinoPrueba to PerfilRuta.SINUOSA, guiadoPendiente.valor.value)
    }

    @Test
    fun `terminar borra el guiado pendiente`() {
        control.empezar(destinoPrueba, PerfilRuta.MAS_RAPIDA, rutaPrueba)

        control.terminar()

        assertNull(guiadoPendiente.valor.value)
    }

    @Test
    fun `llegar al destino borra el guiado pendiente`() {
        control.empezar(destinoPrueba, PerfilRuta.MAS_RAPIDA, rutaPrueba)
        val id = checkNotNull(control.tomarSolicitud()).id

        control.publicar(id, EstadoNavegacion.Llegada(destinoPrueba))

        assertNull(guiadoPendiente.valor.value)
    }

    @Test
    fun `pasar a modo libre desde un guiado borra el guiado pendiente`() {
        control.empezar(destinoPrueba, PerfilRuta.MAS_RAPIDA, rutaPrueba)

        control.empezarLibre()

        assertNull(guiadoPendiente.valor.value)
    }

    @Test
    fun `una sesion antigua que llega tarde no borra el guiado vigente`() {
        control.empezar(destinoPrueba, PerfilRuta.MAS_RAPIDA, rutaPrueba)
        val guiado = checkNotNull(control.tomarSolicitud()).id
        control.terminar()
        control.empezar(destinoPrueba, PerfilRuta.SINUOSA, rutaPrueba)

        control.publicar(guiado, EstadoNavegacion.Llegada(destinoPrueba))

        assertEquals(destinoPrueba to PerfilRuta.SINUOSA, guiadoPendiente.valor.value)
    }

    @Test
    fun `si el sistema deniega el servicio no queda guiado pendiente`() {
        val denegado =
            object : ContextWrapper(app) {
                override fun startForegroundService(service: Intent?): ComponentName =
                    throw SecurityException("foreground not allowed")
            }

        ControlNavegacionImpl(denegado, fuente, guiadoPendiente, alcance).empezar(
            destinoPrueba,
            PerfilRuta.MAS_RAPIDA,
            rutaPrueba,
        )

        assertNull(guiadoPendiente.valor.value)
    }

    @Test
    fun `si la base falla al guardar la navegacion sigue`() {
        val roto =
            object : RepositorioGuiadoPendiente {
                override val pendiente: Flow<GuiadoPendiente?> = emptyFlow()

                override suspend fun guardar(
                    destino: Destino,
                    perfil: PerfilRuta,
                ): Unit = throw android.database.sqlite.SQLiteException("disco lleno")

                override suspend fun borrar() = Unit
            }
        val control = ControlNavegacionImpl(app, fuente, roto, alcance)

        control.empezar(destinoPrueba, PerfilRuta.MAS_RAPIDA, rutaPrueba)
        control.terminar()

        assertEquals(EstadoNavegacion.Inactiva, control.estado.value)
    }
}

private class GuiadoPendienteEnMemoria : RepositorioGuiadoPendiente {
    val valor = MutableStateFlow<Pair<Destino, PerfilRuta>?>(null)

    override val pendiente: Flow<GuiadoPendiente?> = emptyFlow()

    override suspend fun guardar(
        destino: Destino,
        perfil: PerfilRuta,
    ) {
        valor.value = destino to perfil
    }

    override suspend fun borrar() {
        valor.value = null
    }
}
