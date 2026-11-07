package app.trombito.rallycopilot.data.busqueda

import android.database.sqlite.SQLiteDatabase
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.cos

/**
 * El pueblo, la ciudad o la aldea más cercana a un punto, sobre la tabla `lugares`: un recuadro de [radioM] alrededor
 * del punto recorta las filas y la distancia exacta (haversine) decide si entra en el radio. La posición va siempre
 * como argumentos `?`, nunca dentro del SQL.
 */
internal object LugarCercano {
    private const val METROS_POR_GRADO = 111_320.0
    private const val CANDIDATOS = 5

    fun buscar(
        db: SQLiteDatabase,
        punto: PuntoGeo,
        radioM: Double,
    ): ResultadoBusqueda? {
        val dLat = radioM / METROS_POR_GRADO
        val dLon = radioM / (METROS_POR_GRADO * cos(Math.toRadians(punto.lat)).coerceAtLeast(MIN_COS))
        val argumentos =
            arrayOf(
                (punto.lat - dLat).toString(),
                (punto.lat + dLat).toString(),
                (punto.lon - dLon).toString(),
                (punto.lon + dLon).toString(),
            ) + argumentosDistancia(punto)
        val candidatos = ArrayList<ResultadoBusqueda>()
        db
            .rawQuery(
                "$SQL_SELECT WHERE categoria IN ('ciudad', 'pueblo', 'aldea') " +
                    "AND lat BETWEEN CAST(? AS REAL) AND CAST(? AS REAL) " +
                    "AND lon BETWEEN CAST(? AS REAL) AND CAST(? AS REAL) " +
                    "ORDER BY $SQL_DISTANCIA LIMIT $CANDIDATOS",
                argumentos,
            ).use { leerResultados(it, candidatos) }
        return candidatos.filter { it.punto.distanciaA(punto) <= radioM }.minByOrNull { it.punto.distanciaA(punto) }
    }

    private const val MIN_COS = 0.01
}
