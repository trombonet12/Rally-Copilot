package app.trombito.rallycopilot.data.busqueda

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.motor.busqueda.ConsultaBusqueda
import app.trombito.rallycopilot.motor.busqueda.NormalizadorNombre
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Búsqueda offline sobre `busqueda-mallorca.sqlite` (tabla `lugares`, `user_version` 2; esquema en `busqueda.py` del
 * repo de datos). Abre el archivo de solo lectura, lo reabre si cambia su `lastModified` (datos nuevos) y devuelve
 * vacío si falta, no abre o tiene otro `user_version`. Antes de abrirlo espera a que termine la siembra de los datos
 * del APK.
 *
 * Con la tabla `palabras` (índice nuevo): cada palabra de la consulta (o un sinónimo, ver [ConsultaBusqueda]) debe ser
 * prefijo de alguna palabra del nombre o del municipio del lugar (rango sobre la PK `(palabra, lugar)`). Orden: más
 * palabras casadas enteras, calles primero si se pide una calle, importancia y, con `cerca`, cercanía. Una consulta que
 * es solo una categoría («hospital») da los más cercanos de esa categoría y se completa con la búsqueda por palabras.
 *
 * Sin `palabras` (índice viejo): primero el prefijo del nombre (usa el índice `nombre_norm`) y después las demás
 * palabras que empiecen así; en cada paso, por importancia y, con `cerca`, por cercanía.
 */
class BuscadorSqlite(
    private val archivo: File,
    private val dispatcher: CoroutineDispatcher,
    private val siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna,
) : Buscador {
    private val cerrojo = Mutex()
    private var base: SQLiteDatabase? = null
    private var modificado: Long = SIN_ABRIR
    private var conPalabras = false

    override suspend fun buscar(
        texto: String,
        cerca: PuntoGeo?,
        limite: Int,
    ): List<ResultadoBusqueda> {
        val consulta = NormalizadorNombre.normalizar(texto)
        if (consulta.length < LETRAS_MINIMAS || limite <= 0) return emptyList()
        val interpretada = ConsultaBusqueda.de(texto)
        siembra.esperar()
        return withContext(dispatcher) {
            cerrojo.withLock {
                val db = abierta() ?: return@withLock emptyList()
                try {
                    if (conPalabras) {
                        ConsultasPalabras.buscar(db, interpretada, cerca, limite)
                    } else {
                        consultar(db, consulta, cerca, limite)
                    }
                } catch (e: SQLiteException) {
                    Log.w(ETIQUETA, "Consulta fallida: ${e.javaClass.simpleName}")
                    emptyList()
                }
            }
        }
    }

    override suspend fun nombresDeLugares(limite: Int): List<String> {
        if (limite <= 0) return emptyList()
        siembra.esperar()
        return withContext(dispatcher) {
            cerrojo.withLock {
                val db = abierta() ?: return@withLock emptyList()
                try {
                    val nombres = ArrayList<String>()
                    db
                        .rawQuery(
                            "SELECT nombre FROM lugares WHERE categoria IN ('ciudad', 'pueblo', 'aldea') " +
                                "GROUP BY nombre ORDER BY MAX(importancia) DESC, nombre LIMIT $limite",
                            null,
                        ).use { while (it.moveToNext()) nombres += it.getString(0) }
                    nombres
                } catch (e: SQLiteException) {
                    Log.w(ETIQUETA, "Consulta de localidades fallida: ${e.javaClass.simpleName}")
                    emptyList()
                }
            }
        }
    }

    override suspend fun lugarMasCercano(
        punto: PuntoGeo,
        radioM: Double,
    ): ResultadoBusqueda? {
        if (radioM <= 0.0) return null
        siembra.esperar()
        return withContext(dispatcher) {
            cerrojo.withLock {
                val db = abierta() ?: return@withLock null
                try {
                    LugarCercano.buscar(db, punto, radioM)
                } catch (e: SQLiteException) {
                    Log.w(ETIQUETA, "Consulta de lugar cercano fallida: ${e.javaClass.simpleName}")
                    null
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
        conPalabras = false
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
        conPalabras = db != null && tieneTablaPalabras(db)
        return db
    }

    private fun tieneTablaPalabras(db: SQLiteDatabase): Boolean =
        try {
            db
                .rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'palabras'", null)
                .use { it.moveToFirst() }
        } catch (e: SQLiteException) {
            Log.w(ETIQUETA, "No se pudo comprobar la tabla de palabras: ${e.javaClass.simpleName}")
            false
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
        val orden = "importancia DESC" + if (cerca != null) ", $SQL_DISTANCIA" else ""
        val argumentosOrden = cerca?.let { argumentosDistancia(it) }.orEmpty()

        val resultados = ArrayList<ResultadoBusqueda>()
        db
            .rawQuery(
                "$SQL_SELECT WHERE nombre_norm >= ? AND nombre_norm < ? ORDER BY $orden LIMIT $limite",
                arrayOf(desde, hasta) + argumentosOrden,
            ).use { leerResultados(it, resultados) }

        val restantes = limite - resultados.size
        if (restantes > 0) {
            db
                .rawQuery(
                    "$SQL_SELECT WHERE nombre_norm LIKE ? ESCAPE '\\' AND NOT (nombre_norm >= ? AND nombre_norm < ?) " +
                        "ORDER BY $orden LIMIT $restantes",
                    arrayOf("% ${escaparLike(consulta)}%", desde, hasta) + argumentosOrden,
                ).use { leerResultados(it, resultados) }
        }
        return resultados
    }

    private fun escaparLike(texto: String): String = texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        const val ETIQUETA = "BuscadorSqlite"
        const val VERSION_ESQUEMA = 2
        const val LETRAS_MINIMAS = 2
        const val SIN_ABRIR = -1L
    }
}
