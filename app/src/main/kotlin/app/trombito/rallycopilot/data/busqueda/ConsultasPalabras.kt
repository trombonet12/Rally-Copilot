package app.trombito.rallycopilot.data.busqueda

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.motor.busqueda.ConsultaBusqueda
import app.trombito.rallycopilot.motor.busqueda.PalabraConsulta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.cos

internal const val SQL_SELECT = "SELECT nombre, categoria, municipio, lat, lon FROM lugares"

/** Distancia al cuadrado en grados con la longitud escalada por cos(lat): solo sirve para ordenar. */
internal const val SQL_DISTANCIA =
    "((lat - CAST(? AS REAL)) * (lat - CAST(? AS REAL)) + " +
        "(lon - CAST(? AS REAL)) * (lon - CAST(? AS REAL)) * CAST(? AS REAL))"

internal fun leerResultados(
    cursor: Cursor,
    destino: MutableList<ResultadoBusqueda>,
) {
    while (cursor.moveToNext()) {
        destino +=
            ResultadoBusqueda(
                nombre = cursor.getString(0),
                categoria = cursor.getString(1),
                municipio = cursor.getString(2)?.takeIf { it.isNotBlank() },
                punto = PuntoGeo(cursor.getDouble(3), cursor.getDouble(4)),
            )
    }
}

/** Argumentos de [SQL_DISTANCIA]: lat, lat, lon, lon y k = cos²(lat). La posición va siempre como `?`. */
internal fun argumentosDistancia(cerca: PuntoGeo): Array<String> {
    val k = cos(Math.toRadians(cerca.lat)).let { it * it }
    val lat = cerca.lat.toString()
    val lon = cerca.lon.toString()
    return arrayOf(lat, lat, lon, lon, k.toString())
}

/**
 * Búsqueda por palabras sobre la tabla `palabras` (PK `(palabra, lugar)`, `lugar` = rowid de `lugares`): cada
 * palabra de la consulta, o un sinónimo, debe ser prefijo de alguna palabra del lugar. Orden: palabras casadas
 * enteras, calles primero si se pide una calle, importancia y cercanía. Una consulta que es solo una categoría da
 * primero los más cercanos (o más importantes) de esa categoría y se completa con la búsqueda por palabras.
 */
internal object ConsultasPalabras {
    private const val MAX_PALABRAS = 8
    private const val SELECT_L = "SELECT l.nombre, l.categoria, l.municipio, l.lat, l.lon FROM lugares l"
    private const val DISTANCIA_L =
        "((l.lat - CAST(? AS REAL)) * (l.lat - CAST(? AS REAL)) + " +
            "(l.lon - CAST(? AS REAL)) * (l.lon - CAST(? AS REAL)) * CAST(? AS REAL))"
    private const val RANGO_PALABRA = "SELECT lugar FROM palabras WHERE palabra >= ? AND palabra < ?"

    fun buscar(
        db: SQLiteDatabase,
        consulta: ConsultaBusqueda,
        cerca: PuntoGeo?,
        limite: Int,
    ): List<ResultadoBusqueda> =
        when {
            consulta.vacia -> emptyList()
            consulta.categoria == null -> porPalabras(db, consulta, cerca, limite)
            else -> porCategoria(db, consulta, consulta.categoria.orEmpty(), cerca, limite)
        }

    private fun porCategoria(
        db: SQLiteDatabase,
        consulta: ConsultaBusqueda,
        categoria: String,
        cerca: PuntoGeo?,
        limite: Int,
    ): List<ResultadoBusqueda> {
        val resultados = ArrayList<ResultadoBusqueda>()
        val orden = if (cerca != null) "$SQL_DISTANCIA, importancia DESC" else "importancia DESC"
        db
            .rawQuery(
                "$SQL_SELECT WHERE categoria = ? ORDER BY $orden LIMIT $limite",
                arrayOf(categoria) + cerca?.let { argumentosDistancia(it) }.orEmpty(),
            ).use { leerResultados(it, resultados) }
        if (resultados.size < limite) {
            val vistos = resultados.mapTo(HashSet()) { it.clave() }
            for (r in porPalabras(db, consulta, cerca, limite)) {
                if (resultados.size < limite && vistos.add(r.clave())) resultados += r
            }
        }
        return resultados
    }

    private fun ResultadoBusqueda.clave() = Triple(nombre, punto.lat, punto.lon)

    private fun porPalabras(
        db: SQLiteDatabase,
        consulta: ConsultaBusqueda,
        cerca: PuntoGeo?,
        limite: Int,
    ): List<ResultadoBusqueda> {
        val palabras = consulta.palabras.take(MAX_PALABRAS)
        val filtros = ArrayList<String>()
        val argumentos = ArrayList<String>()
        for (p in palabras) {
            filtros += "l.rowid IN (" + p.alternativas.joinToString(" UNION ") { RANGO_PALABRA } + ")"
            for (alternativa in p.alternativas) {
                argumentos += alternativa
                argumentos += alternativa.siguientePrefijo()
            }
        }
        val enteras = palabras.joinToString(" + ") { exacta(it) }
        val orden = ArrayList<String>()
        orden += "($enteras) DESC"
        if (consulta.pideCalle) orden += "(l.categoria = 'calle') DESC"
        orden += "l.importancia DESC"
        if (cerca != null) orden += DISTANCIA_L
        // Mismo orden que los `?` del SQL: rangos, palabras enteras y, al final, la distancia.
        for (p in palabras) argumentos += p.alternativas
        if (cerca != null) argumentos += argumentosDistancia(cerca).toList()

        val resultados = ArrayList<ResultadoBusqueda>()
        db
            .rawQuery(
                "$SELECT_L WHERE ${filtros.joinToString(" AND ")} ORDER BY ${orden.joinToString(", ")} LIMIT $limite",
                argumentos.toTypedArray(),
            ).use { leerResultados(it, resultados) }
        return resultados
    }

    /** 1 si el lugar tiene entera alguna alternativa de la palabra. */
    private fun exacta(palabra: PalabraConsulta): String =
        "EXISTS (SELECT 1 FROM palabras e WHERE e.lugar = l.rowid AND e.palabra IN (" +
            palabra.alternativas.joinToString(",") { "?" } + "))"

    private fun String.siguientePrefijo(): String = dropLast(1) + (last() + 1)
}
