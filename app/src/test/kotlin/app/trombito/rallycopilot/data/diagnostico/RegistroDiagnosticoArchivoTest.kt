package app.trombito.rallycopilot.data.diagnostico

import app.trombito.rallycopilot.domain.diagnostico.NivelRegistro
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

class RegistroDiagnosticoArchivoTest {
    @get:Rule val temporal = TemporaryFolder()

    private val instante = LocalDateTime.of(2026, 11, 14, 9, 5, 7, 42_000_000).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun registro(
        carpeta: () -> File? = { temporal.root },
        activo: () -> Boolean = { true },
        maxBytes: Long = RegistroDiagnosticoArchivo.MAX_BYTES,
    ) = RegistroDiagnosticoArchivo(
        carpeta = carpeta,
        activo = activo,
        dispatcher = Dispatchers.Unconfined,
        ahora = { instante },
        zona = ZoneOffset.UTC,
        maxBytes = maxBytes,
    )

    private fun log() = File(temporal.root, "registros/rally.log")

    @Test
    fun `escribe la linea con el formato fecha nivel etiqueta mensaje`() {
        registro().registrarSincrono(NivelRegistro.INFO, "Gps", "gps recuperado")
        assertEquals(listOf("2026-11-14 09:05:07.042 INFO Gps gps recuperado"), log().readLines())
    }

    @Test
    fun `una traza multilinea se escribe linea a linea con el mismo prefijo`() {
        registro().registrarSincrono(NivelRegistro.ERROR, "Crash", "boom\n\tat a.B.c(B.kt:1)")
        assertEquals(
            listOf(
                "2026-11-14 09:05:07.042 ERROR Crash boom",
                "2026-11-14 09:05:07.042 ERROR Crash \tat a.B.c(B.kt:1)",
            ),
            log().readLines(),
        )
    }

    @Test
    fun `registrar encola y un hilo escribe`() {
        val r = registro()
        r.info("Test", "async")
        val limite = System.currentTimeMillis() + 5_000
        while (!log().exists() && System.currentTimeMillis() < limite) Thread.sleep(10)
        assertTrue(log().readText().contains("INFO Test async"))
    }

    @Test
    fun `rota rally log a rally 1 log al pasar del maximo y solo conserva dos archivos`() {
        val r = registro(maxBytes = 200)
        repeat(40) { r.registrarSincrono(NivelRegistro.INFO, "T", "linea numero $it") }
        val carpeta = File(temporal.root, "registros")
        assertEquals(setOf("rally.log", "rally.1.log"), carpeta.list()!!.toSet())
        assertTrue(File(carpeta, "rally.log").length() <= 200)
        assertTrue(File(carpeta, "rally.1.log").length() <= 200)
        // Lo más reciente está en rally.log y lo anterior en rally.1.log.
        assertTrue(File(carpeta, "rally.log").readText().contains("linea numero 39"))
        assertFalse(File(carpeta, "rally.1.log").readText().contains("linea numero 39"))
    }

    @Test
    fun `no escribe lineas con coordenadas`() {
        val r = registro()
        r.registrarSincrono(NivelRegistro.INFO, "T", "posicion 39.57, 2.65")
        r.registrarSincrono(NivelRegistro.INFO, "T", "pos 39.571234;2.654321")
        r.registrarSincrono(NivelRegistro.INFO, "T", "lat=39.57 velocidad=50")
        r.registrarSincrono(NivelRegistro.INFO, "T", "ubicacion -3,5712, 40,1234")
        assertFalse(log().exists())
    }

    @Test
    fun `no escribe secretos pero si velocidades precisiones y distancias`() {
        val r = registro()
        r.registrarSincrono(NivelRegistro.INFO, "T", "api_key=abc123")
        r.registrarSincrono(NivelRegistro.INFO, "T", "velocidad_kmh=87 precision_m=4.5 km=12.30 via=Ma-13")
        assertEquals(1, log().readLines().size)
        assertTrue(log().readText().contains("velocidad_kmh=87 precision_m=4.5 km=12.30 via=Ma-13"))
    }

    @Test
    fun `el filtro de una traza descarta solo la linea sospechosa`() {
        registro().registrarSincrono(NivelRegistro.ERROR, "Crash", "fallo\nmensaje 39.57, 2.65\nfin")
        val lineas = log().readLines()
        assertEquals(2, lineas.size)
        assertTrue(lineas[0].endsWith("Crash fallo"))
        assertTrue(lineas[1].endsWith("Crash fin"))
    }

    @Test
    fun `con el interruptor apagado no registra`() {
        var activo = false
        val r = registro(activo = { activo })
        r.registrarSincrono(NivelRegistro.INFO, "T", "no")
        assertFalse(log().exists())
        activo = true
        r.registrarSincrono(NivelRegistro.INFO, "T", "si")
        assertEquals(1, log().readLines().size)
    }

    @Test
    fun `no lanza con carpeta nula`() {
        val r = registro(carpeta = { null })
        r.registrarSincrono(NivelRegistro.INFO, "T", "x")
        r.info("T", "y")
    }

    @Test
    fun `no lanza si la carpeta es un archivo o fallan el interruptor o el almacenamiento`() {
        val archivo = temporal.newFile("ocupado")
        registro(carpeta = { archivo }).registrarSincrono(NivelRegistro.INFO, "T", "x")
        registro(activo = { error("sin preferencias") }).registrarSincrono(NivelRegistro.INFO, "T", "x")
        registro(carpeta = { error("sin almacenamiento") }).registrarSincrono(NivelRegistro.INFO, "T", "x")
    }

    @Test
    fun `no lanza con el disco lleno`() {
        // /dev/full (Linux) falla al escribir; en Windows no existe y la carpeta es inválida: no lanza en ambos.
        registro(carpeta = { File("/dev/full") }).registrarSincrono(NivelRegistro.INFO, "T", "x")
    }
}
