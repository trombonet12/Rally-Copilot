package app.trombito.rallycopilot.domain.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Ruta

/**
 * Criterio de la ruta, en el orden de los chips del planificador: [MAS_RAPIDA] (Rápida), [EQUILIBRADA] (la mejor
 * ganancia de curvas por minuto extra, hasta 1,25 veces la duración de la rápida) y [SINUOSA] (Curvas: la más revirada
 * que no pase de 1,6 veces la rápida; sin autopistas ni peajes). El nombre `SINUOSA` se conserva porque es el que se
 * guarda en el guiado pendiente.
 */
enum class PerfilRuta { MAS_RAPIDA, EQUILIBRADA, SINUOSA }

/** Calcula rutas sin red con los datos de Mallorca instalados (Valhalla en el dispositivo). */
interface Enrutador {
    suspend fun ruta(
        origen: PuntoGeo,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta

    /**
     * Ruta de [origen] a [destino] pasando por las [paradas] intermedias en ese orden (una leg por tramo; la ruta
     * lleva los índices de las paradas en `Ruta.indicesParadas`). Sin paradas es [ruta].
     */
    suspend fun rutaConParadas(
        origen: PuntoGeo,
        paradas: List<PuntoGeo>,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta =
        if (paradas.isEmpty()) ruta(origen, destino, perfil) else ResultadoRuta.Error("Sin soporte de paradas")

    /**
     * Como [rutaConParadas] pero con los metros de curvas medidos (`Ok.curvasM`), para los chips de la vista previa.
     * Quien no los mide los deja en null.
     */
    suspend fun rutaMedida(
        origen: PuntoGeo,
        paradas: List<PuntoGeo>,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta = rutaConParadas(origen, paradas, destino, perfil)
}

sealed interface ResultadoRuta {
    /**
     * [calculoMs]: tiempo total del cálculo; en la primera llamada incluye abrir el motor y los datos. [curvasM]:
     * metros «de curvas» de la ruta (`PuntuacionCurvas`), o null si no se han medido.
     */
    data class Ok(
        val ruta: Ruta,
        val calculoMs: Long,
        val curvasM: Double? = null,
    ) : ResultadoRuta

    /** El paquete de datos no incluye (o aún no se ha instalado) el archivo de rutas. */
    data object SinDatos : ResultadoRuta

    data class Error(
        val motivo: String,
    ) : ResultadoRuta
}
