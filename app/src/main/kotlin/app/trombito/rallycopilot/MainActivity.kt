package app.trombito.rallycopilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.ui.ActualizacionDatosViewModel
import app.trombito.rallycopilot.ui.AppNavHost
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val actualizacion: ActualizacionDatosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        actualizacion.comprobarActualizacion()
        setContent {
            // Hasta que Ajustes guarde el tema (H10) se usa el predeterminado: oscuro.
            val oscuro = Tema.PREDETERMINADO.oscuro
            DisposableEffect(oscuro) {
                val barras = SystemBarStyle.auto(TRANSPARENTE, TRANSPARENTE) { oscuro }
                enableEdgeToEdge(statusBarStyle = barras, navigationBarStyle = barras)
                onDispose {}
            }
            RallyCopilotTheme(darkTheme = oscuro) {
                Surface { AppNavHost() }
            }
        }
    }

    private companion object {
        const val TRANSPARENTE = android.graphics.Color.TRANSPARENT
    }
}
