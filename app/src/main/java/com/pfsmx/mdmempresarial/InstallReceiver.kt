package com.pfsmx.mdmempresarial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast

class InstallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "InstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: "unknown"
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "📦 RESULTADO DE INSTALACIÓN")
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "Package: $packageName")
        Log.i(TAG, "Status Code: $status")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.w(TAG, "⏳ PENDING_USER_ACTION - Se requiere interacción del usuario")
                // Algunos dispositivos pueden requerir esto incluso siendo Device Owner
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmIntent != null) {
                    try {
                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirmIntent)
                        Log.i(TAG, "✅ Intent de confirmación lanzado")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error lanzando intent de confirmación: ${e.message}")
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "✅ INSTALACIÓN EXITOSA")
                Toast.makeText(context, "✅ $packageName instalada", Toast.LENGTH_SHORT).show()
            }

            PackageInstaller.STATUS_FAILURE -> {
                Log.e(TAG, "❌ INSTALACIÓN FALLIDA")
                Log.e(TAG, "Mensaje: $message")
                Toast.makeText(context, "❌ Error instalando $packageName", Toast.LENGTH_LONG).show()
            }

            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                Log.e(TAG, "🚫 INSTALACIÓN BLOQUEADA")
                Log.e(TAG, "Mensaje: $message")
                Log.e(TAG, "Posibles causas:")
                Log.e(TAG, "  - Restricciones de usuario activas (DISALLOW_INSTALL_APPS)")
                Log.e(TAG, "  - Play Protect bloqueando la instalación")
                Log.e(TAG, "  - Otra política de seguridad activa")
                Toast.makeText(context, "🚫 Instalación bloqueada: $packageName", Toast.LENGTH_LONG).show()
            }

            PackageInstaller.STATUS_FAILURE_INVALID -> {
                Log.e(TAG, "❌ APK INVÁLIDO")
                Log.e(TAG, "Mensaje: $message")
                Toast.makeText(context, "❌ APK inválido: $packageName", Toast.LENGTH_LONG).show()
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                Log.e(TAG, "⚠️ CONFLICTO DE INSTALACIÓN")
                Log.e(TAG, "Mensaje: $message")
                Log.e(TAG, "Posible causa: Ya existe una versión diferente instalada")
                Toast.makeText(context, "⚠️ Conflicto: $packageName", Toast.LENGTH_LONG).show()
            }

            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Log.e(TAG, "💾 ERROR DE ALMACENAMIENTO")
                Log.e(TAG, "Mensaje: $message")
                Toast.makeText(context, "💾 Sin espacio: $packageName", Toast.LENGTH_LONG).show()
            }

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                Log.e(TAG, "⚠️ APP INCOMPATIBLE")
                Log.e(TAG, "Mensaje: $message")
                Toast.makeText(context, "⚠️ Incompatible: $packageName", Toast.LENGTH_LONG).show()
            }

            else -> {
                Log.w(TAG, "⚠️ ESTADO DESCONOCIDO: $status")
                Log.w(TAG, "Mensaje: $message")
            }
        }

        Log.i(TAG, "════════════════════════════════════════")
    }
}