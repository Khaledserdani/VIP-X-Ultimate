package com.vipx.ultimate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizationVaultTest {
    @Test
    fun acceptsBoundedSingleLineAuthorizationValues() {
        assertTrue(AuthorizationVault.isValidAuthorization(""))
        assertTrue(AuthorizationVault.isValidAuthorization("Bearer example-token"))
        assertTrue(AuthorizationVault.isValidAuthorization("x".repeat(8192)))
    }

    @Test
    fun rejectsHeaderInjectionAndOversizedValues() {
        assertFalse(AuthorizationVault.isValidAuthorization("Bearer first\\r\\nInjected: value"))
        assertFalse(AuthorizationVault.isValidAuthorization("Bearer first\\nInjected: value"))
        assertFalse(AuthorizationVault.isValidAuthorization("x".repeat(8193)))
    }
}
