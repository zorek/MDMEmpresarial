package com.pfsmx.mdmempresarial.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.pfsmx.mdmempresarial.MainActivity
import com.pfsmx.mdmempresarial.R
import com.pfsmx.mdmempresarial.api.MDMApiClient
import com.pfsmx.mdmempresarial.manager.PolicyManager
import kotlinx.coroutines.*

/**
 * Servicio unificado que maneja:
 * 1. Sincronización periódica con el servidor
 * 2. Monitoreo del modo de emergencia
 * 3. Reaplicación de políticas cuando expira la emergencia
 */
open class UnifiedSyncService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var policyManager: PolicyManager

    private var locationUpdateJob: Job? = null


    private var syncJob: Job? = null
    private var emergencyMonitorJob: Job? = null

    companion object {
        private const val TAG = "UnifiedSyncService"
        private const val CHANNEL_ID = "mdm_sync_channel"
        private const val NOTIFICATION_ID = 1001

        private const val SYNC_INTERVAL_MS = 300000L // 5 minutos
        private const val EMERGENCY_CHECK_INTERVAL_MS = 60000L // 1 minuto
    }

    override fun onCreate() {
        super.onCreate()
        policyManager = PolicyManager(this)
        createNotificationChannel()
        Log.i(TAG, "✅ UnifiedSyncService creado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "🔄 UnifiedSyncService iniciado")
        tick("Servicio iniciado")


        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Verificar si somos Device Owner
        if (!policyManager.isDeviceOwner()) {
            Log.w(TAG, "⚠️ No somos Device Owner, deteniendo servicio")
            stopSelf()
            return START_NOT_STICKY
        }

        // Iniciar jobs
        startSyncJob()
        startEmergencyMonitorJob()
        startLocationTracking() // ✅ AGREGAR ESTO


        return START_STICKY
    }




    private fun tick(label: String) {
        val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        Log.i(TAG, "⏱️ $label @ $ts")
        updateNotification("$label @ $ts")
    }








    /**
     * Inicia el job de sincronización periódica
     */
    private fun startSyncJob() {
        syncJob?.cancel()

        syncJob = serviceScope.launch {
            // Primera sincronización inmediata
            delay(5000) // Esperar 5 segundos
            tick("Primera sync")

            syncPolicies()

            // Sincronizaciones periódicas
            while (isActive) {
                try {
                    delay(SYNC_INTERVAL_MS)
                    tick("Sync periódica")

                    syncPolicies()
                } catch (e: CancellationException) {
                    Log.i(TAG, "🛑 Sincronización periódica cancelada")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error en sincronización periódica: ${e.message}")
                    delay(60000) // Esperar 1 minuto antes de reintentar
                }
            }
        }

        Log.i(TAG, "✅ Job de sincronización iniciado")
    }


    // Después de startPeriodicSync(), agrega:
    private fun startLocationTracking() {
        locationUpdateJob?.cancel()

        locationUpdateJob = serviceScope.launch {
            while (isActive) {
                try {
                    tick("Location: envío programado")
                    sendLocationUpdate()
                    delay(15 * 60 * 1000) // 15 minutos
                } catch (e: Exception) {
                    Log.e(TAG, "Error en tracking de ubicación: ${e.message}")
                    delay(5 * 60 * 1000) // Reintentar en 5 minutos
                }
            }
        }
    }

    private suspend fun sendLocationUpdate() = withContext(Dispatchers.IO) {
        try {
            if (!policyManager.isDeviceOwner()) {
                Log.w(TAG, "No es Device Owner, saltando ubicación")
                return@withContext
            }

            // Verificar permiso de ubicación
            if (checkLocationPermission()) {
                val location = getLastKnownLocation()

                if (location != null) {
                    val apiClient = MDMApiClient(this@UnifiedSyncService)
                    val success = apiClient.sendLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        altitude = location.altitude,
                        speed = location.speed,
                        bearing = location.bearing,
                        provider = location.provider,
                        batteryLevel = getBatteryLevel()
                    )

                    if (success) {
                        Log.i(TAG, "📍 Ubicación enviada correctamente")
                    } else {
                        Log.w(TAG, "⚠️ Error enviando ubicación")
                    }
                } else {
                    Log.w(TAG, "⚠️ No se pudo obtener ubicación")
                }
            } else {
                Log.w(TAG, "⚠️ Sin permiso de ubicación")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enviando ubicación: ${e.message}")
        }
    }

    private fun checkLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

   /// @SuppressLint("MissingPermission")
   @SuppressLint("MissingPermission")
   private fun getLastKnownLocation(): android.location.Location? {
       return try {
           val locationManager = getSystemService(android.location.LocationManager::class.java)

           // Intentar obtener de GPS primero
           var location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)

           // Si no hay GPS, usar Network
           if (location == null) {
               location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
           }

           // Si no hay Network, usar Passive
           if (location == null) {
               location = locationManager.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
           }

           if (location != null) {
               Log.i(TAG, "📍 Ubicación obtenida: ${location.latitude}, ${location.longitude}")
           } else {
               Log.w(TAG, "⚠️ No se pudo obtener ubicación")
           }

           location
       } catch (e: Exception) {
           Log.e(TAG, "Error obteniendo ubicación: ${e.message}")
           null
       }
   }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = getSystemService(android.os.BatteryManager::class.java)
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo nivel de batería: ${e.message}")
            -1
        }
    }






    /**
     * Inicia el job de monitoreo de modo emergencia
     */
    private fun startEmergencyMonitorJob() {
        emergencyMonitorJob?.cancel()

        emergencyMonitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    delay(EMERGENCY_CHECK_INTERVAL_MS)
                    checkEmergencyMode()
                } catch (e: CancellationException) {
                    Log.i(TAG, "🛑 Monitoreo de emergencia cancelado")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error en monitoreo de emergencia: ${e.message}")
                }
            }
        }

        Log.i(TAG, "✅ Job de monitoreo de emergencia iniciado")
    }

    /**
     * Sincroniza políticas con el servidor
     */
    private suspend fun syncPolicies() {
        try {
            Log.i(TAG, "🔄 Sincronizando con servidor...")

            // No sincronizar si el modo emergencia está activo
            if (policyManager.isEmergencyUnlockActive()) {
                Log.i(TAG, "🆘 Modo emergencia activo, omitiendo sincronización")
                updateNotification("🆘 Modo emergencia activo")
                return
            }

            val success = policyManager.syncWithServer()

            if (success) {
                Log.i(TAG, "✅ Sincronización exitosa")
                updateNotification("✅ Sincronizado")
            } else {
                Log.w(TAG, "⚠️ Sincronización con advertencias")
                updateNotification("⚠️ Sincronizado con advertencias")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sincronizando: ${e.message}")
            e.printStackTrace()
            updateNotification("❌ Error de sincronización")
        }
    }

    /**
     * Verifica el estado del modo emergencia
     */
    private suspend fun checkEmergencyMode() {
        try {
            val wasActive = policyManager.isEmergencyUnlockActive()

            if (!wasActive) {
                return
            }

            // Verificar nuevamente después de un segundo
            delay(1000)
            val stillActive = policyManager.isEmergencyUnlockActive()

            // Si ya no está activo, reaplicar políticas
            if (!stillActive) {
                Log.i(TAG, "🔒 Modo emergencia expirado, reaplicando políticas...")
                updateNotification("🔒 Reaplicando políticas...")

                val success = policyManager.syncWithServer()

                if (success) {
                    Log.i(TAG, "✅ Políticas reaplicadas correctamente")
                    updateNotification("✅ Políticas activas")
                } else {
                    Log.w(TAG, "⚠️ Error reaplicando políticas")
                    updateNotification("⚠️ Error reaplicando")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verificando modo emergencia: ${e.message}")
        }
    }

    /**
     * Crea el canal de notificación
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MDM Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sincronización y monitoreo de políticas MDM"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Crea la notificación del servicio
     */
    private fun createNotification(contentText: String = "Monitoreo activo"): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ MDM Empresarial")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * Actualiza el texto de la notificación
     */
    private fun updateNotification(contentText: String) {
        try {
            val notification = createNotification(contentText)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando notificación: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()

        syncJob?.cancel()
        emergencyMonitorJob?.cancel()
        locationUpdateJob?.cancel()
        serviceScope.cancel()


        Log.i(TAG, "❌ UnifiedSyncService destruido")
    }
}