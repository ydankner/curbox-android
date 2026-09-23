package neth.iecal.curbox.ui.fragments.main.reducers.accessRequirements

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.data.models.AccessRequirement
import neth.iecal.curbox.databinding.FragmentAccessRequirementsBinding
import neth.iecal.curbox.databinding.ItemAccessRequirementBinding
import neth.iecal.curbox.ui.activity.FragmentActivity
import neth.iecal.curbox.utils.DataStoreManager

class AccessRequirementsFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "access_requirements"
    }

    private var _binding: FragmentAccessRequirementsBinding? = null
    private val binding get() = _binding!!
    private val adapter = RequirementAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccessRequirementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvRequirements.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRequirements.adapter = adapter
        binding.fabAddRequirement.setOnClickListener { openEditor(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            DataStoreManager(requireContext()).settingsForEditing.collectLatest { settings ->
                adapter.submit(settings.accessRequirements)
                binding.tvEmpty.isVisible = settings.accessRequirements.isEmpty()
            }
        }
    }

    private fun openEditor(requirementId: String?) {
        startActivity(
            Intent(requireContext(), FragmentActivity::class.java)
                .putExtra("fragment", AccessRequirementEditorFragment.FRAGMENT_ID)
                .putExtra(AccessRequirementEditorFragment.EXTRA_REQUIREMENT_ID, requirementId)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class RequirementAdapter : RecyclerView.Adapter<RequirementAdapter.Holder>() {
        private var items: List<AccessRequirement> = emptyList()

        fun submit(requirements: List<AccessRequirement>) {
            items = requirements
            notifyDataSetChanged()
        }

        inner class Holder(val binding: ItemAccessRequirementBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemAccessRequirementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val requirement = items[position]
            val context = holder.binding.root.context
            holder.binding.tvRequirementName.text = requirement.name
            holder.binding.tvRequirementSummary.text = context.requirementSummary(requirement)
            holder.binding.root.setOnClickListener { openEditor(requirement.id) }
        }
    }
}
