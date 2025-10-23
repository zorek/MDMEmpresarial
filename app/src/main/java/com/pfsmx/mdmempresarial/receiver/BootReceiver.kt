package com.pfsmx.mdmempresarial.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.pfsmx.mdmempresarial.manager.PolicyManager
import com.pfsmx.mdmempresarial.service.SyncService
import com.pfsmx.mdmempresarial.service.UnifiedSyncService
import com.pfsmx.mdmempresarial.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🔄 DISPOSITIVO REINICIADO")
        Log.i(TAG, "════════════════════════════════════════")

        val policyManager = PolicyManager(context)

        // Verificar si somos Device Owner
        if (!policyManager.isDeviceOwner()) {
            Log.w(TAG, "⚠️ No somos Device Owner, omitiendo configuración")
            return
        }

        Log.i(TAG, "✅ Device Owner confirmado")

        // 1. Verificar si hay modo de emergencia activo
        if (policyManager.isEmergencyUnlockActive()) {
            Log.w(TAG, "🆘 Modo emergencia activo - No se aplicarán restricciones")

            // Iniciar servicio de monitoreo
            try {
                val serviceIntent = Intent(context, UnifiedSyncService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.i(TAG, "✅ Servicio de monitoreo iniciado")
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando servicio: ${e.message}")
            }

            return
        }

        // 2. Programar sincronización periódica
        try {
            SyncWorker.scheduleSyncWork(context)
            Log.i(TAG, "✅ Sincronización periódica programada")
        } catch (e: Exception) {
            Log.e(TAG, "Error programando sincronización: ${e.message}")
        }

        // 3. Reaplicar política actual (si existe)
        scope.launch {
            try {
                Log.i(TAG, "📋 Verificando política guardada...")

                val currentPolicy = policyManager.getCurrentPolicy()

                if (currentPolicy != null) {
                    Log.i(TAG, "📋 Reaplicando política: ${currentPolicy.optString("name")}")

                    val success = policyManager.applyPolicy(currentPolicy)

                    if (success) {
                        Log.i(TAG, "✅ Política reaplicada correctamente")
                    } else {
                        Log.w(TAG, "⚠️ Error reaplicando política")
                    }
                } else {
                    Log.i(TAG, "ℹ️ No hay política guardada")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error reaplicando política: ${e.message}")
                e.printStackTrace()
            }
        }

        // 4. Sincronizar con servidor (después de 30 segundos)
        scope.launch {
            try {
                // Esperar un poco para que el sistema se estabilice
                kotlinx.coroutines.delay(30000) // 30 segundos

                Log.i(TAG, "🔄 Iniciando sincronización post-boot...")

                val syncSuccess = policyManager.syncWithServer()

                if (syncSuccess) {
                    Log.i(TAG, "✅ Sincronización post-boot exitosa")
                } else {
                    Log.w(TAG, "⚠️ Sincronización post-boot con advertencias")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error en sincronización post-boot: ${e.message}")
                e.printStackTrace()
            }
        }

        // 5. Registrar dispositivo si no está registrado
        scope.launch {
            try {
                // Esperar 60 segundos antes de intentar registrar
                kotlinx.coroutines.delay(60000)

                Log.i(TAG, "📡 Verificando registro del dispositivo...")

                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )

                val apiClient = com.pfsmx.mdmempresarial.api.MDMApiClient(context)

                val registered = apiClient.registerDevice(
                    deviceId = deviceId,
                    model = Build.MODEL,
                    manufacturer = Build.MANUFACTURER,
                    androidVersion = Build.VERSION.RELEASE
                )

                if (registered) {
                    Log.i(TAG, "✅ Dispositivo registrado/actualizado en servidor")
                } else {
                    Log.w(TAG, "⚠️ No se pudo registrar dispositivo en servidor")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error registrando dispositivo: ${e.message}")
            }
        }

        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "✅ CONFIGURACIÓN POST-BOOT COMPLETADA")
        Log.i(TAG, "════════════════════════════════════════")
    }
}