package app.trombito.rallycopilot.di

import android.content.ContentResolver
import android.content.Context
import app.trombito.rallycopilot.data.importar.AbridorDocumentos
import app.trombito.rallycopilot.data.importar.AbridorDocumentosAndroid
import app.trombito.rallycopilot.data.importar.ImportadorTramos
import app.trombito.rallycopilot.domain.importar.ImportaArchivo
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportarModule {
    @Binds
    abstract fun abridorDocumentos(implementacion: AbridorDocumentosAndroid): AbridorDocumentos

    @Binds
    abstract fun importaArchivo(implementacion: ImportadorTramos): ImportaArchivo

    companion object {
        @Provides
        fun contentResolver(
            @ApplicationContext contexto: Context,
        ): ContentResolver = contexto.contentResolver
    }
}
