package app.trombito.rallycopilot.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities =
        [
            ArchivoInstaladoEntity::class,
            AjustesMapasEntity::class,
            EstadoDatosEntity::class,
            AjustesConduccionEntity::class,
            RecorridoEntity::class,
            RecorridoPuntoEntity::class,
        ],
    version = 3,
    exportSchema = true,
)
abstract class RallyDatabase : RoomDatabase() {
    abstract fun datosMapaDao(): DatosMapaDao

    abstract fun ajustesMapasDao(): AjustesMapasDao

    abstract fun ajustesConduccionDao(): AjustesConduccionDao

    abstract fun recorridosDao(): RecorridosDao

    companion object {
        const val NOMBRE = "rally.db"
    }
}
