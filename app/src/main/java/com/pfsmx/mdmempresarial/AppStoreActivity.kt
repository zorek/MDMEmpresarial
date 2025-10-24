package com.pfsmx.mdmempresarial

import android.app.PendingIntent
import android.app.ProgressDialog
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pfsmx.mdmempresarial.api.MDMApiClient
import com.pfsmx.mdmempresarial.manager.PolicyManager
import com.pfsmx.mdmempresarial.receiver.MDMDeviceAdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import com.pfsmx.mdmempresarial.utils.AppUtils
import com.pfsmx.mdmempresarial.utils.AppUtils.formatFileSize
import com.pfsmx.mdmempresarial.utils.AppUtils.getInstalledVersionCode
import com.pfsmx.mdmempresarial.utils.AppUtils.isAppInstalled

class AppStoreActivity : AppCompatActivity() {
    private val dpm by lazy {
        getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppStoreAdapter
    // ✅ CORRECTO - Se inicializa solo cuando se usa (después de onCreate)
    private val policyManager by lazy { PolicyManager(this) }
    private val apiClient by lazy { MDMApiClient(this) }
    private lateinit var emptyView: View
    private lateinit var progressBar: ProgressBar

    private var apps = mutableListOf<ManagedApp>()


    private val adminComponent: ComponentName by lazy {
        ComponentName(this, MDMDeviceAdminReceiver::class.java)
    }


    companion object {
        private const val TAG = "AppStoreActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_app_store)

           // policyManager = PolicyManager(this)
           // apiClient = MDMApiClient(this)

            setupToolbar()
            initViews()
            loadApps()

        } catch (e: Exception) {
            Log.e(TAG, "Error en onCreate", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = "🏪 App Store"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.appsRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        progressBar = findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppStoreAdapter(apps) { app -> showAppDetails(app) }
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            loadApps()
        }
    }

    // ==================== UTILIDADES ====================

    private fun isAppInstalled(packageName: String): Boolean {
        return AppUtils.isAppInstalled(this, packageName)
    }

    private fun getInstalledVersionCode(packageName: String): Int {
        return AppUtils.getInstalledVersionCode(this, packageName)
    }

    private fun formatFileSize(bytes: Long): String {
        return AppUtils.formatFileSize(bytes)
    }

    // ELIMINAR las versiones antiguas de estas funciones (si existen)

    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

                val result = withContext(Dispatchers.IO) {
                    apiClient.getAvailableApps(deviceId)
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    if (result != null && result.isNotEmpty()) {
                        apps.clear()
                        apps.addAll(result)
                        adapter.notifyDataSetChanged()

                        recyclerView.visibility = View.VISIBLE
                        emptyView.visibility = View.GONE
                    } else {
                        recyclerView.visibility = View.GONE
                        emptyView.visibility = View.VISIBLE
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE

                    Log.e(TAG, "Error cargando apps", e)
                    Toast.makeText(
                        this@AppStoreActivity,
                        "Error cargando apps: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Permite instalación desde el App Store temporalmente
     */
    fun allowAppStoreInstallation(packageName: String): Boolean {
        return try {
            Log.i(TAG, "✅ Permitiendo instalación de $packageName desde App Store")

            // Desbloquear la app si está bloqueada
            try {
                dpm.setApplicationHidden(adminComponent, packageName, false)
            } catch (e: Exception) {
                Log.w(TAG, "App no estaba bloqueada: ${e.message}")
            }

            // Deshabilitar restricción de instalación temporalmente
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
     * Re-aplica restricciones después de instalación
     */


    /**
     * Obtiene la política actual guardada
     */
    private fun getCurrentPolicy(): JSONObject? {
        return try {
            val prefs = this.getSharedPreferences("mdm_policies", Context.MODE_PRIVATE)
            val policyString = prefs.getString("current_policy", null)
            if (policyString != null) {
                JSONObject(policyString)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo política actual: ${e.message}")
            null
        }
    }






    private fun showAppDetails(app: ManagedApp) {
        val isInstalled = isAppInstalled(app.packageName)
        val installedVersion = if (isInstalled) getInstalledVersionCode(app.packageName) else 0
        val needsUpdate = isInstalled && installedVersion < (app.version.code ?: 0)

        val message = buildString {
            append("📱 ${app.name}\n\n")
            append("📦 Package: ${app.packageName}\n")
            append("📌 Versión: ${app.version.name ?: "N/A"}\n")
            append("📂 Categoría: ${getCategoryName(app.category)}\n")
            append("💾 Tamaño: ${formatFileSize(app.apk.size)}\n\n")

            if (app.description.isNotEmpty()) {
                append("📝 ${app.description}\n\n")
            }

            if (isInstalled) {
                append("✅ Ya instalada (versión $installedVersion)\n")
                if (needsUpdate) {
                    append("🔄 Actualización disponible\n")
                }
            } else {
                append("❌ No instalada\n")
            }

            if (app.requiredPermissions.isNotEmpty()) {
                append("\n🔐 Permisos requeridos:\n")
                app.requiredPermissions.take(5).forEach { perm ->
                    append("  • ${getPermissionName(perm)}\n")
                }
                if (app.requiredPermissions.size > 5) {
                    append("  • Y ${app.requiredPermissions.size - 5} más...\n")
                }
            }
        }

        val buttonText = when {
            needsUpdate -> "🔄 Actualizar"
            isInstalled -> "✅ Ya Instalada"
            else -> "📥 Instalar"
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("${app.icon} ${app.name}")
            .setMessage(message)
            .setNegativeButton("Cerrar", null)

        // Solo mostrar botón de instalar/actualizar si no está instalada o necesita actualización
        if (!isInstalled || needsUpdate) {
            builder.setPositiveButton(buttonText) { _, _ ->
                downloadAndInstall(app)
            }
        } else {
            builder.setPositiveButton("OK", null)
        }

        builder.show()
    }


    private suspend fun prepareForInstallation(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔧 Preparando dispositivo para instalación de $packageName")


            if (!policyManager.isDeviceOwner()) {
                Log.e(TAG, "❌ No somos Device Owner")
                return@withContext false
            }

            // 1. Desbloquear la app si está oculta
            try {
                val isHidden = dpm.isApplicationHidden(adminComponent, packageName)
                if (isHidden) {
                    dpm.setApplicationHidden(adminComponent, packageName, false)
                    Log.i(TAG, "✅ App desbloqueada")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo verificar/desbloquear app: ${e.message}")
            }

            // 2. CRÍTICO: Remover TODAS las restricciones de instalación
            try {
                // Restricción principal que está causando el error
                dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_APPS)
                Log.i(TAG, "✅ DISALLOW_INSTALL_APPS removida")

                // Otras restricciones relacionadas
                dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                Log.i(TAG, "✅ DISALLOW_INSTALL_UNKNOWN_SOURCES removida")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
                    Log.i(TAG, "✅ DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY removida")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error removiendo restricciones: ${e.message}")
                return@withContext false
            }

            // 3. Pequeña espera para asegurar que los cambios se apliquen
            delay(500)

            // 4. Verificar que las restricciones se removieron
            val stillRestricted = dpm.getUserRestrictions(adminComponent)
                .containsKey(android.os.UserManager.DISALLOW_INSTALL_APPS)

            if (stillRestricted) {
                Log.e(TAG, "❌ Las restricciones siguen activas después de intentar removerlas")
                return@withContext false
            }

            Log.i(TAG, "✅ Dispositivo preparado para instalación")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error preparando para instalación: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }

    // ==================== DESCARGA E INSTALACIÓN ====================


    private fun downloadAndInstall(app: ManagedApp) {
        if (!policyManager.isDeviceOwner()) {
            Toast.makeText(this, "❌ No eres Device Owner", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Descargando ${app.name}...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            max = 100
            progress = 0
            show()
        }

        lifecycleScope.launch {
            try {
                val apkFile = apiClient.downloadApk(app._id) { progress ->
                    // Actualizar progreso en el hilo principal
                    progressDialog.progress = progress
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    if (apkFile != null && apkFile.exists()) {
                        installApk(app, apkFile)
                    } else {
                        Toast.makeText(
                            this@AppStoreActivity,
                            "❌ Error descargando APK",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    Log.e(TAG, "Error descargando app", e)
                    Toast.makeText(
                        this@AppStoreActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    private fun installApkSilently(apkFile: File, app: ManagedApp): Boolean {
        return try {
            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "📦 INSTALACIÓN SILENCIOSA")
            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "App: ${app.name}")
            Log.i(TAG, "Package: ${app.packageName}")
            Log.i(TAG, "APK File: ${apkFile.absolutePath}")
            Log.i(TAG, "APK Size: ${apkFile.length()} bytes (${apkFile.length() / 1024 / 1024} MB)")
            Log.i(TAG, "APK Exists: ${apkFile.exists()}")
            Log.i(TAG, "APK Readable: ${apkFile.canRead()}")

            if (!apkFile.exists()) {
                Log.e(TAG, "❌ El archivo APK no existe")
                return false
            }

            if (!apkFile.canRead()) {
                Log.e(TAG, "❌ No se puede leer el archivo APK")
                return false
            }

            // Verificar que las restricciones NO estén activas
            val restrictions = dpm.getUserRestrictions(adminComponent)
            if (restrictions.containsKey(android.os.UserManager.DISALLOW_INSTALL_APPS)) {
                Log.e(TAG, "❌ CRÍTICO: DISALLOW_INSTALL_APPS sigue activa!")
                Log.e(TAG, "La instalación fallará. Verifica prepareDeviceForInstallation()")
                // Aún así intentamos, pero advertimos
            } else {
                Log.i(TAG, "✅ DISALLOW_INSTALL_APPS no está activa - OK para instalar")
            }

            val packageInstaller = packageManager.packageInstaller

            // Crear parámetros de sesión
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                // Establecer el nombre del paquete para ayudar al sistema
                setAppPackageName(app.packageName)

                // Para Device Owner, podemos usar INSTALL_REASON_POLICY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_POLICY)
                        Log.i(TAG, "✅ Install reason: POLICY")
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo establecer install reason: ${e.message}")
                    }
                }

                // Requerir confirmación del usuario solo si no somos Device Owner
                // Como somos Device Owner, esto debería ser automático
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                        Log.i(TAG, "✅ User action: NOT_REQUIRED")
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo establecer user action: ${e.message}")
                    }
                }
            }

            Log.i(TAG, "📝 Creando sesión de instalación...")
            val sessionId = packageInstaller.createSession(params)
            Log.i(TAG, "✅ Sesión creada: ID = $sessionId")

            Log.i(TAG, "📝 Abriendo sesión...")
            val session = packageInstaller.openSession(sessionId)

            try {
                Log.i(TAG, "📝 Copiando APK a la sesión...")

                // Abrir stream de escritura
                session.openWrite("package", 0, -1).use { outputStream ->
                    FileInputStream(apkFile).use { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytes = 0L
                        val fileSize = apkFile.length()

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead

                            // Log progreso cada 10%
                            val progress = (totalBytes * 100 / fileSize).toInt()
                            if (progress % 10 == 0 && totalBytes > 0) {
                                Log.d(TAG, "   Progreso: $progress% ($totalBytes / $fileSize bytes)")
                            }
                        }

                        // Asegurar que todos los datos se escribieron
                        outputStream.flush()
                        session.fsync(outputStream)

                        Log.i(TAG, "✅ APK copiado completamente: $totalBytes bytes")
                    }
                }

                Log.i(TAG, "📝 Creando PendingIntent para resultado...")

                // Crear intent para recibir el resultado
                val intent = Intent(this, InstallReceiver::class.java).apply {
                    putExtra("packageName", app.packageName)
                    putExtra("appName", app.name)
                    putExtra("sessionId", sessionId)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    sessionId,
                    intent,
                    flags
                )

                Log.i(TAG, "📝 Committing sesión...")

                // Commit la sesión - esto inicia la instalación
                session.commit(pendingIntent.intentSender)
                Log.i(TAG, "✅ Sesión commiteada - instalación en proceso")

                // Cerrar la sesión
                session.close()
                Log.i(TAG, "✅ Sesión cerrada")

                Log.i(TAG, "════════════════════════════════════════")
                Log.i(TAG, "✅ INSTALACIÓN INICIADA EXITOSAMENTE")
                Log.i(TAG, "Esperando resultado del InstallReceiver...")
                Log.i(TAG, "════════════════════════════════════════")

                return true

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error durante el proceso de sesión")
                Log.e(TAG, "Error: ${e.message}")
                e.printStackTrace()

                // Intentar cerrar la sesión en caso de error
                try {
                    session.abandon()
                    Log.i(TAG, "🗑️ Sesión abandonada")
                } catch (ex: Exception) {
                    Log.w(TAG, "No se pudo abandonar sesión: ${ex.message}")
                }

                throw e
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "❌ SECURITY EXCEPTION EN INSTALACIÓN")
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "Error: ${e.message}")
            e.printStackTrace()

            // Este es el error que estabas viendo
            if (e.message?.contains("User restriction") == true) {
                Log.e(TAG, "")
                Log.e(TAG, "💡 DIAGNÓSTICO:")
                Log.e(TAG, "   Las restricciones de usuario siguen activas")
                Log.e(TAG, "   Verifica que prepareDeviceForInstallation() se ejecutó correctamente")
                Log.e(TAG, "   Verifica que eres Device Owner")
                Log.e(TAG, "")

                // Verificar estado actual
                try {
                    val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
                    Log.e(TAG, "   Device Owner: $isDeviceOwner")

                    val restrictions = dpm.getUserRestrictions(adminComponent)
                    Log.e(TAG, "   DISALLOW_INSTALL_APPS activa: ${restrictions.containsKey(android.os.UserManager.DISALLOW_INSTALL_APPS)}")
                    Log.e(TAG, "   DISALLOW_INSTALL_UNKNOWN_SOURCES activa: ${restrictions.containsKey(android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)}")
                } catch (ex: Exception) {
                    Log.e(TAG, "   No se pudo verificar estado: ${ex.message}")
                }
            }

            Log.e(TAG, "════════════════════════════════════════")

            // Intentar método alternativo como fallback
            return tryAlternativeInstallMethod(apkFile, app)

        } catch (e: Exception) {
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "❌ ERROR EN INSTALACIÓN SILENCIOSA")
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "Tipo: ${e.javaClass.simpleName}")
            Log.e(TAG, "Mensaje: ${e.message}")
            e.printStackTrace()
            Log.e(TAG, "════════════════════════════════════════")

            // Intentar método alternativo como fallback
            return tryAlternativeInstallMethod(apkFile, app)
        }
    }


    private fun tryAlternativeInstallMethod(apkFile: File, app: ManagedApp): Boolean {
        return try {
            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "🔄 INTENTANDO MÉTODO ALTERNATIVO")
            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "Usando comando: pm install -r")

            // Este método requiere privilegios de shell
            // Solo funciona si tenemos acceso root o somos app de sistema
            val command = arrayOf("pm", "install", "-r", "-t", apkFile.absolutePath)
            Log.i(TAG, "Comando: ${command.joinToString(" ")}")

            val process = Runtime.getRuntime().exec(command)

            // Leer salida estándar
            val outputReader = java.io.BufferedReader(
                java.io.InputStreamReader(process.inputStream)
            )
            val output = outputReader.readText()

            // Leer salida de error
            val errorReader = java.io.BufferedReader(
                java.io.InputStreamReader(process.errorStream)
            )
            val errorOutput = errorReader.readText()

            val exitCode = process.waitFor()

            Log.i(TAG, "Exit code: $exitCode")
            if (output.isNotEmpty()) {
                Log.i(TAG, "Output: $output")
            }
            if (errorOutput.isNotEmpty()) {
                Log.e(TAG, "Error output: $errorOutput")
            }

            if (exitCode == 0 || output.contains("Success")) {
                Log.i(TAG, "════════════════════════════════════════")
                Log.i(TAG, "✅ INSTALACIÓN ALTERNATIVA EXITOSA")
                Log.i(TAG, "════════════════════════════════════════")
                return true
            } else {
                Log.e(TAG, "════════════════════════════════════════")
                Log.e(TAG, "❌ INSTALACIÓN ALTERNATIVA FALLÓ")
                Log.e(TAG, "════════════════════════════════════════")

                // Analizar error específico
                when {
                    errorOutput.contains("INSTALL_FAILED_USER_RESTRICTED") -> {
                        Log.e(TAG, "💡 Error: Usuario restringido")
                        Log.e(TAG, "   Las restricciones aún están activas")
                    }
                    errorOutput.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") -> {
                        Log.e(TAG, "💡 Error: Almacenamiento insuficiente")
                    }
                    errorOutput.contains("INSTALL_FAILED_ALREADY_EXISTS") -> {
                        Log.e(TAG, "💡 Error: Ya existe una versión")
                        Log.e(TAG, "   Intenta desinstalar primero")
                    }
                    else -> {
                        Log.e(TAG, "💡 Error desconocido del sistema")
                    }
                }

                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "❌ MÉTODO ALTERNATIVO TAMBIÉN FALLÓ")
            Log.e(TAG, "════════════════════════════════════════")
            Log.e(TAG, "Error: ${e.message}")
            e.printStackTrace()
            Log.e(TAG, "════════════════════════════════════════")
            return false
        }
    }

    private fun installApk(app: ManagedApp, apkFile: File) {
        lifecycleScope.launch {
            try {
                Log.i(TAG, "════════════════════════════════════════")
                Log.i(TAG, "🚀 INICIANDO INSTALACIÓN DE ${app.name}")
                Log.i(TAG, "════════════════════════════════════════")

                Log.i(TAG, "🔧 Paso 1: Preparando dispositivo...")
                val prepared = withContext(Dispatchers.IO) {
                    prepareDeviceForInstallation(app.packageName)
                }

                if (!prepared) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AppStoreActivity,
                            "❌ No se pudo preparar el dispositivo. Verifica que seas Device Owner.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                Log.i(TAG, "✅ Dispositivo preparado correctamente")

                Log.i(TAG, "⏳ Paso 2: Esperando aplicación de cambios...")
                delay(1000)

                Log.i(TAG, "📦 Paso 3: Instalando APK...")
                val success = withContext(Dispatchers.IO) {
                    installApkSilently(apkFile, app)
                }

                if (success) {
                    Log.i(TAG, "✅ Instalación iniciada exitosamente")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AppStoreActivity,
                            "✅ ${app.name} instalando...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    Log.i(TAG, "⏳ Paso 4: Esperando finalización de instalación...")
                    delay(3000)

                    val installed = isAppInstalled(app.packageName)
                    if (installed) {
                        Log.i(TAG, "✅ App instalada correctamente: ${app.packageName}")

                        Log.i(TAG, "🔐 Paso 5: Otorgando permisos...")
                        grantPermissions(app)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AppStoreActivity,
                                "✅ ${app.name} instalada exitosamente",
                                Toast.LENGTH_SHORT
                            ).show()
                            loadApps()
                        }
                    } else {
                        Log.w(TAG, "⚠️ App no se detectó después de instalación")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AppStoreActivity,
                                "⚠️ Instalación en proceso...",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    Log.i(TAG, "🔒 Paso 6: Re-aplicando restricciones...")
                    delay(1000)
                    withContext(Dispatchers.IO) {
                        policyManager.reapplyRestrictions()
                    }
                    Log.i(TAG, "✅ Restricciones re-aplicadas")

                } else {
                    Log.e(TAG, "❌ Error en la instalación")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AppStoreActivity,
                            "❌ Error instalando ${app.name}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    withContext(Dispatchers.IO) {
                        policyManager.reapplyRestrictions()
                    }
                }

                Log.i(TAG, "════════════════════════════════════════")
                Log.i(TAG, "✅ PROCESO COMPLETADO")
                Log.i(TAG, "════════════════════════════════════════")

            } catch (e: Exception) {
                Log.e(TAG, "════════════════════════════════════════")
                Log.e(TAG, "❌ ERROR EN INSTALACIÓN")
                Log.e(TAG, "════════════════════════════════════════")
                Log.e(TAG, "Error: ${e.message}", e)

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        policyManager.reapplyRestrictions()
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error re-aplicando restricciones: ${ex.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AppStoreActivity,
                        "❌ Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }









    // 🔧 FUNCIÓN 2: NUEVA - Preparar dispositivo para instalación
    private suspend fun prepareDeviceForInstallation(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔧 Preparando dispositivo para instalación...")

            if (!policyManager.isDeviceOwner()) {
                Log.e(TAG, "❌ No somos Device Owner")
                return@withContext false
            }

            try {
                val isHidden = dpm.isApplicationHidden(adminComponent, packageName)
                if (isHidden) {
                    dpm.setApplicationHidden(adminComponent, packageName, false)
                    Log.i(TAG, "✅ App desbloqueada: $packageName")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo verificar/desbloquear app: ${e.message}")
            }

            val currentRestrictions = try {
                dpm.getUserRestrictions(adminComponent)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron obtener restricciones actuales: ${e.message}")
                Bundle()
            }

            Log.i(TAG, "📋 Restricciones activas antes de remover:")
            if (currentRestrictions.containsKey(android.os.UserManager.DISALLOW_INSTALL_APPS)) {
                Log.i(TAG, "   - DISALLOW_INSTALL_APPS: ACTIVA ❌")
            }
            if (currentRestrictions.containsKey(android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)) {
                Log.i(TAG, "   - DISALLOW_INSTALL_UNKNOWN_SOURCES: ACTIVA ❌")
            }

            try {
                Log.i(TAG, "🔓 Removiendo restricciones de instalación...")

                dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_APPS)
                Log.i(TAG, "   ✅ DISALLOW_INSTALL_APPS removida")

                dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                Log.i(TAG, "   ✅ DISALLOW_INSTALL_UNKNOWN_SOURCES removida")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
                        Log.i(TAG, "   ✅ DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY removida")
                    } catch (e: Exception) {
                        Log.w(TAG, "   ⚠️ No se pudo remover DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error removiendo restricciones: ${e.message}")
                e.printStackTrace()
                return@withContext false
            }

            delay(500)

            val newRestrictions = try {
                dpm.getUserRestrictions(adminComponent)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron verificar restricciones: ${e.message}")
                Bundle()
            }

            val stillRestricted = newRestrictions.containsKey(android.os.UserManager.DISALLOW_INSTALL_APPS)

            if (stillRestricted) {
                Log.e(TAG, "❌ DISALLOW_INSTALL_APPS aún está activa después de intentar removerla")
                Log.e(TAG, "Esto puede indicar que otra política o perfil está aplicando la restricción")
                return@withContext false
            }

            Log.i(TAG, "✅ Todas las restricciones removidas correctamente")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error preparando dispositivo: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun installMDMUpdate(apkFile: File, app: ManagedApp) {
        lifecycleScope.launch {
            try {
                val progressDialog = ProgressDialog(this@AppStoreActivity).apply {
                    setMessage("Actualizando MDM Empresarial...")
                    setCancelable(false)
                    show()
                }

                val success = withContext(Dispatchers.IO) {
                    installApkSilently(apkFile, app)
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    if (success) {
                        AlertDialog.Builder(this@AppStoreActivity)
                            .setTitle("✅ MDM Actualizado")
                            .setMessage("La aplicación MDM se ha actualizado correctamente.\n\nLa app se reiniciará ahora.")
                            .setPositiveButton("Reiniciar") { _, _ ->
                                // Reiniciar la app
                                val intent = packageManager.getLaunchIntentForPackage(packageName)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                                finish()

                                // Matar proceso actual
                                android.os.Process.killProcess(android.os.Process.myPid())
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        AlertDialog.Builder(this@AppStoreActivity)
                            .setTitle("❌ Error")
                            .setMessage("No se pudo actualizar el MDM. Por favor, intenta de nuevo.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando MDM", e)
                Toast.makeText(
                    this@AppStoreActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }



    private fun grantPermissions(app: ManagedApp) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Thread.sleep(3000) // Esperar a que termine la instalación

                Log.i(TAG, "⏳ Configurando permisos para ${app.name}...")

                // Si es app de delivery, usar configuración especial
                if (app.category == "delivery" || app.specialConfig.grantOverlayPermission) {
                    policyManager.setupAppForDelivery(app.packageName)
                } else {
                    // Configuración normal
                    if (app.requiredPermissions.isNotEmpty()) {
                        policyManager.grantPermissionsToApp(app.packageName, app.requiredPermissions)
                    }

                    // Overlay si está marcado
                    if (app.specialConfig.grantOverlayPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            policyManager.forceOverlayPermission(app.packageName)
                        }
                    }

                    // Batería si está marcado
                    if (app.specialConfig.disableBatteryOptimization) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            policyManager.grantPermissionsToApp(
                                app.packageName,
                                listOf(android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            )
                        }
                    }

                    // Background si está marcado
                    if (app.specialConfig.protectInBackground) {
                        policyManager.setupDeliveryAppWithOverlay(app.packageName)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AppStoreActivity,
                        "✅ Permisos configurados para ${app.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                Log.i(TAG, "✅ Permisos otorgados a ${app.name}")

            } catch (e: Exception) {
                Log.e(TAG, "Error otorgando permisos: ${e.message}")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AppStoreActivity,
                        "⚠️ Algunos permisos pueden requerir configuración manual",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    // ==================== UTILIDADES ====================



    private fun getCategoryName(category: String): String {
        return when (category) {
            "delivery" -> "Delivery"
            "productivity" -> "Productividad"
            "communication" -> "Comunicación"
            "utility" -> "Utilidad"
            "business" -> "Negocios"
            else -> "Otras"
        }
    }

    private fun getPermissionName(permission: String): String {
        return when {
            permission.contains("LOCATION") -> "📍 Ubicación"
            permission.contains("CAMERA") -> "📷 Cámara"
            permission.contains("STORAGE") -> "💾 Almacenamiento"
            permission.contains("PHONE") -> "📞 Teléfono"
            permission.contains("SMS") -> "💬 SMS"
            permission.contains("CONTACTS") -> "👥 Contactos"
            permission.contains("MICROPHONE") || permission.contains("RECORD_AUDIO") -> "🎤 Micrófono"
            permission.contains("CALENDAR") -> "📅 Calendario"
            permission.contains("INTERNET") -> "🌐 Internet"
            else -> permission.substringAfterLast(".")
        }
    }



    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

// ==================== DATA CLASSES ====================

data class ManagedApp(
    val _id: String,
    val packageName: String,
    val name: String,
    val description: String,
    val icon: String,
    val category: String,
    val version: Version,
    val requiredPermissions: List<String>,
    val specialConfig: SpecialConfig,
    val apk: ApkInfo
)

data class Version(
    val name: String?,
    val code: Int?
)

data class SpecialConfig(
    val grantOverlayPermission: Boolean,
    val disableBatteryOptimization: Boolean,
    val protectInBackground: Boolean,
    val autoUpdate: Boolean
)

data class ApkInfo(
    val filename: String,
    val size: Long,
    val uploadedAt: String
)

// ==================== ADAPTER ====================

class AppStoreAdapter(
    private val apps: List<ManagedApp>,
    private val onAppClick: (ManagedApp) -> Unit
) : RecyclerView.Adapter<AppStoreAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconText: TextView = view.findViewById(R.id.appIcon)
        val nameText: TextView = view.findViewById(R.id.appName)
        val descriptionText: TextView = view.findViewById(R.id.appDescription)
        val versionText: TextView = view.findViewById(R.id.appVersion)
        val categoryText: TextView = view.findViewById(R.id.appCategory)
        val statusText: TextView = view.findViewById(R.id.appStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_store, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val context = holder.itemView.context

        holder.iconText.text = app.icon
        holder.nameText.text = app.name
        holder.descriptionText.text = app.description.ifEmpty { "Sin descripción" }
        holder.versionText.text = "v${app.version.name ?: "N/A"}"
        holder.categoryText.text = getCategoryName(app.category)

        // Verificar si está instalada
        val isInstalled = isAppInstalled(context, app.packageName)
        val installedVersion = if (isInstalled) getInstalledVersionCode(context, app.packageName) else 0
        val needsUpdate = isInstalled && installedVersion < (app.version.code ?: 0)

        holder.statusText.text = when {
            needsUpdate -> "🔄 Actualización"
            isInstalled -> "✅ Instalada"
            else -> "📥 Disponible"
        }

        holder.statusText.setTextColor(
            context.getColor(
                when {
                    needsUpdate -> android.R.color.holo_orange_dark
                    isInstalled -> android.R.color.holo_green_dark
                    else -> android.R.color.holo_blue_dark
                }
            )
        )

        holder.itemView.setOnClickListener {
            onAppClick(app)
        }
    }

    override fun getItemCount() = apps.size

    private fun getCategoryName(category: String): String {
        return when (category) {
            "delivery" -> "Delivery"
            "productivity" -> "Productividad"
            "communication" -> "Comunicación"
            "utility" -> "Utilidad"
            "business" -> "Negocios"
            else -> "Otras"
        }
    }



}


