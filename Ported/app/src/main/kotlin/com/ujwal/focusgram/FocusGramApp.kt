package com.ujwal.focusgram

import android.app.Application
import android.util.Log
import com.ujwal.focusgram.core.database.FocusGramDatabase
import com.ujwal.focusgram.core.datastore.LegacyFlutterDataMigrator
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.engine.GeckoRuntimeManager
import com.ujwal.focusgram.core.security.EncryptedStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FocusGramApp
 * Application entry point for FocusGram Native.
 * Manages global singleton lifecycles such as GeckoRuntime, Room Database, and Settings DataStore.
 */
class FocusGramApp : Application() {

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    lateinit var database: FocusGramDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var encryptedStorage: EncryptedStorage
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (!isMainProcess()) {
            Log.d(TAG, "GeckoView child process detected (${getProcessNameCompat()}). Skipping app singleton initialization.")
            return
        }

        Log.i(TAG, "FocusGram Native Application starting up on main process...")

        // Initialize local persistence & security components
        database = FocusGramDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        encryptedStorage = EncryptedStorage(this)

        // Pre-warm GeckoRuntime, mount uBlock Origin, and run legacy migration
        applicationScope.launch(Dispatchers.Main) {
            try {
                // 1. Migrate legacy Flutter data if present
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val migrator = LegacyFlutterDataMigrator(this@FocusGramApp, settingsRepository, database)
                    migrator.checkAndMigrate()
                }

                // 2. Pre-warm GeckoRuntime singleton & mount uBlock Origin on UI thread
                val manager = GeckoRuntimeManager.getInstance(this@FocusGramApp)
                manager.extensionManager.initializeUBlockOrigin()
                Log.i(TAG, "GeckoRuntime & uBlock Origin initialized successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Initialization error in background scope: ${e.message}", e)
            }
        }
    }

    private fun isMainProcess(): Boolean {
        val currentProcessName = getProcessNameCompat()
        return currentProcessName == packageName
    }

    private fun getProcessNameCompat(): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager
            val pid = android.os.Process.myPid()
            am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: packageName
        }
    }

    companion object {
        private const val TAG = "FocusGramApp"

        lateinit var instance: FocusGramApp
            private set
    }
}
