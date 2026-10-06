package com.dailyrupi.app.di

import android.content.Context
import com.dailyrupi.app.data.AppPreferences
import com.dailyrupi.app.data.DataStoreAppPreferences
import com.dailyrupi.app.data.KeystoreCookieStorage
import com.dailyrupi.app.data.SessionEvents
import com.dailyrupi.core.net.ApiClient
import com.dailyrupi.core.net.DailyRupiApi
import com.dailyrupi.core.net.PersistentCookieJar
import com.dailyrupi.core.net.ServerAddress
import com.dailyrupi.core.net.ServerCheck
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A scope that lives as long as the app, for work that must outlast a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun serverAddress(): ServerAddress = ServerAddress()

    @Provides
    @Singleton
    fun cookieJar(@ApplicationContext context: Context): PersistentCookieJar =
        PersistentCookieJar(KeystoreCookieStorage(context))

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): AppPreferences = DataStoreAppPreferences(context)

    @Provides
    @Singleton
    fun api(server: ServerAddress, cookies: PersistentCookieJar, events: SessionEvents): DailyRupiApi =
        ApiClient.api(ApiClient.okHttp(server, cookies, events::sessionExpired))

    @Provides
    @Singleton
    fun serverCheck(): ServerCheck = ServerCheck()

    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
