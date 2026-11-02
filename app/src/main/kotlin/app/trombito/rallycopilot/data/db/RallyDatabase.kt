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
            MarcaEntity::class,
            EdicionNotaEntity::class,
        ],
    version = 11,
    exportSchema = true,
)
abstract class RallyDatabase : RoomDatabase() {
    abstract fun datosMapaDao(): DatosMapaDao

    abstract fun ajustesMapasDao(): AjustesMapasDao

    abstract fun ajustesConduccionDao(): AjustesConduccionDao

    abstract fun recorridosDao(): RecorridosDao

    abstract fun guiadoPendienteDao(): GuiadoPendienteDao

    abstract fun tramosDao(): TramosDao

    abstract fun marcasDao(): MarcasDao

    abstract fun edicionesDao(): EdicionesDao

    companion object {
        const val NOMBRE = "rally.db"
    }
}
