package com.pfsmx.mdmempresarial


import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.pfsmx.mdmempresarial.receiver.MDMDeviceAdminReceiver

class ProvisioningActivity : Activity() {

    companion object {
        private const val TAG = "ProvisioningActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "Iniciando provisioning...")

        val action = intent.action

        if (DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE == action) {
            handleProvisioning()
        } else {
            Log.w(TAG, "Acción no reconocida: $action")
            finish()
        }
    }

    private fun handleProvisioning() {
        try {
            val componentName = ComponentName(this, MDMDeviceAdminReceiver::class.java)

            val adminExtras = Bundle()
            adminExtras.putString("server_url", "https://mdm.pfsmx.app/")

            Log.i(TAG, "Provisioning configurado correctamente")
            Toast.makeText(this, "Configurando dispositivo...", Toast.LENGTH_LONG).show()

            setResult(RESULT_OK)
            finish()

        } catch (e: Exception) {
            Log.e(TAG, "Error en provisioning", e)
            Toast.makeText(this, "Error en provisioning: ${e.message}", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
        }
    }
}