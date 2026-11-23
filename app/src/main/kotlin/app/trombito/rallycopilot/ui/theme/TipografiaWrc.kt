package app.trombito.rallycopilot.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Estilos nombrados del sistema WRC. No llevan color: lo pone quien los usa (onSurface, primary…). Tamaños en sp.

/** Cifras de ancho fijo (`tnum`): los dígitos no bailan al cambiar y las columnas de tiempos quedan alineadas. */
private const val CIFRAS_TABULARES = "tnum"

private fun cifra(
    tamano: Int,
    tracking: Double,
    tabular: Boolean = true,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Black,
    fontStyle = FontStyle.Italic,
    fontSize = tamano.sp,
    lineHeight = (tamano * 1.1).sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tabular) CIFRAS_TABULARES else null,
)

/** Cifra grande (dato protagonista de una pantalla: velocidad, tiempo total): cursiva Black 56 sp, ancho fijo. */
val CifraGrande = cifra(56, -1.5)

/** Cifra media (velocidad en la píldora, parciales): cursiva Black 40 sp, ancho fijo. */
val CifraMedia = cifra(40, -1.0)

/** Cifra pequeña (dato de una tarjeta, totales): cursiva Black 22 sp, ancho fijo. */
val CifraPequena = cifra(22, -0.25)

/** Tiempo de tramo o parcial (`1:23.456`) fuera del HUD: cursiva Black 32 sp, ancho fijo. */
val Tiempo = cifra(32, -0.5)

/** Rótulo de conducción (nombre del tramo, maniobra, deltas): cursiva Black 28 sp. */
val Rotulo = cifra(28, -0.5, tabular = false)

/** Título de pantalla o de sección: 22 sp Bold, en ámbar (`primary`), sin mayúsculas forzadas. */
val Titulo =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )

/** Título de un estado vacío: 18 sp SemiBold. */
val TituloVacio =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )

/** Cuerpo: 16 sp. */
val Cuerpo =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

/** Ayuda y notas al pie: 14 sp, en `onSurfaceVariant`. */
val Ayuda =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

/** Etiqueta bajo un icono cuando no es obvio: 12 sp SemiBold. */
val EtiquetaIcono =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
