package neth.iecal.curbox.ui.fragments.main.reducers.accessRequirements

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.AccessCondition
import neth.iecal.curbox.data.models.AccessRequirement
import neth.iecal.curbox.databinding.FragmentAccessRequirementEditorBinding
import neth.iecal.curbox.databinding.ItemAccessConditionBinding
import neth.iecal.curbox.utils.AnkiCardQueue
import neth.iecal.curbox.utils.DataStoreManager
import java.util.UUID

class AccessRequirementEditorFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "access_requirement_editor"
        const val EXTRA_REQUIREMENT_ID = "requirement_id"
        private const val DEFAULT_MINUTES = 5
        private const val MAX_MINUTES = 24 * 60
    }

    private var _binding: FragmentAccessRequirementEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var dataStore: DataStoreManager
    private var requirementId: String? = null
    private val conditions = mutableListOf<AccessCondition>()

    private val ankiPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(requireContext(), R.string.anki_permission_needed, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccessRequirementEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dataStore = DataStoreManager(requireContext())
        requirementId = requireActivity().intent.getStringExtra(EXTRA_REQUIREMENT_ID)

        requirementId?.let { id ->
            binding.tvTitle.setText(R.string.access_requirement_edit)
            binding.btnDeleteRequirement.visibility = View.VISIBLE
            viewLifecycleOwner.lifecycleScope.launch {
                val requirement = dataStore.settingsForEditing.first().accessRequirements
                    .find { it.id == id } ?: return@launch
                binding.etRequirementName.setText(requirement.name)
                binding.toggleMode.check(
                    if (requirement.isAllRequired) R.id.btn_mode_all else R.id.btn_mode_any
                )
                conditions.clear()
                conditions.addAll(requirement.conditions)
                renderConditions()
            }
        }

        renderConditions()
        binding.btnAddCondition.setOnClickListener { chooseConditionType() }
        binding.btnDeleteRequirement.setOnClickListener { deleteRequirement() }
        binding.fabSaveRequirement.setOnClickListener { saveRequirement() }
    }

    private fun chooseConditionType() {
        val options = arrayOf(
            getString(R.string.access_condition_type_anki),
            getString(R.string.access_condition_type_app)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.access_requirement_add_condition)
            .setItems(options) { _, which -> if (which == 0) addAnkiCondition() else pickApp() }
            .show()
    }

    private fun addAnkiCondition() {
        if (conditions.none { it.type == AccessCondition.TYPE_ANKI_CLEARED }) {
            conditions += AccessCondition(type = AccessCondition.TYPE_ANKI_CLEARED)
            renderConditions()
        }
        if (!AnkiCardQueue.hasPermission(requireContext())) {
            ankiPermissionLauncher.launch(AnkiCardQueue.PERMISSION)
        }
    }

    private fun pickApp() {
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = loadLaunchableApps()
            if (_binding == null) return@launch
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.access_condition_pick_app)
                .setItems(apps.map { it.second }.toTypedArray()) { _, which ->
                    askMinutes(apps[which].first)
                }
                .show()
        }
    }

    private fun askMinutes(packageName: String) {
        val context = requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(DEFAULT_MINUTES.toString())
            setSelection(text.length)
        }
        val padding = (24 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(context).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.access_condition_minutes)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val minutes = input.text.toString().toIntOrNull()?.coerceIn(1, MAX_MINUTES)
                    ?: DEFAULT_MINUTES
                conditions.removeAll {
                    it.type == AccessCondition.TYPE_APP_USAGE && it.packageName == packageName
                }
                conditions += AccessCondition(
                    type = AccessCondition.TYPE_APP_USAGE,
                    packageName = packageName,
                    minutes = minutes
                )
                renderConditions()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private suspend fun loadLaunchableApps(): List<Pair<String, String>> {
        val packageManager = requireContext().packageManager
        val ownPackage = requireContext().packageName
        return withContext(Dispatchers.IO) {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
                .filter { it.first != ownPackage }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    }

    private fun renderConditions() {
        val container = binding.conditionsContainer
        container.removeAllViews()
        conditions.forEachIndexed { index, condition ->
            val row = ItemAccessConditionBinding.inflate(layoutInflater, container, false)
            row.tvCondition.text = requireContext().conditionText(condition)
            row.btnRemoveCondition.setOnClickListener {
                conditions.removeAt(index)
                renderConditions()
            }
            container.addView(row.root)
        }
    }

    private fun saveRequirement() {
        val name = binding.etRequirementName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.etRequirementName.error = getString(R.string.access_requirement_name_required)
            return
        }
        if (conditions.isEmpty()) {
            Toast.makeText(requireContext(), R.string.access_requirement_needs_condition, Toast.LENGTH_SHORT).show()
            return
        }
        val requirement = AccessRequirement(
            id = requirementId ?: UUID.randomUUID().toString(),
            name = name,
            isAllRequired = binding.toggleMode.checkedButtonId == R.id.btn_mode_all,
            conditions = conditions.toList()
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val current = dataStore.settingsForEditing.first().accessRequirements
            val updated = if (current.any { it.id == requirement.id }) {
                current.map { if (it.id == requirement.id) requirement else it }
            } else {
                current + requirement
            }
            dataStore.updateAccessRequirements(updated)
            Toast.makeText(requireContext(), R.string.access_requirement_saved, Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }
    }

    private fun deleteRequirement() {
        val id = requirementId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = dataStore.settingsForEditing.first()
            val isInUse = settings.blockedAppGroups.any { it.accessRequirementId == id } ||
                settings.keywordBlockerConfig.keywordGroups.any { it.accessRequirementId == id }
            if (isInUse) {
                Toast.makeText(requireContext(), R.string.access_requirement_in_use, Toast.LENGTH_LONG).show()
                return@launch
            }
            dataStore.updateAccessRequirements(settings.accessRequirements.filterNot { it.id == id })
            Toast.makeText(requireContext(), R.string.access_requirement_deleted, Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
