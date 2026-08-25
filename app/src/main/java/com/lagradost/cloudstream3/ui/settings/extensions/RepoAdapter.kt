package com.lagradost.cloudstream3.ui.settings.extensions

import android.view.LayoutInflater
import android.view.ViewGroup
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.RepositoryItemBinding
import com.lagradost.cloudstream3.plugins.RepositoryManager.PREBUILT_REPOSITORIES
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.getImageFromDrawable
import com.lagradost.cloudstream3.utils.txt

class RepoAdapter(
    val isSetup: Boolean,
    val clickCallback: RepoAdapter.(RepositoryData) -> Unit,
    val imageClickCallback: RepoAdapter.(RepositoryData) -> Unit,
) :
    NoStateAdapter<RepositoryData>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
        a.url == b.url
    })) {

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val layout = RepositoryItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolderState(layout)
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        when (val binding = holder.view) {
            is RepositoryItemBinding -> clearImage(binding.entryIcon)
        }
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: RepositoryData, position: Int) {
        val isPrebuilt = PREBUILT_REPOSITORIES.contains(item)
        val drawable =
            if (isSetup) R.drawable.netflix_download else R.drawable.ic_baseline_delete_outline_24
        when (val binding = holder.view) {
            is RepositoryItemBinding -> {
                binding.apply {
                    if (!isPrebuilt || isSetup) {
                        actionButton.setImageResource(drawable)
                    }

                    actionButton.setOnClickListener {
                        imageClickCallback(item)
                    }

                    repositoryItemRoot.setOnClickListener {
                        clickCallback(item)
                    }

                    repositoryItemRoot.setOnLongClickListener {
                        val shareableRepoData =
                            "${item.name}$SHAREABLE_REPO_SEPARATOR\n ${item.url}"
                        clipboardHelper(txt(R.string.repo_copy_label), shareableRepoData)
                        true
                    }

                    mainText.text = item.name
                    subText.text = item.url
                    if (!item.iconUrl.isNullOrEmpty()) {
                        entryIcon.loadImage(item.iconUrl) {
                            error(
                                getImageFromDrawable(
                                    binding.root.context,
                                    R.drawable.ic_github_logo
                                )
                            )
                        }
                    } else {
                        entryIcon.loadImage(R.drawable.ic_github_logo)
                    }
                }
            }
        }
    }

    companion object {
        const val SHAREABLE_REPO_SEPARATOR = " : "
    }
}
