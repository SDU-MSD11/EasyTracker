package com.sdumsd11.easytracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.sdumsd11.easytracker.data.ApiExpenseRepository
import com.sdumsd11.easytracker.data.ApiGroupRepository
import com.sdumsd11.easytracker.data.ApiUserRepository
import com.sdumsd11.easytracker.data.ExpenseRepository
import com.sdumsd11.easytracker.data.GroupRepository
import com.sdumsd11.easytracker.data.NotificationService
import com.sdumsd11.easytracker.data.UserRepository
import com.sdumsd11.easytracker.data.fake.FakeNotificationService
import com.sdumsd11.easytracker.data.local.EasyTrackerDatabase
import com.sdumsd11.easytracker.data.prefs.UserPreferences
import com.sdumsd11.easytracker.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

private const val DB_NAME = "easytracker.db"
private const val PREFS_NAME = "easytracker_prefs"

private val Context.appDataStore by preferencesDataStore(name = PREFS_NAME)

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EasyTrackerDatabase =
        Room.databaseBuilder(context, EasyTrackerDatabase::class.java, DB_NAME)
            // Pre-release only; api-integration adds real migrations before release.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.appDataStore

    @Provides
    @Singleton
    fun provideUserPreferences(dataStore: DataStore<Preferences>): UserPreferences =
        UserPreferences(dataStore)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    // Api* impls are the bound interfaces (NetworkModule owns ApiService/OkHttp/Retrofit);
    // Fake* impls stay for tests/previews only.
    @Provides
    @Singleton
    fun provideNotificationService(): NotificationService = FakeNotificationService()

    @Provides
    @Singleton
    fun provideUserRepository(
        api: ApiService,
        db: EasyTrackerDatabase,
        prefs: UserPreferences,
        @IoDispatcher io: CoroutineDispatcher,
    ): UserRepository = ApiUserRepository(api, db, prefs, io)

    @Provides
    @Singleton
    fun provideGroupRepository(
        api: ApiService,
        db: EasyTrackerDatabase,
        prefs: UserPreferences,
        @IoDispatcher io: CoroutineDispatcher,
    ): GroupRepository = ApiGroupRepository(api, db, prefs, io)

    @Provides
    @Singleton
    fun provideExpenseRepository(
        api: ApiService,
        db: EasyTrackerDatabase,
        prefs: UserPreferences,
        @IoDispatcher io: CoroutineDispatcher,
    ): ExpenseRepository = ApiExpenseRepository(api, db, prefs, io)
}
