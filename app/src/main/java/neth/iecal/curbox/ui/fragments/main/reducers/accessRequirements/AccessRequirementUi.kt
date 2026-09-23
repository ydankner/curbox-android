package neth.iecal.curbox.ui.fragments.main.reducers.accessRequirements

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.AccessCondition
import neth.iecal.curbox.data.models.AccessRequirement
import neth.iecal.curbox.ui.activity.FragmentActivity

internal fun Context.appLabel(packageName: String): String = runCatching {
    packageManager.getApplicationLabel(
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    ).toString()
}.getOrDefault(packageName)

internal fun Context.conditionText(condition: AccessCondition): String = when (condition.type) {
    AccessCondition.TYPE_APP_USAGE ->
        getString(R.string.access_condition_summary_app, appLabel(condition.packageName), condition.minutes)
    else -> getString(R.string.access_condition_type_anki)
}

internal fun Context.requirementSummary(requirement: AccessRequirement): String {
    val parts = requirement.conditions.joinToString(", ") { conditionText(it) }
    return getString(
        if (requirement.isAllRequired && requirement.conditions.size > 1) R.string.access_requirement_summary_all
        else R.string.access_requirement_summary_any,
        parts
    )
}

internal fun Context.requirementButtonText(requirements: List<AccessRequirement>, selectedId: String): String {
    val name = requirements.find { it.id == selectedId }?.name
        ?: return getString(R.string.group_access_requirement_none)
    return getString(R.string.group_access_requirement_button, name)
}

internal fun Context.openAccessRequirements() {
    startActivity(
        Intent(this, FragmentActivity::class.java)
            .putExtra("fragment", AccessRequirementsFragment.FRAGMENT_ID)
    )
}

/** Lets a group editor choose which requirement guards the group. */
internal fun Fragment.pickAccessRequirement(
    requirements: List<AccessRequirement>,
    selectedId: String,
    onPicked: (String) -> Unit
) {
    val context = requireContext()
    val ids = listOf("") + requirements.map { it.id }
    val labels = listOf(context.getString(R.string.group_access_requirement_none)) +
        requirements.map { it.name }
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.group_access_requirement_pick)
        .setSingleChoiceItems(labels.toTypedArray(), ids.indexOf(selectedId).coerceAtLeast(0)) { dialog, which ->
            onPicked(ids[which])
            dialog.dismiss()
        }
        .setNeutralButton(R.string.access_requirements_manage) { _, _ -> context.openAccessRequirements() }
        .show()
}
