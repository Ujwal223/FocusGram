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
 * Application entry point managing process isolation, storage singletons, and engine pre-warming.
 *
 * GeckoView spawns sandboxed child processes for tab and compositor isolation; service initialization
 * is constrained exclusively to the primary application process.
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

        database = FocusGramDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        encryptedStorage = EncryptedStorage(this)

        // Pre-warming GeckoRuntime and mounting uBlock on Main avoids cold-start frame drops
        // when the primary WebView composable binds to the session manager.
        applicationScope.launch(Dispatchers.Main) {
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val migrator = LegacyFlutterDataMigrator(this@FocusGramApp, settingsRepository, database)
                    migrator.checkAndMigrate()
                }

                val manager = GeckoRuntimeManager.getInstance(this@FocusGramApp)
                manager.extensionManager.initializeUBlockOrigin()
                Log.i(TAG, "GeckoRuntime & Content Blocker initialized successfully.")
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
