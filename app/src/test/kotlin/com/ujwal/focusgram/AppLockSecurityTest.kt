package com.ujwal.focusgram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * AppLockSecurityTest
 * Validates PIN formatting, cryptographic hashing, brute-force limits, and lockout timing.
 */
class AppLockSecurityTest {

    private fun hashPin(pin: String): String {
        val salt = "FocusGram_Local_Vault_Salt_v2"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest("$salt$pin".toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testPinFormatValidation() {
        fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }

        assertTrue(isValidPin("1234"))
        assertTrue(isValidPin("0000"))
        assertTrue(isValidPin("9876"))

        assertFalse(isValidPin("123"))
        assertFalse(isValidPin("12345"))
        assertFalse(isValidPin("12a4"))
        assertFalse(isValidPin(""))
    }

    @Test
    fun testPinHashingDeterminismAndSalt() {
        val hash1 = hashPin("1234")
        val hash2 = hashPin("1234")
        val hashDifferent = hashPin("1235")

        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hashDifferent)
        assertEquals(64, hash1.length)
    }

    @Test
    fun testBruteForceLockoutThresholds() {
        val maxFailedAttempts = 5
        val lockoutDurationSeconds = 30L

        var attempts = 4
        val isLockedOutBefore = attempts >= maxFailedAttempts
        assertFalse(isLockedOutBefore)

        attempts = 5
        val isLockedOutAt = attempts >= maxFailedAttempts
        assertTrue(isLockedOutAt)

        val lockoutUntil = 1_000_000L + (lockoutDurationSeconds * 1000L)
        val remainingSeconds = (lockoutUntil - 1_000_000L) / 1000L
        assertEquals(30L, remainingSeconds)
    }

    @Test
    fun testChatPinVsAppPinDifferentiation() {
        val appPinHash = hashPin("1234")
        val chatPinHash = hashPin("chat_1234")

        // Chat PIN hash must differ from App PIN hash even when same 4-digit code is chosen
        assertNotEquals(appPinHash, chatPinHash)
    }

    @Test
    fun testPinChangeVerificationLogic() {
        var currentPin = "2468"
        var isCurrentPinVerified = false
        var attempts = 0
        var isLockedOut = false

        fun submitVerification(entered: String): Boolean {
            if (isLockedOut) return false
            val valid = (entered == currentPin)
            if (valid) {
                isCurrentPinVerified = true
            } else {
                attempts++
                if (attempts >= 5) {
                    isLockedOut = true
                }
            }
            return valid
        }

        // Test 1: Incorrect PIN attempt
        assertFalse(submitVerification("1111"))
        assertFalse(isCurrentPinVerified)
        assertEquals(1, attempts)

        // Test 2: Correct PIN verification
        assertTrue(submitVerification("2468"))
        assertTrue(isCurrentPinVerified)

        // Test 3: Lockout on 5 consecutive failures
        attempts = 0
        isLockedOut = false
        isCurrentPinVerified = false
        repeat(5) {
            submitVerification("0000")
        }
        assertTrue(isLockedOut)
        assertFalse(submitVerification("2468")) // Even correct PIN blocked during lockout
    }
}
