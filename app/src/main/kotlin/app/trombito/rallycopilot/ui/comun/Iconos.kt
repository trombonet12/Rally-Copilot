package app.trombito.rallycopilot.ui.comun

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Iconos de Material (Apache 2.0) como vectores propios: no se añade material-icons como dependencia por unos trazados.

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

    val Capas: ImageVector by lazy {
        icono(
            "Capas",
            "M11.99 18.54l-7.37-5.73L3 14.07l9 7 9-7-1.63-1.27-7.38 5.74zM12 16l7.36-5.73L21 9l-9-7-9 7 " +
                "1.63 1.27L12 16z",
        )
    }

    val Mapa: ImageVector by lazy {
        icono(
            "Mapa",
            "M20.5 3l-.16.03L15 5.1 9 3 3.36 4.9c-.21.07-.36.25-.36.48V20.5c0 .28.22.5.5.5l.16-.03L9 18.9l6 " +
                "2.1 5.64-1.9c.21-.07.36-.25.36-.48V3.5c0-.28-.22-.5-.5-.5zM15 19l-6-2.11V5l6 2.11V19z",
        )
    }

    /** Satélite dibujado a mano: cuerpo en rombo con dos paneles solares y sus brazos. */
    val Satelite: ImageVector by lazy {
        icono(
            "Satelite",
            "M12,8 L16,12 L12,16 L8,12Z M10.59,5.78 L7.05,2.24 L2.24,7.05 L5.78,10.59Z " +
                "M10.66,9.81 L8.46,7.62 L7.62,8.46 L9.81,10.66Z M13.41,18.22 L16.95,21.76 L21.76,16.95 " +
                "L18.22,13.41Z M13.34,14.19 L15.54,16.38 L16.38,15.54 L14.19,13.34Z",
        )
    }

    val Relieve: ImageVector by lazy {
        icono("Relieve", "M14 6l-3.75 5 2.85 3.8-1.6 1.2C9.81 13.75 7 10 7 10l-6 8h22L14 6z")
    }

    /** Rumbo arriba: la flecha de navegación. */
    val Rumbo: ImageVector by lazy {
        icono("Rumbo", "M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z")
    }

    /** Norte arriba: la brújula. */
    val Brujula: ImageVector by lazy {
        icono(
            "Brujula",
            "M12 10.9c-.61 0-1.1.49-1.1 1.1s.49 1.1 1.1 1.1c.61 0 1.1-.49 1.1-1.1s-.49-1.1-1.1-1.1zM12 2C6.48 2 " +
                "2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm2.19 12.19L6 18l3.81-8.19L18 6l-3.81 8.19z",
        )
    }

    val Lupa: ImageVector by lazy {
        icono(
            "Lupa",
            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 " +
                "16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 " +
                "5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
        )
    }
}
