package neth.iecal.curbox.utils

import neth.iecal.curbox.data.models.AccessRequirement

/** The parts of access requirement handling that do not need Android. */
object AccessRequirementRules {

    /** A requirement without conditions guards nothing, so it counts as met. */
    fun isMet(isAllRequired: Boolean, conditionsMet: List<Boolean>): Boolean = when {
        conditionsMet.isEmpty() -> true
        isAllRequired -> conditionsMet.all { it }
        else -> conditionsMet.any { it }
    }

    /**
     * Identifies what a requirement asks for. A requirement met earlier today stays met only
     * while this stays the same, so editing it makes the user meet the new version.
     */
    fun fingerprint(requirement: AccessRequirement): String = buildString {
        append(if (requirement.isAllRequired) "all" else "any")
        requirement.conditions.forEach { condition ->
            append('|').append(condition.type)
                .append(':').append(condition.packageName)
                .append(':').append(condition.minutes)
        }
    }
}
