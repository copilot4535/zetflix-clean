package com.lagradost.cloudstream3.ui.download

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentChildDownloadsBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

class DownloadChildFragment :
    BaseFragment<FragmentChildDownloadsBinding>(BindingCreator.Inflate(FragmentChildDownloadsBinding::inflate)) {
    private val downloadViewModel: DownloadViewModel by activityViewModels()

    companion object {
        fun newInstance(name: String, folder: String): Bundle {
            return Bundle().apply {
                putString("name", name)
                putString("folder", folder)
            }
        }
    }

    override fun onBindingCreated(binding: FragmentChildDownloadsBinding, savedInstanceState: Bundle?) {
        val adapter = DownloadAdapter(
            { },
            { click ->
                if (click.action == DOWNLOAD_ACTION_DELETE_FILE) {
                    context?.let { ctx ->
                        downloadViewModel.handleSingleDelete(ctx, click.data.id)
                    }
                } else handleDownloadClick(click)
            },
            { itemId, isChecked ->
                if (isChecked) {
                    downloadViewModel.addSelected(itemId)
                } else downloadViewModel.removeSelected(itemId)
            }
        )

        observe(downloadViewModel.childCards) { cards ->
            when (cards) {
                is Resource.Success -> {
                    val d = cards.value
                    adapter.submitList(d as List<VisualDownloadCached>)
                }
                else -> {}
            }
        }

        binding.apply {
            downloadChildList.adapter = adapter
            downloadChildList.setLinearListLayout(
                isHorizontal = false,
                nextRight = FOCUS_SELF,
            )
            downloadChildToolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
            downloadChildToolbar.setNavigationOnClickListener {
                dispatchBackPressed()
            }

            btnCancel.setOnClickListener {
                downloadViewModel.cancelSelection()
            }

            btnDelete.setOnClickListener {
                downloadViewModel.handleMultiDelete(requireContext())
            }

            btnToggleAll.setOnClickListener {
                val allSelected = downloadViewModel.isAllChildrenSelected()
                if (allSelected) {
                    downloadViewModel.clearSelectedItems()
                } else {
                    downloadViewModel.selectAllChildren()
                }
            }
        }

        observeNullable(downloadViewModel.selectedItemIds) { selection ->
            val isMultiDeleteState = selection != null
            adapter.setIsMultiDeleteState(isMultiDeleteState)
            binding.downloadDeleteAppbar.isVisible = isMultiDeleteState
            binding.downloadChildToolbar.isGone = isMultiDeleteState

            if (selection != null) {
                binding.btnDelete.isVisible = selection.isNotEmpty()
                binding.selectItemsText.isVisible = selection.isEmpty()

                val allSelected = downloadViewModel.isAllChildrenSelected()
                if (allSelected) {
                    binding.btnToggleAll.setText(R.string.deselect_all)
                } else binding.btnToggleAll.setText(R.string.select_all)
            }
        }

        val folder = arguments?.getString("folder")
        if (folder != null) {
            downloadViewModel.updateChildList(requireContext(), folder)
        }
    }

    override fun onResume() {
        super.onResume()
        afterPluginsLoadedEvent += ::onAllPluginsLoaded
    }

    override fun onStop() {
        super.onStop()
        afterPluginsLoadedEvent -= ::onAllPluginsLoaded
    }

    private fun onAllPluginsLoaded(success: Boolean) {
        val folder = arguments?.getString("folder")
        if (folder != null) {
            downloadViewModel.updateChildList(requireContext(), folder)
        }
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape()
        )
    }
}
