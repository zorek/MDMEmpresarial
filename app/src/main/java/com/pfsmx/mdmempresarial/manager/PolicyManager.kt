package com.pfsmx.mdmempresarial.manager

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.pfsmx.mdmempresarial.api.MDMApiClient
import com.pfsmx.mdmempresarial.receiver.MDMDeviceAdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PolicyManager(private val context: Context) {

    private val dpm: DevicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val componentName = ComponentName(context, MDMDeviceAdminReceiver::class.java)
    private val apiClient = MDMApiClient(context)
    private val prefs = context.getSharedPreferences("mdm_config", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "PolicyManager"
        private const val PREF_EMERGENCY_CODE = "emergency_code"
        private const val PREF_EMERGENCY_CODE_TIMESTAMP = "emergency_code_timestamp"
        private const val PREF_CURRENT_POLICY = "current_policy"
        private const val PREF_EMERGENCY_UNLOCK_UNTIL = "emergency_unlock_until"

        private val MOTO_G24_DANGEROUS_POLICIES = listOf(
            "forceLocationOn",
            "preventLocationToggle",
            "blockWifiConfig",
            "blockBluetoothConfig",
            "blockFactoryReset",
            "disableStatusBar",
            "blockAirplaneMode"  // Agregar si causa problemas
        )


    }

    // ==================== VERIFICACIONES ====================
    private val adminComponent: ComponentName by lazy {
        ComponentName(context, MDMDeviceAdminReceiver::class.java)
    }
    fun isDeviceOwner(): Boolean {
        return try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando Device Owner: ${e.message}")
            false
        }
    }

    fun isAdminActive(): Boolean {
        return dpm.isAdminActive(componentName)
    }

    // ==================== CÓDIGO DE EMERGENCIA ====================

    /**
     * Guarda el código de emergencia recibido del servidor
     */
    fun saveEmergencyCode(code: String) {
        prefs.edit().apply {
            putString(PREF_EMERGENCY_CODE, code)
            putLong(PREF_EMERGENCY_CODE_TIMESTAMP, System.currentTimeMillis())
            apply()
        }

        Log.i(TAG, "🆘 Código de emergencia actualizado")
    }


    private fun sanitizePolicyForCurrentDevice(policy: JSONObject): JSONObject {
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        // Crear copia de la política
        val sanitizedPolicy = JSONObject(policy.toString())

        // ═══════════════════════════════════════════════════════════
        // VERIFICACIÓN ESPECÍFICA PARA MOTO G24
        // ═══════════════════════════════════════════════════════════
        if (manufacturer.contains("motorola") && model.contains("g24")) {
            Log.w(TAG, "════════════════════════════════════════")
            Log.w(TAG, "⚠️ MOTO G24 DETECTADO - FILTRANDO POLÍTICAS PELIGROSAS")
            Log.w(TAG, "════════════════════════════════════════")

            val restrictions = sanitizedPolicy.optJSONObject("systemRestrictions")

            if (restrictions != null) {
                var policiesRemoved = 0

                // Verificar y remover cada política peligrosa
                for (dangerous in MOTO_G24_DANGEROUS_POLICIES) {
                    if (restrictions.has(dangerous) && restrictions.optBoolean(dangerous, false)) {
                        // Remover la política peligrosa
                        restrictions.remove(dangerous)
                        policiesRemoved++

                        Log.w(TAG, "  🚫 OMITIDA: '$dangerous' (causa bootloop en Moto G24)")
                    }
                }

                if (policiesRemoved > 0) {
                    Log.w(TAG, "════════════════════════════════════════")
                    Log.w(TAG, "📊 RESUMEN: $policiesRemoved política(s) peligrosa(s) omitida(s)")
                    Log.w(TAG, "✅ Las demás políticas SÍ se aplicarán")
                    Log.w(TAG, "════════════════════════════════════════")
                } else {
                    Log.i(TAG, "✅ No se encontraron políticas peligrosas - Aplicando todas")
                    Log.w(TAG, "════════════════════════════════════════")
                }
            }
        }

        // ═══════════════════════════════════════════════════════════
        // AQUÍ PUEDES AGREGAR MÁS MODELOS SI ES NECESARIO
        // ═══════════════════════════════════════════════════════════

        // Ejemplo para Samsung:
        // if (manufacturer.contains("samsung")) {
        //     // Filtrar políticas específicas de Samsung
        // }

        return sanitizedPolicy
    }






    /**
     * Valida el código de emergencia ingresado por el usuario
     */
    fun validateEmergencyCode(inputCode: String): Boolean {
        val savedCode = prefs.getString(PREF_EMERGENCY_CODE, null)

        if (savedCode.isNullOrEmpty()) {
            Log.w(TAG, "⚠️ No hay código de emergencia guardado")
            return false
        }

        val isValid = inputCode == savedCode

        Log.i(TAG, "🆘 Validación de código de emergencia: ${if (isValid) "VÁLIDO" else "INVÁLIDO"}")

        return isValid
    }

    /**
     * Activa el desbloqueo de emergencia temporal
     */
    fun activateEmergencyUnlock(durationMinutes: Int = 30) {
        val unlockUntil = System.currentTimeMillis() + (durationMinutes * 60 * 1000)

        prefs.edit().apply {
            putLong(PREF_EMERGENCY_UNLOCK_UNTIL, unlockUntil)
            apply()
        }

        Log.i(TAG, "🆘 Desbloqueo de emergencia activado por $durationMinutes minutos")
    }

    /**
     * Verifica si el desbloqueo de emergencia está activo
     */
    fun isEmergencyUnlockActive(): Boolean {
        val unlockUntil = prefs.getLong(PREF_EMERGENCY_UNLOCK_UNTIL, 0)
        val isActive = unlockUntil > System.currentTimeMillis()

        if (isActive) {
            val remainingMinutes = (unlockUntil - System.currentTimeMillis()) / 60000
            Log.i(TAG, "🆘 Desbloqueo de emergencia activo (${remainingMinutes}min restantes)")
        }

        return isActive
    }

    /**
     * Desactiva el desbloqueo de emergencia
     */
    fun deactivateEmergencyUnlock() {
        prefs.edit().remove(PREF_EMERGENCY_UNLOCK_UNTIL).apply()
        Log.i(TAG, "🆘 Desbloqueo de emergencia desactivado")
    }

    // ==================== POLÍTICAS DINÁMICAS ====================

    /**
     * Guarda la política actual recibida del servidor
     */
    fun saveCurrentPolicy(policyJson: String) {
        prefs.edit().apply {
            putString(PREF_CURRENT_POLICY, policyJson)
            apply()
        }

        Log.i(TAG, "📋 Política guardada localmente")
    }

    /**
     * Obtiene la política actual guardada
     */
    fun getCurrentPolicy(): JSONObject? {
        val policyJson = prefs.getString(PREF_CURRENT_POLICY, null)

        return try {
            if (policyJson != null) JSONObject(policyJson) else null
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando política: ${e.message}")
            null
        }
    }

    /**
     * Aplica una política recibida del servidor
     */

// ==================== FUNCIONES DE DESBLOQUEO ====================

    /**
     * Desbloquea todas las apps previamente bloqueadas
     */
    fun unblockAllApps(): Boolean {
        if (!isDeviceOwner()) {
            Log.e(TAG, "❌ No somos Device Owner")
            return false
        }

        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🔓 DESBLOQUEANDO TODAS LAS APPS")
        Log.i(TAG, "════════════════════════════════════════")

        try {
            val policy = getCurrentPolicy()
            val appsToUnblock = if (policy != null) {
                getCurrentBlockedApps()
            } else {
                // Si no hay política, desbloquear apps comunes
                getCommonBlockedApps()
            }

            Log.i(TAG, "Apps a desbloquear: ${appsToUnblock.size}")

            var unblocked = 0
            appsToUnblock.forEach { packageName ->
                try {
                    val wasHidden = dpm.isApplicationHidden(componentName, packageName)
                    if (wasHidden) {
                        dpm.setApplicationHidden(componentName, packageName, false)
                        unblocked++
                        Log.d(TAG, "  ✅ Desbloqueada: $packageName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "  ❌ Error desbloqueando $packageName: ${e.message}")
                }
            }


            // Asegurar navegadores sin políticas activas
            try {
                clearBrowserPoliciesAndUnhide()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error limpiando políticas de navegador en unblockAllApps: ${e.message}")
            }

            // Asegurar navegadores sin políticas activas
            try {
                clearLocationAndStatusBarPolicies()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error limpiando políticas de ubicación 1: ${e.message}")
            }




            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "✅ $unblocked APPS DESBLOQUEADAS")
            Log.i(TAG, "════════════════════════════════════════")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error desbloqueando apps: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Obtiene lista de apps comúnmente bloqueadas
     */
    private fun getCommonBlockedApps(): List<String> {
        return listOf(
            // Redes sociales
            "com.facebook.katana",
            "com.instagram.android",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.snapchat.android",
            "com.whatsapp",

            // Streaming
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.spotify.music",
            "com.amazon.avod.thirdpartyclient",

            // Google Apps
            "com.google.android.apps.photos",
            "com.google.android.apps.docs",
            "com.google.android.keep",
            "com.google.android.apps.maps",

            // Navegadores
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.brave.browser",
            "com.UCMobile.intl",

            // Play Store
            "com.android.vending"
        )
    }



    /**
     * Remueve todas las restricciones del sistema
     */
    fun removeAllRestrictions(): Boolean {
        if (!isDeviceOwner()) {
            Log.e(TAG, "❌ No somos Device Owner")
            return false
        }

        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🔓 REMOVIENDO RESTRICCIONES DEL SISTEMA")
        Log.i(TAG, "════════════════════════════════════════")

        try {
            val restrictions = listOf(
                android.os.UserManager.DISALLOW_CONFIG_TETHERING,
                android.os.UserManager.DISALLOW_INSTALL_APPS,
                android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                android.os.UserManager.DISALLOW_USB_FILE_TRANSFER,
                android.os.UserManager.DISALLOW_CONFIG_WIFI,
                android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH,
                android.os.UserManager.DISALLOW_FACTORY_RESET,
                android.os.UserManager.DISALLOW_ADD_USER,
                android.os.UserManager.DISALLOW_REMOVE_USER,
                android.os.UserManager.DISALLOW_CONFIG_CREDENTIALS,
                android.os.UserManager.DISALLOW_SHARE_LOCATION,
                android.os.UserManager.DISALLOW_DEBUGGING_FEATURES,
                android.os.UserManager.DISALLOW_CONFIG_DATE_TIME,
                android.os.UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT
            )

            var removed = 0
            restrictions.forEach { restriction ->
                try {
                    dpm.clearUserRestriction(componentName, restriction)
                    removed++
                    Log.d(TAG, "  ✅ Removida: $restriction")
                } catch (e: Exception) {
                    Log.e(TAG, "  ⚠️ Error removiendo $restriction: ${e.message}")
                }
            }

            // Desbloquear cámara
            try {
                dpm.setCameraDisabled(componentName, false)
                Log.d(TAG, "  ✅ Cámara desbloqueada")
            } catch (e: Exception) {
                Log.e(TAG, "  ⚠️ Error desbloqueando cámara: ${e.message}")
            }

            // Desbloquear capturas de pantalla
            try {
                dpm.setScreenCaptureDisabled(componentName, false)
                Log.d(TAG, "  ✅ Capturas de pantalla desbloqueadas")
            } catch (e: Exception) {
                Log.e(TAG, "  ⚠️ Error desbloqueando capturas: ${e.message}")
            }


            // Al final de removeAllRestrictions()
            try {
                clearBrowserPoliciesAndUnhide()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error limpiando políticas de navegador: ${e.message}")
            }

            try {
                clearLocationAndStatusBarPolicies()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error limpiando políticas de ubicación 1: ${e.message}")
            }







            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "✅ $removed RESTRICCIONES REMOVIDAS")
            Log.i(TAG, "════════════════════════════════════════")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error removiendo restricciones: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Limpia completamente todas las políticas aplicadas
     */
    fun clearAllPolicies(): Boolean {
        if (!isDeviceOwner()) {
            Log.e(TAG, "❌ No somos Device Owner")
            return false
        }

        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🧹 LIMPIANDO TODAS LAS POLÍTICAS")
        Log.i(TAG, "════════════════════════════════════════")

        try {


            // 1. Desbloquear apps
            unblockAllApps()

            // 2. Remover restricciones del sistema
            removeAllRestrictions()

            // 3. Limpiar política guardada
            prefs.edit().remove(PREF_CURRENT_POLICY).apply()


            // Justo antes del "✅ POLÍTICAS LIMPIADAS COMPLETAMENTE"
            try {
                clearBrowserPoliciesAndUnhide()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error limpiando políticas de navegador en clearAllPolicies: ${e.message}")
            }

            try {
                dpm.clearUserRestriction(componentName, android.os.UserManager.DISALLOW_AIRPLANE_MODE)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo limpiar DISALLOW_AIRPLANE_MODE: ${e.message}")
            }




            try {
                clearLocationAndStatusBarPolicies()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error limpiando políticas de ubicación 1: ${e.message}")
            }



            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "✅ POLÍTICAS LIMPIADAS COMPLETAMENTE")
            Log.i(TAG, "════════════════════════════════════════")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando políticas: ${e.message}")
            e.printStackTrace()
            return false
        }
    }


    private fun isSafePolicyForCurrentDevice(policy: JSONObject): Boolean {
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        // ═══════════════════════════════════════════════════════════
        // VERIFICACIÓN ESPECÍFICA PARA MOTO G24
        // ═══════════════════════════════════════════════════════════
        if (manufacturer.contains("motorola") && model.contains("g24")) {
            Log.i(TAG, "🔍 Verificando política para Moto G24...")

            val restrictions = policy.optJSONObject("systemRestrictions")

            if (restrictions != null) {
                // Verificar cada política peligrosa
                for (dangerous in MOTO_G24_DANGEROUS_POLICIES) {
                    if (restrictions.optBoolean(dangerous, false)) {
                        Log.e(TAG, "════════════════════════════════════════")
                        Log.e(TAG, "❌ POLÍTICA NO SEGURA DETECTADA")
                        Log.e(TAG, "════════════════════════════════════════")
                        Log.e(TAG, "Dispositivo: ${Build.MODEL} (${Build.MANUFACTURER})")
                        Log.e(TAG, "Política peligrosa: '$dangerous'")
                        Log.e(TAG, "Esta política causa bootloop en Moto G24")
                        Log.e(TAG, "════════════════════════════════════════")
                        return false
                    }
                }

                // Verificación especial: combinación de ubicación
                val forceLocation = restrictions.optBoolean("forceLocationOn", false)
                val preventToggle = restrictions.optBoolean("preventLocationToggle", false)

                if (forceLocation && preventToggle) {
                    Log.e(TAG, "❌ Combinación peligrosa: forceLocationOn + preventLocationToggle")
                    return false
                }

                Log.i(TAG, "✅ Política verificada - SEGURA para Moto G24")
            }
        }

        // ═══════════════════════════════════════════════════════════
        // AQUÍ PUEDES AGREGAR VERIFICACIONES PARA OTROS MODELOS
        // ═══════════════════════════════════════════════════════════

        // Ejemplo para Samsung:
        // if (manufacturer.contains("samsung")) {
        //     // Verificaciones específicas de Samsung
        // }

        return true
    }




    /**
     * Aplica una política recibida del servidor
     */
    suspend fun applyPolicy(policy: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "📋 APLICANDO POLÍTICA DINÁMICA")
            Log.i(TAG, "════════════════════════════════════════")

            val sanitizedPolicy = sanitizePolicyForCurrentDevice(policy)

            // ✅ AGREGAR ESTA VERIFICACIÓN AL INICIO:
            // Verificar si la política es segura para este dispositivo
            if (!isSafePolicyForCurrentDevice(sanitizedPolicy)) {
                Log.e(TAG, "════════════════════════════════════════")
                Log.e(TAG, "❌ POLÍTICA RECHAZADA")
                Log.e(TAG, "════════════════════════════════════════")
                Log.e(TAG, "La política contiene configuraciones peligrosas")
                Log.e(TAG, "para ${Build.MODEL} (${Build.MANUFACTURER})")
                Log.e(TAG, "Activando modo seguro preventivo...")
                Log.e(TAG, "════════════════════════════════════════")

                SafetyManager.activateSafeMode(
                    context,
                    "Política no segura para ${Build.MODEL}"
                )

                return@withContext false
            }


            if (!isDeviceOwner()) {
                Log.e(TAG, "❌ No somos Device Owner")
                return@withContext false
            }

            // Verificar si hay desbloqueo de emergencia activo
            if (isEmergencyUnlockActive()) {
                Log.w(TAG, "🆘 Desbloqueo de emergencia activo - Políticas suspendidas")
                return@withContext true
            }






            val policyName = policy.optString("name", "Desconocida")
            Log.i(TAG, "Política: $policyName")
            Log.i(TAG, "Modelo: ${Build.MODEL} (${Build.MANUFACTURER})")
            Log.i(TAG, "════════════════════════════════════════")




            // ✅ NUEVO: Limpiar políticas anteriores ANTES de aplicar la nueva
            Log.i(TAG, "🧹 Limpiando políticas anteriores...")
            unblockAllApps()
            removeAllRestrictions()

            // Guardar política localmente
            saveCurrentPolicy(sanitizedPolicy.toString())

            // 1. Aplicar restricciones de apps
            applyAppRestrictions(sanitizedPolicy)

            // 2. Aplicar restricciones del sistema
            applySystemRestrictions(sanitizedPolicy)

            // 3. Aplicar políticas de navegador
            applyBrowserPolicies(sanitizedPolicy)

            // 4. Configurar apps de delivery
            configureDeliveryApps(sanitizedPolicy)


            if (sanitizedPolicy.optJSONObject("dev")?.optBoolean("allowUsbDebugging", false) == true) {
                try {
                    dpm.clearUserRestriction(componentName, android.os.UserManager.DISALLOW_DEBUGGING_FEATURES)
                    Log.i(TAG, "✅ Developer Options/ADB permitidos por política")
                } catch (_: Exception) {}
            }


            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "✅ POLÍTICA APLICADA CORRECTAMENTE")
            Log.i(TAG, "════════════════════════════════════════")

            true

        } catch (e: Exception) {
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "❌ ERROR APLICANDO POLÍTICA")
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "Error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Aplica restricciones de apps según la política
     */
    private fun applyAppRestrictions(policy: JSONObject) {
        try {
            Log.i(TAG, "📱 Aplicando restricciones de apps...")

            val blockedApps = policy.optJSONObject("blockedApps") ?: return
            val appsToBlock = mutableListOf<String>()

            // Redes sociales
            if (blockedApps.optJSONObject("socialMedia")?.optBoolean("enabled") == true) {
                val apps = blockedApps.getJSONObject("socialMedia").optJSONArray("apps")
                apps?.let { addJsonArrayToList(it, appsToBlock) }
                Log.i(TAG, "  🚫 Bloqueando redes sociales")
            }

            // Streaming
            if (blockedApps.optJSONObject("streaming")?.optBoolean("enabled") == true) {
                val apps = blockedApps.getJSONObject("streaming").optJSONArray("apps")
                apps?.let { addJsonArrayToList(it, appsToBlock) }
                Log.i(TAG, "  🚫 Bloqueando streaming")
            }

            // Google Apps
            if (blockedApps.optJSONObject("googleApps")?.optBoolean("enabled") == true) {
                val apps = blockedApps.getJSONObject("googleApps").optJSONArray("apps")
                apps?.let { addJsonArrayToList(it, appsToBlock) }
                Log.i(TAG, "  🚫 Bloqueando apps de Google")
            }

            // Navegadores alternativos
            if (blockedApps.optJSONObject("browsers")?.optBoolean("enabled") == true) {
                val apps = blockedApps.getJSONObject("browsers").optJSONArray("apps")
                apps?.let { addJsonArrayToList(it, appsToBlock) }
                Log.i(TAG, "  🚫 Bloqueando navegadores alternativos")
            }

            // Play Store
            if (blockedApps.optJSONObject("playStore")?.optBoolean("enabled") == true) {
                appsToBlock.add("com.android.vending")
                Log.i(TAG, "  🚫 Bloqueando Play Store")
            }

            // Apps personalizadas
            val customApps = blockedApps.optJSONArray("custom")
            customApps?.let { addJsonArrayToList(it, appsToBlock) }

            // Aplicar bloqueos
            Log.i(TAG, "Total de apps a bloquear: ${appsToBlock.size}")
            appsToBlock.forEach { packageName ->
                setApplicationHidden(packageName, true)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando restricciones de apps: ${e.message}")
        }
    }

    /**
     * Aplica restricciones del sistema según la política
     */
    private fun applySystemRestrictions(policy: JSONObject) {
        try {
            Log.i(TAG, "🔒 Aplicando restricciones del sistema...")

            val restrictions = policy.optJSONObject("systemRestrictions") ?: return

            // ======== EXISTENTES ========
            if (restrictions.optBoolean("blockHotspot")) {
                addUserRestriction(android.os.UserManager.DISALLOW_CONFIG_TETHERING)
                Log.i(TAG, "  🚫 Hotspot bloqueado")
            }

            // ✅ AGREGAR ESTO:
            if (restrictions.optBoolean("blockAirplaneMode")) {
                addUserRestriction(android.os.UserManager.DISALLOW_AIRPLANE_MODE)
                Log.i(TAG, "  ✈️ Modo avión bloqueado (no pueden activar/desactivar)")
            }

            if (restrictions.optBoolean("blockInstallation")) {
                addUserRestriction(android.os.UserManager.DISALLOW_INSTALL_APPS)
                addUserRestriction(android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                Log.i(TAG, "  🚫 Instalación de apps bloqueada")
            }

            if (restrictions.optBoolean("blockUSB")) {
                addUserRestriction(android.os.UserManager.DISALLOW_USB_FILE_TRANSFER)
                Log.i(TAG, "  🚫 Transferencia USB bloqueada")
            }

            if (restrictions.optBoolean("blockWifiConfig")) {
                addUserRestriction(android.os.UserManager.DISALLOW_CONFIG_WIFI)
                Log.i(TAG, "  🚫 Configuración WiFi bloqueada")
            }

            if (restrictions.optBoolean("blockBluetoothConfig")) {
                addUserRestriction(android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH)
                Log.i(TAG, "  🚫 Configuración Bluetooth bloqueada")
            }

            if (restrictions.optBoolean("blockFactoryReset", true)) {
                addUserRestriction(android.os.UserManager.DISALLOW_FACTORY_RESET)
                Log.i(TAG, "  🚫 Factory Reset bloqueado")
            }

            if (restrictions.optBoolean("blockScreenCapture")) {
                dpm.setScreenCaptureDisabled(componentName, true)
                Log.i(TAG, "  🚫 Capturas de pantalla bloqueadas")
            }

            if (restrictions.optBoolean("blockCamera")) {
                dpm.setCameraDisabled(componentName, true)
                Log.i(TAG, "  🚫 Cámara bloqueada")
            }

            // ======== NUEVO: UBICACIÓN ========
            val forceLocationOn = restrictions.optBoolean("forceLocationOn", false)
            val preventLocationToggle = restrictions.optBoolean("preventLocationToggle", false)
            val allowUsbDebugging = policy.optJSONObject("dev")?.optBoolean("allowUsbDebugging", false) == true


            if (forceLocationOn) {
                try {
                    // Desde Android 9 (P) los DO pueden forzar ubicación
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        dpm.setLocationEnabled(componentName, true)
                        Log.i(TAG, "  📍 Ubicación forzada a ON")
                    } else {
                        Log.w(TAG, "  ⚠️ setLocationEnabled requiere Android 9+")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "  ⚠️ No se pudo forzar ubicación: ${e.message}")
                }
            }

            if (preventLocationToggle) {
                addUserRestriction(android.os.UserManager.DISALLOW_CONFIG_LOCATION)
                if (!allowUsbDebugging) {
                    addUserRestriction(android.os.UserManager.DISALLOW_DEBUGGING_FEATURES)
                } else {
                    Log.i(TAG, "🧑‍💻 ADB permitido por política (no bloqueamos Developer Options)")
                }
            }

            // ======== NUEVO: STATUS BAR / QUICK SETTINGS ========
            if (restrictions.optBoolean("disableStatusBar", false)) {
                // Requiere Device Owner en dispositivo administrado (Android 9+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        dpm.setStatusBarDisabled(componentName, true)
                        Log.i(TAG, "  🛑 Status bar/Quick Settings deshabilitados")
                    } catch (e: Exception) {
                        Log.w(TAG, "  ⚠️ No se pudo deshabilitar status bar: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "  ⚠️ disableStatusBar requiere Android 9+")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando restricciones del sistema: ${e.message}")
        }
    }




    /**
     * Configura apps de delivery con permisos especiales
     */
    private fun configureDeliveryApps(policy: JSONObject) {
        try {
            Log.i(TAG, "🚚 Configurando apps de delivery...")

            val deliveryApps = policy.optJSONArray("deliveryApps") ?: return

            for (i in 0 until deliveryApps.length()) {
                val app = deliveryApps.getJSONObject(i)
                val packageName = app.optString("packageName")
                val name = app.optString("name")
                val grantOverlay = app.optBoolean("grantOverlayPermission", true)
                val disableBattery = app.optBoolean("disableBatteryOptimization", true)
                val grantAll = app.optBoolean("grantAllPermissions", true)

                Log.i(TAG, "  🚚 Configurando: $name ($packageName)")

                if (grantAll) {
                    setupDeliveryAppWithOverlay(packageName)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error configurando apps de delivery: ${e.message}")
        }
    }


// === Helpers para limpiar políticas de navegador ===

    /** Regresa el Bundle de restricciones vacío (equivale a sin políticas) */
    private fun clearAppRestrictions(packageName: String) {
        try {
            dpm.setApplicationRestrictions(adminComponent, packageName, android.os.Bundle())
            Log.i(TAG, "✅ Restricciones limpiadas para $packageName")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ No se pudieron limpiar restricciones de $packageName: ${e.message}")
        }
    }

    /** Paquetes de navegadores que podrías haber bloqueado */
    private fun getBrowserPackages(): List<String> = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.brave.browser",
        "org.mozilla.firefox",
        "org.mozilla.focus",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.microsoft.emmx",            // Edge
        "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser",  // Samsung Internet
        "com.kiwibrowser.browser"
    )

    /** Limpia TODAS las políticas/flags aplicadas a navegadores (Chrome y variantes) */
    private fun clearBrowserPoliciesAndUnhide() {
        Log.i(TAG, "🌐 Limpiando políticas de navegadores y mostrando paquetes ocultos...")
        getBrowserPackages().forEach { pkg ->
            // 1) Quitar application restrictions (listas de URLs, SafeSearch, downloads, etc.)
            clearAppRestrictions(pkg)

            // 2) Asegurar que el navegador esté visible (por si se ocultó en blockOtherBrowsers)
            try {
                if (isAppInstalled(pkg)) {
                    val wasHidden = dpm.isApplicationHidden(adminComponent, pkg)
                    if (wasHidden) {
                        dpm.setApplicationHidden(adminComponent, pkg, false)
                        Log.i(TAG, "✅ Navegador reexpuesto: $pkg")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ No se pudo reexponer $pkg: ${e.message}")
            }
        }

        // 3) Forzar recarga de políticas de Chrome (ya tienes este helper)
        try {
            forceChromePolicyReload()
        } catch (_: Exception) { /* best effort */ }

        // 4) Best-effort: force-stop Chrome para garantizarlas en caliente
        try {
            Runtime.getRuntime().exec("am force-stop com.android.chrome").waitFor()
            Log.i(TAG, "🔄 Chrome detenido para recargar sin políticas")
        } catch (_: Exception) { /* best effort */ }
    }


    private fun clearLocationAndStatusBarPolicies() {
        try {
            // Permitir configurar ubicación de nuevo
            dpm.clearUserRestriction(componentName, android.os.UserManager.DISALLOW_CONFIG_LOCATION)
            dpm.clearUserRestriction(componentName, android.os.UserManager.DISALLOW_DEBUGGING_FEATURES) // ← agregar

            // Reactivar status bar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setStatusBarDisabled(componentName, false)
            }
            Log.i(TAG, "✅ Ubicación configurable y status bar restaurados")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error limpiando ubicación/status bar: ${e.message}")
        }
    }
















    /**
     * Aplica restricciones de navegación web
     */
    private fun applyWebRestrictions(webRestrictions: JSONObject): Boolean {
        return try {
            val blockMode = webRestrictions.optString("blockMode", "NONE")

            if (blockMode == "NONE") {
                Log.i(TAG, "🌐 Sin restricciones web")

                // Limpiar restricciones de Chrome
                dpm.setApplicationRestrictions(
                    adminComponent,
                    "com.android.chrome",
                    android.os.Bundle()
                )

                return true
            }

            // Obtener URLs
            val allowedUrlsArray = webRestrictions.optJSONArray("allowedUrls")
            val blockedUrlsArray = webRestrictions.optJSONArray("blockedUrls")

            val allowedUrls = mutableListOf<String>()
            val blockedUrls = mutableListOf<String>()

            // Convertir JSONArrays a listas
            allowedUrlsArray?.let {
                for (i in 0 until it.length()) {
                    allowedUrls.add(it.getString(i))
                }
            }

            blockedUrlsArray?.let {
                for (i in 0 until it.length()) {
                    blockedUrls.add(it.getString(i))
                }
            }

            Log.i(TAG, "🌐 Aplicando restricciones web:")
            Log.i(TAG, "   Modo: $blockMode")
            Log.i(TAG, "   URLs permitidas: ${allowedUrls.size}")
            Log.i(TAG, "   URLs bloqueadas: ${blockedUrls.size}")

            // Aplicar restricciones a Chrome
            val chromeSuccess = applyChromeRestrictions(allowedUrls, blockedUrls, blockMode)

            // Si el modo es WHITELIST, bloquear todos los navegadores excepto Chrome
            if (blockMode == "WHITELIST") {
                blockOtherBrowsers()
            }

            chromeSuccess

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error aplicando restricciones web: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Configura políticas específicas de Chrome
     */
    private fun applyChromeRestrictions(
        allowedUrls: List<String>,
        blockedUrls: List<String>,
        blockMode: String
    ): Boolean {
        return try {
            Log.i(TAG, "🌐 Configurando restricciones de Chrome")

            val restrictions = android.os.Bundle()

            when (blockMode) {
                "BLACKLIST" -> {
                    // Modo: Permitir todo excepto las bloqueadas
                    if (blockedUrls.isNotEmpty()) {
                        restrictions.putStringArray("URLBlocklist", blockedUrls.toTypedArray())
                        Log.i(TAG, "📛 Chrome: Bloqueando ${blockedUrls.size} URLs")
                    }

                    // Siempre permitir las URLs de la whitelist
                    if (allowedUrls.isNotEmpty()) {
                        restrictions.putStringArray("URLAllowlist", allowedUrls.toTypedArray())
                        Log.i(TAG, "✅ Chrome: Permitiendo ${allowedUrls.size} URLs")
                    }
                }

                "WHITELIST" -> {
                    // Modo: Bloquear todo excepto las permitidas

                    // 1. Bloquear TODAS las URLs con wildcard
                    restrictions.putStringArray("URLBlocklist", arrayOf("*"))
                    Log.i(TAG, "🚫 Chrome: Bloqueando TODAS las URLs")

                    // 2. Permitir solo las URLs específicas
                    if (allowedUrls.isNotEmpty()) {
                        // Normalizar URLs para Chrome
                        val normalizedUrls = allowedUrls.map { url ->
                            when {
                                url.startsWith("http://") || url.startsWith("https://") -> {
                                    // Ya tiene protocolo
                                    if (url.endsWith("/*")) url else "$url/*"
                                }
                                url.startsWith("*.") -> {
                                    // Subdominios wildcard
                                    "https://${url.substring(2)}/*"
                                }
                                else -> {
                                    // Dominio simple
                                    "https://$url/*"
                                }
                            }
                        }

                        restrictions.putStringArray("URLAllowlist", normalizedUrls.toTypedArray())
                        Log.i(TAG, "✅ Chrome: Permitiendo solo ${normalizedUrls.size} URLs")
                        normalizedUrls.forEach { url ->
                            Log.i(TAG, "   ✓ $url")
                        }
                    } else {
                        Log.w(TAG, "⚠️ Chrome: Lista de URLs permitidas está vacía!")
                    }

                    // 3. Deshabilitar modo incógnito (evitar bypass)
                    restrictions.putInt("IncognitoModeAvailability", 1) // 1 = disabled
                    Log.i(TAG, "🔒 Chrome: Modo incógnito deshabilitado")

                    // 4. Deshabilitar herramientas de desarrollador
                    restrictions.putBoolean("DeveloperToolsDisabled", true)
                    Log.i(TAG, "🔒 Chrome: Herramientas de desarrollador deshabilitadas")
                }
            }

            // Configuraciones adicionales de seguridad

            // Deshabilitar descargas
            restrictions.putInt("DownloadRestrictions", 3) // 3 = block all downloads
            Log.i(TAG, "📥 Chrome: Descargas bloqueadas")

            // Forzar SafeSearch
            restrictions.putBoolean("ForceSafeSearch", true)

            // Aplicar las restricciones a Chrome
            dpm.setApplicationRestrictions(
                adminComponent,
                "com.android.chrome",
                restrictions
            )

            Log.i(TAG, "✅ Restricciones de Chrome aplicadas correctamente")

            // También aplicar a Chrome Beta y Dev si están instalados
            val chromeVariants = listOf(
                "com.chrome.beta",
                "com.chrome.dev"
            )

            chromeVariants.forEach { pkg ->
                try {
                    if (isAppInstalled(pkg)) {
                        dpm.setApplicationRestrictions(adminComponent, pkg, restrictions)
                        Log.i(TAG, "✅ Restricciones aplicadas a $pkg")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ No se pudo configurar $pkg: ${e.message}")
                }
            }

            // Forzar recarga de Chrome
            if (blockMode == "WHITELIST") {
                forceChromePolicyReload()
            }

            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando Chrome: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Aplica políticas de navegador usando browserPolicies del servidor
     */
    private fun applyBrowserPolicies(policy: JSONObject) {
        try {
            Log.i(TAG, "🌐 Aplicando políticas de navegador...")

            // Verificar si hay browserPolicies
            if (!policy.has("browserPolicies")) {
                Log.i(TAG, "   Sin browserPolicies en la política")

                // Limpiar restricciones de Chrome
                dpm.setApplicationRestrictions(
                    adminComponent,
                    "com.android.chrome",
                    android.os.Bundle()
                )
                return
            }

            val browserPolicies = policy.getJSONObject("browserPolicies")
            val blockAllUrls = browserPolicies.optBoolean("blockAllUrls", false)
            val forceSafeSearch = browserPolicies.optBoolean("forceSafeSearch", false)

            Log.i(TAG, "   Bloquear todas las URLs: $blockAllUrls")
            Log.i(TAG, "   Forzar SafeSearch: $forceSafeSearch")

            // Si no hay bloqueo, limpiar restricciones
            if (!blockAllUrls) {
                Log.i(TAG, "   Sin restricciones - Chrome libre")

                dpm.setApplicationRestrictions(
                    adminComponent,
                    "com.android.chrome",
                    android.os.Bundle()
                )
                return
            }

            // Obtener URLs
            val allowedUrlsArray = browserPolicies.optJSONArray("allowedUrls")
            val blockedUrlsArray = browserPolicies.optJSONArray("blockedUrls")

            val allowedUrls = mutableListOf<String>()
            val blockedUrls = mutableListOf<String>()

            allowedUrlsArray?.let {
                for (i in 0 until it.length()) {
                    val url = it.getString(i).trim()
                    if (url.isNotEmpty()) {
                        allowedUrls.add(url)
                    }
                }
            }

            blockedUrlsArray?.let {
                for (i in 0 until it.length()) {
                    val url = it.getString(i).trim()
                    if (url.isNotEmpty()) {
                        blockedUrls.add(url)
                    }
                }
            }

            Log.i(TAG, "   URLs permitidas: ${allowedUrls.size}")
            Log.i(TAG, "   URLs bloqueadas: ${blockedUrls.size}")

            // Crear restricciones de Chrome
            val restrictions = android.os.Bundle()

            if (blockAllUrls) {
                // WHITELIST MODE: Bloquear todo excepto las permitidas
                Log.i(TAG, "   🚫 Modo WHITELIST: Bloqueando TODAS las URLs")

                // 1. Bloquear todo
                restrictions.putStringArray("URLBlocklist", arrayOf("*"))

                // 2. Permitir solo las específicas
                if (allowedUrls.isNotEmpty()) {
                    val normalizedUrls = mutableListOf<String>()

                    allowedUrls.forEach { url ->
                        // Limpiar URL
                        val cleanUrl = url.replace("https://", "")
                            .replace("http://", "")
                            .replace("www.", "")
                            .replace("/*", "")
                            .replace("*", "")
                            .trim()

                        if (cleanUrl.isNotEmpty()) {
                            // Agregar todas las variantes posibles
                            normalizedUrls.add("https://$cleanUrl")
                            normalizedUrls.add("https://$cleanUrl/*")
                            normalizedUrls.add("https://www.$cleanUrl")
                            normalizedUrls.add("https://www.$cleanUrl/*")
                            normalizedUrls.add("http://$cleanUrl")
                            normalizedUrls.add("http://$cleanUrl/*")
                            normalizedUrls.add("http://www.$cleanUrl")
                            normalizedUrls.add("http://www.$cleanUrl/*")

                            // Agregar subdominios wildcard
                            normalizedUrls.add("https://*.$cleanUrl")
                            normalizedUrls.add("https://*.$cleanUrl/*")
                            normalizedUrls.add("http://*.$cleanUrl")
                            normalizedUrls.add("http://*.$cleanUrl/*")
                        }
                    }

                    restrictions.putStringArray("URLAllowlist", normalizedUrls.toTypedArray())
                    Log.i(TAG, "   ✅ Permitiendo ${normalizedUrls.size} variantes de URLs:")
                    allowedUrls.take(5).forEach { url ->
                        Log.i(TAG, "      • $url")
                    }
                    if (allowedUrls.size > 5) {
                        Log.i(TAG, "      • ... y ${allowedUrls.size - 5} más")
                    }
                } else {
                    Log.w(TAG, "   ⚠️ WHITELIST activo pero sin URLs permitidas!")
                }

                // Deshabilitar bypass methods
                restrictions.putInt("IncognitoModeAvailability", 1)
                restrictions.putBoolean("DeveloperToolsDisabled", true)

                Log.i(TAG, "   🔒 Modo incógnito y dev tools deshabilitados")

                // Bloquear otros navegadores
                blockOtherBrowsers()

            } else if (blockedUrls.isNotEmpty()) {
                // BLACKLIST MODE: Permitir todo excepto las bloqueadas
                Log.i(TAG, "   📛 Modo BLACKLIST: Bloqueando ${blockedUrls.size} URLs específicas")

                restrictions.putStringArray("URLBlocklist", blockedUrls.toTypedArray())

                if (allowedUrls.isNotEmpty()) {
                    restrictions.putStringArray("URLAllowlist", allowedUrls.toTypedArray())
                }
            }

            // SafeSearch
            if (forceSafeSearch) {
                restrictions.putBoolean("ForceSafeSearch", true)
                Log.i(TAG, "   🔒 SafeSearch forzado")
            }

            // Bloquear descargas cuando hay restricciones
            if (blockAllUrls) {
                restrictions.putInt("DownloadRestrictions", 3)
                Log.i(TAG, "   📥 Descargas bloqueadas")
            }

            // Aplicar restricciones a Chrome
            dpm.setApplicationRestrictions(
                adminComponent,
                "com.android.chrome",
                restrictions
            )

            Log.i(TAG, "   ✅ Restricciones aplicadas a Chrome")

            // Forzar recarga si es whitelist
            if (blockAllUrls) {
                try {
                    Runtime.getRuntime().exec("am force-stop com.android.chrome").waitFor()
                    Thread.sleep(500)
                    Log.i(TAG, "   🔄 Chrome reiniciado para aplicar políticas")
                } catch (e: Exception) {
                    Log.w(TAG, "   ⚠️ No se pudo reiniciar Chrome: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error aplicando políticas de navegador: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Bloquea navegadores alternativos
     */
    private fun blockOtherBrowsers() {
        try {
            val otherBrowsers = listOf(
                "com.brave.browser",
                "org.mozilla.firefox",
                "com.opera.browser",
                "com.opera.mini.native",
                "com.microsoft.emmx", // Edge
                "com.duckduckgo.mobile.android",
                "com.sec.android.app.sbrowser", // Samsung Internet
                "org.mozilla.focus",
                "com.kiwibrowser.browser"
            )

            var blockedCount = 0
            otherBrowsers.forEach { pkg ->
                try {
                    if (isAppInstalled(pkg)) {
                        dpm.setApplicationHidden(adminComponent, pkg, true)
                        blockedCount++
                    }
                } catch (e: Exception) {
                    // Ignorar errores
                }
            }

            if (blockedCount > 0) {
                Log.i(TAG, "   🚫 Bloqueados $blockedCount navegadores alternativos")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error bloqueando navegadores: ${e.message}")
        }
    }

    /**
     * Verifica si una app está instalada
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fuerza a Chrome a recargar sus políticas
     */
    private fun forceChromePolicyReload(): Boolean {
        return try {
            Log.i(TAG, "🔄 Forzando recarga de políticas de Chrome...")

            // Método 1: Ocultar/mostrar Chrome (fuerza recarga de políticas)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    dpm.setApplicationHidden(adminComponent, "com.android.chrome", true)
                    Thread.sleep(500)
                    dpm.setApplicationHidden(adminComponent, "com.android.chrome", false)
                    Log.i(TAG, "✅ Chrome reiniciado")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ No se pudo reiniciar Chrome: ${e.message}")
                }
            }

            // Método 2: Force stop
            try {
                Runtime.getRuntime().exec("am force-stop com.android.chrome").waitFor()
                Thread.sleep(500)
                Log.i(TAG, "✅ Chrome detenido")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ No se pudo detener Chrome: ${e.message}")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error forzando recarga de Chrome: ${e.message}")
            false
        }
    }

    // ==================== UTILIDADES ====================

    private fun addJsonArrayToList(jsonArray: JSONArray, list: MutableList<String>) {
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optString(i)
            if (item.isNotEmpty()) {
                list.add(item)
            }
        }
    }

    private fun setApplicationHidden(packageName: String, hidden: Boolean) {
        try {
            val isInstalled = isAppInstalled(packageName)

            if (!isInstalled) {
                return
            }

            val result = dpm.setApplicationHidden(componentName, packageName, hidden)

            if (result) {
                Log.d(TAG, "App ${if (hidden) "ocultada" else "mostrada"}: $packageName")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error ocultando app $packageName: ${e.message}")
        }
    }



    private fun addUserRestriction(restriction: String) {
        try {
            dpm.addUserRestriction(componentName, restriction)
        } catch (e: Exception) {
            Log.e(TAG, "Error añadiendo restricción $restriction: ${e.message}")
        }
    }

    fun getCurrentBlockedApps(): List<String> {
        val policy = getCurrentPolicy() ?: return emptyList()
        val blockedApps = mutableListOf<String>()

        try {
            val blockedAppsObj = policy.optJSONObject("blockedApps") ?: return emptyList()

            // Obtener todas las categorías de apps bloqueadas
            listOf("socialMedia", "streaming", "googleApps", "browsers").forEach { category ->
                val categoryObj = blockedAppsObj.optJSONObject(category)
                if (categoryObj?.optBoolean("enabled") == true) {
                    val apps = categoryObj.optJSONArray("apps")
                    apps?.let { addJsonArrayToList(it, blockedApps) }
                }
            }

            // Play Store
            if (blockedAppsObj.optJSONObject("playStore")?.optBoolean("enabled") == true) {
                blockedApps.add("com.android.vending")
            }

            // Custom
            val customApps = blockedAppsObj.optJSONArray("custom")
            customApps?.let { addJsonArrayToList(it, blockedApps) }

        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo apps bloqueadas: ${e.message}")
        }

        return blockedApps.distinct()
    }

// ==================== CONFIGURACIÓN DE APPS DE DELIVERY ====================

    /**
     * Otorga permisos runtime a una app específica
     */
    fun grantPermissionsToApp(packageName: String, permissions: List<String>): Boolean {
        if (!isDeviceOwner()) {
            Log.e(TAG, "No somos Device Owner, no podemos otorgar permisos")
            return false
        }

        try {
            permissions.forEach { permission ->
                val granted = dpm.setPermissionGrantState(
                    componentName,
                    packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )

                if (granted) {
                    Log.i(TAG, "✅ Permiso otorgado: $permission a $packageName")
                } else {
                    Log.w(TAG, "⚠️ No se pudo otorgar: $permission a $packageName")
                }
            }

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error otorgando permisos: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Fuerza el permiso de overlay (aparecer sobre otras apps) usando AppOps
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun forceOverlayPermission(packageName: String): Boolean {
        return try {
            Log.i(TAG, "🎯 Forzando permiso de overlay para $packageName")

            val uid = context.packageManager.getPackageUid(packageName, 0)

            // Método 1: AppOps via reflection
            val success1 = setAppOpsMode(packageName, uid, 24, AppOpsManager.MODE_ALLOWED)

            // Método 2: Comando shell directo
            val success2 = try {
                Runtime.getRuntime().exec("appops set $packageName SYSTEM_ALERT_WINDOW allow").waitFor()
                true
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Shell command falló: ${e.message}")
                false
            }

            // Método 3: Otorgar permiso directamente
            val success3 = try {
                Runtime.getRuntime().exec("pm grant $packageName android.permission.SYSTEM_ALERT_WINDOW").waitFor()
                true
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ PM grant falló: ${e.message}")
                false
            }

            if (success1 || success2 || success3) {
                Log.i(TAG, "✅ Overlay habilitado para $packageName")
                true
            } else {
                Log.w(TAG, "⚠️ No se pudo habilitar overlay completamente")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error forzando overlay: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Configuración completa para apps de delivery
     * Habilita todo lo necesario para que funcionen sin restricciones
     */
    /**
     * Configuración completa para apps de delivery
     * Habilita todo lo necesario para que funcionen sin restricciones
     */
    fun setupAppForDelivery(packageName: String): Boolean {
        return try {
            Log.i(TAG, "🚚 Configurando app de delivery: $packageName")

            if (!isDeviceOwner()) {
                Log.e(TAG, "❌ No es Device Owner")
                return false
            }

            val uid = context.packageManager.getPackageUid(packageName, 0)

            // 1. OVERLAY PERMISSION (aparecer sobre otras apps)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setAppOpsMode(packageName, uid, 24, AppOpsManager.MODE_ALLOWED) // SYSTEM_ALERT_WINDOW
                Log.i(TAG, "✅ Overlay configurado")
            }

            // 2. LOCATION PERMISSIONS (todas)
            val locationPermissions = mutableListOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                locationPermissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }

            grantPermissionsToApp(packageName, locationPermissions)
            Log.i(TAG, "✅ Permisos de ubicación otorgados")

            // 3. DESHABILITAR OPTIMIZACIÓN DE BATERÍA
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setAppOpsMode(packageName, uid, 63, AppOpsManager.MODE_ALLOWED) // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                Log.i(TAG, "✅ Optimización de batería deshabilitada")
            }

            // 4. PERMITIR INICIO EN BACKGROUND
            setAppOpsMode(packageName, uid, 63, AppOpsManager.MODE_ALLOWED) // RUN_IN_BACKGROUND

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setAppOpsMode(packageName, uid, 76, AppOpsManager.MODE_ALLOWED) // START_FOREGROUND
            }
            Log.i(TAG, "✅ Background y foreground permitidos")

            // 5. PERMISOS ADICIONALES COMUNES
            val additionalPermissions = mutableListOf<String>()

            // Cámara (para escanear códigos)
            additionalPermissions.add(android.Manifest.permission.CAMERA)

            // Teléfono (para llamar a clientes)
            additionalPermissions.add(android.Manifest.permission.CALL_PHONE)
            additionalPermissions.add(android.Manifest.permission.READ_PHONE_STATE)

            // Storage (para guardar evidencias)
            additionalPermissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            additionalPermissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

            // Notificaciones (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                additionalPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }

            grantPermissionsToApp(packageName, additionalPermissions)
            Log.i(TAG, "✅ Permisos adicionales otorgados")

            // 6. DESHABILITAR RESTRICCIONES DE DATOS (usando shell)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Usar comando shell para quitar restricciones de datos
                    val commands = arrayOf(
                        "cmd netpolicy set restrict-background false",
                        "cmd netpolicy add restrict-background-whitelist $uid"
                    )

                    commands.forEach { cmd ->
                        try {
                            Runtime.getRuntime().exec(cmd).waitFor()
                            Log.i(TAG, "✅ Comando ejecutado: $cmd")
                        } catch (e: Exception) {
                            Log.w(TAG, "⚠️ Comando falló: $cmd")
                        }
                    }

                    Log.i(TAG, "✅ Restricciones de datos configuradas")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error con política de red: ${e.message}")
            }

            // 7. REMOVER RESTRICCIONES DE APP
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, MDMDeviceAdminReceiver::class.java)

                dpm.setApplicationRestrictions(
                    adminComponent,
                    packageName,
                    android.os.Bundle()
                )

                Log.i(TAG, "✅ Restricciones de aplicación removidas")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error removiendo restricciones: ${e.message}")
            }

            // 8. AGREGAR A POWER WHITELIST (si es posible)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                    // Intentar abrir la configuración (puede requerir interacción del usuario)
                    context.startActivity(intent)

                    Log.i(TAG, "✅ Solicitud de optimización de batería enviada")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ No se pudo solicitar whitelist de batería: ${e.message}")
                }
            }

            Log.i(TAG, "✅ Configuración completa de delivery finalizada para $packageName")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en configuración de delivery: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Configura un AppOp usando reflection
     */
    private fun setAppOpsMode(packageName: String, uid: Int, op: Int, mode: Int): Boolean {
        return try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

            val setModeMethod = AppOpsManager::class.java.getMethod(
                "setMode",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType
            )

            setModeMethod.invoke(appOpsManager, op, uid, packageName, mode)
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error configurando AppOp $op: ${e.message}")

            // Intentar método alternativo con shell
            try {
                val opName = when (op) {
                    24 -> "SYSTEM_ALERT_WINDOW"
                    63 -> "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
                    76 -> "START_FOREGROUND"
                    else -> op.toString()
                }

                val modeStr = when (mode) {
                    AppOpsManager.MODE_ALLOWED -> "allow"
                    AppOpsManager.MODE_IGNORED -> "ignore"
                    AppOpsManager.MODE_ERRORED -> "deny"
                    else -> "default"
                }

                val command = "appops set $packageName $opName $modeStr"
                Runtime.getRuntime().exec(command).waitFor()

                Log.i(TAG, "✅ AppOp configurado via shell: $command")
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Error con método alternativo: ${e2.message}")
                false
            }
        }
    }
    /**
     * Modifica AppOps usando reflexión (privilegio de Device Owner)
     */
    private fun setAppOpsMode(
        appOps: AppOpsManager,
        uid: Int,
        packageName: String,
        operation: String,
        mode: Int
    ): Boolean {
        return try {
            // Intentar método público primero (Android 9+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val method = AppOpsManager::class.java.getMethod(
                        "setMode",
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        String::class.java,
                        Int::class.javaPrimitiveType
                    )
                    method.invoke(appOps, operation, uid, packageName, mode)
                    Log.i(TAG, "✅ AppOps modificado con método público")
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Método público falló, intentando reflexión: ${e.message}")
                }
            }

            // Método con reflexión (Android 6-8)
            val method = AppOpsManager::class.java.getDeclaredMethod(
                "setMode",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true

            // Obtener el código de operación numérico
            val opCode = getOpCode(operation)

            method.invoke(appOps, opCode, uid, packageName, mode)
            Log.i(TAG, "✅ AppOps modificado con reflexión")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error modificando AppOps: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Obtiene el código numérico de una operación de AppOps
     */
    private fun getOpCode(operation: String): Int {
        return try {
            val field = AppOpsManager::class.java.getDeclaredField("OP_SYSTEM_ALERT_WINDOW")
            field.isAccessible = true
            field.getInt(null)
        } catch (e: Exception) {
            // Código conocido para SYSTEM_ALERT_WINDOW
            24
        }
    }

    /**
     * Deshabilita optimización de batería para una app
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun disableBatteryOptimizationForApp(packageName: String): Boolean {
        return try {
            // Como Device Owner, podemos otorgar el permiso
            dpm.setPermissionGrantState(
                componentName,
                packageName,
                android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )

            Log.i(TAG, "✅ Optimización de batería deshabilitada para $packageName")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error deshabilitando batería: ${e.message}")
            false
        }
    }

    /**
     * Protege la app para que no se cierre en background
     */
    private fun setAppAsProtected(packageName: String): Boolean {
        return try {
            // Mantener app visible
            dpm.setApplicationHidden(componentName, packageName, false)

            // Agregar a lista de apps protegidas (Lock Task)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val currentPackages = dpm.getLockTaskPackages(componentName).toMutableList()
                if (!currentPackages.contains(packageName)) {
                    currentPackages.add(packageName)
                    dpm.setLockTaskPackages(componentName, currentPackages.toTypedArray())
                    Log.i(TAG, "✅ App protegida en background: $packageName")
                }
            }

            true

        } catch (e: Exception) {
            Log.e(TAG, "Error protegiendo app: ${e.message}")
            false
        }
    }








    /**
     * Configuración completa de app de delivery con overlay
     */
    fun setupDeliveryAppWithOverlay(packageName: String): Boolean {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🚚 CONFIGURANDO APP DE DELIVERY")
        Log.i(TAG, "Package: $packageName")
        Log.i(TAG, "════════════════════════════════════════")

        if (!isDeviceOwner()) {
            Log.e(TAG, "❌ No somos Device Owner")
            return false
        }

        try {
            // 1. Verificar instalación
            val isInstalled = try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }

            if (!isInstalled) {
                Log.w(TAG, "⚠️ App no instalada: $packageName")
                return false
            }

            Log.i(TAG, "✅ App instalada")

            // 2. Otorgar permisos runtime estándar
            val permissions = mutableListOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.READ_PHONE_STATE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }

            Log.i(TAG, "📋 Otorgando ${permissions.size} permisos...")
            grantPermissionsToApp(packageName, permissions)

            // 3. Forzar overlay
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.i(TAG, "🔝 Forzando permiso de overlay...")
                forceOverlayPermission(packageName)
            }

            // 4. Deshabilitar optimización de batería
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.i(TAG, "⚡ Deshabilitando optimización de batería...")
                disableBatteryOptimizationForApp(packageName)
            }

            // 5. Proteger app en background
            Log.i(TAG, "🛡️ Protegiendo app en background...")
            setAppAsProtected(packageName)

            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "✅ APP DE DELIVERY CONFIGURADA")
            Log.i(TAG, "════════════════════════════════════════")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "❌ ERROR CONFIGURANDO APP")
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "Error: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Configura múltiples apps de delivery
     */
    fun setupMultipleDeliveryApps(packageNames: List<String>): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()

        packageNames.forEach { packageName ->
            val success = setupDeliveryAppWithOverlay(packageName)
            results[packageName] = success
        }

        return results
    }

    /**
     * Bloquea el dispositivo
     */
    fun lockDevice() {
        try {
            if (!isDeviceOwner()) {
                Log.e(TAG, "❌ No somos Device Owner")
                return
            }

            Log.i(TAG, "🔒 Bloqueando dispositivo...")
            dpm.lockNow()

        } catch (e: Exception) {
            Log.e(TAG, "Error bloqueando dispositivo: ${e.message}")
        }
    }

    /**
     * Sincroniza con el servidor
     */
    /**
     * Sincroniza con el servidor
     */
    suspend fun syncWithServer() = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔄 Sincronizando con servidor...")

            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )







            val response = apiClient.syncPolicies(deviceId, getCurrentBlockedApps())

            if (response != null) {
                Log.i(TAG, "✅ Sincronización exitosa")

                // Guardar código de emergencia si viene
                response.emergencyCode?.let { code ->
                    if (code.isNotEmpty()) {
                        saveEmergencyCode(code)
                        Log.i(TAG, "🆘 Código de emergencia actualizado")
                    }
                }

                // ✅ NUEVO: Si el control está desactivado, limpiar todo
                if (!response.dataControlEnabled) {
                    Log.i(TAG, "🔓 Control desactivado desde el servidor")
                    clearAllPolicies()
                    return@withContext true
                }

                // Procesar comandos pendientes
                response.commands?.forEach { command ->
                    when (command.type) {
                        "lock" -> {
                            Log.w(TAG, "🔒 Comando de bloqueo recibido")
                            lockDevice()
                            apiClient.reportCommandExecuted(deviceId, "lock", true, null)
                        }
                        "apply_policy" -> {
                            Log.i(TAG, "📋 Comando de aplicar política recibido")
                            if (response.policy != null) {
                                val success = applyPolicy(response.policy)
                                apiClient.reportCommandExecuted(deviceId, "apply_policy", success, null)
                            }
                        }
                        "setup_delivery_apps" -> {
                            Log.i(TAG, "🚚 Comando de configurar apps recibido")
                            @Suppress("UNCHECKED_CAST")
                            val packageNames = command.payload?.get("packageNames") as? List<String>
                            if (packageNames != null) {
                                val results = setupMultipleDeliveryApps(packageNames)
                                val allSuccess = results.values.all { it }
                                apiClient.reportCommandExecuted(
                                    deviceId,
                                    "setup_delivery_apps",
                                    allSuccess,
                                    if (allSuccess) null else "Algunas apps fallaron"
                                )
                            }
                        }
                    }
                }

                // Aplicar política si hay una asignada y control está activo
                if (response.dataControlEnabled && response.policy != null) {
                    applyPolicy(response.policy)
                } else if (!response.dataControlEnabled) {
                    // Si no hay control activo, asegurarse de limpiar todo
                    clearAllPolicies()
                }

                true
            } else {
                Log.e(TAG, "❌ Error en sincronización")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error en sincronización: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Permite instalación temporal desde App Store
     */
    fun allowAppStoreInstallation(packageName: String): Boolean {
        return try {
            Log.i(TAG, "✅ Permitiendo instalación temporal de $packageName")

            // Desbloquear la app si está bloqueada
            try {
                dpm.setApplicationHidden(adminComponent, packageName, false)
            } catch (e: Exception) {
                // Ignorar si no estaba bloqueada
            }

            // Remover restricciones de instalación temporalmente
            try {
                dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_APPS)
                dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron limpiar restricciones: ${e.message}")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error permitiendo instalación: ${e.message}")
            false
        }
    }

    /**
     * Re-aplica las restricciones después de la instalación
     */
    fun reapplyRestrictions() {
        try {
            Log.i(TAG, "♻️ Re-aplicando restricciones")

            // Re-leer la política guardada
            val prefs = context.getSharedPreferences("mdm_policies", Context.MODE_PRIVATE)
            val policyString = prefs.getString("current_policy", null)

            if (policyString != null && policyString.isNotEmpty()) {
                val policy = JSONObject(policyString)

                // Re-aplicar restricciones de sistema si existen
                if (policy.has("systemRestrictions")) {
                    val sysRestrictions = policy.getJSONObject("systemRestrictions")

                    // Bloqueo de instalación
                    if (sysRestrictions.optBoolean("blockInstallation", false)) {
                        dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_APPS)
                        Log.i(TAG, "   🔒 Bloqueo de instalación re-aplicado")
                    }
                }

                // Re-aplicar bloqueos de apps si había whitelist activo
                if (policy.has("appsRestrictions")) {
                    applyAppRestrictions(policy)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error re-aplicando restricciones: ${e.message}")
        }
    }


}