package com.pfsmx.mdmempresarial

import android.app.ProgressDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pfsmx.mdmempresarial.manager.PolicyManager
import com.pfsmx.mdmempresarial.receiver.MDMDeviceAdminReceiver
import com.pfsmx.mdmempresarial.service.SyncService
import com.pfsmx.mdmempresarial.service.UnifiedSyncService
import com.pfsmx.mdmempresarial.worker.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var policyManager: PolicyManager
    private lateinit var statusText: TextView
    private lateinit var deviceIdText: TextView
    private lateinit var btnSetupDeviceOwner: Button
    private lateinit var btnRegister: Button
    private lateinit var btnSync: Button
    private lateinit var btnEmergency: Button
    private lateinit var btnTestDelivery: Button

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        policyManager = PolicyManager(this)

        initViews()
        updateStatus()

        // Programar sincronización periódica
        SyncWorker.scheduleSyncWork(this)
        startUnifiedSyncService()
    }

    fun Context.startUnifiedSyncService() {
        val intent = Intent(this, UnifiedSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        deviceIdText = findViewById(R.id.deviceIdText)
        btnSetupDeviceOwner = findViewById(R.id.btnSetupDeviceOwner)
        btnRegister = findViewById(R.id.btnRegister)
        btnSync = findViewById(R.id.btnSync)
        btnEmergency = findViewById(R.id.btnEmergency)
        btnTestDelivery = findViewById(R.id.btnTestDelivery)
        val btnAppStore: Button = findViewById(R.id.btnAppStore)
        btnAppStore.setOnClickListener {
            openAppStore()
        }

        // Mostrar Device ID
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        deviceIdText.text = "ID: $deviceId"

        btnSetupDeviceOwner.setOnClickListener {
            showDeviceOwnerInstructions()
        }

        btnRegister.setOnClickListener {
            registerDevice()
        }

        btnSync.setOnClickListener {
            syncWithServer()
        }

        btnEmergency.setOnClickListener {
            showEmergencyDialog()
        }

        btnTestDelivery.setOnClickListener {
            testDeliveryAppSetup()
        }
    }



    // Agrega esta función:
    private fun openAppStore() {
        if (!policyManager.isDeviceOwner()) {
            Toast.makeText(this, "❌ No eres Device Owner", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, AppStoreActivity::class.java)
        startActivity(intent)
    }


    private fun updateStatus() {
        val isDeviceOwner = policyManager.isDeviceOwner()
        val isAdminActive = policyManager.isAdminActive()
        val isEmergencyActive = policyManager.isEmergencyUnlockActive()

        val statusBuilder = StringBuilder()

        statusBuilder.append("═══════════════════════════\n")
        statusBuilder.append("📱 ESTADO DEL SISTEMA\n")
        statusBuilder.append("═══════════════════════════\n\n")

        if (isDeviceOwner) {
            statusBuilder.append("✅ Device Owner: ACTIVO\n")
            statusBuilder.append("✅ Administrador: ACTIVO\n")
        } else if (isAdminActive) {
            statusBuilder.append("⚠️ Device Owner: INACTIVO\n")
            statusBuilder.append("✅ Administrador: ACTIVO\n")
            statusBuilder.append("\n⚠️ Funciones limitadas\n")
        } else {
            statusBuilder.append("❌ Device Owner: INACTIVO\n")
            statusBuilder.append("❌ Administrador: INACTIVO\n")
            statusBuilder.append("\n❌ No se pueden aplicar políticas\n")
        }

        statusBuilder.append("\n")

        if (isEmergencyActive) {
            statusBuilder.append("🆘 MODO EMERGENCIA ACTIVO\n")
            statusBuilder.append("⚠️ Políticas suspendidas\n\n")
        }

        val policy = policyManager.getCurrentPolicy()
        if (policy != null) {
            val policyName = policy.optString("name", "Desconocida")
            statusBuilder.append("📋 Política actual:\n")
            statusBuilder.append("   $policyName\n\n")

            val blockedApps = policyManager.getCurrentBlockedApps()
            statusBuilder.append("🚫 Apps bloqueadas: ${blockedApps.size}\n")
        } else {
            statusBuilder.append("📋 Sin política asignada\n")
        }

        statusBuilder.append("\n═══════════════════════════\n")
        statusBuilder.append("Modelo: ${Build.MODEL}\n")
        statusBuilder.append("Android: ${Build.VERSION.RELEASE}\n")
        statusBuilder.append("═══════════════════════════")

        statusText.text = statusBuilder.toString()

        // Habilitar/deshabilitar botones según estado
        btnRegister.isEnabled = isDeviceOwner
        btnSync.isEnabled = isDeviceOwner
        btnTestDelivery.isEnabled = isDeviceOwner
        btnSetupDeviceOwner.isEnabled = !isDeviceOwner
    }

    private fun showDeviceOwnerInstructions() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val message = """
            📱 CONFIGURAR COMO DEVICE OWNER
            
            ⚠️ El dispositivo debe estar sin cuentas Google
            
            PASOS:
            
            1. Conecta el dispositivo por USB
            
            2. Habilita Depuración USB en:
               Ajustes > Opciones de desarrollador
            
            3. En tu computadora, ejecuta:
            
            adb shell dpm set-device-owner com.pfsmx.mdmempresarial/.receiver.MDMDeviceAdminReceiver
            
            4. Si funciona, verás:
               "Success: Device owner set..."
            
            5. Reinicia la app
            
            ═══════════════════════════
            
            ID del dispositivo:
            $deviceId
            
            ═══════════════════════════
            
            ⚠️ IMPORTANTE:
            - No debe haber cuentas Google
            - Si falla, haz Factory Reset
            - Configura ANTES de agregar cuentas
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("🛡️ Configuración Device Owner")
            .setMessage(message)
            .setPositiveButton("Copiar Comando") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText(
                    "ADB Command",
                    "adb shell dpm set-device-owner com.pfsmx.mdmempresarial/.receiver.MDMDeviceAdminReceiver"
                )
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "✅ Comando copiado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun registerDevice() {
        if (!policyManager.isDeviceOwner()) {
            Toast.makeText(this, "❌ No eres Device Owner", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Registrando dispositivo...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                val model = Build.MODEL
                val manufacturer = Build.MANUFACTURER
                val androidVersion = Build.VERSION.RELEASE

                val apiClient = com.pfsmx.mdmempresarial.api.MDMApiClient(this@MainActivity)

                val success = apiClient.registerDevice(
                    deviceId = deviceId,
                    model = model,
                    manufacturer = manufacturer,
                    androidVersion = androidVersion
                )

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    if (success) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("✅ Éxito")
                            .setMessage("Dispositivo registrado correctamente en el servidor.\n\nAhora puedes:\n1. Asignar una política desde el panel web\n2. Sincronizar para recibir configuración")
                            .setPositiveButton("OK") { _, _ ->
                                updateStatus()
                            }
                            .show()
                    } else {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("❌ Error")
                            .setMessage("No se pudo registrar el dispositivo.\n\nVerifica:\n- Conexión a internet\n- URL del servidor en MDMApiClient.kt\n- Servidor corriendo")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    Log.e(TAG, "Error registrando dispositivo", e)

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("❌ Error")
                        .setMessage("Error: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun syncWithServer() {
        if (!policyManager.isDeviceOwner()) {
            Toast.makeText(this, "❌ No eres Device Owner", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Sincronizando con servidor...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val success = policyManager.syncWithServer()

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    if (success) {
                        val dialog = AlertDialog.Builder(this@MainActivity)
                            .setTitle("✅ Sincronización Exitosa")
                            .setMessage("El dispositivo se ha sincronizado correctamente.\n\nSe han aplicado las políticas asignadas.")
                            .setPositiveButton("OK", null)
                            .create()

                        dialog.setOnDismissListener {
                            updateStatus()
                        }

                        dialog.show()
                    } else {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("⚠️ Sin Cambios")
                            .setMessage("La sincronización completó pero no hubo cambios o hubo errores.\n\nRevisa los logs para más detalles.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    Log.e(TAG, "Error sincronizando", e)

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("❌ Error")
                        .setMessage("Error en sincronización: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    /**
     * Otorga permisos de ubicación a nuestra propia app
     */
    fun grantLocationPermissionsToSelf(): Boolean {
        return try {
            val packageName = this.packageName

            val locationPermissions = mutableListOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                locationPermissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }

            grantPermissionsToApp(packageName, locationPermissions)

            Log.i(TAG, "✅ Permisos de ubicación otorgados a nuestra app")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error otorgando permisos de ubicación: ${e.message}")
            false
        }
    }

    fun grantPermissionsToApp(packageName: String, permissions: List<String>) {
         val dpm: DevicePolicyManager = this.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, MDMDeviceAdminReceiver::class.java)


        for (permission in permissions) {
            try {
                dpm.setPermissionGrantState(
                    admin,
                    packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
                Log.i("DeviceOwner", "✅ Permiso $permission otorgado a $packageName")
            } catch (e: SecurityException) {
                Log.e("DeviceOwner", "❌ Error otorgando $permission: ${e.message}")
            }
        }
    }






    private fun showEmergencyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_emergency_code, null)
        val codeInput = dialogView.findViewById<EditText>(R.id.emergencyCodeInput)
        val statusText = dialogView.findViewById<TextView>(R.id.emergencyStatusText)

        // Mostrar estado actual
        if (policyManager.isEmergencyUnlockActive()) {
            statusText.text = "🆘 Modo emergencia ACTIVO\n\nLas políticas están suspendidas temporalmente."
            statusText.setTextColor(getColor(android.R.color.holo_orange_dark))
        } else {
            statusText.text = "🔒 Modo normal\n\nIngresa el código de emergencia para desbloquear temporalmente."
            statusText.setTextColor(getColor(android.R.color.darker_gray))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("🆘 Código de Emergencia")
            .setView(dialogView)
            .setPositiveButton("Validar") { _, _ ->
                val code = codeInput.text.toString().trim()
                validateEmergencyCode(code)
            }
            .setNegativeButton("Cancelar", null)
            .create()

        // Si ya está en modo emergencia, agregar botón para desactivar
        if (policyManager.isEmergencyUnlockActive()) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Desactivar") { _, _ ->
                deactivateEmergencyMode()
            }
        }

        dialog.show()
    }

    private fun validateEmergencyCode(code: String) {
        if (code.isEmpty()) {
            Toast.makeText(this, "Ingresa un código", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Validando código...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                // 1. Intentar validar localmente primero
                val locallyValid = policyManager.validateEmergencyCode(code)

                if (locallyValid) {
                    // Código válido localmente
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        activateEmergencyMode(30) // 30 minutos por defecto
                    }
                    return@launch
                }

                // 2. Si no es válido localmente, intentar con el servidor
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                val apiClient = com.pfsmx.mdmempresarial.api.MDMApiClient(this@MainActivity)

                val response = apiClient.validateEmergencyCode(deviceId, code)

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    if (response != null && response.valid) {
                        // Guardar código para futuras validaciones offline
                        policyManager.saveEmergencyCode(code)

                        // Activar modo emergencia
                        val duration = response.duration ?: 30
                        activateEmergencyMode(duration)
                    } else {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("❌ Código Inválido")
                            .setMessage(response?.message ?: "El código ingresado no es correcto.\n\nIntenta nuevamente o contacta al administrador.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    Log.e(TAG, "Error validando código de emergencia", e)

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("❌ Error")
                        .setMessage("No se pudo validar el código.\n\nSin conexión a internet, solo se puede usar el último código conocido.\n\nError: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun activateEmergencyMode(durationMinutes: Int) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Activando modo emergencia...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                // 1. Activar modo emergencia
                policyManager.activateEmergencyUnlock(durationMinutes)

                // 2. Desbloquear todo inmediatamente
                withContext(Dispatchers.IO) {
                    policyManager.clearAllPolicies()
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("✅ Modo Emergencia Activado")
                        .setMessage("""
                        Las políticas se han suspendido por $durationMinutes minutos.
                        
                        ✅ Apps desbloqueadas
                        ✅ Restricciones removidas
                        ✅ Dispositivo completamente funcional
                        
                        ⚠️ Las políticas se reactivarán automáticamente cuando expire el tiempo.
                    """.trimIndent())
                        .setPositiveButton("Entendido") { _, _ ->
                            updateStatus()

                            // Iniciar servicio para monitorear expiración
                            val intent = Intent(this@MainActivity, UnifiedSyncService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                        }
                        .setCancelable(false)
                        .show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    Log.e(TAG, "Error activando modo emergencia", e)

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("❌ Error")
                        .setMessage("No se pudo activar el modo emergencia: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun deactivateEmergencyMode() {
        AlertDialog.Builder(this)
            .setTitle("🔒 Desactivar Modo Emergencia")
            .setMessage("¿Reactivar las políticas ahora?\n\nLas restricciones volverán a aplicarse inmediatamente.")
            .setPositiveButton("Sí, Reactivar") { _, _ ->
                policyManager.deactivateEmergencyUnlock()

                Toast.makeText(this, "✅ Políticas reactivadas", Toast.LENGTH_SHORT).show()
                updateStatus()

                // Sincronizar para reaplicar políticas
                syncWithServer()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun testDeliveryAppSetup() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_package_input, null)
        val packageInput = dialogView.findViewById<EditText>(R.id.packageNameInput)

        packageInput.hint = "com.rappi.partner"

        AlertDialog.Builder(this)
            .setTitle("🚚 Probar Configuración")
            .setMessage("Ingresa el package name de la app de delivery:")
            .setView(dialogView)
            .setPositiveButton("Configurar") { _, _ ->
                val packageName = packageInput.text.toString().trim()

                if (packageName.isEmpty()) {
                    Toast.makeText(this, "Ingresa un package name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                setupDeliveryApp(packageName)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupDeliveryApp(packageName: String) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Configurando app de delivery...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    policyManager.setupAppForDelivery(packageName)
                }

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    if (success) {
                        val message = """
                        App configurada correctamente:
                        
                        ✅ Todos los permisos otorgados
                        ✅ Overlay habilitado (aparecer sobre otras apps)
                        ✅ Batería sin restricciones
                        ✅ Background sin restricciones
                        ✅ Datos sin restricciones
                        
                        La app ahora puede funcionar sin ninguna limitación.
                        
                        ⚠️ Algunos ajustes pueden tardar unos segundos en aplicarse.
                    """.trimIndent()

                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("✅ Configuración Completa")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                    } else {
                        val message = """
                        No se pudo completar toda la configuración.
                        
                        Verifica:
                        - Que la app esté instalada
                        - Que el package name sea correcto
                        - Los logs para más detalles
                    """.trimIndent()

                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("⚠️ Configuración Incompleta")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    Log.e(TAG, "Error configurando delivery app", e)

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("❌ Error")
                        .setMessage("Error: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}