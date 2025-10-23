package com.pfsmx.mdmempresarial.manager

import android.content.Context
import android.util.Log

object BootLoopDetector {

    private const val TAG = "BootLoopDetector"
    private const val PREFS_NAME = "bootloop_detector"
    private const val KEY_BOOT_COUNT = "boot_count"
    private const val KEY_LAST_BOOT_TIME = "last_boot_time"
    private const val KEY_SAFE_MODE_ENABLED = "safe_mode_enabled"

    private const val BOOT_THRESHOLD = 3  // 3 reinicios en poco tiempo = problema
    private const val TIME_WINDOW_MS = 10 * 60 * 1000L  // 10 minutos

    /**
     * Registra un boot y detecta si hay bootloop
     */
    fun registerBoot(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val currentTime = System.currentTimeMillis()
        val lastBootTime = prefs.getLong(KEY_LAST_BOOT_TIME, 0)
        val bootCount = prefs.getInt(KEY_BOOT_COUNT, 0)

        val timeSinceLastBoot = currentTime - lastBootTime

        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "🔄 BOOT DETECTADO")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "⏱️ Tiempo desde último boot: ${timeSinceLastBoot / 1000} segundos")
        Log.i(TAG, "🔢 Boots recientes: $bootCount")

        val newBootCount = if (timeSinceLastBoot < TIME_WINDOW_MS) {
            bootCount + 1
        } else {
            1  // Resetear contador si pasó mucho tiempo
        }

        prefs.edit()
            .putLong(KEY_LAST_BOOT_TIME, currentTime)
            .putInt(KEY_BOOT_COUNT, newBootCount)
            .apply()

        // Detectar bootloop
        if (newBootCount >= BOOT_THRESHOLD) {
            Log.e(TAG, "⚠️⚠️⚠️ BOOTLOOP DETECTADO ⚠️⚠️⚠️")
            Log.e(TAG, "$newBootCount reinicios en ${TIME_WINDOW_MS / 1000} segundos")
            Log.e(TAG, "Activando modo seguro automáticamente...")

            prefs.edit()
                .putBoolean(KEY_SAFE_MODE_ENABLED, true)
                .putInt(KEY_BOOT_COUNT, 0)  // Resetear
                .apply()

            return true  // Bootloop detectado
        }

        Log.i(TAG, "✅ Boot normal (${newBootCount}/${BOOT_THRESHOLD})")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        return false  // Boot normal
    }

    /**
     * Verifica si el modo seguro está activado
     */
    fun isSafeModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SAFE_MODE_ENABLED, false)
    }

    /**
     * Desactiva el modo seguro (cuando el usuario confirma que todo está bien)
     */
    fun disableSafeMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_SAFE_MODE_ENABLED, false)
            .putInt(KEY_BOOT_COUNT, 0)
            .apply()

        Log.i(TAG, "✅ Modo seguro desactivado")
    }

    /**
     * Obtiene estadísticas de boots
     */
    fun getBootStats(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bootCount = prefs.getInt(KEY_BOOT_COUNT, 0)
        val lastBootTime = prefs.getLong(KEY_LAST_BOOT_TIME, 0)
        val safeModeEnabled = prefs.getBoolean(KEY_SAFE_MODE_ENABLED, false)

        return """
            Boots recientes: $bootCount
            Último boot: ${if (lastBootTime > 0) java.util.Date(lastBootTime) else "Nunca"}
            Modo seguro: ${if (safeModeEnabled) "ACTIVADO" else "Desactivado"}
        """.trimIndent()
    }
}