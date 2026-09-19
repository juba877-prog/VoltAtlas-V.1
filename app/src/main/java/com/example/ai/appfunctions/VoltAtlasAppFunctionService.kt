package com.example.ai.appfunctions

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/**
 * Android system service entry point for VoltAtlas AppFunctions.
 * Enables system agents, assistant shortcuts, and external tool runners
 * to invoke VoltAtlas electrical engineering calculation shortcuts.
 */
class VoltAtlasAppFunctionService : Service() {

    private val binder = LocalBinder()
    val appFunctions: VoltAtlasAppFunctions by lazy { VoltAtlasAppFunctions() }

    inner class LocalBinder : Binder() {
        fun getService(): VoltAtlasAppFunctionService = this@VoltAtlasAppFunctionService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    companion object {
        const val ACTION_APP_FUNCTION_SERVICE = "android.app.appfunctions.AppFunctionService"
    }
}
