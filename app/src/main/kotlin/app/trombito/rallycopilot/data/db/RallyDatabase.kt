package app.trombito.rallycopilot.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ArchivoInstaladoEntity::class, AjustesMapasEntity::class, EstadoDatosEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RallyDatabase : RoomDatabase() {
    abstract fun datosMapaDao(): DatosMapaDao

    abstract fun ajustesMapasDao(): AjustesMapasDao

    companion object {
        const val NOMBRE = "rally.db"
    }
}
