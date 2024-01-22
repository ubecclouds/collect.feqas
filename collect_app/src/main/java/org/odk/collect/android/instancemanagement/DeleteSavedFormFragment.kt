package org.odk.collect.android.instancemanagement

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.odk.collect.android.R
import org.odk.collect.android.databinding.DeleteBlankFormLayoutBinding
import org.odk.collect.android.formlists.sorting.FormListSortingBottomSheetDialog
import org.odk.collect.android.formmanagement.InstancesDataService
import org.odk.collect.androidshared.ui.FragmentFactoryBuilder
import org.odk.collect.androidshared.ui.multiselect.MultiSelectAdapter
import org.odk.collect.androidshared.ui.multiselect.MultiSelectControlsFragment
import org.odk.collect.androidshared.ui.multiselect.MultiSelectItem
import org.odk.collect.androidshared.ui.multiselect.MultiSelectViewModel
import org.odk.collect.async.Scheduler
import org.odk.collect.forms.instances.Instance
import org.odk.collect.strings.R.string

class DeleteSavedFormFragment(
    private val viewModelFactory: ViewModelProvider.Factory,
    private val menuHost: MenuHost
) : Fragment() {

    private val savedFormListViewModel: SavedFormListViewModel by viewModels { viewModelFactory }
    private lateinit var multiSelectViewModel: MultiSelectViewModel<Instance>

    override fun onAttach(context: Context) {
        super.onAttach(context)

        multiSelectViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    return MultiSelectViewModel(
                        savedFormListViewModel.formsToDisplay.map {
                            it.map { instance -> MultiSelectItem(instance.dbId, instance) }
                        }
                    ) as T
                }
            }
        )[MultiSelectViewModel::class.java] as MultiSelectViewModel<Instance>

        childFragmentManager.fragmentFactory = FragmentFactoryBuilder()
            .forClass(MultiSelectControlsFragment::class) {
                MultiSelectControlsFragment(
                    getString(string.delete_file),
                    multiSelectViewModel
                )
            }
            .build()

        childFragmentManager.setFragmentResultListener(
            MultiSelectControlsFragment.REQUEST_ACTION,
            this
        ) { _, result ->
            val selected = result.getLongArray(MultiSelectControlsFragment.RESULT_SELECTED)!!
            onDeleteSelected(selected)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.delete_blank_form_layout,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = DeleteBlankFormLayoutBinding.bind(view)
        val recyclerView = binding.list
        val adapter = MultiSelectAdapter(multiSelectViewModel) { parent ->
            InstanceItemViewHolder(parent.context)
        }

        recyclerView.adapter = adapter

        multiSelectViewModel.getData().observe(viewLifecycleOwner) {
            adapter.data = it
        }

        menuHost.addMenuProvider(
            InstanceListMenuProvider(requireContext()),
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    private fun onDeleteSelected(selected: LongArray) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(string.delete_file)
            .setMessage(
                getString(
                    string.delete_confirm,
                    selected.size.toString()
                )
            )
            .setPositiveButton(getString(string.delete_yes)) { _, _ ->
                savedFormListViewModel.deleteForms(selected)
            }
            .setNegativeButton(getString(string.delete_no), null)
            .show()
    }
}

private class InstanceItemView(context: Context) : FrameLayout(context) {

    val textView = TextView(context).also { addView(it) }
    val checkBox = CheckBox(context).also { addView(it) }
}

private class InstanceItemViewHolder(context: Context) :
    MultiSelectAdapter.ViewHolder<Instance>(InstanceItemView(context)) {

    val view = itemView as InstanceItemView

    override fun setItem(item: Instance) {
        view.textView.text = item.displayName
    }

    override fun getCheckbox(): CheckBox {
        return view.checkBox
    }
}

private class InstanceListMenuProvider(private val context: Context) : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.form_list_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        FormListSortingBottomSheetDialog(
            context,
            emptyList(),
            0
        ) {}.show()
        return true
    }
}

class SavedFormListViewModel(private val scheduler: Scheduler, private val instancesDataService: InstancesDataService) : ViewModel() {

    val formsToDisplay: LiveData<List<Instance>> = instancesDataService.instances

    fun deleteForms(databaseIds: LongArray) {
        scheduler.immediate(background = true) {
            databaseIds.forEach { instancesDataService.deleteInstance(it) }
        }
    }
}
