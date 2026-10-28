package app.trombito.rallycopilot.ui.comun

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Iconos de Material (Apache 2.0) como vectores propios: no se añade material-icons como dependencia por tres trazados.

private const val LADO = 24f

private fun icono(
    nombre: String,
    trazado: String,
): ImageVector {
    val constructor =
        ImageVector.Builder(
            name = nombre,
            defaultWidth = LADO.dp,
            defaultHeight = LADO.dp,
            viewportWidth = LADO,
            viewportHeight = LADO,
        )
    constructor.addPath(pathData = addPathNodes(trazado), fill = SolidColor(Color.Black))
    return constructor.build()
}

object Iconos {
    val Atras: ImageVector by lazy {
        icono("Atras", "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z")
    }

    val Cerrar: ImageVector by lazy {
        icono(
            "Cerrar",
            "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 " +
                "19 17.59 13.41 12z",
        )
    }

    val MiUbicacion: ImageVector by lazy {
        icono(
            "MiUbicacion",
            "M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3c-.46-4.17-3.77-7.48-7.94-7.94" +
                "V1h-2v2.06C6.83 3.52 3.52 6.83 3.06 11H1v2h2.06c.46 4.17 3.77 7.48 7.94 7.94V23h2v-2.06" +
                "c4.17-.46 7.48-3.77 7.94-7.94H23v-2h-2.06zM12 19c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
        )
    }
}
