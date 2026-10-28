package app.trombito.rallycopilot.domain.busqueda

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Un resultado del índice offline de Mallorca (`busqueda-mallorca.sqlite`). [categoria] en español: playa, pueblo… */
data class ResultadoBusqueda(
    val nombre: String,
    val categoria: String,
    val municipio: String?,
    val punto: PuntoGeo,
)

/** Búsqueda de destino sin red: lugares, calles y POIs de OSM. Las coordenadas y enlaces pegados van aparte (motor). */
interface Buscador {
    /** Vacío si no hay índice instalado o el texto tiene menos de 2 letras. [cerca] ordena por cercanía a igual peso. */
    suspend fun buscar(
        texto: String,
        cerca: PuntoGeo?,
        limite: Int = 20,
    ): List<ResultadoBusqueda>
}
