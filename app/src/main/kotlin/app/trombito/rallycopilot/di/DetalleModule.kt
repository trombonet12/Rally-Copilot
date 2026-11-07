package app.trombito.rallycopilot.di

import android.content.Context
import androidx.work.WorkManager
import app.trombito.rallycopilot.data.datosmapa.ClienteHttp
import app.trombito.rallycopilot.data.datosmapa.ConfigDatosMapa
import app.trombito.rallycopilot.data.db.AjustesMapasDao
import app.trombito.rallycopilot.data.detalle.EnergiaAndroid
import app.trombito.rallycopilot.data.detalle.EstadoEnergia
import app.trombito.rallycopilot.data.detalle.FuenteTeselas
import app.trombito.rallycopilot.data.detalle.FuenteTeselasIgn
import app.trombito.rallycopilot.data.detalle.PrecargaDetalleImpl
import app.trombito.rallycopilot.data.detalle.RutasPendientes
import app.trombito.rallycopilot.domain.datos.PrecargaDetalle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DetalleModule {
    private const val URL_PROYECTO = "https://github.com/trombonet12/Rally-Copilot"

    @Provides
    @Singleton
    fun fuenteTeselas(
        config: ConfigDatosMapa,
        @DispatcherIo dispatcher: CoroutineDispatcher,
    ): FuenteTeselas =
        FuenteTeselasIgn(
            ClienteHttp.paraIgn(config.copy(userAgent = "${config.userAgent} (+$URL_PROYECTO)")),
            dispatcher,
        )

    @Provides
    fun estadoEnergia(
        @ApplicationContext contexto: Context,
    ): EstadoEnergia = EnergiaAndroid(contexto)

    @Provides
    @Singleton
    fun rutasPendientes(
        @ApplicationContext contexto: Context,
    ): RutasPendientes = RutasPendientes(File(contexto.filesDir, "detalle-rutas"))

    @Provides
    @Singleton
    fun precargaDetalle(
        workManager: WorkManager,
        config: ConfigDatosMapa,
        ajustesDao: AjustesMapasDao,
        pendientes: RutasPendientes,
        @DispatcherIo dispatcher: CoroutineDispatcher,
    ): PrecargaDetalle = PrecargaDetalleImpl(workManager, config, ajustesDao, pendientes, dispatcher)
}
