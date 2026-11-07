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
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.TODAS_LAS_MIGRACIONES
import app.trombito.rallycopilot.domain.datos.PrecargaDetalle
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
    @Suppress("SpreadOperator") // una sola vez al abrir la base; Room solo acepta las migraciones como vararg
    fun baseDeDatos(
        @ApplicationContext contexto: Context,
    ): RallyDatabase =
        Room
            .databaseBuilder(contexto, RallyDatabase::class.java, RallyDatabase.NOMBRE)
            .addMigrations(*TODAS_LAS_MIGRACIONES)
            .build()

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
    @Suppress("LongParameterList") // proveedor de Hilt: cada colaborador llega inyectado
    fun repositorioDatosMapa(
        dao: DatosMapaDao,
        ajustesDao: AjustesMapasDao,
        workManager: WorkManager,
        config: ConfigDatosMapa,
        sembrador: SembradorDatos,
        precarga: PrecargaDetalle,
    ): RepositorioDatosMapa =
        RepositorioDatosMapaImpl(dao, ajustesDao, workManager, config, Dispatchers.IO, sembrador, precarga)

    @Provides
    fun esperaDatosSembrados(sembrador: SembradorDatos): EsperaDatosSembrados = sembrador
}
