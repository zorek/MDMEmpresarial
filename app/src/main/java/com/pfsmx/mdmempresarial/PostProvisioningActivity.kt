package com.pfsmx.mdmempresarial.ui

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.pfsmx.mdmempresarial.MainActivity

class PostProvisioningActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PostProvisioningActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.wtf(TAG, "════════════════════════════════════════")
        Log.wtf(TAG, "🎯 POST-PROVISIONING ACTIVITY INICIADA")
        Log.wtf(TAG, "════════════════════════════════════════")

        // Notificar que el provisioning está completo
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager

        // Finalizar provisioning
        finish()

        // Iniciar MainActivity
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)

            Log.wtf(TAG, "✅ MainActivity iniciada desde PostProvisioning")
        }, 1000)
    }
}