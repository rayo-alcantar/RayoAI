package com.rayoai.domain.model

import com.rayoai.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistributionBuildConfigTest {
    @Test
    fun preservesDistributionIdentityAndPlayRestrictionsInEveryVariant() {
        val github = BuildConfig.FLAVOR == "github"
        assertEquals("com.rayoai", BuildConfig.APPLICATION_ID)
        assertEquals(github, BuildConfig.GITHUB_UPDATES_ENABLED)
        assertEquals(github, BuildConfig.DONATIONS_ENABLED)
        assertTrue(BuildConfig.VERSION_CODE > 0)
        assertNotNull(AppVersion.parse(BuildConfig.VERSION_NAME))
    }
}
