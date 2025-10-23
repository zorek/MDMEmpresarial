package com.pfsmx.mdmempresarial.api

import android.content.Context
import android.os.Build
import android.util.Log
import com.pfsmx.mdmempresarial.ApkInfo
import com.pfsmx.mdmempresarial.ManagedApp
import com.pfsmx.mdmempresarial.SpecialConfig
import com.pfsmx.mdmempresarial.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MDMApiClient(private val context: Context) {

    companion object {
        private const val TAG = "MDMApiClient"

        // ⚠️ CAMBIAR EN PRODUCCIÓN
        private const val BASE_URL = "https://mdm.pfsmx.app" // Cambiar a tu dominio

        private const val REGISTER_ENDPOINT = "$BASE_URL/api/devices/register"
        private const val SYNC_ENDPOINT = "$BASE_URL/api/devices/%s/sync"
        private const val COMMAND_EXECUTED_ENDPOINT = "$BASE_URL/api/devices/%s/command-executed"
        private const val VALIDATE_EMERGENCY_ENDPOINT = "$BASE_URL/api/emergency/validate"

        private const val TIMEOUT = 15000 // 15 segundos
    }

    data class SyncResponse(
        val success: Boolean,
        val dataControlEnabled: Boolean,
        val policy: JSONObject?,  // ✅ Cambiado de Map a JSONObject
        val customOverrides: Map<String, Any>?,
        val commands: List<Command>?,
        val emergencyCode: String?
    )

    data class Command(
        val type: String,
        val timestamp: String,
        val payload: Map<String, Any>?,
        val executed: Boolean
    )

    data class EmergencyValidationResponse(
        val success: Boolean,
        val valid: Boolean,
        val duration: Int?,
        val message: String?
    )

    /**
     * Registra el dispositivo en el servidor
     */
    suspend fun registerDevice(
        deviceId: String,
        model: String,
        manufacturer: String,
        androidVersion: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "📡 Registrando dispositivo en servidor...")

            val jsonBody = JSONObject().apply {
                put("deviceId", deviceId)
                put("model", model)
                put("manufacturer", manufacturer)
                put("androidVersion", androidVersion)
            }

            val response = makeRequest(REGISTER_ENDPOINT, "POST", jsonBody)

            if (response != null) {
                val success = response.optBoolean("success", false)

                if (success) {
                    Log.i(TAG, "✅ Dispositivo registrado exitosamente")
                } else {
                    Log.e(TAG, "❌ Error registrando dispositivo: ${response.optString("message")}")
                }

                return@withContext success
            } else {
                Log.e(TAG, "❌ No se recibió respuesta del servidor")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registrando dispositivo: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Sincroniza políticas con el servidor
     */
    /**
     * Sincroniza políticas con el servidor
     */
    suspend fun syncPolicies(
        deviceId: String,
        blockedApps: List<String>
    ): SyncResponse? = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "📡 Sincronizando con servidor...")

            val jsonBody = JSONObject().apply {
                put("blockedApps", JSONArray(blockedApps))
            }

            val endpoint = String.format(SYNC_ENDPOINT, deviceId)
            val response = makeRequest(endpoint, "POST", jsonBody)

            if (response != null) {
                val success = response.optBoolean("success", false)

                if (success) {
                    Log.i(TAG, "✅ Sincronización exitosa")

                    // Parsear comandos
                    val commandsArray = response.optJSONArray("commands")
                    val commands = mutableListOf<Command>()

                    if (commandsArray != null) {
                        for (i in 0 until commandsArray.length()) {
                            val cmdObj = commandsArray.getJSONObject(i)

                            val payload = cmdObj.optJSONObject("payload")?.let { payloadJson ->
                                val map = mutableMapOf<String, Any>()
                                payloadJson.keys().forEach { key ->
                                    val value = payloadJson.get(key)
                                    map[key] = value
                                }
                                map
                            }

                            commands.add(
                                Command(
                                    type = cmdObj.optString("type"),
                                    timestamp = cmdObj.optString("timestamp"),
                                    payload = payload,
                                    executed = cmdObj.optBoolean("executed", false)
                                )
                            )
                        }

                        Log.i(TAG, "📦 ${commands.size} comandos recibidos")
                    }

                    // ✅ CAMBIO: Mantener política como JSONObject
                    val policyObj = response.optJSONObject("policy")

                    if (policyObj != null) {
                        Log.i(TAG, "📋 Política recibida: ${policyObj.optString("name", "Sin nombre")}")
                    }

                    // Parsear overrides
                    val overridesObj = response.optJSONObject("customOverrides")
                    val overrides = overridesObj?.let { jsonToMap(it) }

                    // Obtener código de emergencia si está presente
                    val emergencyCode = response.optString("emergencyCode", null)

                    return@withContext SyncResponse(
                        success = true,
                        dataControlEnabled = response.optBoolean("dataControlEnabled", false),
                        policy = policyObj,  // ✅ Enviar como JSONObject
                        customOverrides = overrides,
                        commands = commands,
                        emergencyCode = emergencyCode
                    )
                } else {
                    Log.e(TAG, "❌ Error en sincronización: ${response.optString("message")}")
                }
            }

            null

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sincronizando: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Envía ubicación del dispositivo al servidor
     */
    suspend fun sendLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        altitude: Double?,
        speed: Float?,
        bearing: Float?,
        provider: String?,
        batteryLevel: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )

            val url = URL("http://fletesdelsur.com:8081/api_loc")
            val connection = url.openConnection() as HttpURLConnection

            val locationData = JSONObject().apply {
                put("deviceId", deviceId)
                put("latitude", latitude)
                put("longitude", longitude)
                put("accuracy", accuracy.toDouble())
                if (altitude != null) put("altitude", altitude)
                if (speed != null) put("speed", speed.toDouble())
                if (bearing != null) put("bearing", bearing.toDouble())
                if (provider != null) put("provider", provider)
                put("batteryLevel", batteryLevel)
            }

            connection.apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(locationData.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            connection.disconnect()

            responseCode == HttpURLConnection.HTTP_OK

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enviando ubicación: ${e.message}")
            false
        }
    }









    /**
     * Reporta que un comando fue ejecutado
     */
    suspend fun reportCommandExecuted(
        deviceId: String,
        commandType: String,
        success: Boolean,
        error: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "📡 Reportando comando ejecutado: $commandType")

            val jsonBody = JSONObject().apply {
                put("commandType", commandType)
                put("success", success)
                if (error != null) {
                    put("error", error)
                }
            }

            val endpoint = String.format(COMMAND_EXECUTED_ENDPOINT, deviceId)
            val response = makeRequest(endpoint, "POST", jsonBody)

            if (response != null) {
                val responseSuccess = response.optBoolean("success", false)

                if (responseSuccess) {
                    Log.i(TAG, "✅ Comando reportado exitosamente")
                }

                return@withContext responseSuccess
            }

            false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reportando comando: ${e.message}")
            false
        }
    }

    /**
     * Valida el código de emergencia con el servidor
     */
    suspend fun validateEmergencyCode(
        deviceId: String,
        code: String
    ): EmergencyValidationResponse? = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "📡 Validando código de emergencia...")

            val jsonBody = JSONObject().apply {
                put("deviceId", deviceId)
                put("code", code)
            }

            val response = makeRequest(VALIDATE_EMERGENCY_ENDPOINT, "POST", jsonBody)

            if (response != null) {
                val success = response.optBoolean("success", false)

                if (success) {
                    val valid = response.optBoolean("valid", false)
                    val duration = if (response.has("duration")) {
                        response.optInt("duration")
                    } else null
                    val message = response.optString("message", null)

                    Log.i(TAG, "✅ Validación completada: ${if (valid) "VÁLIDO" else "INVÁLIDO"}")

                    return@withContext EmergencyValidationResponse(
                        success = true,
                        valid = valid,
                        duration = duration,
                        message = message
                    )
                }
            }

            null

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error validando código: ${e.message}")
            e.printStackTrace()
            null
        }
    }


    /**
     * Obtiene lista de apps disponibles para el dispositivo
     */
    /**
     * Obtiene lista de apps disponibles para el dispositivo
     */
    suspend fun getAvailableApps(deviceId: String): List<ManagedApp>? = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "📡 Obteniendo apps disponibles...")

            val url = URL("$BASE_URL/api/apps/available/$deviceId")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }

                reader.close()
                connection.disconnect()

                val jsonResponse = JSONObject(response.toString())
                val success = jsonResponse.optBoolean("success", false)

                if (success) {
                    val appsArray = jsonResponse.getJSONArray("apps")
                    val apps = mutableListOf<ManagedApp>()

                    for (i in 0 until appsArray.length()) {
                        val appObj = appsArray.getJSONObject(i)

                        val versionObj = appObj.getJSONObject("version")
                        val configObj = appObj.getJSONObject("specialConfig")
                        val apkObj = appObj.getJSONObject("apk")
                        val permsArray = appObj.getJSONArray("requiredPermissions")

                        val permissions = mutableListOf<String>()
                        for (j in 0 until permsArray.length()) {
                            permissions.add(permsArray.getString(j))
                        }

                        apps.add(
                            ManagedApp(
                                _id = appObj.getString("_id"),
                                packageName = appObj.getString("packageName"),
                                name = appObj.getString("name"),
                                description = appObj.optString("description", ""),
                                icon = appObj.optString("icon", "📱"),
                                category = appObj.getString("category"),
                                version = Version(
                                    name = versionObj.optString("name", null),
                                    code = versionObj.optInt("code", 0)
                                ),
                                requiredPermissions = permissions,
                                specialConfig = SpecialConfig(
                                    grantOverlayPermission = configObj.optBoolean("grantOverlayPermission", false),
                                    disableBatteryOptimization = configObj.optBoolean("disableBatteryOptimization", false),
                                    protectInBackground = configObj.optBoolean("protectInBackground", false),
                                    autoUpdate = configObj.optBoolean("autoUpdate", true)
                                ),
                                apk = ApkInfo(
                                    filename = apkObj.getString("filename"),
                                    size = apkObj.getLong("size"),
                                    uploadedAt = apkObj.getString("uploadedAt")
                                )
                            )
                        )
                    }

                    Log.i(TAG, "✅ ${apps.size} apps disponibles")
                    return@withContext apps
                }
            }

            connection.disconnect()
            null

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo apps: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Descarga un APK del servidor
     */
    /**
     * Descarga un APK del servidor con reporte de progreso
     */
    suspend fun downloadApk(appId: String, onProgress: (Int) -> Unit = {}): File? = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "📥 Descargando APK...")

            val url = URL("$BASE_URL/api/apps/download/$appId")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 60000 // 60 segundos
                readTimeout = 60000
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val contentLength = connection.contentLength
                val inputStream = connection.inputStream

                // Crear archivo temporal
                val tempFile = File(
                    context.getExternalFilesDir(null),
                    "temp_${System.currentTimeMillis()}.apk"
                )

                val outputStream = java.io.FileOutputStream(tempFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead

                    // Reportar progreso
                    if (contentLength > 0) {
                        val progress = ((totalBytes * 100) / contentLength).toInt()
                        withContext(Dispatchers.Main) {
                            onProgress(progress)
                        }
                    }
                }

                outputStream.close()
                inputStream.close()
                connection.disconnect()

                Log.i(TAG, "✅ APK descargado: ${tempFile.absolutePath} ($totalBytes bytes)")

                return@withContext tempFile
            }

            connection.disconnect()
            null

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error descargando APK: ${e.message}")
            e.printStackTrace()
            null
        }
    }














    /**
     * Realiza una petición HTTP al servidor
     */
    private fun makeRequest(
        endpoint: String,
        method: String,
        jsonBody: JSONObject? = null
    ): JSONObject? {
        var connection: HttpURLConnection? = null

        try {
            val url = URL(endpoint)
            connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = method
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")

                if (jsonBody != null) {
                    doOutput = true

                    val writer = OutputStreamWriter(outputStream)
                    writer.write(jsonBody.toString())
                    writer.flush()
                    writer.close()
                }
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }

                reader.close()

                return JSONObject(response.toString())
            } else {
                Log.e(TAG, "Error HTTP: $responseCode")

                // Intentar leer error del body
                try {
                    val reader = BufferedReader(InputStreamReader(connection.errorStream))
                    val errorResponse = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        errorResponse.append(line)
                    }

                    reader.close()
                    Log.e(TAG, "Error body: $errorResponse")
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo leer error body")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error en petición HTTP: ${e.message}")
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }

        return null
    }

    /**
     * Convierte un JSONObject a Map recursivamente
     */
    private fun jsonToMap(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        json.keys().forEach { key ->
            val value = json.get(key)

            map[key] = when (value) {
                is JSONObject -> jsonToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null as Any
                else -> value
            }
        }

        return map
    }

    /**
     * Convierte un JSONArray a List recursivamente
     */
    private fun jsonArrayToList(array: JSONArray): List<Any> {
        val list = mutableListOf<Any>()

        for (i in 0 until array.length()) {
            val value = array.get(i)

            list.add(when (value) {
                is JSONObject -> jsonToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null as Any
                else -> value
            })
        }

        return list
    }
}