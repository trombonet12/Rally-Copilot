package app.trombito.rallycopilot.data.navegacion

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

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
    fun `tras Terminar no queda notificacion fantasma de Iniciando`() {
        val controlador = Robolectric.buildService(ServicioNavegacion::class.java).create()
        val servicio = controlador.get()
        servicio.control.empezarLibre()
        controlador.withIntent(shadowOf(app).nextStartedService).startCommand(0, 1)
        val gestor = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        servicio.control.terminar()
        controlador.destroy()
        // Deja que cualquier colector pendiente llegue al hilo principal y a los hilos de Default.
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }

        assertEquals(EstadoNavegacion.Inactiva, servicio.control.estado.value)
        assertTrue(gestor.activeNotifications.none { it.id == NotificacionNavegacion.ID_NOTIFICACION })
    }

    @Test
    fun `sin permiso de ubicacion empezar publica Error y no arranca el servicio`() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val servicio = Robolectric.buildService(ServicioNavegacion::class.java).create().get()

        servicio.control.empezarLibre()

        assertTrue(servicio.control.estado.value is EstadoNavegacion.Error)
        assertEquals(null, shadowOf(app).nextStartedService)
    }

    private fun servicioEnPrimerPlano(
        copiloto: Boolean,
    ): Pair<ServiceController<ServicioNavegacion>, ServicioNavegacion> {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val controlador = Robolectric.buildService(ServicioNavegacion::class.java).create()
        val servicio = controlador.get()
        runBlocking { servicio.ajustes.elegirActivarConCopiloto(copiloto) }
        servicio.control.empezarLibre()
        controlador.withIntent(shadowOf(app).nextStartedService).startCommand(0, 1)
        return controlador to servicio
    }

    // Service.getForegroundServiceType (API 29): lo que Robolectric guarda de la última llamada a startForeground.
    private fun tipoDe(servicio: ServicioNavegacion): Int = servicio.foregroundServiceType

    private fun esperarTipo(
        servicio: ServicioNavegacion,
        esperado: Int,
    ) {
        repeat(150) {
            shadowOf(Looper.getMainLooper()).idle()
            if (tipoDe(servicio) == esperado) return
            Thread.sleep(20)
        }
        assertEquals(esperado, tipoDe(servicio))
    }

    @Test
    fun `con Copiloto desactivado el primer plano no pide el tipo microfono aunque haya permiso`() {
        val (controlador, servicio) = servicioEnPrimerPlano(copiloto = false)

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, tipoDe(servicio))
        controlador.destroy()
    }

    @Test
    fun `con Copiloto activado y permiso el primer plano pide ubicacion y microfono`() {
        val (controlador, servicio) = servicioEnPrimerPlano(copiloto = true)

        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            tipoDe(servicio),
        )
        controlador.destroy()
    }

    @Test
    fun `con Copiloto activado pero sin permiso de microfono solo pide ubicacion`() {
        val controlador = Robolectric.buildService(ServicioNavegacion::class.java).create()
        val servicio = controlador.get()
        runBlocking { servicio.ajustes.elegirActivarConCopiloto(true) }
        servicio.control.empezarLibre()
        controlador.withIntent(shadowOf(app).nextStartedService).startCommand(0, 1)

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, tipoDe(servicio))
        controlador.destroy()
    }

    @Test
    fun `al cambiar Copiloto en marcha se vuelve a declarar el primer plano con el tipo nuevo`() {
        val controlador = Robolectric.buildService(ServicioNavegacion::class.java).create()
        val servicio = controlador.get()
        servicio.fuente = FuenteSinPosiciones
        // Ajustes falsos solo para el servicio: sin la base real no hay carreras con la escucha de Vosk, que no
        // carga en la JVM del test (la real espera 5 s sin permiso y el test acaba antes).
        val ajustes = FalsoRepositorioAjustesConduccion(AjustesConduccion(activarConCopiloto = false))
        servicio.ajustes = ajustes
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        servicio.control.empezarLibre()
        controlador.withIntent(shadowOf(app).nextStartedService).startCommand(0, 1)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, tipoDe(servicio))

        runBlocking { ajustes.elegirActivarConCopiloto(true) }
        esperarTipo(
            servicio,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        runBlocking { ajustes.elegirActivarConCopiloto(false) }
        esperarTipo(servicio, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        controlador.destroy()
    }
}

/** GPS que nunca da posiciones: la conducción queda esperando y el servicio sigue en primer plano. */
private object FuenteSinPosiciones : FuenteUbicacion {
    override fun fijaciones(): Flow<Fijacion> = flow { awaitCancellation() }

    override fun tienePermiso(): Boolean = true
}
