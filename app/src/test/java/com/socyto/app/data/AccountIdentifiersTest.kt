package com.socyto.app.data

import org.junit.Assert.*
import org.junit.Test

class AccountIdentifiersTest {
    @Test fun firmAndClientCodeMappings() {
        assertTrue(AccountIdentifiers.isAccountCode("F123456789"))
        assertTrue(AccountIdentifiers.isAccountCode(" c123456789 "))
        assertEquals("f123456789@login.socyto.invalid",
            AccountIdentifiers.authenticationEmail(" F123456789 "))
        assertEquals("c123456789@login.socyto.invalid",
            AccountIdentifiers.authenticationEmail("c123456789"))
    }

    @Test fun emailAdminCompatibility() {
        assertEquals("admin@example.org",
            AccountIdentifiers.authenticationEmail(" ADMIN@Example.org "))
    }

    @Test fun firmAndClientMayHaveSingleEmailOrMobileContact() {
        assertTrue(AccountIdentifiers.validEmail("client@practice.in"))
        assertTrue(AccountIdentifiers.validPhone("+919876543210"))
        assertFalse(AccountIdentifiers.validEmail("+919876543210"))
        assertFalse(AccountIdentifiers.validPhone("client@practice.in"))
        assertFalse(AccountIdentifiers.validIdentifier(""))
    }

    @Test fun unifiedInputValidation() {
        assertTrue(AccountIdentifiers.validEmail("admin@example.org"))
        assertTrue(AccountIdentifiers.validIdentifier("admin@example.org"))
        assertTrue(AccountIdentifiers.validIdentifier("+919876543210"))
        assertTrue(AccountIdentifiers.validIdentifier("F123456789"))
        assertTrue(AccountIdentifiers.validIdentifier("c123456789"))
        assertFalse(AccountIdentifiers.validIdentifier("invalid"))
        assertFalse(AccountIdentifiers.validIdentifier("1234567890"))
        assertFalse(AccountIdentifiers.validIdentifier("not@valid"))
    }

    @Test fun invalidCodesAndPhones() {
        assertFalse(AccountIdentifiers.isAccountCode("F123"))
        assertFalse(AccountIdentifiers.isAccountCode("X123456789"))
        assertFalse(AccountIdentifiers.isAccountCode("F1234567890"))
        assertTrue(AccountIdentifiers.validPhone("+919876543210"))
        assertTrue(AccountIdentifiers.validPhone("9876543210"))
        assertEquals("+919876543210", AccountIdentifiers.normalizedPhone("9876543210"))
        assertEquals("+919876543210", AccountIdentifiers.normalizedContact("9876543210"))
        assertFalse(AccountIdentifiers.validPhone("1234567890"))
        assertFalse(AccountIdentifiers.validPhone("+001234567890"))
    }
}
