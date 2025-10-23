package com.pfsmx.mdmempresarial.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.pfsmx.mdmempresarial.manager.PolicyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "mdm_sync_work"

        fun scheduleSyncWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES // Cada 15 minutos
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES) // Esperar 1 minuto antes de la primera ejecución
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Log.i(TAG, "✅ Sincronización periódica programada (cada 15 minutos)")
        }

        fun cancelSyncWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "❌ Sincronización periódica cancelada")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔄 Iniciando sincronización automática...")

            val policyManager = PolicyManager(applicationContext)

            // Verificar si es Device Owner
            if (!policyManager.isDeviceOwner()) {
                Log.w(TAG, "⚠️ No somos Device Owner, omitiendo sincronización")
                return@withContext Result.success()
            }

            // Sincronizar con servidor
            val success = policyManager.syncWithServer()

            if (success) {
                Log.i(TAG, "✅ Sincronización automática completada")
                Result.success()
            } else {
                Log.w(TAG, "⚠️ Sincronización completada con advertencias")
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en sincronización automática: ${e.message}")
            e.printStackTrace()

            // Reintentar en caso de error
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}