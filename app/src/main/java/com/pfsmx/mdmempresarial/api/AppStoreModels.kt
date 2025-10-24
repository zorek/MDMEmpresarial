package com.pfsmx.mdmempresarial.api


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