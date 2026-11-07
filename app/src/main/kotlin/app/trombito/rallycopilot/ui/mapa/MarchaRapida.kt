package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.trombito.rallycopilot.ui.comun.esperarPorFotogramas

/** Por encima de esta velocidad (km/h) los botones que se usan parado (capas, notas grandes, recentrar) se esconden. */
internal const val VELOCIDAD_OCULTAR_BOTONES_KMH = 20

/** Por debajo de esta velocidad (km/h), mantenida [PARADO_PARA_MOSTRAR_MS], vuelven. Entre las dos, no cambia nada. */
internal const val VELOCIDAD_MOSTRAR_BOTONES_KMH = 10

/** Tiempo que hay que ir despacio (o parado) para que los botones vuelvan: así no parpadean en una curva lenta. */
internal const val PARADO_PARA_MOSTRAR_MS = 3_000L

/** Dónde cae la velocidad respecto a los dos umbrales con histéresis. */
internal enum class ZonaVelocidad { RAPIDA, INTERMEDIA, LENTA }

/** Clasifica la [velocidadKmh]; sin dato (null, sin GPS o velocidad caducada) cuenta como parado. */
internal fun zonaDeVelocidad(velocidadKmh: Int?): ZonaVelocidad =
    when {
        velocidadKmh == null -> ZonaVelocidad.LENTA
        velocidadKmh > VELOCIDAD_OCULTAR_BOTONES_KMH -> ZonaVelocidad.RAPIDA
        velocidadKmh < VELOCIDAD_MOSTRAR_BOTONES_KMH -> ZonaVelocidad.LENTA
        else -> ZonaVelocidad.INTERMEDIA
    }

/**
 * Si se va lo bastante deprisa para esconder los botones secundarios: se esconden al pasar de 20 km/h y vuelven tras
 * 3 s por debajo de 10 km/h (parado). Entre 10 y 20 km/h se queda como estaba, para que no parpadeen.
 */
@Composable
internal fun rememberMarchaRapida(velocidadKmh: Int?): Boolean {
    var rapida by remember { mutableStateOf(zonaDeVelocidad(velocidadKmh) == ZonaVelocidad.RAPIDA) }
    val zona = zonaDeVelocidad(velocidadKmh)
    LaunchedEffect(zona) {
        when (zona) {
            ZonaVelocidad.RAPIDA -> {
                rapida = true
            }

            ZonaVelocidad.LENTA -> {
                esperarPorFotogramas(PARADO_PARA_MOSTRAR_MS)
                rapida = false
            }

            ZonaVelocidad.INTERMEDIA -> {
                Unit
            }
        }
    }
    return rapida
}
