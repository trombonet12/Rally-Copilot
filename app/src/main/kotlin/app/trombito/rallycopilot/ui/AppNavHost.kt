package app.trombito.rallycopilot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.trombito.rallycopilot.ui.ajustes.AccionesAyuda
import app.trombito.rallycopilot.ui.ajustes.AcercaDeRoute
import app.trombito.rallycopilot.ui.ajustes.AjustesRoute
import app.trombito.rallycopilot.ui.ajustes.AjustesSeccionRoute
import app.trombito.rallycopilot.ui.ajustes.AyudaNotasScreen
import app.trombito.rallycopilot.ui.ajustes.AyudaVozScreen
import app.trombito.rallycopilot.ui.ajustes.SeccionAjustes
import app.trombito.rallycopilot.ui.analisis.AnalisisRoute
import app.trombito.rallycopilot.ui.inicio.EntradaInicio
import app.trombito.rallycopilot.ui.inicio.InicioRoute
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
import app.trombito.rallycopilot.ui.tutorial.BienvenidaRoute
import app.trombito.rallycopilot.ui.tutorial.EstadoPistas
import app.trombito.rallycopilot.ui.tutorial.LocalPistas
import app.trombito.rallycopilot.ui.tutorial.TutorialViewModel

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
fun AppNavHost(
    importacion: ImportarViewModel = hiltViewModel(),
    tutorial: TutorialViewModel = hiltViewModel(),
) {
    val nav = rememberNavController()
    NavegarAlImportarDeFuera(importacion, nav)
    val vistas by tutorial.pistasVistas.collectAsStateWithLifecycle()
    val pistas = remember(vistas) { EstadoPistas(vistas, tutorial::onPistaEntendida) }
    // La bienvenida va antes de Inicio solo la primera vez; sin parpadeo porque el flag se lee al crear el ViewModel.
    val arranque: Any = if (tutorial.bienvenidaVista) RutaInicio else RutaBienvenida
    CompositionLocalProvider(LocalPistas provides pistas) {
        NavHost(navController = nav, startDestination = arranque) { destinos(nav, importacion, tutorial) }
    }
}

private fun NavGraphBuilder.destinos(
    nav: NavHostController,
    importacion: ImportarViewModel,
    tutorial: TutorialViewModel,
) {
    composable<RutaInicio> { InicioRoute(onEntrada = { abrirEntrada(nav, it) }) }
    composable<RutaMapa> { DestinoMapa(nav) }
    composable<RutaMapasOffline> {
        MapasOfflineRoute(onVolver = { nav.popBackStack() }, onDescargarZona = { nav.navigate(RutaZonaDetalle) })
    }
    composable<RutaZonaDetalle> { ZonaDetalleRoute(onVolver = { nav.popBackStack() }) }
    destinosAjustes(nav, tutorial)
    destinosTutorial(nav, tutorial)
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

/** La bienvenida (primer arranque o «Ver el tutorial otra vez») y las dos pantallas de ayuda de Ajustes. */
private fun NavGraphBuilder.destinosTutorial(
    nav: NavHostController,
    tutorial: TutorialViewModel,
) {
    composable<RutaBienvenida> {
        BienvenidaRoute(
            onTerminar = {
                tutorial.onBienvenidaVista()
                // Desde Ajustes se vuelve a Ajustes; en el primer arranque (sin pantalla debajo), se va a Inicio.
                if (nav.previousBackStackEntry != null) {
                    nav.popBackStack()
                } else {
                    nav.navigate(RutaInicio) { popUpTo<RutaBienvenida> { inclusive = true } }
                }
            },
        )
    }
    composable<RutaAyudaNotas> { AyudaNotasScreen(onVolver = { nav.popBackStack() }) }
    composable<RutaAyudaVoz> { AyudaVozScreen(onVolver = { nav.popBackStack() }) }
}

/** Portada de Ajustes y una subpantalla por sección; Acerca de y Mapas offline tienen su ruta propia. */
private fun NavGraphBuilder.destinosAjustes(
    nav: NavHostController,
    tutorial: TutorialViewModel,
) {
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
            ayuda =
                AccionesAyuda(
                    onVerTutorial = {
                        tutorial.onReiniciar()
                        nav.navigate(RutaBienvenida)
                    },
                    onNotas = { nav.navigate(RutaAyudaNotas) },
                    onVoz = { nav.navigate(RutaAyudaVoz) },
                ),
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
