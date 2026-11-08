package app.trombito.rallycopilot.ui.tramos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono

/**
 * Abre el análisis de una pasada (tramo y número de pasada) desde la tarjeta de meta; null donde no hay a dónde
 * navegar. Lo da la ruta del mapa: así no hay que pasarlo por todas las capas de la pantalla.
 */
val LocalAbrirAnalisis = staticCompositionLocalOf<((tramoId: String, pasadaId: Long) -> Unit)?> { null }

/**
 * Lo que la salida manual necesita de fuera: [onDecir] hace que el copiloto diga una palabra de la cuenta atrás y
 * [onSalida] da la salida del cronómetro. Lo da la ruta del mapa.
 */
class AccionesSalidaCrono(
    val onDecir: (String) -> Unit,
    val onSalida: () -> Unit,
)

/** Acciones de la salida manual; null donde no hay conducción a la que dar la salida (sin botón «SALIDA»). */
val LocalAccionesSalidaCrono = staticCompositionLocalOf<AccionesSalidaCrono?> { null }

/**
 * Lo que el cronómetro pone arriba del mapa, bajo el banner de maniobra, la franja de notas y el banner de alerta: el
 * HUD completo si el tramo es conocido (con mejor pasada), la tarjeta de meta unos segundos al cruzarla, la línea del
 * subtramo en marcha y la tarjeta del último subtramo cerrado durante [DURACION_TARJETA_SUBTRAMO_MS]; en cualquier
 * otro caso nada (con un tramo nuevo o sin tramo solo hay [CronometroMini] en el panel inferior). Con [compacto]
 * (apaisado) todo es más bajo.
 */
@Composable
fun CronoSuperior(
    crono: CronoEnCurso?,
    modifier: Modifier = Modifier,
    compacto: Boolean = false,
    reloj: () -> Long = System::currentTimeMillis,
) {
    if (crono == null) return
    val resultado = crono.resultado
    if (resultado != null) {
        MetaTemporal(resultado, compacto, modifier)
        return
    }
    val hud = vistaCrono(crono) == VistaCrono.HUD
    val vivo = crono.subtramo
    val tarjeta = rememberSubtramoReciente(crono.ultimoSubtramo, reloj)
    if (hud || vivo != null || tarjeta != null) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (hud) HudCrono(crono, Modifier, compacto, reloj)
            if (vivo != null) SubtramoEnVivo(crono, vivo, compacto = compacto, reloj = reloj)
            if (tarjeta != null) TarjetaSubtramo(tarjeta, compacta = compacto)
        }
    }
}

/**
 * El cronómetro pequeño del panel inferior: solo sin tramo conocido y mientras no haya resultado de meta. Con la
 * salida manual pendiente añade el botón «SALIDA», que abre la [CuentaAtras] (si [LocalAccionesSalidaCrono] la
 * permite); la cuenta atrás se retira sola al acabar o al tocar fuera.
 */
@Composable
fun CronoMiniEnPanel(
    crono: CronoEnCurso?,
    modifier: Modifier = Modifier,
    reloj: () -> Long = System::currentTimeMillis,
    escala: EscalaCronoMini = EscalaCronoMini.NORMAL,
) {
    if (crono != null && crono.resultado == null && vistaCrono(crono) == VistaCrono.MINI) {
        val acciones = LocalAccionesSalidaCrono.current
        var contando by remember { mutableStateOf(false) }
        CronometroMini(
            crono,
            modifier,
            reloj,
            onSalida = acciones?.let { { contando = true } },
            escala = escala,
        )
        if (contando && acciones != null) {
            CuentaAtras(onDecir = acciones.onDecir, onYa = acciones.onSalida, onCerrar = { contando = false })
        }
    }
}

/** La tarjeta de meta, que se retira sola a los [DURACION_TARJETA_META_MS] de aparecer ese resultado. */
@Composable
private fun MetaTemporal(
    resultado: ResultadoCrono,
    compacta: Boolean,
    modifier: Modifier,
) {
    var visible by remember(resultado) { mutableStateOf(true) }
    val alVerAnalisis = accionVerAnalisis(LocalAbrirAnalisis.current, resultado)
    LaunchedEffect(resultado) {
        esperarEnPrincipal(DURACION_TARJETA_META_MS)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        TarjetaMeta(resultado, compacta = compacta, onVerAnalisis = alVerAnalisis)
    }
}

/** El botón «Ver análisis» solo si hay a dónde navegar y la pasada ya está guardada (tiene tramo y número). */
private fun accionVerAnalisis(
    abrir: ((tramoId: String, pasadaId: Long) -> Unit)?,
    resultado: ResultadoCrono,
): (() -> Unit)? {
    val tramoId = resultado.tramoId
    val pasadaId = resultado.pasadaId
    if (abrir == null || tramoId == null || pasadaId == null) return null
    return { abrir(tramoId, pasadaId) }
}
