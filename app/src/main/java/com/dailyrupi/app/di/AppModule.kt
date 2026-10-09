package com.dailyrupi.app.di

import android.content.Context
import androidx.room.Room
import com.dailyrupi.app.data.AppPreferences
import com.dailyrupi.app.data.DataStoreAppPreferences
import com.dailyrupi.app.data.KeystoreCookieStorage
import com.dailyrupi.app.data.ReferenceCache
import com.dailyrupi.app.data.SessionEvents
import com.dailyrupi.app.data.local.AppDatabase
import com.dailyrupi.app.data.local.RoomLocalExpenseStore
import com.dailyrupi.app.data.local.RoomReferenceCache
import com.dailyrupi.app.sync.PreferencesSyncCursor
import com.dailyrupi.app.sync.SyncRunner
import com.dailyrupi.app.sync.SyncScheduler
import com.dailyrupi.app.sync.WorkManagerSyncScheduler
import com.dailyrupi.core.auth.OfflineLogin
import com.dailyrupi.core.net.ApiClient
import com.dailyrupi.core.net.DailyRupiApi
import com.dailyrupi.core.net.PersistentCookieJar
import com.dailyrupi.core.net.ServerAddress
import com.dailyrupi.core.net.ServerCheck
import com.dailyrupi.core.sync.LocalExpenseStore
import com.dailyrupi.core.sync.SyncEngine
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
    fun offlineLogin(@ApplicationContext context: Context): OfflineLogin =
        OfflineLogin(KeystoreCookieStorage(context, name = "offline-login"))

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

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "daily-rupi.db").build()

    @Provides
    @Singleton
    fun expenseStore(database: AppDatabase): LocalExpenseStore = RoomLocalExpenseStore(database.expenses())

    @Provides
    @Singleton
    fun referenceCache(database: AppDatabase): ReferenceCache = RoomReferenceCache(database.expenses())

    @Provides
    @Singleton
    fun syncScheduler(@ApplicationContext context: Context): SyncScheduler = WorkManagerSyncScheduler(context)

    @Provides
    @Singleton
    fun syncRunner(
        api: DailyRupiApi,
        store: LocalExpenseStore,
        prefs: AppPreferences,
        cookies: PersistentCookieJar,
    ): SyncRunner = SyncRunner(SyncEngine(api, store, PreferencesSyncCursor(prefs)), prefs, cookies)
}
