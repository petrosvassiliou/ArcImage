package com.arcimage.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateRepositoryTest {

    @Test
    fun `SemanticVersion compares versions accurately`() {
        assertTrue(SemanticVersion.isNewer("1.1.0", "1.0.0"))
        assertTrue(SemanticVersion.isNewer("v2.0.0", "1.9.9"))
        assertTrue(SemanticVersion.isNewer("1.1.1", "1.1.0"))
        assertFalse(SemanticVersion.isNewer("1.0.0", "1.0.0"))
        assertFalse(SemanticVersion.isNewer("1.0.0", "1.1.0"))
    }

    @Test
    fun `parseGithubLatestReleaseJson detects newer verified release`() {
        val json = """
            {
              "tag_name": "v1.2.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/petrosvassiliou/ArcImage/releases/tag/v1.2.0",
              "body": "Faster archive inspector and country flags."
            }
        """.trimIndent()

        val result = parseGithubLatestReleaseJson(json, currentVersion = "1.1.0")
        assertTrue(result is UpdateCheckResult.ReleasePageUpdateAvailable)
        assertTrue(result.hasUpdate)
        val available = result as UpdateCheckResult.ReleasePageUpdateAvailable
        assertEquals("1.2.0", available.latestVersion)
        assertEquals(
            "https://github.com/petrosvassiliou/ArcImage/releases/tag/v1.2.0",
            available.releaseUrl
        )
    }

    @Test
    fun `parseGithubLatestReleaseJson ignores drafts, prereleases, equal versions, and untrusted URLs`() {
        val sameVersionJson = """
            {
              "tag_name": "v1.1.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/petrosvassiliou/ArcImage/releases/tag/v1.1.0"
            }
        """.trimIndent()
        assertFalse(parseGithubLatestReleaseJson(sameVersionJson, "1.1.0").hasUpdate)

        val draftJson = """
            {
              "tag_name": "v2.0.0",
              "draft": true,
              "prerelease": false,
              "html_url": "https://github.com/petrosvassiliou/ArcImage/releases/tag/v2.0.0"
            }
        """.trimIndent()
        assertFalse(parseGithubLatestReleaseJson(draftJson, "1.1.0").hasUpdate)

        val untrustedUrlJson = """
            {
              "tag_name": "v2.0.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://evil.example.com/ArcImage.apk"
            }
        """.trimIndent()
        assertFalse(parseGithubLatestReleaseJson(untrustedUrlJson, "1.1.0").hasUpdate)
    }

    @Test
    fun `AppUpdateRepositoryImpl persists auto-check preference`() {
        val context = RuntimeEnvironment.getApplication()
        val repo = AppUpdateRepositoryImpl(context, currentVersionOverride = "1.1.0")
        assertTrue(repo.isAutoCheckEnabled())
        repo.setAutoCheckEnabled(false)
        assertFalse(repo.isAutoCheckEnabled())
        repo.setAutoCheckEnabled(true)
        assertTrue(repo.isAutoCheckEnabled())
    }
}
