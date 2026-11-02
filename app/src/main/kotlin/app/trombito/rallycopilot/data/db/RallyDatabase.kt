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
            TramoEntity::class,
            PasadaEntity::class,
        ],
    version = 8,
    exportSchema = true,
)
abstract class RallyDatabase : RoomDatabase() {
    abstract fun datosMapaDao(): DatosMapaDao

    abstract fun ajustesMapasDao(): AjustesMapasDao

    abstract fun ajustesConduccionDao(): AjustesConduccionDao

    abstract fun recorridosDao(): RecorridosDao

    abstract fun guiadoPendienteDao(): GuiadoPendienteDao

    abstract fun tramosDao(): TramosDao

    companion object {
        const val NOMBRE = "rally.db"
    }
}
