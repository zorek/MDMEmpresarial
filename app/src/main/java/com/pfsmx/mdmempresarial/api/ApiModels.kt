package com.pfsmx.mdmempresarial.api

data class RegisterDeviceRequest(
    val deviceId: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val serialNumber: String? = null
)

data class RegisterDeviceResponse(
    val success: Boolean,
    val deviceId: String,
    val message: String
)

data class DeviceSyncRequest(
    val deviceId: String,
    val blockedApps: List<String> = emptyList()
)

// ⚠️ NUEVO: Estructura de políticas personalizadas
data class PoliciesResponse(
    val dataControlEnabled: Boolean? = false,
    val policyProfile: String? = "standard", // complete, standard, restrictive, custom
    val customPolicies: CustomPolicies? = null,
    val blockedApps: List<String>? = emptyList(),
    val commands: List<Command>? = emptyList()
)

data class CustomPolicies(
    // Apps específicas a bloquear/permitir
    val blockSocialMedia: Boolean = true,
    val blockStreaming: Boolean = true,
    val blockGoogleApps: Boolean = true,
    val blockPlayStore: Boolean = true,
    val blockBrowsers: Boolean = false,

    // URLs permitidas en Chrome
    val allowedUrls: List<String> = emptyList(),
    val blockAllUrls: Boolean = false,

    // Restricciones del sistema
    val blockHotspot: Boolean = true,
    val blockInstallation: Boolean = true,
    val blockUSB: Boolean = false,
    val blockFactoryReset: Boolean = true,
    val blockWifiConfig: Boolean = false,
    val blockBluetoothConfig: Boolean = false,

    // Apps personalizadas (para casos especiales)
    val customBlockedApps: List<String> = emptyList(),
    val customAllowedApps: List<String> = emptyList()
)

data class Command(
    val type: String,
    val parameters: Map<String, Any>? = emptyMap(),
    val timestamp: String? = null
)

data class HealthResponse(
    val status: String,
    val timestamp: String,
    val storage: String,
    val devices: Int
)