package app.trombito.rallycopilot.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Estilos nombrados del sistema WRC. No llevan color: lo pone quien los usa (onSurface, primary…). Tamaños en sp.

private fun cifra(
    tamano: Int,
    tracking: Double,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Black,
    fontStyle = FontStyle.Italic,
    fontSize = tamano.sp,
    lineHeight = (tamano * 1.1).sp,
    letterSpacing = tracking.sp,
)

/** Cifra protagonista de conducción (crono, velocidad grande): cursiva Black 72 sp. */
val CifraGrande = cifra(72, -2.0)

/** Cifra media (velocidad en la píldora, parciales): cursiva Black 40 sp. */
val CifraMedia = cifra(40, -1.0)

/** Rótulo de conducción (nombre del tramo, maniobra, deltas): cursiva Black 28 sp. */
val Rotulo = cifra(28, -0.5)

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
