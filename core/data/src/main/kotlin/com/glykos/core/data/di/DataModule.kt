package com.glykos.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.glykos.core.data.BuildConfig
import com.glykos.core.data.demo.DemoAwareSource
import com.glykos.core.data.demo.DemoCredentials
import com.glykos.core.data.demo.DemoGlucoseSource
import com.glykos.core.data.demo.DemoMode
import com.glykos.core.data.librelinkup.LibreLinkUpSource
import com.glykos.core.data.settings.SettingsRepository
import com.glykos.core.data.source.GlucoseSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "glykos_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 20_000
        }
    }

    @Provides
    @Singleton
    fun provideDemoMode(): DemoMode = DemoMode(DemoCredentials.of(BuildConfig.DEMO_EMAIL, BuildConfig.DEMO_PASSWORD))

    @Provides
    @Singleton
    fun provideGlucoseSource(http: HttpClient, settings: SettingsRepository, demoMode: DemoMode): GlucoseSource =
        DemoAwareSource(
            real = LibreLinkUpSource(httpClient = http, credentials = settings::credentials),
            demo = DemoGlucoseSource(),
            demoCredentials = demoMode.credentials,
            credentials = settings::credentials,
        )
}
