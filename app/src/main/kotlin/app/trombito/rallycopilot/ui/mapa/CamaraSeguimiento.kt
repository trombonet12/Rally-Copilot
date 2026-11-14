package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

/** Inclinación del mapa con el rumbo arriba (vista 3D de videojuego): se ve más carretera por delante. */
internal const val INCLINACION_RUMBO = 50.0

/** Con el norte arriba el mapa va plano. */
internal const val INCLINACION_NORTE = 0.0

/** Altura (fracción del alto del mapa, desde arriba) a la que queda el marcador en vertical. */
internal const val MARCADOR_VERTICAL = 0.80

/**
 * En horizontal hay menos alto: el marcador no baja tanto. El mapa va a pantalla completa y la hoja de conducción
 * (96-112 dp) lo tapa por abajo: con estos valores el coche queda justo por encima de ella.
 */
internal const val MARCADOR_HORIZONTAL = 0.70

/** Inclinación del mapa mientras sigue al coche según la orientación elegida. */
internal fun inclinacionSeguimiento(orientacion: Orientacion): Double =
    if (orientacion == Orientacion.NORTE) INCLINACION_NORTE else INCLINACION_RUMBO

/** Separación entre el borde superior de la píldora de velocidad y el borde inferior del icono del coche. */
internal const val MARGEN_SOBRE_ZONA_DP = 5

/** Alto del icono de posición más alto (el casco, 60 dp): el coche se coloca por su centro. */
internal const val ALTO_ICONO_MAX_DP = 60

/** Mitad del icono de posición más alto: lo que sube el centro sobre el margen si no se conoce el icono. */
internal const val MITAD_ICONO_MAX_DP = ALTO_ICONO_MAX_DP / 2

/** Alto (dp) del icono de posición elegido: el casco mide 60 dp y los demás 48 dp. */
internal fun altoIconoDp(icono: IconoPosicion): Int =
    if (icono == IconoPosicion.CASCO) ALTO_ICONO_MAX_DP else ALTO_ICONO_DP

private const val ALTO_ICONO_DP = 48

/** La zona inferior solo deja bajar el coche si se mantiene más baja este tiempo (la flecha de exceso no lo rebota). */
internal const val RETARDO_BAJADA_ZONA_MS = 3_000L

/** Duración del cambio de relleno cuando la zona inferior cambia con la cámara ya siguiendo. */
internal const val DURACION_RELLENO_ZONA_MS = 400L

/** Con el centro del mapa en la mitad el relleno superior es 0: más arriba no se puede subir el coche. */
private const val MARCADOR_MINIMO = 0.5

/**
 * Altura (fracción del alto del mapa, desde arriba) a la que queda el coche: la de siempre ([MARCADOR_VERTICAL] o
 * [MARCADOR_HORIZONTAL]) o, en vertical, más arriba si la zona inferior ([zonaInferiorPx]: la píldora de velocidad con
 * la burbuja de escucha si la hay y la hoja de conducción, medida desde el borde inferior del mapa; 0 = sin medir) lo
 * taparía: el borde inferior del icono ([altoIconoDp]) queda [MARGEN_SOBRE_ZONA_DP] sobre el borde superior de la
 * píldora, es decir, el centro a esa distancia más la mitad del icono. Los botones de la derecha no se miden (quedan
 * fuera de la vertical del coche). En horizontal la píldora va en la columna de la izquierda y no se mide.
 */
internal fun fraccionMarcador(
    anchoPx: Int,
    altoPx: Int,
    zonaInferiorPx: Int = 0,
    densidad: Float = 1f,
    altoIconoDp: Int = ALTO_ICONO_MAX_DP,
): Double {
    val horizontal = anchoPx > altoPx
    val porDefecto = if (horizontal) MARCADOR_HORIZONTAL else MARCADOR_VERTICAL
    if (altoPx <= 0 || horizontal || zonaInferiorPx <= 0) return porDefecto
    val reservaPx = (MARGEN_SOBRE_ZONA_DP + altoIconoDp / 2.0) * densidad.toDouble()
    return ((altoPx - zonaInferiorPx - reservaPx) / altoPx).coerceIn(MARCADOR_MINIMO, porDefecto)
}

/**
 * Relleno superior (px) del mapa para que el centro de la cámara, donde queda el marcador, esté a [fraccion] del alto
 * (por defecto la de [fraccionMarcador] sin zona medida: [MARCADOR_VERTICAL], o [MARCADOR_HORIZONTAL] si el mapa es
 * apaisado); el centro de la zona visible es `relleno + (alto - relleno) / 2`. Es un relleno fijo del mapa, no una
 * animación: las fijaciones de 1 Hz no lo cancelan ni lo mueven.
 */
internal fun rellenoSuperiorSeguimiento(
    anchoPx: Int,
    altoPx: Int,
    fraccion: Double = fraccionMarcador(anchoPx, altoPx),
): Int {
    if (altoPx <= 0) return 0
    return ((2 * fraccion - 1) * altoPx).toInt().coerceAtLeast(0)
}

/**
 * Alto estable de la zona inferior: sube de inmediato cuando la zona crece y baja solo si se mantiene más baja durante
 * [retardoMs]; un valor nuevo antes de que pase el tiempo reinicia la espera (y uno igual o mayor que el aplicado la
 * cancela). Así la flecha de exceso o la burbuja de escucha, que aparecen y desaparecen, no hacen rebotar la cámara.
 */
internal fun Flow<Int>.sinRebotes(retardoMs: Long = RETARDO_BAJADA_ZONA_MS): Flow<Int> =
    channelFlow {
        var aplicada: Int? = null
        collectLatest { medida ->
            val actual = aplicada
            if (actual != null && medida < actual) delay(retardoMs)
            aplicada = medida
            send(medida)
        }
    }

/**
 * Alto (px, desde el borde inferior del mapa) de la zona inferior de conducción (píldora y hoja). [medidaPx] lo
 * escribe la pantalla al medirla y [establePx] lo lee el mapa ya sin rebotes (ver [sinRebotes]); 0 = sin medir.
 */
internal class ZonaInferiorMedida {
    val medidaPx = mutableIntStateOf(0)
    val establePx = mutableIntStateOf(0)
}

/** La zona inferior de la pantalla del mapa; null fuera de ella (el mapa usa entonces la altura de siempre). */
internal val LocalZonaInferior = staticCompositionLocalOf<ZonaInferiorMedida?> { null }

/**
 * Distingue quién descarta el seguimiento. Al cambiar de modo con el componente de ubicación ya siguiendo (norte arriba
 * a rumbo arriba, o al reponer el relleno) MapLibre avisa de que el seguimiento se descartó, igual que cuando el
 * usuario mueve el mapa: sin distinguirlos, el propio cambio de orientación dejaba la cámara sin seguir, sin inclinar
 * y con el botón de recentrar. El aviso llega durante la llamada, de ahí que baste una marca síncrona.
 */
internal class DescarteSeguimiento {
    private var aplicandoApp = false

    /** El descarte que se acaba de avisar lo hizo el usuario (y no la app al aplicar el modo de cámara). */
    fun esDelUsuario(): Boolean = !aplicandoApp

    /** Ejecuta [accion] (modo, relleno e inclinación de la cámara) marcando que los descartes son de la app. */
    fun <T> aplicando(accion: () -> T): T {
        val previo = aplicandoApp
        aplicandoApp = true
        try {
            return accion()
        } finally {
            aplicandoApp = previo
        }
    }
}
