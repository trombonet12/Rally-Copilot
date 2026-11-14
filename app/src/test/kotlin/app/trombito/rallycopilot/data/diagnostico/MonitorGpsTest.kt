package app.trombito.rallycopilot.data.diagnostico

import app.trombito.rallycopilot.domain.diagnostico.NivelRegistro
import app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class RegistroEnMemoria : RegistroDiagnostico {
    val lineas = mutableListOf<String>()

    override fun registrar(
        nivel: NivelRegistro,
        etiqueta: String,
        mensaje: String,
    ) {
        lineas += "$nivel $etiqueta $mensaje"
    }

    override fun registrarSincrono(
        nivel: NivelRegistro,
        etiqueta: String,
        mensaje: String,
    ) = registrar(nivel, etiqueta, mensaje)
}

class MonitorGpsTest {
    private val registro = RegistroEnMemoria()

    private fun fijacion(
        t: Long,
        precision: Double = 5.0,
    ) = Fijacion(PuntoGeo(39.6, 2.9), t, 10.0, 90.0, precision)

    @Test
    fun `anota la perdida de senal y la recuperacion`() {
        val monitor = MonitorGps(registro)
        for (s in 1..5) monitor.fijacion(fijacion(s * 1_000L), s * 1_000L)
        monitor.tick(20_000)
        assertTrue(registro.lineas.single().startsWith("AVISO Gps gps sin senal"))
        for (s in 21..24) monitor.fijacion(fijacion(s * 1_000L), s * 1_000L)
        assertTrue(registro.lineas.last().endsWith("gps recuperado"))
    }

    @Test
    fun `cada minuto resume fijaciones por minuto y precision media sin coordenadas`() {
        val monitor = MonitorGps(registro)
        for (s in 0..60) monitor.fijacion(fijacion(s * 1_000L, if (s % 2 == 0) 4.0 else 6.0), s * 1_000L)
        val resumen = registro.lineas.single()
        assertTrue(resumen, resumen.contains("gps resumen fijaciones_min=61.0 utiles=61 total=61"))
        assertTrue(resumen, resumen.contains("precision_media_m=5.0"))
        assertFalse(FiltroRegistro.sospechosa(resumen))
    }
}
