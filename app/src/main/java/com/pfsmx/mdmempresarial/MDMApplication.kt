package com.pfsmx.mdmempresarial

import android.app.Application
import android.util.Log

class MDMApplication : Application() {

    companion object {
        private const val TAG = "MDMApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MDM Application iniciada")
    }
}