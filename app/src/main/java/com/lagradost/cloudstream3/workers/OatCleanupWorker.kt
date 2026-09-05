package com.lagradost.cloudstream3.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker to handle background cleanup of OAT files.
 * This prevents blocking the main thread during app updates.
 */
class OatCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            PluginManager.deleteAllOatFiles(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
