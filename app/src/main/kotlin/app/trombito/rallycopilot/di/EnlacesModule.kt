package app.trombito.rallycopilot.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.trombito.rallycopilot.data.enlaces.ResolvedorEnlacesHttp
import app.trombito.rallycopilot.domain.enlaces.ResolvedorEnlaces
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EnlacesModule {
    @Provides
    @Singleton
    fun resolvedorEnlaces(
        @ApplicationContext contexto: Context,
    ): ResolvedorEnlaces {
        val conectividad = contexto.getSystemService(ConnectivityManager::class.java)
        val hayRed = {
            val red = conectividad?.activeNetwork
            red != null &&
                conectividad.getNetworkCapabilities(red)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ==
                true
        }
        return ResolvedorEnlacesHttp(hayRed, Dispatchers.IO)
    }
}
