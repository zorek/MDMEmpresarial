package com.pfsmx.mdmempresarial.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object AppUtils {

    /**
     * Verifica si una app está instalada
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Obtiene el version code de una app instalada
     */
    fun getInstalledVersionCode(context: Context, packageName: String): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Obtiene el version name de una app instalada
     */
    fun getInstalledVersionName(context: Context, packageName: String): String? {
        return try {
            context.packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Formatea tamaño de archivo
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.1f GB".format(gb)
    }
}