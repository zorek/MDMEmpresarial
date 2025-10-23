package com.pfsmx.mdmempresarial.manager

import android.content.Context
import android.os.Build
import android.util.Log

object SafetyManager {

    private const val TAG = "SafetyManager"

    // ⚠️ Apps que NUNCA deben bloquearse en NINGUNA marca
    private val CRITICAL_ANDROID_APPS = listOf(
        // Sistema base Android
        "com.android.systemui",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",

        // ⚠️ LAUNCHERS (CRÍTICO - Sin estos no hay Home)
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",

        // Google Play Services (CRÍTICO)
        //"com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.ext.services",

        // Servicios esenciales
        "com.android.phone",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.providers.settings",
        "com.android.providers.contacts",

        // Frameworks
        "android",
        "system"
    )

    // Apps críticas por fabricante
    private val SAMSUNG_CRITICAL_APPS = listOf(
        // Launchers Samsung
        "com.samsung.android.oneui.home",
        "com.samsung.android.lool",
        "com.samsung.android.app.launcher",
        "com.sec.android.app.launcher",

        "com.samsung.android.messaging",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.knox.securefolder",
        "com.samsung.android.scloud",
        "com.samsung.android.sm.devicesecurity",
        "com.sec.android.app.servicemodeapp",
        "com.wssyncmldm"
    )

    private val XIAOMI_CRITICAL_APPS = listOf(
        // Launcher Xiaomi
        "com.miui.home",

        "com.android.thememanager",
        "com.miui.securitycenter",
        "com.miui.cloudservice",
        "com.xiaomi.finddevice",
        "com.miui.systemAdSolution",
        "com.android.updater",
        "com.xiaomi.account",
        "com.miui.cloudbackup",
        "com.xiaomi.miplay_client"
    )

    private val HUAWEI_CRITICAL_APPS = listOf(
        // Launcher Huawei
        "com.huawei.android.launcher",

        "com.huawei.systemmanager",
        "com.huawei.appmarket",
        "com.huawei.hwid",
        "com.huawei.phoneservice",
        "com.huawei.android.hsf",
        "com.huawei.android.finddevice"
    )

    private val MOTOROLA_CRITICAL_APPS = listOf(
        // ⚠️ LAUNCHER MOTOROLA (EL MÁS IMPORTANTE)
        "com.motorola.launcher3",
        "com.motorola.launcher",

        "com.motorola.ccc.ota",
        "com.motorola.motocare",
        "com.motorola.camera3",
        "com.motorola.camerasettings",
        "com.motorola.actions"
    )

    private val OPPO_CRITICAL_APPS = listOf(
        // Launcher Oppo
        "com.oppo.launcher",

        "com.coloros.safecenter",
        "com.coloros.phonemanager",
        "com.oppo.usercenter",
        "com.coloros.systemclone"
    )

    private val VIVO_CRITICAL_APPS = listOf(
        // Launcher Vivo
        "com.bbk.launcher2",

        "com.vivo.smartmultiwindow",
        "com.vivo.securedaemonservice",
        "com.vivo.safecenter"
    )

    private val REALME_CRITICAL_APPS = listOf(
        // Launcher Realme
        "com.oppo.launcher",

        "com.coloros.safecenter",
        "com.oppo.operationManual"
    )

    private val ONEPLUS_CRITICAL_APPS = listOf(
        // Launcher OnePlus
        "net.oneplus.launcher",

        "com.oneplus.security",
        "com.oneplus.opsystemhelper"
    )

    private val LG_CRITICAL_APPS = listOf(
        // Launcher LG
        "com.lge.launcher3",
        "com.lge.launcher2",

        "com.lge.smartsetting",
        "com.lge.update.client"
    )

    private val NOKIA_CRITICAL_APPS = listOf(
        "com.hmdglobal.app.activation",
        "com.evenwell.custmanager"
    )

    /**
     * Obtiene todas las apps críticas según el fabricante del dispositivo
     */
    fun getCriticalApps(context: Context): Set<String> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🛡️ DETECTANDO APPS CRÍTICAS")
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "📱 Manufacturer: $manufacturer")
        Log.i(TAG, "🏷️ Brand: $brand")
        Log.i(TAG, "📦 Model: ${Build.MODEL}")

        val criticalApps = mutableSetOf<String>()

        // Siempre incluir apps Android base
        criticalApps.addAll(CRITICAL_ANDROID_APPS)

        // Agregar apps específicas del fabricante
        when {
            manufacturer.contains("samsung") || brand.contains("samsung") -> {
                criticalApps.addAll(SAMSUNG_CRITICAL_APPS)
                Log.i(TAG, "✅ Protección Samsung activada")
            }
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                    brand.contains("redmi") || brand.contains("poco") -> {
                criticalApps.addAll(XIAOMI_CRITICAL_APPS)
                Log.i(TAG, "✅ Protección Xiaomi/Redmi/Poco activada")
            }
            manufacturer.contains("huawei") || brand.contains("huawei") ||
                    brand.contains("honor") -> {
                criticalApps.addAll(HUAWEI_CRITICAL_APPS)
                Log.i(TAG, "✅ Protección Huawei/Honor activada")
            }
            manufacturer.contains("motorola") || brand.contains("motorola") -> {
                criticalApps.addAll(MOTOROLA_CRITICAL_APPS)
                Log.i(TAG, "✅ Protección Motorola activada")
            }
            manufacturer.contains("oppo") || brand.contains("oppo") -> {
                criticalApps.addAll(OPPO_CRITICAL_APPS)
                Log.i(TAG, "✅ Protección Oppo activada")
            }
            manufacturer.contains("vivo") || brand.contains("vivo") -> {
                criticalApps.addAll(VIVO_CRITICAL_APPS)
                Log.i(TAG, "✅ Protección Vivo activada")
            }
            manufacturer.contains("realme") || brand.contains("realme") -> {
                criticalApps.addAll(REALME_CRITICAL_APPS)
                Log.i(TAG, "✅ Protección Realme activada")
            }
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> {
                criticalApps.addAll(ONEPLUS_CRITICAL_APPS)
                Log.i(TAG, "✅ Protección OnePlus activada")
            }
            manufacturer.contains("lge") || brand.contains("lge") || brand.contains("lg") -> {
                criticalApps.addAll(LG_CRITICAL_APPS)
                Log.i(TAG, "✅ Protección LG activada")
            }
            manufacturer.contains("hmd") || brand.contains("nokia") -> {
                criticalApps.addAll(NOKIA_CRITICAL_APPS)
                Log.i(TAG, "✅ Protección Nokia activada")
            }
            else -> {
                Log.w(TAG, "⚠️ Fabricante desconocido - Solo protección Android base")
            }
        }

        // Siempre incluir la app MDM
        criticalApps.add(context.packageName)

        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🛡️ Total apps críticas protegidas: ${criticalApps.size}")
        Log.i(TAG, "════════════════════════════════════════")

        return criticalApps
    }

    /**
     * Verifica si una app es crítica y no debe bloquearse
     */
    fun isCriticalApp(context: Context, packageName: String): Boolean {
        return packageName in getCriticalApps(context)
    }

    /**
     * Valida una lista de apps permitidas antes de aplicarlas
     * Retorna la lista corregida con apps críticas incluidas
     */
    fun validateAllowedApps(context: Context, requestedApps: List<String>): List<String> {
        val criticalApps = getCriticalApps(context)
        val validatedApps = mutableSetOf<String>()

        // Siempre incluir apps críticas
        validatedApps.addAll(criticalApps)

        // Agregar apps solicitadas
        validatedApps.addAll(requestedApps)

        val addedCritical = criticalApps.size - requestedApps.count { it in criticalApps }

        if (addedCritical > 0) {
            Log.w(TAG, "⚠️ Se agregaron automáticamente $addedCritical apps críticas")
            Log.w(TAG, "   Esto incluye el launcher y apps del sistema esenciales")
        }

        return validatedApps.toList()
    }

    /**
     * Obtiene información del dispositivo para logging
     */
    fun getDeviceInfo(): String {
        return """
            Manufacturer: ${Build.MANUFACTURER}
            Brand: ${Build.BRAND}
            Model: ${Build.MODEL}
            Device: ${Build.DEVICE}
            Product: ${Build.PRODUCT}
            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        """.trimIndent()
    }
}