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
            GuiadoPendienteEntity::class,
        ],
    version = 4,
    exportSchema = true,
)
abstract class RallyDatabase : RoomDatabase() {
    abstract fun datosMapaDao(): DatosMapaDao

    abstract fun ajustesMapasDao(): AjustesMapasDao

    abstract fun ajustesConduccionDao(): AjustesConduccionDao

    abstract fun recorridosDao(): RecorridosDao

    abstract fun guiadoPendienteDao(): GuiadoPendienteDao

    companion object {
        const val NOMBRE = "rally.db"
    }
}
