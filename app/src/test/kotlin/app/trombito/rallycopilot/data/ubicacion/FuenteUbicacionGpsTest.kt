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
        shadowOf(manager).setProviderEnabled(LocationManager.FUSED_PROVIDER, true)
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

    private fun aproximada(
        lat: Double,
        lon: Double,
        segundo: Long,
        proveedor: String = LocationManager.FUSED_PROVIDER,
    ) = Location(proveedor).apply {
        latitude = lat
        longitude = lon
        time = 1_700_000_000_000L + segundo * 1000
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() + segundo * 1_000_000_000L
        accuracy = 25f
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
    fun `al empezar emite la ultima posicion conocida si tiene menos de 2 min`() =
        runTest(UnconfinedTestDispatcher()) {
            simular(posicion(39.61, 2.91, segundo = -90))
            val recibidas = mutableListOf<Fijacion>()
            val job = launch { fuente.fijaciones().toList(recibidas) }

            assertEquals(1, recibidas.size)
            assertEquals(39.61, recibidas[0].punto.lat, 1e-9)
            job.cancel()
        }

    @Test
    fun `la ultima posicion conocida de hace mas de 2 min no se emite`() =
        runTest(UnconfinedTestDispatcher()) {
            simular(posicion(39.61, 2.91, segundo = -150))
            val recibidas = mutableListOf<Fijacion>()
            val job = launch { fuente.fijaciones().toList(recibidas) }

            assertTrue(recibidas.isEmpty())
            job.cancel()
        }

    @Test
    fun `entre la ultima del gps y la del fused se elige la mas reciente`() =
        runTest(UnconfinedTestDispatcher()) {
            simular(posicion(39.60, 2.90, segundo = -60))
            simular(aproximada(39.70, 2.95, segundo = -20))
            val recibidas = mutableListOf<Fijacion>()
            val job = launch { fuente.fijaciones().toList(recibidas) }

            assertEquals(1, recibidas.size)
            assertEquals(39.70, recibidas[0].punto.lat, 1e-9)
            assertNull(recibidas[0].velocidadMs)
            job.cancel()
        }

    @Test
    fun `en interior sin gps emite las posiciones del fused sin velocidad ni rumbo`() =
        runTest(UnconfinedTestDispatcher()) {
            val recibidas = mutableListOf<Fijacion>()
            val job = launch { fuente.fijaciones().toList(recibidas) }

            simular(aproximada(39.62, 2.92, segundo = 1))
            simular(aproximada(39.62, 2.9201, segundo = 2))

            assertEquals(2, recibidas.size)
            assertEquals(39.62, recibidas[0].punto.lat, 1e-9)
            assertNull(recibidas[0].velocidadMs)
            assertNull(recibidas[0].rumboGrados)
            assertEquals(25.0, recibidas[0].precisionM!!, 1e-6)
            job.cancel()
        }

    @Test
    fun `con gps reciente se ignora el fused y si el gps se pierde 3 s vuelve`() =
        runTest(UnconfinedTestDispatcher()) {
            val recibidas = mutableListOf<Fijacion>()
            val job = launch { fuente.fijaciones().toList(recibidas) }

            simular(posicion(39.6, 2.9, segundo = 10))
            simular(aproximada(39.7, 2.95, segundo = 11))
            simular(aproximada(39.7, 2.95, segundo = 12))
            assertEquals(1, recibidas.size)
            assertEquals(15.0, recibidas[0].velocidadMs!!, 1e-6)

            simular(aproximada(39.7, 2.95, segundo = 13))
            assertEquals(2, recibidas.size)
            assertEquals(39.7, recibidas[1].punto.lat, 1e-9)
            job.cancel()
        }

    @Test
    fun `sin gps entre los proveedores funciona solo con el fused`() =
        runTest(UnconfinedTestDispatcher()) {
            shadowOf(manager).removeProvider(LocationManager.GPS_PROVIDER)
            val recibidas = mutableListOf<Fijacion>()
            val job = launch { fuente.fijaciones().toList(recibidas) }

            simular(aproximada(39.62, 2.92, segundo = 1))

            assertEquals(1, recibidas.size)
            job.cancel()
        }

    @Test
    fun `sin ningun proveedor util el flujo termina vacio`() =
        runTest(UnconfinedTestDispatcher()) {
            for (p in manager.allProviders) shadowOf(manager).removeProvider(p)

            assertTrue(fuente.fijaciones().toList().isEmpty())
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
    fun `tiempoMs es el reloj monotono y no la hora UTC de la posicion`() {
        val l =
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 39.6
                longitude = 2.9
                time = 1_700_000_000_000L
                elapsedRealtimeNanos = 123_456_789_000_000L
            }
        val repetida =
            Location(l).apply {
                elapsedRealtimeNanos = 123_457_789_000_000L
            }

        assertEquals(123_456_789L, l.aFijacion()!!.tiempoMs)
        // Con la misma hora UTC, el tiempo de la fijación sigue avanzando.
        assertEquals(123_457_789L, repetida.aFijacion()!!.tiempoMs)
    }

    @Test
    fun `un rumbo con precision peor de 20 grados no cuenta`() {
        // El GPS del emulador (y algunos móviles a poca velocidad) dicen tener rumbo 0 con ±30°.
        val dudoso = posicion(39.6, 2.9, 1, rumbo = 0f).apply { bearingAccuracyDegrees = 30f }
        val bueno = posicion(39.6, 2.9, 1, rumbo = 270f).apply { bearingAccuracyDegrees = 5f }
        val sinPrecision = posicion(39.6, 2.9, 1, rumbo = 270f)

        assertNull(dudoso.aFijacion()!!.rumboGrados)
        assertEquals(270.0, bueno.aFijacion()!!.rumboGrados!!, 0.0)
        assertEquals(270.0, sinPrecision.aFijacion()!!.rumboGrados!!, 0.0)
    }

    @Test
    fun `las coordenadas fuera de rango se descartan`() {
        val invalida = Location(LocationManager.GPS_PROVIDER)
        invalida.latitude = 91.0
        invalida.longitude = 2.9

        assertNull(invalida.aFijacion())
    }
}
