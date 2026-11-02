package app.trombito.rallycopilot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
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
import app.trombito.rallycopilot.ui.mapasoffline.ZonaDetalleRoute
import app.trombito.rallycopilot.ui.mistramos.DetalleTramoRoute
import app.trombito.rallycopilot.ui.mistramos.DibujarRoute
import app.trombito.rallycopilot.ui.mistramos.EditarTramoRoute
import app.trombito.rallycopilot.ui.mistramos.ImportarViewModel
import app.trombito.rallycopilot.ui.mistramos.MisTramosRoute
import app.trombito.rallycopilot.ui.perfil.PerfilRoute
import app.trombito.rallycopilot.ui.reconocimiento.ReconocimientoRoute

@Composable
fun AppNavHost(importacion: ImportarViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    NavegarAlImportarDeFuera(importacion, nav)
    NavHost(navController = nav, startDestination = RutaInicio) {
        composable<RutaInicio> { InicioScreen(onEntrada = { abrirEntrada(nav, it) }) }
        composable<RutaMapa> {
            // Sin flecha de volver en el mapa: lo cierra el atrás del sistema (la pila de navegación).
            MapaRoute(
                onAbrirMapasOffline = { nav.navigate(RutaMapasOffline) },
                onVerAnalisis = { tramoId, pasadaId -> nav.navigate(RutaAnalisis(tramoId, pasadaId)) },
            )
        }
        composable<RutaMapasOffline> {
            MapasOfflineRoute(onVolver = { nav.popBackStack() }, onDescargarZona = { nav.navigate(RutaZonaDetalle) })
        }
        composable<RutaZonaDetalle> { ZonaDetalleRoute(onVolver = { nav.popBackStack() }) }
        composable<RutaAjustes> {
            AjustesRoute(
                onMapasOffline = { nav.navigate(RutaMapasOffline) },
                onAcercaDe = { nav.navigate(RutaAcercaDe) },
                onVolver = { nav.popBackStack() },
            )
        }
        composable<RutaAcercaDe> { AcercaDeRoute(onVolver = { nav.popBackStack() }) }
        composable<RutaMisTramos> {
            MisTramosRoute(
                onVolver = { nav.popBackStack() },
                onAbrirTramo = { nav.navigate(RutaTramo(it)) },
                importacion = importacion,
                onDibujar = { nav.navigate(RutaDibujar) },
            )
        }
        composable<RutaDibujar> {
            DibujarRoute(
                onVolver = { nav.popBackStack() },
                onGuardado = { id -> nav.navigate(RutaTramo(id)) { popUpTo<RutaDibujar> { inclusive = true } } },
            )
        }
        composable<RutaTramo> {
            DetalleTramoRoute(
                onVolver = { nav.popBackStack() },
                onEditarNotas = { nav.navigate(RutaEditarTramo(it)) },
                onVerAnalisis = { tramoId, pasadaId -> nav.navigate(RutaAnalisis(tramoId, pasadaId)) },
            )
        }
        composable<RutaEditarTramo> { EditarTramoRoute(onVolver = { nav.popBackStack() }) }
        composable<RutaReconocimiento> {
            ReconocimientoRoute(
                onVolver = { nav.popBackStack() },
                onEmpezado = { nav.navigate(RutaMapa) },
            )
        }
        composable<RutaAnalisis> { AnalisisRoute(onVolver = { nav.popBackStack() }) }
        composable<RutaPerfil> { PerfilRoute(onVolver = { nav.popBackStack() }) }
    }
}

/** Un GPX/KML que llega de otra app se muestra en «Mis tramos», con el resultado de la importación. */
@Composable
private fun NavegarAlImportarDeFuera(
    importacion: ImportarViewModel,
    nav: NavHostController,
) {
    LaunchedEffect(importacion, nav) {
        importacion.irAMisTramos.collect {
            val yaEsta = nav.currentDestination?.hasRoute<RutaMisTramos>() == true
            if (!yaEsta) nav.navigate(RutaMisTramos) { launchSingleTop = true }
        }
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
        EntradaInicio.RECONOCIMIENTO -> nav.navigate(RutaReconocimiento)
        EntradaInicio.PERFIL -> nav.navigate(RutaPerfil)
        else -> Unit
    }
}
