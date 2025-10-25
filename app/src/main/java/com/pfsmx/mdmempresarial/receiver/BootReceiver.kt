package com.pfsmx.mdmempresarial.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.pfsmx.mdmempresarial.manager.BootLoopDetector
import com.pfsmx.mdmempresarial.manager.PolicyManager
import com.pfsmx.mdmempresarial.manager.SafetyManager
import com.pfsmx.mdmempresarial.service.UnifiedSyncService
import com.pfsmx.mdmempresarial.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"

        // ✅ NUEVA LÓGICA: Ya NO activamos modo seguro en modelos problemáticos
        // Solo los detectamos y filtramos políticas peligrosas automáticamente
        private val MODELS_WITH_POLICY_FILTERING = listOf(
            "moto g24",           // Filtra políticas automáticamente
            "moto g24 power",
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🔄 DISPOSITIVO REINICIADO")
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "📱 Modelo: ${Build.MODEL}")
        Log.i(TAG, "🏭 Fabricante: ${Build.MANUFACTURER}")
        Log.i(TAG, "🤖 Android: ${Build.VERSION.RELEASE}")
        Log.i(TAG, "════════════════════════════════════════")

        try {
            // ✅ Detectar bootloop
            val isBootLoop = BootLoopDetector.registerBoot(context)

            if (isBootLoop) {
                Log.e(TAG, "⚠️⚠️⚠️ BOOTLOOP DETECTADO - ACTIVANDO MODO SEGURO ⚠️⚠️⚠️")
                SafetyManager.activateSafeMode(context, "Bootloop detectado: 3+ reinicios en 10 minutos")
                return
            }

            // ✅ NUEVA LÓGICA: Detectar modelos con filtrado de políticas
            if (isModelWithPolicyFiltering()) {
                Log.w(TAG, "════════════════════════════════════════")
                Log.w(TAG, "⚠️ MODELO CON FILTRADO DE POLÍTICAS")
                Log.w(TAG, "════════════════════════════════════════")
                Log.w(TAG, "Modelo: ${Build.MODEL}")
                Log.w(TAG, "Fabricante: ${Build.MANUFACTURER}")
                Log.w(TAG, "════════════════════════════════════════")
                Log.w(TAG, "ℹ️ Este modelo tiene políticas filtradas automáticamente")
                Log.w(TAG, "✅ Políticas seguras se aplicarán normalmente")
                Log.w(TAG, "🚫 Políticas peligrosas se omitirán automáticamente")
                Log.w(TAG, "════════════════════════════════════════")

                // ✅ NO activamos modo seguro
                // ✅ Continuamos con el flujo normal
                // ✅ PolicyManager se encargará de filtrar las políticas peligrosas
            }

            // ✅ Verificar batería
            val batteryLevel = getBatteryLevel(context)
            if (batteryLevel < 20) {
                Log.e(TAG, "🔋 Batería baja ($batteryLevel%) - Activando modo seguro preventivo")
                SafetyManager.activateSafeMode(context, "Batería baja: $batteryLevel%")
                return
            }

            val policyManager = PolicyManager(context)

            // Verificar si somos Device Owner
            if (!policyManager.isDeviceOwner()) {
                Log.w(TAG, "⚠️ No somos Device Owner, omitiendo configuración")
                return
            }

            Log.i(TAG, "✅ Device Owner confirmado")
            Log.i(TAG, "🔋 Batería: $batteryLevel%")

            // Verificar si hay modo de emergencia activo
            if (policyManager.isEmergencyUnlockActive()) {
                Log.w(TAG, "🆘 Modo emergencia activo - No se aplicarán restricciones")

                try {
                    val serviceIntent = Intent(context, UnifiedSyncService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.i(TAG, "✅ Servicio de monitoreo iniciado (modo emergencia)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error iniciando servicio: ${e.message}")
                }

                return
            }

            // Verificar SafetyManager antes de aplicar políticas
            if (SafetyManager.isSafeModeActive(context)) {
                Log.w(TAG, "🛡️ Modo seguro activo - No se aplicarán políticas")
                Log.w(TAG, SafetyManager.getSafeModeInfo(context))
                return
            }

            // Programar sincronización periódica
            try {
                SyncWorker.scheduleSyncWork(context)
                Log.i(TAG, "✅ Sincronización periódica programada")
            } catch (e: Exception) {
                Log.e(TAG, "Error programando sincronización: ${e.message}")
            }

            // Retrasar operaciones pesadas
            scope.launch {
                try {
                    // Esperar 2 minutos para que el sistema se estabilice
                    Log.i(TAG, "⏳ Esperando 2 minutos para estabilización del sistema...")
                    delay(120000) // 2 minutos

                    Log.i(TAG, "📋 Verificando política guardada...")
                    val currentPolicy = policyManager.getCurrentPolicy()

                    if (currentPolicy != null) {
                        Log.i(TAG, "📋 Política encontrada: ${currentPolicy.optString("name")}")

                        // ✅ IMPORTANTE: PolicyManager filtrará automáticamente las políticas peligrosas
                        if (!SafetyManager.isSafeModeActive(context)) {
                            val success = policyManager.applyPolicy(currentPolicy)

                            if (success) {
                                Log.i(TAG, "✅ Política reaplicada correctamente")

                                // ✅ Info adicional para modelos con filtrado
                                if (isModelWithPolicyFiltering()) {
                                    Log.i(TAG, "ℹ️ Políticas peligrosas omitidas automáticamente")
                                }
                            } else {
                                Log.w(TAG, "⚠️ Error reaplicando política")
                                SafetyManager.activateSafeMode(context, "Error aplicando política en boot")
                            }
                        }
                    } else {
                        Log.i(TAG, "ℹ️ No hay política guardada")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error reaplicando política: ${e.message}")
                    e.printStackTrace()

                    try {
                        SafetyManager.activateSafeMode(context, "Excepción en boot: ${e.message}")
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error activando modo seguro: ${e2.message}")
                    }
                }
            }

            // Sincronización con servidor MÁS RETRASADA (3 minutos)
            scope.launch {
                try {
                    Log.i(TAG, "⏳ Esperando 3 minutos antes de sincronizar con servidor...")
                    delay(180000) // 3 minutos

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

            // Registro de dispositivo MÁS RETRASADO (5 minutos)
            scope.launch {
                try {
                    Log.i(TAG, "⏳ Esperando 5 minutos antes de registrar dispositivo...")
                    delay(300000) // 5 minutos

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

        } catch (e: Exception) {
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "❌ ERROR CRÍTICO EN BOOT")
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "Error: ${e.message}")
            e.printStackTrace()

            try {
                SafetyManager.activateSafeMode(context, "Error crítico en boot: ${e.message}")
            } catch (e2: Exception) {
                Log.e(TAG, "Error activando modo seguro: ${e2.message}")
            }
        }
    }

    /**
     * ✅ NUEVA FUNCIÓN: Detecta si el modelo tiene filtrado automático de políticas
     * Ya NO activa modo seguro, solo informa
     */
    private fun isModelWithPolicyFiltering(): Boolean {
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        // Verificar lista exacta de modelos
        if (MODELS_WITH_POLICY_FILTERING.any { model.contains(it) }) {
            return true
        }

        // Verificar patrones
        if (manufacturer.contains("motorola") && model.contains("g24")) {
            return true
        }

        return false
    }

    /**
     * Obtiene el nivel de batería actual
     */
    private fun getBatteryLevel(context: Context): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo nivel de batería: ${e.message}")
            100 // Asumir batería llena si no se puede leer
        }
    }
}