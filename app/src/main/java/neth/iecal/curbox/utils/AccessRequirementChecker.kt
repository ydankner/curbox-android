package neth.iecal.curbox.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import kotlinx.coroutines.runBlocking
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.AccessCondition
import neth.iecal.curbox.data.models.AccessRequirement
import java.time.LocalDate
import java.time.ZoneId

/**
 * Decides whether an [AccessRequirement] is met right now. Once met it stays met until
 * midnight, so a card that becomes due again later in the day does not lock the user out.
 */
class AccessRequirementChecker(
    private val context: Context,
    /** Foreground time of (package, sinceMs) that the usage tracker has not written yet. */
    private val liveUsage: (String, Long) -> Long = { _, _ -> 0L },
) {
    data class Status(val isMet: Boolean, val details: String)

    private data class ConditionResult(val isMet: Boolean, val text: String)

    private val prefs = context.getSharedPreferences("access_requirements", Context.MODE_PRIVATE)
    private val usageStats = UsageStatsHelper(context)

    fun check(requirement: AccessRequirement, nowMs: Long = System.currentTimeMillis()): Status {
        val latchKey = "met_${requirement.id}"
        val latchValue = "${TimeTools.getCurrentDate()}|${AccessRequirementRules.fingerprint(requirement)}"
        if (prefs.getString(latchKey, null) == latchValue) return Status(true, "")

        val dayStartMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val results = requirement.conditions.map { evaluate(it, dayStartMs, nowMs) }
        val isMet = AccessRequirementRules.isMet(requirement.isAllRequired, results.map { it.isMet })
        if (isMet) prefs.edit { putString(latchKey, latchValue) }
        return Status(isMet, describe(requirement, results))
    }

    private fun evaluate(condition: AccessCondition, dayStartMs: Long, nowMs: Long): ConditionResult =
        when (condition.type) {
            AccessCondition.TYPE_APP_USAGE -> {
                val label = appLabel(condition.packageName)
                val usedMs = runBlocking {
                    usageStats.getForegroundUsageBetween(setOf(condition.packageName), dayStartMs, nowMs)
                } + liveUsage(condition.packageName, dayStartMs)
                val usedMinutes = (usedMs / 60_000L).toInt()
                if (usedMinutes >= condition.minutes) {
                    ConditionResult(
                        true,
                        context.getString(R.string.access_condition_app_usage_done, label, condition.minutes)
                    )
                } else {
                    ConditionResult(
                        false,
                        context.getString(
                            R.string.access_condition_app_usage_left,
                            label,
                            condition.minutes,
                            usedMinutes
                        )
                    )
                }
            }
            else -> when (val remaining = AnkiCardQueue.remainingCards(context)) {
                null -> ConditionResult(false, context.getString(R.string.access_condition_anki_unreadable))
                0 -> ConditionResult(true, context.getString(R.string.access_condition_anki_done))
                else -> ConditionResult(
                    false,
                    context.resources.getQuantityString(R.plurals.access_condition_anki_left, remaining, remaining)
                )
            }
        }

    private fun describe(requirement: AccessRequirement, results: List<ConditionResult>): String {
        val header = context.getString(
            if (requirement.isAllRequired && results.size > 1) R.string.access_requirement_needs_all
            else R.string.access_requirement_needs_any
        )
        return buildString {
            append(header)
            results.forEach { result ->
                append('\n').append(if (result.isMet) "✓ " else "• ").append(result.text)
            }
        }
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString()
    }.getOrDefault(packageName)
}
