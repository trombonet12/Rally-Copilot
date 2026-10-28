package app.trombito.rallycopilot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Letra del sistema, como en Sous Chef. Los titulares van en negrita cursiva con tracking negativo, al estilo de los
 * rótulos de retransmisión de rally; el cuerpo, en Normal.
 */
fun rallyTypography(): Typography {
    val base = Typography()

    fun TextStyle.rotulo(
        weight: FontWeight,
        tracking: Double = 0.0,
    ) = copy(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontStyle = FontStyle.Italic,
        letterSpacing = tracking.sp,
    )

    fun TextStyle.ui(weight: FontWeight) = copy(fontFamily = FontFamily.Default, fontWeight = weight)

    fun TextStyle.texto() = copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal)
    return base.copy(
        displayLarge = base.displayLarge.rotulo(FontWeight.Black, -1.0),
        displayMedium = base.displayMedium.rotulo(FontWeight.Black, -0.8),
        displaySmall = base.displaySmall.rotulo(FontWeight.ExtraBold, -0.5),
        headlineLarge = base.headlineLarge.rotulo(FontWeight.ExtraBold, -0.5),
        headlineMedium = base.headlineMedium.rotulo(FontWeight.Bold, -0.3),
        headlineSmall = base.headlineSmall.rotulo(FontWeight.Bold, -0.2),
        titleLarge = base.titleLarge.ui(FontWeight.Bold),
        titleMedium = base.titleMedium.ui(FontWeight.SemiBold),
        titleSmall = base.titleSmall.ui(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.texto().copy(fontSize = 18.sp, lineHeight = 27.sp),
        bodyMedium = base.bodyMedium.texto().copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodySmall = base.bodySmall.texto().copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.ui(FontWeight.Bold),
        labelMedium = base.labelMedium.ui(FontWeight.SemiBold),
        labelSmall = base.labelSmall.ui(FontWeight.SemiBold),
    )
}
