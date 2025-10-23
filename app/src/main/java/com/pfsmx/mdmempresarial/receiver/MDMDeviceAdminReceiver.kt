package com.pfsmx.mdmempresarial.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.util.Log

class MDMDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "MDMDeviceAdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.wtf(TAG, "════════════════════════════════════════")
        Log.wtf(TAG, "✅ DEVICE ADMIN HABILITADO")
        Log.wtf(TAG, "════════════════════════════════════════")

        saveLog(context, "Device Admin Enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.wtf(TAG, "⚠️ Device Admin deshabilitado")
        saveLog(context, "Device Admin Disabled")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)

        Log.wtf(TAG, "════════════════════════════════════════")
        Log.wtf(TAG, "🎉 PROVISIONING COMPLETADO VÍA QR")
        Log.wtf(TAG, "════════════════════════════════════════")

        saveLog(context, "Provisioning Complete - START")

        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(context, MDMDeviceAdminReceiver::class.java)

            // Información del dispositivo
            Log.wtf(TAG, "📱 Info del dispositivo:")
            Log.wtf(TAG, "   Package: ${context.packageName}")
            Log.wtf(TAG, "   Component: ${componentName.flattenToString()}")
            Log.wtf(TAG, "   Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            saveLog(context, "Device info logged")

            // Verificar que somos Device Owner
            val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
            Log.wtf(TAG, "¿Es Device Owner? $isDeviceOwner")
            saveLog(context, "Is Device Owner: $isDeviceOwner")

            if (!isDeviceOwner) {
                Log.wtf(TAG, "❌ NO somos Device Owner")
                saveLog(context, "ERROR: Not Device Owner")

                // Intentar obtener más info del Device Owner actual
                try {
                    // Verificar si hay algún Device Owner configurado
                    val isDeviceOwnerSet = dpm.isDeviceOwnerApp(context.packageName)
                    Log.wtf(TAG, "¿Nuestro paquete es Device Owner? $isDeviceOwnerSet")

                    // Obtener lista de administradores activos
                    val activeAdmins = dpm.activeAdmins
                    if (activeAdmins != null && activeAdmins.isNotEmpty()) {
                        Log.wtf(TAG, "Administradores activos encontrados:")
                        saveLog(context, "Active admins found: ${activeAdmins.size}")
                        activeAdmins.forEach { admin ->
                            Log.wtf(TAG, "  - ${admin.packageName} / ${admin.className}")
                            saveLog(context, "Admin: ${admin.packageName}/${admin.className}")
                        }
                    } else {
                        Log.wtf(TAG, "No hay administradores activos")
                        saveLog(context, "No active admins")
                    }

                } catch (e: Exception) {
                    Log.wtf(TAG, "Error obteniendo info de administradores: ${e.message}")
                    saveLog(context, "Error getting admin info: ${e.message}")
                }

                return
            }

            // Obtener extras del provisioning
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val extras = intent.getParcelableExtra<PersistableBundle>(
                    DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE
                )

                if (extras != null) {
                    Log.wtf(TAG, "📋 Extras recibidos:")
                    saveLog(context, "Extras found")
                    extras.keySet().forEach { key ->
                        val value = extras.get(key)
                        Log.wtf(TAG, "   $key = $value")
                        saveLog(context, "Extra: $key = $value")
                    }
                } else {
                    Log.wtf(TAG, "⚠️ No se recibieron extras")
                    saveLog(context, "No extras received")
                }
            }

            // Marcar como provisionado
            val prefs = context.getSharedPreferences("mdm_config", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("provisioned_via_qr", true)
                putLong("provisioning_timestamp", System.currentTimeMillis())
                putString("provisioning_method", "QR")
                apply()
            }

            Log.wtf(TAG, "✅ Preferencias guardadas")
            saveLog(context, "Preferences saved")

            // Asegurar que la app esté visible
            try {
                val isHidden = dpm.isApplicationHidden(componentName, context.packageName)
                Log.wtf(TAG, "App oculta antes: $isHidden")

                if (isHidden) {
                    dpm.setApplicationHidden(componentName, context.packageName, false)
                    Log.wtf(TAG, "✅ App habilitada (ya no está oculta)")
                } else {
                    Log.wtf(TAG, "✅ App ya estaba visible")
                }

                saveLog(context, "App visibility checked and set")

            } catch (e: Exception) {
                Log.wtf(TAG, "⚠️ Error verificando/habilitando app: ${e.message}")
                saveLog(context, "ERROR with app visibility: ${e.message}")
                e.printStackTrace()
            }

            // Habilitar la app en el launcher
            try {
                val pm = context.packageManager
                val launcherComponent = ComponentName(
                    context,
                    "com.pfsmx.mdmempresarial.ui.MainActivity"
                )
                pm.setComponentEnabledSetting(
                    launcherComponent,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                Log.wtf(TAG, "✅ MainActivity habilitada en launcher")
                saveLog(context, "MainActivity enabled in launcher")
            } catch (e: Exception) {
                Log.wtf(TAG, "⚠️ Error habilitando MainActivity: ${e.message}")
                saveLog(context, "ERROR enabling MainActivity: ${e.message}")
            }

            Log.wtf(TAG, "⏳ Esperando 2 segundos antes de abrir la app...")
            saveLog(context, "Waiting before launch")

            // Esperar y abrir la app
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    Log.wtf(TAG, "🚀 Iniciando MainActivity...")
                    saveLog(context, "Attempting to start MainActivity")

                    val launchIntent = Intent(context, com.pfsmx.mdmempresarial.MainActivity::class.java)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    launchIntent.putExtra("provisioned_via_qr", true)

                    context.startActivity(launchIntent)

                    Log.wtf(TAG, "✅ MainActivity iniciada exitosamente")
                    saveLog(context, "MainActivity started successfully")

                } catch (e: Exception) {
                    Log.wtf(TAG, "❌ Error iniciando MainActivity: ${e.message}")
                    Log.wtf(TAG, "Stack trace:")
                    e.printStackTrace()
                    saveLog(context, "ERROR starting MainActivity: ${e.message}")
                    saveLog(context, "Stack: ${e.stackTraceToString()}")
                }
            }, 2000)

            Log.wtf(TAG, "════════════════════════════════════════")
            Log.wtf(TAG, "✅ onProfileProvisioningComplete COMPLETADO")
            Log.wtf(TAG, "════════════════════════════════════════")
            saveLog(context, "Provisioning Complete - END SUCCESS")

        } catch (e: Exception) {
            Log.wtf(TAG, "════════════════════════════════════════")
            Log.wtf(TAG, "💥 EXCEPCIÓN en onProfileProvisioningComplete")
            Log.wtf(TAG, "════════════════════════════════════════")
            Log.wtf(TAG, "Mensaje: ${e.message}")
            Log.wtf(TAG, "Stack trace:")
            e.printStackTrace()

            saveLog(context, "EXCEPTION in onProfileProvisioningComplete")
            saveLog(context, "Message: ${e.message}")
            saveLog(context, "Stack: ${e.stackTraceToString()}")
        }
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.wtf(TAG, "🔒 Entrando en modo kiosk: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.wtf(TAG, "🔓 Saliendo de modo kiosk")
    }

    // ⚠️ GUARDAR LOGS EN ARCHIVO
    private fun saveLog(context: Context, message: String) {
        try {
            val timestamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                java.util.Locale.US
            ).format(java.util.Date())

            val logFile = java.io.File(context.filesDir, "provisioning.log")
            logFile.appendText("[$timestamp] $message\n")

            // También intentar guardarlo en un lugar accesible por ADB
            try {
                val externalLog = java.io.File(
                    context.getExternalFilesDir(null),
                    "provisioning.log"
                )
                externalLog.appendText("[$timestamp] $message\n")
            } catch (e: Exception) {
                // Ignorar si no se puede escribir en externo
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error guardando log: ${e.message}")
        }
    }
}