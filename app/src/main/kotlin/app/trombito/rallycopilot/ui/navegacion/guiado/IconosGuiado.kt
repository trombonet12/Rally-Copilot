package app.trombito.rallycopilot.ui.navegacion.guiado

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Iconos de Material (Apache 2.0) como vectores propios, igual que ui/comun/Iconos.

private const val LADO = 24f

private fun icono(
    nombre: String,
    trazado: String,
): ImageVector =
    ImageVector
        .Builder(
            name = nombre,
            defaultWidth = LADO.dp,
            defaultHeight = LADO.dp,
            viewportWidth = LADO,
            viewportHeight = LADO,
        ).addPath(pathData = addPathNodes(trazado), fill = SolidColor(Color.Black))
        .build()

internal object IconosGuiado {
    val Recalcular: ImageVector by lazy {
        icono(
            "Recalcular",
            "M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12" +
                "c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12" +
                "c0 4.42 3.58 8 8 8v3l4-4-4-4v3z",
        )
    }

    val Bandera: ImageVector by lazy { icono("Bandera", "M14.4 6L14 4H5v17h2v-7h5.6l.4 2h7V6z") }

    val Reloj: ImageVector by lazy {
        icono(
            "Reloj",
            "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20" +
                "c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
        )
    }

    val Regla: ImageVector by lazy {
        icono(
            "Regla",
            "M21 6H3c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 10H3V8h2v4h2V8h2v4h2V8" +
                "h2v4h2V8h2v4h2V8h2v8z",
        )
    }
}
