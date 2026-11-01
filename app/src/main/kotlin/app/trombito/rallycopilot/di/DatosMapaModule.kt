package app.trombito.rallycopilot.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import app.trombito.rallycopilot.data.datosmapa.ConfigDatosMapa
import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.data.datosmapa.RepositorioDatosMapaImpl
import app.trombito.rallycopilot.data.datosmapa.SembradorDatos
import app.trombito.rallycopilot.data.datosmapa.URL_MANIFEST
import app.trombito.rallycopilot.data.db.AjustesMapasDao
import app.trombito.rallycopilot.data.db.DatosMapaDao
import app.trombito.rallycopilot.data.db.MIGRATION_1_2
import app.trombito.rallycopilot.data.db.MIGRATION_2_3
import app.trombito.rallycopilot.data.db.MIGRATION_3_4
import app.trombito.rallycopilot.data.db.MIGRATION_4_5
import app.trombito.rallycopilot.data.db.MIGRATION_5_6
import app.trombito.rallycopilot.data.db.MIGRATION_6_7
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatosMapaModule {
    @Provides
    @Singleton
    fun baseDeDatos(
        @ApplicationContext contexto: Context,
    ): RallyDatabase =
        Room
            .databaseBuilder(contexto, RallyDatabase::class.java, RallyDatabase.NOMBRE)
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            ).build()

    @Provides
    fun datosMapaDao(base: RallyDatabase): DatosMapaDao = base.datosMapaDao()

    @Provides
    fun ajustesMapasDao(base: RallyDatabase): AjustesMapasDao = base.ajustesMapasDao()

    @Provides
    @Singleton
    fun configDatosMapa(
        @ApplicationContext contexto: Context,
    ): ConfigDatosMapa {
        val version =
            runCatching { contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName }
                .getOrNull() ?: "0"
        return ConfigDatosMapa(
            urlManifest = URL_MANIFEST,
            directorioMapa = File(contexto.filesDir, "mapa"),
            userAgent = "RallyCopilot/$version",
            bytesLibres = { contexto.filesDir.usableSpace },
        )
    }

    @Provides
    @Singleton
    fun workManager(
        @ApplicationContext contexto: Context,
    ): WorkManager = WorkManager.getInstance(contexto)

    @Provides
    @Singleton
    fun repositorioDatosMapa(
        dao: DatosMapaDao,
        ajustesDao: AjustesMapasDao,
        workManager: WorkManager,
        config: ConfigDatosMapa,
        sembrador: SembradorDatos,
    ): RepositorioDatosMapa = RepositorioDatosMapaImpl(dao, ajustesDao, workManager, config, Dispatchers.IO, sembrador)

    @Provides
    fun esperaDatosSembrados(sembrador: SembradorDatos): EsperaDatosSembrados = sembrador
}
