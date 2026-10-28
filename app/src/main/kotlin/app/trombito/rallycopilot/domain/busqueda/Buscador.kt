package app.trombito.rallycopilot.domain.busqueda

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Un resultado del índice offline de Mallorca (`busqueda-mallorca.sqlite`). [categoria] en español: playa, pueblo… */
data class ResultadoBusqueda(
    val nombre: String,
    val categoria: String,
    val municipio: String?,
    val punto: PuntoGeo,
)

/** Búsqueda de destino sin red en lugares, calles y POIs de OSM (coordenadas pegadas: `EnlaceUbicacion`). */
interface Buscador {
    /** Vacío sin índice instalado o con menos de 2 letras. A igual importancia, [cerca] ordena por cercanía. */
    suspend fun buscar(
        texto: String,
        cerca: PuntoGeo?,
        limite: Int = 20,
    ): List<ResultadoBusqueda>
}
