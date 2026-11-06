package app.trombito.rallycopilot.ui.tramos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.trombito.rallycopilot.domain.escucha.DialogoVoz
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.motor.edicion.Orden

/**
 * La orden de voz «salida» con la salida manual: el copiloto ya cuenta atrás por voz y da la salida al cronómetro, así
 * que aquí solo se encienden las luces de [CuentaAtras] a la vez, mudas (sin repetir la voz ni dar la salida otra vez).
 * Se ve cuando el último [dialogo] es la orden de salida sin respuesta (con respuesta es que no hay cuenta atrás:
 * salida automática o nada pendiente) y el [crono] espera la salida manual. Tocar fuera la quita, sin parar la voz.
 */
@Composable
fun CuentaAtrasPorVoz(
    dialogo: DialogoVoz?,
    crono: CronoEnCurso?,
) {
    var contando by remember { mutableStateOf(false) }
    LaunchedEffect(dialogo?.id) {
        val empieza = dialogo?.orden == Orden.Salida && dialogo.respuesta == null
        if (empieza && crono?.esperaSalidaManual == true && !contando) contando = true
    }
    if (contando) CuentaAtras(onDecir = {}, onYa = {}, onCerrar = { contando = false })
}
