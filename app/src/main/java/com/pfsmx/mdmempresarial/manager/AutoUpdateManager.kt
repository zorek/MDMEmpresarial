package com.pfsmx.mdmempresarial.manager

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.pfsmx.mdmempresarial.InstallReceiver
import com.pfsmx.mdmempresarial.ManagedApp
import com.pfsmx.mdmempresarial.api.MDMApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class AutoUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AutoUpdateManager"
    }

    private val apiClient = MDMApiClient(context)
    private val policyManager = PolicyManager(context)

    /**
     * Verifica y aplica actualizaciones automáticas para todas las apps
     */
    suspend fun checkAndApplyUpdates(): Int = withContext(Dispatchers.IO) {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🔄 VERIFICANDO ACTUALIZACIONES")
        Log.i(TAG, "════════════════════════════════════════")

        try {
            // Obtener lista de apps del servidor
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )

            val apps = apiClient.getAvailableApps(deviceId) ?: emptyList()
            Log.i(TAG, "📦 Apps disponibles: ${apps.size}")

            // Filtrar solo las que tienen autoUpdate habilitado
            val autoUpdateApps = apps.filter { it.specialConfig.autoUpdate }
            Log.i(TAG, "🔄 Apps con auto-update: ${autoUpdateApps.size}")

            if (autoUpdateApps.isEmpty()) {
                Log.i(TAG, "ℹ️ No hay apps con auto-update habilitado")
                return@withContext 0
            }

            var updatedCount = 0

            // Verificar cada app
            for (app in autoUpdateApps) {
                try {
                    val needsUpdate = checkIfUpdateNeeded(app)

                    if (needsUpdate) {
                        Log.i(TAG, "📥 Actualizando: ${app.name}")

                        val success = updateApp(app)

                        if (success) {
                            updatedCount++
                            Log.i(TAG, "✅ ${app.name} actualizada correctamente")
                        } else {
                            Log.e(TAG, "❌ Error actualizando ${app.name}")
                        }

                        // Esperar entre actualizaciones para no saturar
                        kotlinx.coroutines.delay(10000) // 10 segundos
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando ${app.name}: ${e.message}")
                    e.printStackTrace()
                }
            }

            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "✅ ACTUALIZACIONES COMPLETADAS: $updatedCount")
            Log.i(TAG, "════════════════════════════════════════")

            return@withContext updatedCount

        } catch (e: Exception) {
            Log.e(TAG, "Error en checkAndApplyUpdates: ${e.message}")
            e.printStackTrace()
            return@withContext 0
        }
    }

    /**
     * Verifica si una app necesita actualización
     */
    private fun checkIfUpdateNeeded(app: ManagedApp): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(app.packageName, 0)

            val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }

            val serverVersionCode = app.version.code ?: 0

            val needsUpdate = serverVersionCode > installedVersionCode

            if (needsUpdate) {
                Log.i(TAG, "🔄 ${app.name}: v$installedVersionCode → v$serverVersionCode")
            } else {
                Log.d(TAG, "✅ ${app.name} está actualizada (v$installedVersionCode)")
            }

            needsUpdate

        } catch (e: PackageManager.NameNotFoundException) {
            // App no está instalada, no necesita update
            Log.d(TAG, "📦 ${app.name} no está instalada")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando versión de ${app.name}: ${e.message}")
            false
        }
    }

    /**
     * Actualiza una app específica
     */
    private suspend fun updateApp(app: ManagedApp): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "📥 Descargando ${app.name}...")

            // 1. Descargar APK
            val apkFile = apiClient.downloadApk(app._id) { progress ->
                if (progress % 25 == 0) { // Log cada 25%
                    Log.d(TAG, "   📥 Descarga: $progress%")
                }
            }

            if (apkFile == null || !apkFile.exists()) {
                Log.e(TAG, "❌ Error descargando APK de ${app.name}")
                return@withContext false
            }

            Log.i(TAG, "✅ APK descargado: ${apkFile.length() / 1024 / 1024}MB")

            // 2. Preparar dispositivo (remover restricciones)
            Log.i(TAG, "🔧 Preparando dispositivo...")
            val prepared = prepareDeviceForInstallation(app.packageName)

            if (!prepared) {
                Log.e(TAG, "❌ No se pudo preparar dispositivo")
                apkFile.delete()
                return@withContext false
            }

            // 3. Instalar
            Log.i(TAG, "📦 Instalando...")
            val installed = installApk(apkFile, app)

            // 4. Limpiar
            apkFile.delete()

            // 5. Re-aplicar restricciones
            kotlinx.coroutines.delay(2000)
            policyManager.reapplyRestrictions()

            return@withContext installed

        } catch (e: Exception) {
            Log.e(TAG, "Error en updateApp: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Prepara el dispositivo removiendo restricciones temporalmente
     */
    private suspend fun prepareDeviceForInstallation(packageName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!policyManager.isDeviceOwner()) {
                    return@withContext false
                }

                policyManager.allowAppStoreInstallation(packageName)
                kotlinx.coroutines.delay(1000)

                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error preparando instalación: ${e.message}")
                false
            }
        }
    }

    /**
     * Instala un APK silenciosamente
     */
    private suspend fun installApk(
        apkFile: File,
        app: ManagedApp
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(app.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setInstallReason(PackageManager.INSTALL_REASON_POLICY)
                }
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.use { activeSession ->
                FileInputStream(apkFile).use { inputStream ->
                    activeSession.openWrite("package", 0, -1).use { outputStream ->
                        inputStream.copyTo(outputStream)
                        activeSession.fsync(outputStream)
                    }
                }

                val intent = Intent(context, InstallReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                activeSession.commit(pendingIntent.intentSender)
            }

            // Esperar un poco para que se instale
            kotlinx.coroutines.delay(5000)

            // Verificar si se instaló
            val installed = try {
                context.packageManager.getPackageInfo(app.packageName, 0)
                true
            } catch (e: Exception) {
                false
            }

            installed

        } catch (e: Exception) {
            Log.e(TAG, "Error instalando APK: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}