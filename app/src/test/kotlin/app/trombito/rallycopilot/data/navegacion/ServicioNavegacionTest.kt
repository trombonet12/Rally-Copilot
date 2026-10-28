package app.trombito.rallycopilot.data.navegacion

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@Suppress("DEPRECATION") // ServiceController.withIntent: no hay alternativa para reenviar intents al mismo servicio
@RunWith(RobolectricTestRunner::class)
class ServicioNavegacionTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun preparar() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun `empezar arranca el servicio y este pasa a primer plano con la notificacion del canal Conduccion`() {
        val controlador = Robolectric.buildService(ServicioNavegacion::class.java).create()
        val servicio = controlador.get()

        servicio.control.empezarLibre()
        val arrancado = shadowOf(app).nextStartedService
        assertEquals(ServicioNavegacion::class.java.name, arrancado.component?.className)
        assertTrue(servicio.control.estado.value is EstadoNavegacion.Libre)

        controlador.withIntent(arrancado).startCommand(0, 1)

        val notificacion = checkNotNull(shadowOf(servicio).lastForegroundNotification)
        assertEquals(NotificacionNavegacion.CANAL, notificacion.channelId)
        assertEquals(NotificacionNavegacion.ID_NOTIFICACION, shadowOf(servicio).lastForegroundNotificationId)
        assertEquals(1, notificacion.actions.size)
        assertNotNull(notificacion.contentIntent)

        val gestor = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals("Conducción", gestor.getNotificationChannel(NotificacionNavegacion.CANAL).name.toString())

        controlador.destroy()
        assertEquals(EstadoNavegacion.Inactiva, servicio.control.estado.value)
    }

    @Test
    fun `la accion Terminar de la notificacion para la conduccion`() {
        val controlador = Robolectric.buildService(ServicioNavegacion::class.java).create()
        val servicio = controlador.get()
        servicio.control.empezarLibre()
        controlador.withIntent(shadowOf(app).nextStartedService).startCommand(0, 1)

        val terminar = Intent(app, ServicioNavegacion::class.java).setAction(ServicioNavegacion.ACCION_TERMINAR)
        controlador.withIntent(terminar).startCommand(0, 2)

        assertEquals(EstadoNavegacion.Inactiva, servicio.control.estado.value)
        assertNotNull(shadowOf(app).nextStoppedService)
    }

    @Test
    fun `sin permiso de ubicacion empezar publica Error y no arranca el servicio`() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val servicio = Robolectric.buildService(ServicioNavegacion::class.java).create().get()

        servicio.control.empezarLibre()

        assertTrue(servicio.control.estado.value is EstadoNavegacion.Error)
        assertEquals(null, shadowOf(app).nextStartedService)
    }
}
