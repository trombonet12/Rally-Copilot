package app.trombito.rallycopilot.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Bases antiguas de Room para los tests de migración: se crean con el SQL exportado en `schemas/.../N.json` (no hay
 * `room-testing`), más unas filas de ejemplo en las tablas que ya existían. El nombre de archivo es corto a propósito:
 * Robolectric pone la base en un directorio temporal cuyo nombre lleva el del test, y en Windows la ruta total (más
 * `-journal`) pasaba de MAX_PATH (260) con nombres largos, dando SQLITE_CANTOPEN de forma intermitente.
 */
internal class BaseAntigua(
    private val contexto: Context,
) {
    fun esquema(version: Int): JsonObject {
        val archivo = File("schemas/app.trombito.rallycopilot.data.db.RallyDatabase/$version.json")
        return Json
            .parseToJsonElement(archivo.readText())
            .jsonObject
            .getValue("database")
            .jsonObject
    }

    fun crear(
        version: Int,
        nombre: String = NOMBRE,
    ): File {
        val archivo = contexto.getDatabasePath(nombre)
        archivo.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(archivo, null)
        val esquemaAntiguo = esquema(version)
        for (entidad in esquemaAntiguo.getValue("entities").jsonArray) {
            val sql =
                entidad.jsonObject
                    .getValue("createSql")
                    .jsonPrimitive.content
            db.execSQL(
                sql.replace(
                    "\${TABLE_NAME}",
                    entidad.jsonObject
                        .getValue("tableName")
                        .jsonPrimitive.content,
                ),
            )
        }
        for (entidad in esquemaAntiguo.getValue("entities").jsonArray) {
            val tabla =
                entidad.jsonObject
                    .getValue("tableName")
                    .jsonPrimitive.content
            entidad.jsonObject["indices"]?.jsonArray?.forEach { indice ->
                val sql =
                    indice.jsonObject
                        .getValue("createSql")
                        .jsonPrimitive.content
                db.execSQL(sql.replace("\${TABLE_NAME}", tabla))
            }
        }
        esquemaAntiguo.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.execSQL("INSERT INTO archivo_instalado VALUES ('mapa', 'mapa.pmtiles', '${"a".repeat(64)}', 123, 'v1')")
        db.execSQL("INSERT INTO ajustes_mapas VALUES (1, 0, 777)")
        db.execSQL("INSERT INTO estado_datos VALUES (1, 'v1', NULL, 'SIN_RED')")
        if (version >= 2) {
            db.execSQL(
                "INSERT INTO ajustes_conduccion (id, nivelZoom, icono, orientacion, capa, relieve) " +
                    "VALUES (1, 2, 'CASCO', 'NORTE', 'SATELITE', 1)",
            )
        }
        db.version = version
        db.close()
        return archivo
    }

    companion object {
        const val NOMBRE = "m.db"
    }
}
