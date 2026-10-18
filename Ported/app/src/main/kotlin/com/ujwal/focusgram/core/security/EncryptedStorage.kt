package com.ujwal.focusgram.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * EncryptedStorage
 * Hardware-backed cryptographic vault utilizing Android KeyStore MasterKey (AES-256 GCM)
 * and EncryptedSharedPreferences (AES-256 SIV + AES-256 GCM).
 *
 * Secures:
 * - App Lock 4-digit PIN (hashed with SHA-256 + salt and encrypted at rest).
 * - Biometric authentication state.
 * - Brute-force lockout and failed attempt tracking.
 */
class EncryptedStorage(context: Context) {

    private val securePrefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        securePrefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            SECURE_VAULT_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Hashes and securely stores user's App Lock PIN.
     */
    fun savePin(pin: String) {
        val hashedPin = hashPin(pin)
        securePrefs.edit()
            .putString(KEY_PIN_HASH, hashedPin)
            .putBoolean(KEY_HAS_PIN, true)
            .apply()
        resetFailedAttempts()
        Log.i(TAG, "App Lock PIN securely stored in MasterKey AES-GCM vault.")
    }

    /**
     * Verifies entered PIN against hardware-encrypted hash.
     */
    fun verifyPin(enteredPin: String): Boolean {
        if (isLockedOut()) {
            Log.w(TAG, "PIN verification rejected: Vault is currently locked out.")
            return false
        }

        val storedHash = securePrefs.getString(KEY_PIN_HASH, null) ?: return false
        val enteredHash = hashPin(enteredPin)

        val isValid = storedHash == enteredHash
        if (isValid) {
            resetFailedAttempts()
        } else {
            recordFailedAttempt()
        }
        return isValid
    }

    fun hasPin(): Boolean {
        return securePrefs.getBoolean(KEY_HAS_PIN, false) && securePrefs.getString(KEY_PIN_HASH, null) != null
    }

    fun clearPin() {
        securePrefs.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_HAS_PIN, false)
            .apply()
        resetFailedAttempts()
        Log.i(TAG, "App Lock PIN removed from vault.")
    }

    // --- Chat Lock PIN Management ---

    fun saveChatPin(pin: String) {
        val hashedPin = hashPin("chat_$pin")
        securePrefs.edit()
            .putString(KEY_CHAT_PIN_HASH, hashedPin)
            .putBoolean(KEY_HAS_CHAT_PIN, true)
            .apply()
        resetFailedAttempts()
        Log.i(TAG, "Chat Lock PIN securely stored in MasterKey AES-GCM vault.")
    }

    fun verifyChatPin(enteredPin: String): Boolean {
        if (isLockedOut()) {
            Log.w(TAG, "Chat PIN verification rejected: Vault is currently locked out.")
            return false
        }
        val storedHash = securePrefs.getString(KEY_CHAT_PIN_HASH, null) ?: return false
        val enteredHash = hashPin("chat_$enteredPin")
        val isValid = storedHash == enteredHash
        if (isValid) {
            resetFailedAttempts()
        } else {
            recordFailedAttempt()
        }
        return isValid
    }

    fun hasChatPin(): Boolean {
        return securePrefs.getBoolean(KEY_HAS_CHAT_PIN, false) && securePrefs.getString(KEY_CHAT_PIN_HASH, null) != null
    }

    fun clearChatPin() {
        securePrefs.edit()
            .remove(KEY_CHAT_PIN_HASH)
            .putBoolean(KEY_HAS_CHAT_PIN, false)
            .apply()
        Log.i(TAG, "Chat Lock PIN removed from vault.")
    }

    // --- Brute Force Protection ---

    fun isLockedOut(): Boolean {
        val lockoutUntil = securePrefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        return System.currentTimeMillis() < lockoutUntil
    }

    fun getRemainingLockoutSeconds(): Long {
        val lockoutUntil = securePrefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        val remaining = (lockoutUntil - System.currentTimeMillis()) / 1000
        return remaining.coerceAtLeast(0)
    }

    private fun recordFailedAttempt() {
        val currentAttempts = securePrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = securePrefs.edit().putInt(KEY_FAILED_ATTEMPTS, currentAttempts)

        if (currentAttempts >= MAX_FAILED_ATTEMPTS) {
            val lockoutDurationMs = LOCKOUT_DURATION_SECONDS * 1000L
            val lockoutUntil = System.currentTimeMillis() + lockoutDurationMs
            editor.putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
            Log.w(TAG, "Maximum failed PIN attempts reached ($currentAttempts). Locked out for $LOCKOUT_DURATION_SECONDS seconds.")
        }
        editor.apply()
    }

    private fun resetFailedAttempts() {
        securePrefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0)
            .apply()
    }

    // --- Generic Key/Value Secure Storage ---

    fun putSecureString(key: String, value: String) {
        securePrefs.edit().putString(key, value).apply()
    }

    fun getSecureString(key: String): String? {
        return securePrefs.getString(key, null)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val saltedBytes = ("focusgram_salt_v2_" + pin).toByteArray(Charsets.UTF_8)
        val hashBytes = digest.digest(saltedBytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "EncryptedStorage"
        private const val SECURE_VAULT_NAME = "focusgram_secure_vault"

        private const val KEY_PIN_HASH = "vault_pin_hash"
        private const val KEY_HAS_PIN = "vault_has_pin"
        private const val KEY_CHAT_PIN_HASH = "vault_chat_pin_hash"
        private const val KEY_HAS_CHAT_PIN = "vault_has_chat_pin"
        private const val KEY_FAILED_ATTEMPTS = "vault_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "vault_lockout_until"

        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_SECONDS = 30
    }
}
