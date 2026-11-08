package app.trombito.rallycopilot.ui

import app.trombito.rallycopilot.ui.ajustes.SeccionAjustes
import kotlinx.serialization.Serializable

/** Pantalla de mapa (modo libre provisional hasta que existan los tramos). */
@Serializable
data object RutaMapa

@Serializable
data object RutaMapasOffline

/** Elegir una zona del mapa para descargarla a z18-19. */
@Serializable
data object RutaZonaDetalle

/** Portada de Ajustes: una tarjeta por sección con su valor actual. */
@Serializable
data object RutaAjustes

/** Subpantalla de una sección de Ajustes: vista previa fija arriba y los controles debajo. */
@Serializable
data class RutaAjustesSeccion(
    val seccion: SeccionAjustes,
)

/** Bienvenida del primer arranque (y «Ver el tutorial otra vez» de Ajustes > Ayuda). */
@Serializable
data object RutaBienvenida

/** Ajustes > Ayuda > Qué significan las notas. */
@Serializable
data object RutaAyudaNotas

/** Ajustes > Ayuda > Qué le puedes decir. */
@Serializable
data object RutaAyudaVoz

@Serializable
data object RutaAcercaDe

/** Lista de «Mis tramos». */
@Serializable
data object RutaMisTramos

/** «Mis carreteras»: mapa de calor de las vías conducidas y estadísticas. */
@Serializable
data object RutaMisCarreteras

/** Mapa en modo dibujo: tocar puntos y guardar la ruta como tramo. */
@Serializable
data object RutaDibujar

/** Detalle de un tramo guardado. */
@Serializable
data class RutaTramo(
    val tramoId: String,
)

/** Edición de las notas y los sectores de un tramo guardado. */
@Serializable
data class RutaEditarTramo(
    val tramoId: String,
)

/** Análisis de una pasada de un tramo. */
@Serializable
data class RutaAnalisis(
    val tramoId: String,
    val pasadaId: Long,
)

/** Perfil del piloto. */
@Serializable
data object RutaPerfil
