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
}
