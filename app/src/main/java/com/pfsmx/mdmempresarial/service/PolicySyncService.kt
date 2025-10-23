package com.pfsmx.mdmempresarial.service

import android.content.Intent
import android.os.IBinder

/**
 * Alias para UnifiedSyncService (mantiene compatibilidad)
 */
class PolicySyncService : UnifiedSyncService() {
    override fun onBind(intent: Intent?): IBinder? = null
}