package app.trombito.rallycopilot.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Sol
import app.trombito.rallycopilot.ui.mapa.camaraUltimaConocida
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration

private val RallyShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

/** Si el tema aplicado es oscuro; lo usan quienes dependen de él fuera de Material (estilo del mapa). */
val LocalTemaOscuro = staticCompositionLocalOf { true }

/** Tema de la app. Oscuro por defecto: decide quien llama (el ajuste de tema), no el sistema. */
@Composable
fun RallyCopilotTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val typography = remember { rallyTypography() }
    CompositionLocalProvider(
        LocalColoresRally provides if (darkTheme) RallyOscuro else RallyClaro,
        LocalTemaOscuro provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = typography,
            shapes = RallyShapes,
            content = content,
        )
    }
}

/**
 * Si este tema se pinta en oscuro ahora. Con [Tema.SISTEMA] sigue el modo del móvil y recompone al cambiarlo; con
 * [Tema.SOL] sigue el sol (ver [rememberSolOscuro]).
 */
@Composable
fun Tema.oscuroAhora(
    reloj: Clock = Clock.systemUTC(),
    ubicacion: (Context) -> PuntoGeo? = ::ultimaUbicacionConocida,
): Boolean {
    val sistemaOscuro = isSystemInDarkTheme()
    val solOscuro = if (this == Tema.SOL) rememberSolOscuro(reloj, ubicacion) else true
    return esOscuro(sistemaOscuro, solOscuro)
}

/** Última posición conocida del sistema como punto; null sin permiso o sin ninguna (entonces cuenta Palma). */
private fun ultimaUbicacionConocida(contexto: Context): PuntoGeo? =
    camaraUltimaConocida(contexto)?.let { PuntoGeo(it.latitud, it.longitud) }

/** Espera mínima entre dos cálculos, por si el reloj no avanza (tests) y para no sondear en bucle. */
private val ESPERA_MINIMA: Duration = Duration.ofSeconds(1)

/**
 * Si ahora toca oscuro según el sol, calculado sin red con [Sol]. No usa el sensor de luz: un túnel no lo cambia.
 * No sondea: programa un único despertar en el siguiente umbral (puesta + 15 min o salida − 15 min) y solo
 * recompone cuando el valor cambia, así que no parpadea. Se recalcula al volver a primer plano (la espera no
 * cuenta con el móvil dormido) y con la ubicación más reciente.
 */
@Composable
internal fun rememberSolOscuro(
    reloj: Clock,
    ubicacion: (Context) -> PuntoGeo?,
): Boolean {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    var oscuro by remember(reloj) { mutableStateOf(Sol.fase(reloj.instant(), ubicacion(contexto)).oscuro) }
    LaunchedEffect(ciclo, reloj) {
        ciclo.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val ahora = reloj.instant()
                val fase = Sol.fase(ahora, ubicacion(contexto))
                oscuro = fase.oscuro
                delay(Duration.between(ahora, fase.proximoCambio).coerceAtLeast(ESPERA_MINIMA).toMillis())
            }
        }
    }
    return oscuro
}
