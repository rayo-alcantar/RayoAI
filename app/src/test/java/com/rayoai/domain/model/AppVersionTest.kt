package com.rayoai.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    private fun version(value: String) = requireNotNull(AppVersion.parse(value))

    @Test
    fun comparesNumericComponentsRatherThanText() {
        assertTrue(version("3.3.12") > version("3.3.9"))
        assertTrue(version("4.0.0") > version("3.99.99"))
        assertTrue(version("3.3.11.1") > version("3.3.11"))
        assertEquals(0, version("3.3").compareTo(version("3.3.0")))
    }

    @Test
    fun supportsLegacyTagsAndTreatsSameNumericIdentityAsNoUpdate() {
        for (value in listOf("v3.3.11", "V3.3.11", "3.3.11-stable", "3.3.11-beta")) {
            assertEquals(value, 0, version(value).compareTo(version("3.3.11")))
        }
    }

    @Test
    fun ignoresMalformedReleaseTagsRatherThanExtractingUnrelatedDigits() {
        for (value in listOf("cmd-beta", "", "build-999", "3.3.11-unknown", "3.3.11-beta.99", "3.3.11/12", "3..11")) {
            assertNull(value, AppVersion.parse(value))
        }
    }

    @Test
    fun largeComponentsDoNotOverflow() {
        assertTrue(version("3.3.999999999999999999999999999999") > version("3.3.11"))
    }

    @Test
    fun legacyAllPreferenceKeepsItsOptInWithoutAThirdDuplicateOption() {
        assertEquals(UpdateChannel.BETA, UpdateChannel.fromStored("ALL"))
        assertEquals(UpdateChannel.BETA, UpdateChannel.fromStored("BETA"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromStored("STABLE"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromStored(null))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromStored("corrupt"))
        assertEquals(UpdateChannel.BETA, UpdateChannel.ALL.canonical)
    }
}
