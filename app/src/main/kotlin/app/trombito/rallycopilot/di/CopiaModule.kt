package app.trombito.rallycopilot.di

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import app.trombito.rallycopilot.data.copia.CopiaSeguridad
import app.trombito.rallycopilot.data.copia.InfoApp
import app.trombito.rallycopilot.data.copia.ReiniciadorAppAndroid
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.domain.copia.CopiaDatos
import app.trombito.rallycopilot.domain.copia.ReiniciadorApp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CopiaModule {
    @Provides
    @Singleton
    fun copiaDatos(
        @ApplicationContext contexto: Context,
        base: RallyDatabase,
        @DispatcherIo dispatcher: CoroutineDispatcher,
    ): CopiaDatos {
        val paquete = runCatching { contexto.packageManager.getPackageInfo(contexto.packageName, 0) }.getOrNull()
        val info =
            InfoApp(
                versionName = paquete?.versionName ?: "0",
                versionCode = paquete?.let(PackageInfoCompat::getLongVersionCode) ?: 0L,
            )
        return CopiaSeguridad(base, contexto.getDatabasePath(RallyDatabase.NOMBRE), info, dispatcher)
    }

    @Provides
    @Singleton
    fun reiniciadorApp(
        @ApplicationContext contexto: Context,
    ): ReiniciadorApp = ReiniciadorAppAndroid(contexto)
}
