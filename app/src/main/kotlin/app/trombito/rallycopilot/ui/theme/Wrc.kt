package app.trombito.rallycopilot.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Sistema visual común «WRC premium». Todas las pantallas usan estos tokens y los componentes de `ui/comun`
// (TarjetaWrc, BotonRedondoMapa, PildoraVelocidad…). El rojo es solo acento: nunca relleno de tarjeta, banner o botón.

/** Carbón translúcido de las tarjetas y botones que flotan sobre el mapa (90 % de opacidad). */
val CarbonTranslucido = Color(0xE60E0F12)

/** Carbón opaco: el mismo tono sin transparencia (iconos y bordes que deben leerse sobre cualquier mapa). */
val CarbonOpaco = Color(0xFF0E0F12)

/** Texto principal sobre el carbón, sea cual sea el tema: el carbón es oscuro también en el tema claro. */
val TextoSobreCarbon = Color(0xFFECEDEF)

/** Texto secundario sobre el carbón. */
val TextoSecundarioSobreCarbon = Color(0xFFBFC3C9)

/** Ámbar de la marca sobre el carbón (el `primary` del tema oscuro, fijo). */
val AmbarSobreCarbon = Color(0xFFE5AB46)

/**
 * El único rojo de la marca: barras de acento, aros de señal, franja de cabecera, exceso y ruta (nunca como relleno).
 * `ColoresRally.franja` y `RojoRuta` apuntan aquí; no se definen más rojos de marca.
 */
val RojoAcento = Color(0xFFE02A32)

/** Radios de esquina: la única tabla; el tema de Material ([formasMaterial]) se deriva de ella. */
object Radios {
    val diminuto = 4.dp
    val pequeno = 8.dp
    val tarjeta = 12.dp
    val hoja = 16.dp
    val pildora = 24.dp

    val formaTarjeta = RoundedCornerShape(tarjeta)
    val formaHoja = RoundedCornerShape(topStart = hoja, topEnd = hoja)
    val formaPanel = RoundedCornerShape(hoja)
    val formaPildora = RoundedCornerShape(pildora)
    val formaCirculo = CircleShape

    /** Formas del tema de Material 3: los componentes sin personalizar heredan estos radios. */
    val formasMaterial =
        Shapes(
            extraSmall = RoundedCornerShape(diminuto),
            small = RoundedCornerShape(pequeno),
            medium = formaTarjeta,
            large = formaPanel,
            extraLarge = formaPildora,
        )
}

/** Duraciones de animación en milisegundos. */
object Movimiento {
    const val CORTA = 150
    const val MEDIA = 300
    const val LARGA = 500

    /** Semiperíodo del pulso rojo del exceso de velocidad. */
    const val PULSO_EXCESO = 600

    /** Semiperíodo del brillo de los esqueletos de carga. */
    const val ESQUELETO = 900
}

/** Medidas fijas del sistema visual. */
object Medidas {
    val iconoLista = 24.dp
    val iconoCabecera = 28.dp
    val iconoVacio = 48.dp
    val zonaTactil = 48.dp
    val accionPrincipal = 56.dp

    /** Todos los botones redondos de la columna del mapa (capas, notas grandes, micrófono, modo, silencio, brújula). */
    val botonMapa = 56.dp
    val indicadorCarga = 32.dp
    val senalLimite = 30.dp
    val bordeActivo = 1.5.dp
    val barraAcento = 4.dp
    val alturaDegradadoMapa = 96.dp
    val asaAncho = 32.dp
    val asaAlto = 4.dp
    val sombraMapa = 6.dp
}
