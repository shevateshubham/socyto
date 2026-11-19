package com.socyto.app.data

import org.junit.Assert.*
import org.junit.Test

class BrandValidatorTest {
    @Test fun acceptsValidBusinessPalette() {
        assertEquals("#1122AA", BrandValidator.colorOrDefault("#1122aa", "#2E4A2B"))
    }

    @Test fun blocksInvalidColorValues() {
        assertEquals("#2E4A2B", BrandValidator.colorOrDefault("red; DROP TABLE", "#2E4A2B"))
        assertEquals("#2E4A2B", BrandValidator.colorOrDefault("#12345", "#2E4A2B"))
    }

    @Test fun rejectsBrandingLogoFromAnotherBusinessHost() {
        assertNull(BrandValidator.logoUrlOrNull(
            "https://tracker.example.com/record.png", "https://socyto-example.supabase.co"))
        assertNull(BrandValidator.logoUrlOrNull(
            "http://socyto-example.supabase.co/storage/v1/object/public/business-logos/logo.png",
            "https://socyto-example.supabase.co"))
    }

    @Test fun permitsProjectPublicLogoStorageOnly() {
        assertEquals(
            "https://socyto-example.supabase.co/storage/v1/object/public/business-logos/firm-a.png",
            BrandValidator.logoUrlOrNull(
                "https://socyto-example.supabase.co/storage/v1/object/public/business-logos/firm-a.png",
                "https://socyto-example.supabase.co"
            )
        )
        assertNull(BrandValidator.logoUrlOrNull(
            "https://socyto-example.supabase.co/storage/v1/object/public/project-invoices/secret.pdf",
            "https://socyto-example.supabase.co"
        ))
    }

    @Test fun ignoresBlankOrUnreasonablyLongDisplayNames() {
        assertEquals("Project workspace", BrandValidator.displayNameOrFallback("  ", "Project workspace"))
        assertEquals("A Firm", BrandValidator.displayNameOrFallback(" A Firm ", "Project workspace"))
    }
}
