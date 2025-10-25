package com.pfsmx.mdmempresarial.manager

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.json.JSONObject

/**
 * SafetyManager - Sistema de seguridad para prevenir bootloops y problemas críticos
 *
 * VERSIÓN ACTUALIZADA: Ya NO activa modo seguro en modelos con filtrado de políticas
 * Solo filtra las políticas peligrosas automáticamente
 */
object SafetyManager {

    private const val TAG = "SafetyManager"
    private const val PREFS_NAME = "mdm_safety"

    // Claves de preferencias
    private const val KEY_SAFE_MODE_ACTIVE = "safe_mode_active"
    private const val KEY_SAFE_MODE_REASON = "safe_mode_reason"
    private const val KEY_SAFE_MODE_TIMESTAMP = "safe_mode_timestamp"
    private const val KEY_SAFE_MODE_COUNT = "safe_mode_count"

    // ✅ NUEVA LÓGICA: Modelos con filtrado automático (NO activan modo seguro)
    private val MODELS_WITH_POLICY_FILTERING = listOf(
        "moto g24",
        "moto g24 power",
    )

    /**
     * Verifica si el dispositivo actual tiene filtrado automático de políticas
     */
    private fun hasAutomaticPolicyFiltering(): Boolean {
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        // Verificar lista de modelos
        if (MODELS_WITH_POLICY_FILTERING.any { model.contains(it) }) {
            return true
        }

        // Verificar patrones específicos
        if (manufacturer.contains("motorola") && model.contains("g24")) {
            return true
        }

        return false
    }

    /**
     * Activa el modo seguro del sistema
     *
     * ✅ MODIFICADO: NO activa en modelos con filtrado automático
     */
    fun activateSafeMode(context: Context, reason: String) {
        // ✅ NUEVA VERIFICACIÓN: Si el modelo tiene filtrado automático, NO activar modo seguro
        if (hasAutomaticPolicyFiltering()) {
            Log.w(TAG, "════════════════════════════════════════")
            Log.w(TAG, "ℹ️ MODELO CON FILTRADO AUTOMÁTICO")
            Log.w(TAG, "════════════════════════════════════════")
            Log.w(TAG, "Modelo: ${Build.MODEL}")
            Log.w(TAG, "Razón de activación ignorada: $reason")
            Log.w(TAG, "════════════════════════════════════════")
            Log.w(TAG, "✅ NO se activa modo seguro")
            Log.w(TAG, "✅ Las políticas peligrosas se filtran automáticamente")
            Log.w(TAG, "✅ El dispositivo funciona con políticas seguras")
            Log.w(TAG, "════════════════════════════════════════")
            return  // ✅ NO activar modo seguro
        }

        // Para otros modelos, activar modo seguro normalmente
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_SAFE_MODE_COUNT, 0) + 1

        prefs.edit().apply {
            putBoolean(KEY_SAFE_MODE_ACTIVE, true)
            putString(KEY_SAFE_MODE_REASON, reason)
            putLong(KEY_SAFE_MODE_TIMESTAMP, System.currentTimeMillis())
            putInt(KEY_SAFE_MODE_COUNT, count)
            apply()
        }

        Log.e(TAG, "════════════════════════════════════════")
        Log.e(TAG, "🛡️ MODO SEGURO ACTIVADO")
        Log.e(TAG, "════════════════════════════════════════")
        Log.e(TAG, "Razón: $reason")
        Log.e(TAG, "Timestamp: ${System.currentTimeMillis()}")
        Log.e(TAG, "Activaciones totales: $count")
        Log.e(TAG, "════════════════════════════════════════")
    }

    /**
     * Desactiva el modo seguro
     */
    fun deactivateSafeMode(context: Context) {
        val prefs = getPrefs(context)

        prefs.edit().apply {
            putBoolean(KEY_SAFE_MODE_ACTIVE, false)
            remove(KEY_SAFE_MODE_REASON)
            remove(KEY_SAFE_MODE_TIMESTAMP)
            apply()
        }

        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "✅ MODO SEGURO DESACTIVADO")
        Log.i(TAG, "════════════════════════════════════════")
    }

    /**
     * Verifica si el modo seguro está activo
     */
    fun isSafeModeActive(context: Context): Boolean {
        // ✅ Los modelos con filtrado automático NUNCA están en modo seguro
        if (hasAutomaticPolicyFiltering()) {
            return false
        }

        return getPrefs(context).getBoolean(KEY_SAFE_MODE_ACTIVE, false)
    }

    /**
     * Obtiene información del modo seguro
     */
    fun getSafeModeInfo(context: Context): String {
        if (!isSafeModeActive(context)) {
            return "Modo seguro: Inactivo"
        }

        val prefs = getPrefs(context)
        val reason = prefs.getString(KEY_SAFE_MODE_REASON, "Desconocida")
        val timestamp = prefs.getLong(KEY_SAFE_MODE_TIMESTAMP, 0)
        val count = prefs.getInt(KEY_SAFE_MODE_COUNT, 0)

        return """
            🛡️ MODO SEGURO ACTIVO
            
            Razón: $reason
            Activado: ${formatTimestamp(timestamp)}
            Activaciones totales: $count
            
            Para desactivar:
            1. Identifique y resuelva el problema
            2. Use la opción de desactivación en la app
            3. O contacte soporte técnico
        """.trimIndent()
    }

    /**
     * Obtiene información detallada en formato JSON
     */
    fun getSafeModeInfoJson(context: Context): JSONObject {
        val json = JSONObject()

        json.put("active", isSafeModeActive(context))
        json.put("hasAutomaticFiltering", hasAutomaticPolicyFiltering())
        json.put("deviceModel", Build.MODEL)
        json.put("deviceManufacturer", Build.MANUFACTURER)

        if (isSafeModeActive(context)) {
            val prefs = getPrefs(context)
            json.put("reason", prefs.getString(KEY_SAFE_MODE_REASON, ""))
            json.put("timestamp", prefs.getLong(KEY_SAFE_MODE_TIMESTAMP, 0))
            json.put("count", prefs.getInt(KEY_SAFE_MODE_COUNT, 0))
        }

        return json
    }

    /**
     * Resetea el contador de activaciones
     */
    fun resetSafeModeCount(context: Context) {
        getPrefs(context).edit().apply {
            putInt(KEY_SAFE_MODE_COUNT, 0)
            apply()
        }
        Log.i(TAG, "✅ Contador de modo seguro reseteado")
    }

    /**
     * Verifica si se debe activar modo seguro por intentos excesivos
     */
    fun checkExcessiveActivations(context: Context): Boolean {
        val count = getPrefs(context).getInt(KEY_SAFE_MODE_COUNT, 0)

        if (count >= 5) {
            Log.e(TAG, "⚠️ ADVERTENCIA: Modo seguro activado $count veces")
            Log.e(TAG, "   Esto indica un problema persistente")
            return true
        }

        return false
    }

    /**
     * Limpia completamente el modo seguro
     */
    fun clearSafeMode(context: Context) {
        getPrefs(context).edit().clear().apply()
        Log.i(TAG, "✅ Modo seguro completamente limpiado")
    }

    // ═══════════════════════════════════════════════════════════
    // FUNCIONES AUXILIARES
    // ═══════════════════════════════════════════════════════════

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Desconocido"

        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }

    // ═══════════════════════════════════════════════════════════
    // FUNCIONES DE DIAGNÓSTICO
    // ═══════════════════════════════════════════════════════════

    /**
     * Genera un reporte completo del estado de seguridad
     */
    fun generateSecurityReport(context: Context): String {
        val sb = StringBuilder()

        sb.appendLine("════════════════════════════════════════")
        sb.appendLine("📊 REPORTE DE SEGURIDAD MDM")
        sb.appendLine("════════════════════════════════════════")
        sb.appendLine()

        // Información del dispositivo
        sb.appendLine("📱 DISPOSITIVO:")
        sb.appendLine("   Modelo: ${Build.MODEL}")
        sb.appendLine("   Fabricante: ${Build.MANUFACTURER}")
        sb.appendLine("   Android: ${Build.VERSION.RELEASE}")
        sb.appendLine("   SDK: ${Build.VERSION.SDK_INT}")
        sb.appendLine()

        // Modo de filtrado automático
        sb.appendLine("🔧 FILTRADO AUTOMÁTICO:")
        if (hasAutomaticPolicyFiltering()) {
            sb.appendLine("   ✅ ACTIVO")
            sb.appendLine("   Este modelo filtra políticas peligrosas automáticamente")
            sb.appendLine("   NO requiere modo seguro")
        } else {
            sb.appendLine("   ⚪ NO APLICA")
            sb.appendLine("   Este modelo no requiere filtrado especial")
        }
        sb.appendLine()

        // Estado de modo seguro
        sb.appendLine("🛡️ MODO SEGURO:")
        if (isSafeModeActive(context)) {
            val prefs = getPrefs(context)
            sb.appendLine("   ❌ ACTIVO")
            sb.appendLine("   Razón: ${prefs.getString(KEY_SAFE_MODE_REASON, "")}")
            sb.appendLine("   Desde: ${formatTimestamp(prefs.getLong(KEY_SAFE_MODE_TIMESTAMP, 0))}")
            sb.appendLine("   Activaciones: ${prefs.getInt(KEY_SAFE_MODE_COUNT, 0)}")
        } else {
            sb.appendLine("   ✅ INACTIVO")
            sb.appendLine("   El sistema funciona normalmente")
        }
        sb.appendLine()

        sb.appendLine("════════════════════════════════════════")

        return sb.toString()
    }

    /**
     * Log del reporte de seguridad
     */
    fun logSecurityReport(context: Context) {
        val report = generateSecurityReport(context)
        Log.i(TAG, report)
    }
}