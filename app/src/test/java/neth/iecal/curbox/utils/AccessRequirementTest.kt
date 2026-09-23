package neth.iecal.curbox.utils

import neth.iecal.curbox.data.models.AccessCondition
import neth.iecal.curbox.data.models.AccessRequirement
import neth.iecal.curbox.data.models.AppGroup
import neth.iecal.curbox.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessRequirementTest {
    private val anki = AccessCondition(type = AccessCondition.TYPE_ANKI_CLEARED)
    private val dayOne5 = AccessCondition(
        type = AccessCondition.TYPE_APP_USAGE,
        packageName = "com.dayoneapp.dayone",
        minutes = 5
    )
    private val dayOne10 = dayOne5.copy(minutes = 10)
    private val ankiOrDayOne = AccessRequirement(id = "r", conditions = listOf(anki, dayOne5))
    private val ankiAndDayOne = ankiOrDayOne.copy(isAllRequired = true)

    @Test
    fun anyNeedsOneConditionAndAllNeedsEvery() {
        assertTrue(AccessRequirementRules.isMet(false, listOf(false, true)))
        assertFalse(AccessRequirementRules.isMet(true, listOf(false, true)))
        assertTrue(AccessRequirementRules.isMet(true, listOf(true, true)))
        assertFalse(AccessRequirementRules.isMet(false, listOf(false, false)))
    }

    @Test
    fun requirementWithoutConditionsIsMet() {
        assertTrue(AccessRequirementRules.isMet(true, emptyList()))
    }

    @Test
    fun fingerprintChangesWithWhatIsAsked() {
        val base = AccessRequirementRules.fingerprint(ankiOrDayOne)
        assertEquals(base, AccessRequirementRules.fingerprint(ankiOrDayOne.copy(name = "renamed")))
        assertNotEquals(base, AccessRequirementRules.fingerprint(ankiAndDayOne))
        assertNotEquals(
            base,
            AccessRequirementRules.fingerprint(ankiOrDayOne.copy(conditions = listOf(anki, dayOne10)))
        )
    }

    @Test
    fun anyToAllIsStricterButAllToAnyIsNot() {
        assertTrue(RestrictionComparator.accessRequirement(ankiOrDayOne, ankiAndDayOne))
        assertFalse(RestrictionComparator.accessRequirement(ankiAndDayOne, ankiOrDayOne))
    }

    @Test
    fun addingAnAlternativeIsWeakerAndAddingAMustIsStricter() {
        val ankiOnly = AccessRequirement(id = "r", conditions = listOf(anki))
        assertFalse(RestrictionComparator.accessRequirement(ankiOnly, ankiOrDayOne))
        assertTrue(RestrictionComparator.accessRequirement(ankiOnly, ankiAndDayOne))
        assertTrue(RestrictionComparator.accessRequirement(ankiOrDayOne, ankiOnly))
    }

    @Test
    fun moreMinutesIsStricterAndFewerIsWeaker() {
        val needs5 = AccessRequirement(id = "r", conditions = listOf(dayOne5))
        val needs10 = AccessRequirement(id = "r", conditions = listOf(dayOne10))
        assertTrue(RestrictionComparator.accessRequirement(needs5, needs10))
        assertFalse(RestrictionComparator.accessRequirement(needs10, needs5))
    }

    @Test
    fun emptyingARequirementIsWeaker() {
        assertFalse(RestrictionComparator.accessRequirement(ankiOrDayOne, ankiOrDayOne.copy(conditions = emptyList())))
    }

    @Test
    fun onlyRequirementsGuardingAnActiveGroupAreGated() {
        val unused = AccessRequirement(id = "unused", conditions = listOf(anki))
        val guarded = Settings(
            blockedAppGroups = listOf(AppGroup(id = "g", isActive = true, accessRequirementId = "r")),
            accessRequirements = listOf(ankiAndDayOne, unused)
        )
        assertTrue(
            RestrictionComparator.accessRequirements(guarded, guarded.copy(accessRequirements = listOf(ankiAndDayOne)))
        )
        assertFalse(
            RestrictionComparator.accessRequirements(guarded, guarded.copy(accessRequirements = listOf(unused)))
        )
        assertFalse(
            RestrictionComparator.accessRequirements(guarded, guarded.copy(accessRequirements = listOf(ankiOrDayOne, unused)))
        )
    }

    @Test
    fun groupMayGainButNotLoseItsRequirement() {
        val group = AppGroup(
            id = "g",
            isActive = true,
            config = neth.iecal.curbox.data.models.AppGroupConfig()
        )
        val guarded = group.copy(accessRequirementId = "r")
        assertTrue(RestrictionComparator.appGroups(listOf(group), listOf(guarded)))
        assertFalse(RestrictionComparator.appGroups(listOf(guarded), listOf(group)))
        assertFalse(RestrictionComparator.appGroups(listOf(guarded), listOf(guarded.copy(accessRequirementId = "other"))))
    }
}
