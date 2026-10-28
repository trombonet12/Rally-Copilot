package app.trombito.rallycopilot.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.trombito.rallycopilot.ui.ajustes.AcercaDeRoute
import app.trombito.rallycopilot.ui.ajustes.AjustesScreen
import app.trombito.rallycopilot.ui.inicio.EntradaInicio
import app.trombito.rallycopilot.ui.inicio.InicioScreen
import app.trombito.rallycopilot.ui.mapa.MapaRoute
import app.trombito.rallycopilot.ui.mapasoffline.MapasOfflineRoute

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = RutaInicio) {
        composable<RutaInicio> { InicioScreen(onEntrada = { abrirEntrada(nav, it) }) }
        composable<RutaMapa> {
            MapaRoute(onVolver = { nav.popBackStack() }, onAbrirMapasOffline = { nav.navigate(RutaMapasOffline) })
        }
        composable<RutaMapasOffline> { MapasOfflineRoute(onVolver = { nav.popBackStack() }) }
        composable<RutaAjustes> {
            AjustesScreen(
                onMapasOffline = { nav.navigate(RutaMapasOffline) },
                onAcercaDe = { nav.navigate(RutaAcercaDe) },
                onVolver = { nav.popBackStack() },
            )
        }
        composable<RutaAcercaDe> { AcercaDeRoute(onVolver = { nav.popBackStack() }) }
    }
}

/** Las entradas desactivadas no llegan aquí (la fila no es pulsable). */
private fun abrirEntrada(
    nav: NavHostController,
    entrada: EntradaInicio,
) {
    when (entrada) {
        EntradaInicio.CONDUCIR -> nav.navigate(RutaMapa)
        EntradaInicio.MAPAS_OFFLINE -> nav.navigate(RutaMapasOffline)
        EntradaInicio.AJUSTES -> nav.navigate(RutaAjustes)
        else -> Unit
    }
}
