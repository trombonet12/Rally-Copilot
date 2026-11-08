package app.trombito.rallycopilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Sol
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Reloj que se mueve a mano. */
private class RelojManual(
    var ahora: Instant,
) : Clock() {
    override fun instant(): Instant = ahora

    override fun getZone(): ZoneId = Sol.MADRID

    override fun withZone(zone: ZoneId): Clock = this
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "notnight")
class TemaSolarTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun local(
        fecha: String,
        hora: String,
    ): Instant = LocalDateTime.parse("${fecha}T$hora").atZone(Sol.MADRID).toInstant()

    private fun oscuroEn(
        instante: Instant,
        tema: Tema = Tema.SOL,
        ubicacion: PuntoGeo? = null,
    ): Boolean {
        var oscuro: Boolean? = null
        val reloj = RelojManual(instante)
        banco.poner { oscuro = tema.oscuroAhora(reloj) { ubicacion } }
        return oscuro!!
    }

    @Test
    fun `sol sin resolver cuenta como oscuro y el resto de temas no cambian`() {
        assertTrue(Tema.SOL.esOscuro(sistemaOscuro = false))
        assertTrue(Tema.SOL.esOscuro(sistemaOscuro = false, solOscuro = true))
        assertFalse(Tema.SOL.esOscuro(sistemaOscuro = true, solOscuro = false))
        assertTrue(Tema.OSCURO.esOscuro(sistemaOscuro = false, solOscuro = false))
        assertFalse(Tema.CLARO.esOscuro(sistemaOscuro = true, solOscuro = true))
        assertEquals(Tema.OSCURO, Tema.PREDETERMINADO)
    }

    @Test
    fun `de dia pinta claro`() {
        assertFalse(oscuroEn(local("2026-06-21", "13:00")))
        assertFalse(oscuroEn(local("2026-12-21", "12:00")))
    }

    @Test
    fun `de noche pinta oscuro`() {
        assertTrue(oscuroEn(local("2026-06-21", "23:30")))
        assertTrue(oscuroEn(local("2026-12-21", "03:00")))
    }

    @Test
    fun `el crepusculo retrasa 15 minutos el oscuro de la tarde y adelanta 15 el claro del alba`() {
        val e = Sol.eventos(Sol.PALMA, LocalDate.of(2026, 12, 21))
        assertFalse(oscuroEn(e.puesta!!.plus(Duration.ofMinutes(10))))
        assertTrue(oscuroEn(e.puesta!!.plus(Duration.ofMinutes(20))))
        assertTrue(oscuroEn(e.salida!!.minus(Duration.ofMinutes(20))))
        assertFalse(oscuroEn(e.salida!!.minus(Duration.ofMinutes(10))))
    }

    @Test
    fun `sin ubicacion usa Palma y con ubicacion usa la del usuario`() {
        val tarde = local("2026-12-21", "17:40") // Palma: anochecer a las 17:30, oscuro desde las 17:45 aprox.
        val sinUbicacion = oscuroEn(tarde, ubicacion = null)
        assertEquals(oscuroEn(tarde, ubicacion = Sol.PALMA), sinUbicacion)
        // En Menorca (Mahón) el sol se pone antes: a esta hora el día ya terminó sin ninguna duda.
        assertTrue(oscuroEn(local("2026-12-21", "18:30"), ubicacion = PuntoGeo(39.89, 4.26)))
    }

    @Test
    fun `los temas fijos no miran al sol`() {
        val noche = local("2026-06-21", "23:30")
        assertFalse(oscuroEn(noche, tema = Tema.CLARO))
        assertTrue(oscuroEn(local("2026-06-21", "13:00"), tema = Tema.OSCURO))
    }

    @Test
    fun `cruza el umbral sin sondear y cambia la paleta de la interfaz`() {
        // El despertador usa reloj real (el `delay` de Compose no sigue al virtual de Robolectric): se arranca a 2 s
        // del umbral de la tarde para esperar poco.
        val umbral = Sol.fase(local("2026-06-21", "13:00")).proximoCambio
        val reloj = RelojManual(umbral.minusSeconds(2))
        var primario: Color? = null
        var recomposiciones = 0
        banco.poner {
            val oscuro = Tema.SOL.oscuroAhora(reloj) { null }
            recomposiciones++
            RallyCopilotTheme(darkTheme = oscuro) { primario = MaterialTheme.colorScheme.primary }
        }
        assertEquals(LightColors.primary, primario)
        val antes = recomposiciones
        // Con el reloj parado no hay recomposiciones: no se sondea.
        banco.reposo()
        assertEquals(antes, recomposiciones)
        // El reloj pasa el umbral y el despertador programado dispara el cambio, una sola vez.
        reloj.ahora = umbral.plusSeconds(1)
        Thread.sleep(ESPERA_REAL_MS)
        banco.reposo()
        assertEquals(DarkColors.primary, primario)
        assertEquals(antes + 1, recomposiciones)
    }

    private companion object {
        const val ESPERA_REAL_MS = 2_600L
    }
}
