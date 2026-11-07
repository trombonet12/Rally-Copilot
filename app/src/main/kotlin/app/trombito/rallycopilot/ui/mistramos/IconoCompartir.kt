package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val LADO = 24f

/** Compartir (Material «share», Apache 2.0): tres nodos unidos. Vector propio, sin añadir material-icons. */
internal val IconoCompartir: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "Compartir",
            defaultWidth = LADO.dp,
            defaultHeight = LADO.dp,
            viewportWidth = LADO,
            viewportHeight = LADO,
        ).apply {
            addPath(
                pathData =
                    addPathNodes(
                        "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11" +
                            "c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 " +
                            "9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16" +
                            "c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92" +
                            "-1.31-2.92-2.92-2.92z",
                    ),
                fill = SolidColor(Color.Black),
            )
        }.build()
}
