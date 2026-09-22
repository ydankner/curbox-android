package neth.iecal.curbox.utils

import neth.iecal.curbox.data.models.AppGroup
import neth.iecal.curbox.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class SettingsBackupCodecTest {
    @Test
    fun roundTripKeepsGroups() {
        val settings = Settings(
            blockedAppGroups = listOf(
                AppGroup(name = "Social", selectedPackages = listOf("com.instagram.android"))
            ),
            isAppUsageTrackingEnabled = false
        )

        val restored = SettingsBackupCodec.decode(SettingsBackupCodec.encode(settings))

        assertEquals(settings.blockedAppGroups, restored.blockedAppGroups)
        assertFalse(restored.isAppUsageTrackingEnabled)
    }

    @Test
    fun missingFieldsUseDefaults() {
        val json = """{"format":"curbox-settings","version":1,"settings":{"isReelCounterOn":false}}"""

        val restored = SettingsBackupCodec.decode(json)

        assertFalse(restored.isReelCounterOn)
        assertNotNull(restored.keywordBlockerConfig)
        assertNotNull(restored.settingsChangeDelayConfig2)
        assertEquals(emptyList<AppGroup>(), restored.blockedAppGroups)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherFiles() {
        SettingsBackupCodec.decode("""{"blockedAppGroups":[]}""")
    }
}
