package app.trombito.rallycopilot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.trombito.rallycopilot.ui.ajustes.AcercaDeRoute
import app.trombito.rallycopilot.ui.ajustes.AjustesRoute
import app.trombito.rallycopilot.ui.ajustes.AjustesSeccionRoute
import app.trombito.rallycopilot.ui.ajustes.SeccionAjustes
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
import app.trombito.rallycopilot.ui.reconocimiento.ReconocimientoViewModel

/** El mapa, sin flecha de volver: lo cierra el atrás del sistema o la ✕ de su hoja (la pila de navegación). */
@Composable
private fun DestinoMapa(nav: NavHostController) {
    MapaRoute(
        onAbrirMapasOffline = { nav.navigate(RutaMapasOffline) },
        onVerAnalisis = { tramoId, pasadaId -> nav.navigate(RutaAnalisis(tramoId, pasadaId)) },
        onSalir = { nav.popBackStack() },
    )
}

@Composable
fun AppNavHost(importacion: ImportarViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    NavegarAlImportarDeFuera(importacion, nav)
    NavHost(navController = nav, startDestination = RutaInicio) {
        composable<RutaInicio> { InicioScreen(onEntrada = { abrirEntrada(nav, it) }) }
        composable<RutaMapa> { DestinoMapa(nav) }
        composable<RutaMapasOffline> {
            MapasOfflineRoute(onVolver = { nav.popBackStack() }, onDescargarZona = { nav.navigate(RutaZonaDetalle) })
        }
        composable<RutaZonaDetalle> { ZonaDetalleRoute(onVolver = { nav.popBackStack() }) }
        destinosAjustes(nav)
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
            val reconocimiento: ReconocimientoViewModel = hiltViewModel()
            DetalleTramoRoute(
                onVolver = { nav.popBackStack() },
                onEditarNotas = { nav.navigate(RutaEditarTramo(it)) },
                onReconocer = {
                    reconocimiento.empezar(it)
                    nav.navigate(RutaMapa)
                },
                onVerAnalisis = { tramoId, pasadaId -> nav.navigate(RutaAnalisis(tramoId, pasadaId)) },
            )
        }
        composable<RutaEditarTramo> { EditarTramoRoute(onVolver = { nav.popBackStack() }) }
        composable<RutaAnalisis> { AnalisisRoute(onVolver = { nav.popBackStack() }) }
        composable<RutaPerfil> {
            PerfilRoute(
                onVolver = { nav.popBackStack() },
                onVerAnalisis = { tramoId, pasadaId -> nav.navigate(RutaAnalisis(tramoId, pasadaId)) },
            )
        }
    }
}

/** Portada de Ajustes y una subpantalla por sección; Acerca de y Mapas offline tienen su ruta propia. */
private fun NavGraphBuilder.destinosAjustes(nav: NavHostController) {
    composable<RutaAjustes> {
        AjustesRoute(
            onSeccion = { seccion ->
                if (seccion == SeccionAjustes.ACERCA_DE) {
                    nav.navigate(RutaAcercaDe)
                } else {
                    nav.navigate(RutaAjustesSeccion(seccion))
                }
            },
            onVolver = { nav.popBackStack() },
        )
    }
    composable<RutaAjustesSeccion> { entrada ->
        // Comparte el ViewModel con la portada: los valores ya están cargados al abrir la subpantalla.
        val portada = remember(entrada) { nav.getBackStackEntry<RutaAjustes>() }
        AjustesSeccionRoute(
            seccion = entrada.toRoute<RutaAjustesSeccion>().seccion,
            onMapasOffline = { nav.navigate(RutaMapasOffline) },
            onVolver = { nav.popBackStack() },
            viewModel = hiltViewModel(portada),
            copia = hiltViewModel(portada),
        )
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
        EntradaInicio.AJUSTES -> nav.navigate(RutaAjustes)
        EntradaInicio.MIS_TRAMOS -> nav.navigate(RutaMisTramos)
        EntradaInicio.PERFIL -> nav.navigate(RutaPerfil)
    }
}
