package com.sdumsd11.easytracker.di

import com.sdumsd11.easytracker.BuildConfig
import com.sdumsd11.easytracker.data.remote.ApiService
import com.sdumsd11.easytracker.data.remote.AuthInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Network graph (api-integration owner).
 *
 * Base URL default targets the backend on the host loopback for the emulator.
 * Physical device on the same LAN: override with
 * `-PEASYTRACKER_BASE_URL=http://<host-LAN-IP>:3000/api/` — or add a debug
 * `buildConfigField` once a debug flavor exists (single buildType today, so the
 * gradle default below applies to all builds; see contract notes).
 */
// Fallback mirror of the gradle BASE_URL default; BuildConfig.BASE_URL always
// carries the effective value (gradle sets it, defaulting to the same URL).
const val DEFAULT_BASE_URL = "http://10.0.2.2:3000/api/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides
    @Singleton
    fun provideOkHttp(auth: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
        // Logging ships in debug builds only (debugImplementation dep).
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC),
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttp: OkHttpClient, moshi: Moshi): Retrofit {
        if (BuildConfig.DEBUG) android.util.Log.d("EasyTrackerNet", "BASE_URL=" + BuildConfig.BASE_URL)
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
