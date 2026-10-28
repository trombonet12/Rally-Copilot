package app.trombito.rallycopilot.data.ubicacion

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FuenteUbicacionGpsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fuente = FuenteUbicacionGps(app)

    @Before
    fun preparar() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    private fun posicion(
        lat: Double,
        lon: Double,
        segundo: Long,
        velocidad: Float? = 15f,
        rumbo: Float? = 90f,
    ) = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = lat
        longitude = lon
        time = 1_700_000_000_000L + segundo * 1000
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() + segundo * 1_000_000_000L
        accuracy = 4f
        velocidad?.let { speed = it }
        rumbo?.let { bearing = it }
    }

    private fun simular(location: Location) {
        shadowOf(manager).simulateLocation(location)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `dos posiciones emiten fijaciones con velocidad y rumbo`() =
        runTest(UnconfinedTestDispatcher()) {
            val recibidas = mutableListOf<Fijacion>()
            val job = launch { fuente.fijaciones().toList(recibidas) }

            simular(posicion(39.6, 2.9, segundo = 1))
            simular(posicion(39.6, 2.9002, segundo = 2).apply { altitude = 120.0 })

            assertEquals(2, recibidas.size)
            val primera = recibidas[0]
            assertEquals(39.6, primera.punto.lat, 1e-9)
            assertEquals(2.9, primera.punto.lon, 1e-9)
            assertNull(primera.punto.alt)
            assertEquals(15.0, primera.velocidadMs!!, 1e-6)
            assertEquals(90.0, primera.rumboGrados!!, 1e-6)
            assertEquals(4.0, primera.precisionM!!, 1e-6)
            assertEquals(1_700_000_001_000L, primera.tiempoMs)
            assertEquals(120.0, recibidas[1].punto.alt!!, 1e-9)
            job.cancel()
        }

    @Test
    fun `sin velocidad ni rumbo en la posicion llegan como nulos`() =
        runTest(UnconfinedTestDispatcher()) {
            val recibidas = mutableListOf<Fijacion>()
            val job = launch { fuente.fijaciones().toList(recibidas) }

            simular(posicion(39.6, 2.9, segundo = 1, velocidad = null, rumbo = null))

            assertEquals(1, recibidas.size)
            assertNull(recibidas[0].velocidadMs)
            assertNull(recibidas[0].rumboGrados)
            job.cancel()
        }

    @Test
    fun `al empezar emite la ultima posicion conocida si tiene menos de 10 s`() =
        runTest(UnconfinedTestDispatcher()) {
            simular(posicion(39.61, 2.91, segundo = -5))
            val recibidas = mutableListOf<Fijacion>()
            val job = launch { fuente.fijaciones().toList(recibidas) }

            assertEquals(1, recibidas.size)
            assertEquals(39.61, recibidas[0].punto.lat, 1e-9)
            job.cancel()
        }

    @Test
    fun `la ultima posicion conocida de hace mas de 10 s no se emite`() =
        runTest(UnconfinedTestDispatcher()) {
            simular(posicion(39.61, 2.91, segundo = -30))
            val recibidas = mutableListOf<Fijacion>()
            val job = launch { fuente.fijaciones().toList(recibidas) }

            assertTrue(recibidas.isEmpty())
            job.cancel()
        }

    @Test
    fun `al dejar de recoger se retira el listener`() =
        runTest(UnconfinedTestDispatcher()) {
            val recibidas = mutableListOf<Fijacion>()
            val job = launch { fuente.fijaciones().toList(recibidas) }
            simular(posicion(39.6, 2.9, segundo = 1))
            assertEquals(1, recibidas.size)

            job.cancel()
            simular(posicion(39.6, 2.9002, segundo = 3))

            assertEquals(1, recibidas.size)
        }

    @Test
    fun `sin permiso tienePermiso es falso y el flujo termina vacio`() =
        runTest(UnconfinedTestDispatcher()) {
            shadowOf(app).denyPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )

            assertFalse(fuente.tienePermiso())
            assertTrue(fuente.fijaciones().toList().isEmpty())
        }

    @Test
    fun `el permiso aproximado tambien cuenta`() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        assertTrue(fuente.tienePermiso())
    }

    @Test
    fun `las coordenadas fuera de rango se descartan`() {
        val invalida = Location(LocationManager.GPS_PROVIDER)
        invalida.latitude = 91.0
        invalida.longitude = 2.9

        assertNull(invalida.aFijacion())
    }
}
