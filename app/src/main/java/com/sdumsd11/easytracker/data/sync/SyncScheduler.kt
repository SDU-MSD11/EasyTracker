package com.sdumsd11.easytracker.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val UNIQUE_SYNC_WORK = "easytracker-sync"

/**
 * Owns the closed-app poll cadence (polling only, no FCM). [start] enqueues unique
 * periodic [SyncWorker] work — 15 minutes is the OS minimum floor, with flex so
 * Doze batches it cheaply; [stop] cancels it. Started on login/register success
 * and stopped on sign-out (see `AuthViewModel`), so logged-out devices never sync.
 */
@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val wm: WorkManager get() = WorkManager.getInstance(context)

    fun start() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("easytracker-sync")
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun stop() {
        wm.cancelUniqueWork(UNIQUE_SYNC_WORK)
    }
}
