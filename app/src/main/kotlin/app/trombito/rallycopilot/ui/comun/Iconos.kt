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

    /** Rayo: la ruta más rápida. */
    val Rayo: ImageVector by lazy {
        icono("Rayo", "M13,2 L5,14 L11,14 L10,22 L19,9 L13,9Z")
    }

    /** Carretera con curvas: la ruta sinuosa. */
    val Curvas: ImageVector by lazy {
        icono("Curvas", "M5,22 C5,10 13,14 13,2 L19,2 C19,14 11,10 11,22Z")
    }

    /** Bandera a cuadros (cuadrícula 4x3 con huecos alternos): terminar el tramo. */
    val Bandera: ImageVector by lazy {
        icono(
            "Bandera",
            "M5,3 H7 V21 H5Z M7,4 H19 V13 H7Z " +
                "M10,4 V7 H13 V4Z M16,4 V7 H19 V4Z M7,7 V10 H10 V7Z M13,7 V10 H16 V7Z " +
                "M10,10 V13 H13 V10Z M16,10 V13 H19 V10Z",
        )
    }

    /** Cronómetro (Material «timer»): el tiempo de la conducción. */
    val Cronometro: ImageVector by lazy {
        icono(
            "Cronometro",
            "M9 1h6v2H9zM11 14h2V8h-2zM19.03 7.39l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 " +
                "14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 " +
                "0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
        )
    }

    /** Tres deslizadores: ajustes rápidos. */
    val Deslizadores: ImageVector by lazy {
        icono(
            "Deslizadores",
            "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10z" +
                "m-6-4h2V7h4V5h-4V3h-2v6z",
        )
    }

    val Volumen: ImageVector by lazy {
        icono(
            "Volumen",
            "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06" +
                "c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z",
        )
    }

    val Microfono: ImageVector by lazy {
        icono(
            "Microfono",
            "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3" +
                "-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z",
        )
    }

    val Informacion: ImageVector by lazy {
        icono(
            "Informacion",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z",
        )
    }

    val Descarga: ImageVector by lazy {
        icono("Descarga", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z")
    }

    /** Nota grande (tarjeta con marco) sobre dos pequeñas: el modo roadbook de notas enormes. */
    val NotasGrandes: ImageVector by lazy {
        icono(
            "NotasGrandes",
            "M3,4 H21 V14 H3Z M5,6 V12 H19 V6Z M3,16 H11 V20 H3Z M13,16 H21 V20 H13Z",
        )
    }

    /** Piloto (Material «person»): el perfil. */
    val Piloto: ImageVector by lazy {
        icono(
            "Piloto",
            "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4z" +
                "m0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
        )
    }

    /** Chevron a la derecha: una fila que abre otra pantalla. */
    val Siguiente: ImageVector by lazy {
        icono("Siguiente", "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z")
    }

    val Notas: ImageVector by lazy {
        icono("Notas", "M3 18h12v-2H3v2zM3 6v2h18V6H3zm0 7h18v-2H3v2z")
    }

    val TemaOscuro: ImageVector by lazy {
        icono(
            "TemaOscuro",
            "M12 3a9 9 0 1 0 9 9c0-.46-.04-.92-.1-1.36a5.39 5.39 0 0 1-4.4 2.26 5.4 5.4 0 0 1-3.14-9.8" +
                "c-.44-.06-.9-.1-1.36-.1z",
        )
    }

    val TemaClaro: ImageVector by lazy {
        icono(
            "TemaClaro",
            "M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zM11 1h2v3h-2zM11 20h2v3h-2zM1 11h3v2H1zM20 11h3v2h-3z" +
                "M4.2 5.6l1.4-1.4 2.1 2.1-1.4 1.4zM16.3 17.7l1.4-1.4 2.1 2.1-1.4 1.4zM4.2 18.4l2.1-2.1 1.4 1.4" +
                "-2.1 2.1zM16.3 6.3l2.1-2.1 1.4 1.4-2.1 2.1z",
        )
    }

    val TemaSistema: ImageVector by lazy {
        icono(
            "TemaSistema",
            "M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z",
        )
    }

    /** Triángulo de play: empezar la ruta. */
    val Empezar: ImageVector by lazy {
        icono("Empezar", "M8,5 V19 L19,12Z")
    }

    /** Dos barras: pausar la repetición. */
    val Pausa: ImageVector by lazy {
        icono("Pausa", "M6,19 H10 V5 H6Z M14,5 V19 H18 V5Z")
    }

    val Lugar: ImageVector by lazy {
        icono(
            "Lugar",
            "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5" +
                "-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z",
        )
    }

    val FlechaArriba: ImageVector by lazy {
        icono("FlechaArriba", "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z")
    }

    val FlechaAbajo: ImageVector by lazy {
        icono("FlechaAbajo", "M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z")
    }

    /** Señal de dirección en rombo con flecha: «Ir aquí». */
    val Direcciones: ImageVector by lazy {
        icono(
            "Direcciones",
            "M21.71 11.29l-9-9c-.39-.39-1.02-.39-1.41 0l-9 9c-.39.39-.39 1.02 0 1.41l9 9c.39.39 1.02.39 1.41 0l9-9" +
                "c.39-.38.39-1.01 0-1.41zM14 14.5V12h-4v3H8v-4c0-.55.45-1 1-1h5V7.5l3.5 3.5-3.5 3.5z",
        )
    }

    /** Pin con un más: «Añadir parada». */
    val AnadirLugar: ImageVector by lazy {
        icono(
            "AnadirLugar",
            "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm4 8h-3v3h-2v-3H8V8h3V5h2v3h3v2z",
        )
    }

    /** Documento con flecha hacia arriba: «Importar un archivo». */
    val Importar: ImageVector by lazy {
        icono(
            "Importar",
            "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z" +
                "M8 15.01l1.41 1.41L11 14.84V19h2v-4.16l1.59 1.59L16 15.01 12.01 11 8 15.01z",
        )
    }

    /** Línea quebrada con nodos: «Dibujar una ruta». */
    val Dibujar: ImageVector by lazy {
        icono(
            "Dibujar",
            "M23 8c0 1.1-.9 2-2 2-.18 0-.35-.02-.51-.07l-3.56 3.55c.05.16.07.34.07.52 0 1.1-.9 2-2 2s-2-.9-2-2" +
                "c0-.18.02-.36.07-.52l-2.55-2.55c-.16.05-.34.07-.52.07s-.36-.02-.52-.07l-4.55 4.56c.05.16.07.33.07.51" +
                " 0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2c.18 0 .35.02.51.07l4.56-4.55C8.02 9.36 8 9.18 8 9c0-1.1.9-2 2-2" +
                "s2 .9 2 2c0 .18-.02.36-.07.52l2.55 2.55c.16-.05.34-.07.52-.07s.36.02.52.07l3.55-3.56" +
                "C19.02 8.35 19 8.18 19 8c0-1.1.9-2 2-2s2 .9 2 2z",
        )
    }

    /** Flecha curva hacia atrás: «Deshacer». */
    val Deshacer: ImageVector by lazy {
        icono(
            "Deshacer",
            "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31" +
                " 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z",
        )
    }

    /** Marca de verificación: «Guardar». */
    val Hecho: ImageVector by lazy {
        icono("Hecho", "M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z")
    }

    val ExpandirMenos: ImageVector by lazy {
        icono("ExpandirMenos", "M12 8l-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14z")
    }

    val ExpandirMas: ImageVector by lazy {
        icono("ExpandirMas", "M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z")
    }

    /** Signo más: «más» de un contador. */
    val Mas: ImageVector by lazy {
        icono("Mas", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    }

    /** Signo menos: «menos» de un contador. */
    val Menos: ImageVector by lazy {
        icono("Menos", "M19 13H5v-2h14v2z")
    }

    val Borrar: ImageVector by lazy {
        icono("Borrar", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z")
    }

    val Editar: ImageVector by lazy {
        icono(
            "Editar",
            "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34" +
                "c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
        )
    }

    /** Estrella llena: favorito. */
    val Estrella: ImageVector by lazy {
        icono("Estrella", "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z")
    }

    /** Estrella con borde: no favorito. */
    val EstrellaVacia: ImageVector by lazy {
        icono(
            "EstrellaVacia",
            "M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24z" +
                "M12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.33 2.88 1 4.28L12 15.4z",
        )
    }

    /** Línea con picos: ver el análisis. */
    val Grafico: ImageVector by lazy {
        icono("Grafico", "M3.5 18.49l6-6.01 4 4L22 6.92l-1.41-1.41-7.09 7.97-4-4L2 16.99z")
    }
}
