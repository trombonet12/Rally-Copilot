package app.trombito.rallycopilot.domain.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Ruta

/** Criterio de la ruta: la más rápida o la que prefiere carreteras sinuosas (sin autopistas ni peajes). */
enum class PerfilRuta { MAS_RAPIDA, SINUOSA }

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
}

sealed interface ResultadoRuta {
    /** [calculoMs]: tiempo total del cálculo; en la primera llamada incluye abrir el motor y los datos. */
    data class Ok(
        val ruta: Ruta,
        val calculoMs: Long,
    ) : ResultadoRuta

    /** El paquete de datos no incluye (o aún no se ha instalado) el archivo de rutas. */
    data object SinDatos : ResultadoRuta

    data class Error(
        val motivo: String,
    ) : ResultadoRuta
}
