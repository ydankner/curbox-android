package neth.iecal.curbox.utils

import neth.iecal.curbox.data.models.AppBlockerWarningScreenConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictionComparatorAppGoalTest {
    private val enabled = AppBlockerWarningScreenConfig(
        isAppGoalRequirementEnabled = true,
        appGoalPackageName = "com.ichi2.anki",
        appGoalRequiredMinutes = 15
    )

    @Test
    fun enablingAppGoalIsStricter() {
        assertTrue(
            RestrictionComparator.warningConfig(
                AppBlockerWarningScreenConfig(),
                enabled
            )
        )
    }

    @Test
    fun disablingAppGoalIsWeaker() {
        assertFalse(
            RestrictionComparator.warningConfig(
                enabled,
                enabled.copy(isAppGoalRequirementEnabled = false)
            )
        )
    }

    @Test
    fun increasingThresholdIsStricter() {
        assertTrue(
            RestrictionComparator.warningConfig(
                enabled,
                enabled.copy(appGoalRequiredMinutes = 30)
            )
        )
    }

    @Test
    fun decreasingThresholdIsWeaker() {
        assertFalse(
            RestrictionComparator.warningConfig(
                enabled,
                enabled.copy(appGoalRequiredMinutes = 5)
            )
        )
    }

    @Test
    fun changingAppIsConservativelyWeaker() {
        assertFalse(
            RestrictionComparator.warningConfig(
                enabled,
                enabled.copy(appGoalPackageName = "com.other.app")
            )
        )
    }
}
