package app.trombito.rallycopilot.data.busqueda

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.motor.busqueda.NormalizadorNombre
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos

/**
 * Búsqueda offline sobre `busqueda-mallorca.sqlite` (tabla `lugares`, `user_version` 2; esquema en `busqueda.py` del
 * repo de datos). Abre el archivo de solo lectura, lo reabre si cambia su `lastModified` (datos nuevos) y devuelve
 * vacío si falta, no abre o tiene otro `user_version`. Antes de abrirlo espera a que termine la siembra de los datos
 * del APK.
 *
 * Consulta: primero el prefijo del nombre (usa el índice `nombre_norm`) y después las demás palabras que empiecen
 * así. En cada paso, por importancia y, con `cerca`, por cercanía. El municipio no se busca: su columna no está
 * normalizada.
 */
class BuscadorSqlite(
    private val archivo: File,
    private val dispatcher: CoroutineDispatcher,
    private val siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna,
) : Buscador {
    private val cerrojo = Mutex()
    private var base: SQLiteDatabase? = null
    private var modificado: Long = SIN_ABRIR

    override suspend fun buscar(
        texto: String,
        cerca: PuntoGeo?,
        limite: Int,
    ): List<ResultadoBusqueda> {
        val consulta = NormalizadorNombre.normalizar(texto)
        if (consulta.length < LETRAS_MINIMAS || limite <= 0) return emptyList()
        siembra.esperar()
        return withContext(dispatcher) {
            cerrojo.withLock {
                val db = abierta() ?: return@withLock emptyList()
                try {
                    consultar(db, consulta, cerca, limite)
                } catch (e: SQLiteException) {
                    Log.w(ETIQUETA, "Consulta fallida: ${e.javaClass.simpleName}")
                    emptyList()
                }
            }
        }
    }

    /** Libera el archivo (la siguiente búsqueda lo reabre). */
    suspend fun cerrar() {
        withContext(dispatcher) {
            cerrojo.withLock { descartar() }
        }
    }

    private fun abierta(): SQLiteDatabase? {
        if (!archivo.isFile) {
            descartar()
            return null
        }
        val actual = archivo.lastModified()
        if (actual != modificado) {
            descartar()
            modificado = actual
            base = abrirSiValida()
        }
        return base
    }

    private fun descartar() {
        base?.close()
        base = null
        modificado = SIN_ABRIR
    }

    private fun abrirSiValida(): SQLiteDatabase? {
        val db =
            try {
                SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: SQLiteException) {
                Log.w(ETIQUETA, "No se pudo abrir el índice de búsqueda: ${e.javaClass.simpleName}")
                null
            }
        if (db != null && db.version != VERSION_ESQUEMA) {
            Log.w(ETIQUETA, "Índice de búsqueda con user_version ${db.version}; se esperaba $VERSION_ESQUEMA")
            db.close()
            return null
        }
        return db
    }

    private fun consultar(
        db: SQLiteDatabase,
        consulta: String,
        cerca: PuntoGeo?,
        limite: Int,
    ): List<ResultadoBusqueda> {
        val desde = consulta
        val hasta = consulta.dropLast(1) + (consulta.last() + 1)
        // lat, lon y k van como argumentos `?` para que la posición no aparezca en el SQL (ni en sus errores).
        val orden = "importancia DESC" + if (cerca != null) ", $DISTANCIA" else ""
        val argumentosOrden = cerca?.let { argumentosDistancia(it) }.orEmpty()

        val resultados = ArrayList<ResultadoBusqueda>()
        db
            .rawQuery(
                "$SELECT WHERE nombre_norm >= ? AND nombre_norm < ? ORDER BY $orden LIMIT $limite",
                arrayOf(desde, hasta) + argumentosOrden,
            ).use { resultados.leer(it) }

        val restantes = limite - resultados.size
        if (restantes > 0) {
            db
                .rawQuery(
                    "$SELECT WHERE nombre_norm LIKE ? ESCAPE '\\' AND NOT (nombre_norm >= ? AND nombre_norm < ?) " +
                        "ORDER BY $orden LIMIT $restantes",
                    arrayOf("% ${escaparLike(consulta)}%", desde, hasta) + argumentosOrden,
                ).use { resultados.leer(it) }
        }
        return resultados
    }

    private fun MutableList<ResultadoBusqueda>.leer(cursor: Cursor) {
        while (cursor.moveToNext()) {
            add(
                ResultadoBusqueda(
                    nombre = cursor.getString(0),
                    categoria = cursor.getString(1),
                    municipio = cursor.getString(2)?.takeIf { it.isNotBlank() },
                    punto = PuntoGeo(cursor.getDouble(3), cursor.getDouble(4)),
                ),
            )
        }
    }

    /** Argumentos de [DISTANCIA]: lat, lat, lon, lon y k = cos²(lat). */
    private fun argumentosDistancia(cerca: PuntoGeo): Array<String> {
        val k = cos(Math.toRadians(cerca.lat)).let { it * it }
        val lat = cerca.lat.toString()
        val lon = cerca.lon.toString()
        return arrayOf(lat, lat, lon, lon, k.toString())
    }

    private fun escaparLike(texto: String): String = texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        const val ETIQUETA = "BuscadorSqlite"
        const val VERSION_ESQUEMA = 2
        const val LETRAS_MINIMAS = 2
        const val SIN_ABRIR = -1L

        /** Distancia al cuadrado en grados con la longitud escalada por cos(lat): solo sirve para ordenar. */
        const val DISTANCIA =
            "((lat - CAST(? AS REAL)) * (lat - CAST(? AS REAL)) + " +
                "(lon - CAST(? AS REAL)) * (lon - CAST(? AS REAL)) * CAST(? AS REAL))"
        const val SELECT = "SELECT nombre, categoria, municipio, lat, lon FROM lugares"
    }
}
