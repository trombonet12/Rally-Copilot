package app.trombito.rallycopilot.ui.alertas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class FormatoDetalleIncidenciaTest {
    private val recursos = ApplicationProvider.getApplicationContext<Context>().resources

    private fun contenido(
        carretera: String? = null,
        completo: Boolean = true,
        detalle: DetalleIncidencia = DetalleIncidencia(),
    ) = ContenidoBocadillo.Incidencia(TipoIncidencia.OBRAS, carretera, detalle, completo)

    private fun lineas(c: ContenidoBocadillo) = lineasDetalleBocadillo(recursos, c)

    @Test
    fun `la linea completa junta carretera, km, sentido, carril, limite y fecha de fin`() {
        val detalle =
            DetalleIncidencia(
                pkDesde = 56.5,
                sentido = "Inca",
                carriles = "carril derecho",
                limiteKmh = 60,
                // 30 de noviembre a mediodia en Mallorca.
                fin = Instant.parse("2026-11-30T11:00:00Z"),
            )
        assertEquals(
            listOf("Ma-13 km 56,5 · sentido Inca · afecta a carril derecho · máx. 60 · hasta 30/11"),
            lineas(contenido("Ma-13", detalle = detalle)),
        )
    }

    @Test
    fun `omite lo que falta`() {
        assertEquals(emptyList<String>(), lineas(contenido()))
        assertEquals(listOf("Ma-13"), lineas(contenido("Ma-13")))
        assertEquals(
            listOf("Ma-13 · máx. 40"),
            lineas(contenido("Ma-13", detalle = DetalleIncidencia(limiteKmh = 40))),
        )
    }

    @Test
    fun `el rango de km sale con guion y sin ceros sobrantes`() {
        val rango = DetalleIncidencia(pkDesde = 56.5, pkHasta = 57.1)
        assertEquals(listOf("Ma-13 km 56,5–57,1"), lineas(contenido("Ma-13", detalle = rango)))
        assertEquals(listOf("km 56"), lineas(contenido(detalle = DetalleIncidencia(pkDesde = 56.0))))
    }

    @Test
    fun `el municipio solo sale si no hay km`() {
        val sinKm = DetalleIncidencia(municipio = "Alcúdia")
        assertEquals(listOf("Ma-13 · Alcúdia"), lineas(contenido("Ma-13", detalle = sinKm)))
        val conKm = DetalleIncidencia(pkDesde = 3.2, municipio = "Alcúdia")
        assertEquals(listOf("Ma-13 km 3,2"), lineas(contenido("Ma-13", detalle = conKm)))
    }

    @Test
    fun `el comentario de la DGT va en una linea aparte`() {
        val detalle = DetalleIncidencia(pkDesde = 1.0, comentario = "  Siga las indicaciones  ")
        assertEquals(
            listOf("Ma-13 km 1", "Siga las indicaciones"),
            lineas(contenido("Ma-13", detalle = detalle)),
        )
        val soloComentario = DetalleIncidencia(comentario = "Siga las indicaciones")
        assertEquals(listOf("Siga las indicaciones"), lineas(contenido(detalle = soloComentario)))
    }

    @Test
    fun `el bocadillo de aproximacion solo lleva carretera y km`() {
        val detalle = DetalleIncidencia(pkDesde = 56.5, sentido = "Inca", comentario = "x", limiteKmh = 60)
        assertEquals(listOf("Ma-13 km 56,5"), lineas(contenido("Ma-13", completo = false, detalle = detalle)))
    }

    @Test
    fun `lo que no es una incidencia no lleva detalle`() {
        assertEquals(emptyList<String>(), lineas(ContenidoBocadillo.Paso(87, 80)))
    }

    @Test
    fun `el km usa coma decimal`() {
        assertEquals("56,5", textoKm(56.5))
        assertEquals("57", textoKm(57.0))
        assertEquals("0,3", textoKm(0.25))
    }
}
