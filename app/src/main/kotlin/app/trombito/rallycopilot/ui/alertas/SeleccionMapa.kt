package app.trombito.rallycopilot.ui.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Lo que el usuario ha tocado en el mapa y tiene su bocadillo abierto. Solo identifica el elemento (no lleva su
 * contenido: el presentador lo busca entre los que hay en ese momento, así sobrevive al refresco de los marcadores
 * mientras el elemento siga existiendo). [clave] es lo que lo identifica: tocar el mismo otra vez cierra el bocadillo.
 */
sealed interface SeleccionMapa {
    val clave: String

    /** Incidencia de la DGT por su id. */
    data class Incidencia(
        val id: String,
    ) : SeleccionMapa {
        override val clave: String get() = "incidencia-$id"
    }

    /** Radar fijo o zona de radar móvil por su id. */
    data class Radar(
        val id: String,
    ) : SeleccionMapa {
        override val clave: String get() = "radar-$id"
    }

    /**
     * Radar de tramo por el id del tramo (se toca su marcador de entrada o su línea). [ancla] es donde se tocó la
     * línea (el bocadillo sale ahí); null = en la entrada del tramo.
     */
    data class TramoRadar(
        val id: String,
        val ancla: PuntoGeo? = null,
    ) : SeleccionMapa {
        override val clave: String get() = "tramo-radar-$id"
    }

    /** Bandera de salida o de meta de un tramo cronometrado conocido. */
    data class Bandera(
        val tramoId: String,
        val esMeta: Boolean,
    ) : SeleccionMapa {
        override val clave: String get() = "bandera-$tramoId-$esMeta"
    }

    /** Curva o aviso de la hoja pintado en el mapa, por su posición en la hoja (m). */
    data class Nota(
        val posicionM: Double,
    ) : SeleccionMapa {
        override val clave: String get() = "nota-$posicionM"
    }
}
