package app.trombito.rallycopilot.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.trombito.rallycopilot.ui.ajustes.AcercaDeRoute
import app.trombito.rallycopilot.ui.ajustes.AjustesRoute
import app.trombito.rallycopilot.ui.analisis.AnalisisRoute
import app.trombito.rallycopilot.ui.inicio.EntradaInicio
import app.trombito.rallycopilot.ui.inicio.InicioScreen
import app.trombito.rallycopilot.ui.mapa.MapaRoute
import app.trombito.rallycopilot.ui.mapasoffline.MapasOfflineRoute
import app.trombito.rallycopilot.ui.mistramos.DetalleTramoRoute
import app.trombito.rallycopilot.ui.mistramos.MisTramosRoute
import app.trombito.rallycopilot.ui.perfil.PerfilRoute

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = RutaInicio) {
        composable<RutaInicio> { InicioScreen(onEntrada = { abrirEntrada(nav, it) }) }
        composable<RutaMapa> {
            // Sin flecha de volver en el mapa: lo cierra el atrás del sistema (la pila de navegación).
            MapaRoute(
                onAbrirMapasOffline = { nav.navigate(RutaMapasOffline) },
                onVerAnalisis = { tramoId, pasadaId -> nav.navigate(RutaAnalisis(tramoId, pasadaId)) },
            )
        }
        composable<RutaMapasOffline> { MapasOfflineRoute(onVolver = { nav.popBackStack() }) }
        composable<RutaAjustes> {
            AjustesRoute(
                onMapasOffline = { nav.navigate(RutaMapasOffline) },
                onAcercaDe = { nav.navigate(RutaAcercaDe) },
                onVolver = { nav.popBackStack() },
            )
        }
        composable<RutaAcercaDe> { AcercaDeRoute(onVolver = { nav.popBackStack() }) }
        composable<RutaMisTramos> {
            MisTramosRoute(onVolver = { nav.popBackStack() }, onAbrirTramo = { nav.navigate(RutaTramo(it)) })
        }
        composable<RutaTramo> {
            DetalleTramoRoute(
                onVolver = { nav.popBackStack() },
                onVerAnalisis = { tramoId, pasadaId -> nav.navigate(RutaAnalisis(tramoId, pasadaId)) },
            )
        }
        composable<RutaAnalisis> { AnalisisRoute(onVolver = { nav.popBackStack() }) }
        composable<RutaPerfil> { PerfilRoute(onVolver = { nav.popBackStack() }) }
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
        EntradaInicio.MIS_TRAMOS -> nav.navigate(RutaMisTramos)
        EntradaInicio.PERFIL -> nav.navigate(RutaPerfil)
        else -> Unit
    }
}
