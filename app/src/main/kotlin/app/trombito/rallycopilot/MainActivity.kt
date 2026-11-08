package app.trombito.rallycopilot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.alertas.RepositorioIncidencias
import app.trombito.rallycopilot.ui.ActualizacionDatosViewModel
import app.trombito.rallycopilot.ui.AppNavHost
import app.trombito.rallycopilot.ui.mistramos.ImportarViewModel
import app.trombito.rallycopilot.ui.mistramos.uriDeImportacion
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.theme.oscuroAhora
import app.trombito.rallycopilot.ui.widget.ActualizadorWidget
import app.trombito.rallycopilot.ui.widget.AtajosViewModel
import app.trombito.rallycopilot.ui.widget.atajo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val actualizacion: ActualizacionDatosViewModel by viewModels()

    private val importar: ImportarViewModel by viewModels()

    private val atajos: AtajosViewModel by viewModels()

    @Inject lateinit var incidencias: RepositorioIncidencias

    @Inject lateinit var ajustes: RepositorioAjustesConduccion

    /**
     * Tema elegido en Ajustes. Arranca con la copia que se guardó en `SharedPreferences` (lectura síncrona de ~1 ms,
     * sin esperar a que Room se abra y migre) y se corrige con el valor de Room; solo en el primer arranque es null
     * y el splash espera (para no parpadear en oscuro si se eligió claro).
     */
    private var tema by mutableStateOf<Tema?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { tema == null }
        super.onCreate(savedInstanceState)
        val arranque = getSharedPreferences(PREFERENCIAS_ARRANQUE, MODE_PRIVATE)
        tema = Tema.entries.firstOrNull { it.name == arranque.getString(CLAVE_TEMA, null) }
        lifecycleScope.launch {
            ajustes.ajustes
                .map { it.tema }
                .distinctUntilChanged()
                .collect {
                    tema = it
                    arranque.edit().putString(CLAVE_TEMA, it.name).apply()
                }
        }
        actualizacion.comprobarActualizacion()
        // Al recrear la actividad (giro) el intent llega otra vez: ya se importó.
        if (savedInstanceState == null) {
            intent.uriDeImportacion()?.let(importar::importarEntrante)
            // Atajo del icono o botón del widget con la app fría; con la app viva llega por onNewIntent.
            intent.atajo()?.let(atajos::pedir)
        }
        // Incidencias de la DGT al abrir la app (a lo sumo cada 5 min); sin red se queda con la copia.
        lifecycleScope.launch { incidencias.refrescar() }
        setContent {
            val oscuro = (tema ?: Tema.PREDETERMINADO).oscuroAhora()
            DisposableEffect(oscuro) {
                val barras = SystemBarStyle.auto(TRANSPARENTE, TRANSPARENTE) { oscuro }
                enableEdgeToEdge(statusBarStyle = barras, navigationBarStyle = barras)
                onDispose {}
            }
            RallyCopilotTheme(darkTheme = oscuro) {
                Surface { AppNavHost(importacion = importar) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.uriDeImportacion()?.let(importar::importarEntrante)
        intent.atajo()?.let(atajos::pedir)
    }

    /** Al salir de la app se repinta el widget «Última salida» (puede haberse guardado una pasada). */
    override fun onStop() {
        super.onStop()
        ActualizadorWidget.pedir(this)
    }

    private companion object {
        const val TRANSPARENTE = android.graphics.Color.TRANSPARENT
        const val PREFERENCIAS_ARRANQUE = "arranque"
        const val CLAVE_TEMA = "tema"
    }
}
