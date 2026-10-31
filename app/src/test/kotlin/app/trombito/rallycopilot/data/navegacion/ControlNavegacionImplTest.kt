package app.trombito.rallycopilot.data.navegacion

import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ControlNavegacionImplTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private val fuente =
        object : FuenteUbicacion {
            override fun fijaciones(): Flow<Fijacion> = emptyFlow()

            override fun tienePermiso() = true
        }

    private val control = ControlNavegacionImpl(app, fuente)

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

        ControlNavegacionImpl(denegado, fuente).also { it.empezarLibre() }.let {
            assertTrue(it.estado.value is EstadoNavegacion.Error)
            assertNull(it.tomarSolicitud())
        }
    }

    @Test
    fun `tomarSolicitud entrega la solicitud una sola vez`() {
        control.empezarLibre()

        assertEquals(SolicitudNavegacion.Libre, checkNotNull(control.tomarSolicitud()).solicitud)
        assertNull(control.tomarSolicitud())
    }
}
