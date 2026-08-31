package com.lagradost.cloudstream3.ui.livestreams

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentHomeBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.home.HomeChildItemAdapter
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.utils.AppContextUtils.ownHide
import com.lagradost.cloudstream3.utils.AppContextUtils.ownShow
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.ui.auth.ZetFlixAuthPrefs
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.showInputMethod
import kotlin.math.absoluteValue

class LiveStreamFragment : BaseFragment<FragmentHomeBinding>(
    BaseFragment.BindingCreator.Bind(FragmentHomeBinding::bind)
) {
    private val liveStreamViewModel: LiveStreamViewModel by viewModels()

    override fun pickLayout(): Int = R.layout.fragment_home

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padTop = false,
            padBottom = isLandscape(),
            padLeft = false
        )

        binding?.stickyHeader?.let {
            fixSystemBarsPadding(
                it,
                heightResId = R.dimen.home_header_height,
                padBottom = false,
                padLeft = false,
                padRight = false
            )
        }

        binding?.homeMasterRecycler?.let {
            fixSystemBarsPadding(
                it,
                padTop = false,
                padBottom = false,
                padLeft = false,
                padRight = false
            )
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindingCreated(binding: FragmentHomeBinding) {
        context?.let { HomeChildItemAdapter.updatePosterSize(it) }
        
        val adapter = LiveParentItemAdapterPreview(liveStreamViewModel)
        
        binding.apply {
            homeMasterRecycler.adapter = adapter
            homeMasterRecycler.setRecycledViewPool(ParentItemAdapter.sharedPool)
            
            // Dynamic header behavior matching HomeFragment
            stickyHeader.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            homeHeaderScrim.isVisible = true
            stickyHeader.elevation = 0f
            
            // Show Logo and Avatar as default AI/UI
            homeStickyLogo.isVisible = true
            homeAvatar.isVisible = true
            homeStickyTitle.isGone = true
            
            // Adjust shimmer for livestream
            homeLoadingShimmerBanner.isVisible = true // Show banner shimmer for consistency
            root.findViewById<View>(R.id.home_loading_shimmer_line)?.isVisible = true
            
            homeMasterRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val offset = recyclerView.computeVerticalScrollOffset()
                    val alpha = (offset / 200f).coerceIn(0f, 1f)
                    val context = context ?: return
                    
                    val color = context.colorFromAttribute(R.attr.primaryBlackBackground)
                    val alphaInt = (alpha * 255).toInt() 
                    stickyHeader.setBackgroundColor(
                        androidx.core.graphics.ColorUtils.setAlphaComponent(color, alphaInt)
                    )
                    
                    homeHeaderScrim.alpha = 1f - alpha
                    stickyHeader.elevation = if (alpha > 0.1f) 4f else 0f
                }
            })

            homeReloadConnectionerror.setOnClickListener {
                liveStreamViewModel.load(true)
            }
            
            homeAvatar.setOnClickListener {
                activity?.navigate(R.id.navigation_account)
            }
            
            // Initial state while loading
            homeLoadingShimmer.startShimmer()
            homeLoadingShimmer.isVisible = true
            homeLoading.isVisible = true
            homeLoadingError.isGone = true
            homeMasterRecycler.isGone = true
        }
        
        observe(liveStreamViewModel.filteredPage) { data ->
            binding.apply {
                // Ensure header elements stay hidden/shown correctly based on search state
                val isSearchGone = homeSearchBar.isGone
                homeStickyLogo.isVisible = isSearchGone
                homeAvatar.isVisible = isSearchGone
                homeSearchIcon.isVisible = isSearchGone
                homeStickyTitle.isGone = true // Always hide title in favor of Logo

                when (data) {
                    is Resource.Success -> {
                        val d = data.value
                        adapter.submitList(d.values.map {
                            it.copy(
                                list = it.list.copy(list = it.list.list.toMutableList())
                            )
                        })

                        homeLoading.isGone = true
                        homeLoadingShimmer.stopShimmer()
                        homeLoadingError.isGone = true
                        homeMasterRecycler.isVisible = true
                    }
                    is Resource.Loading -> {
                        homeLoadingShimmer.startShimmer()
                        homeLoadingShimmer.isVisible = true
                        homeLoading.isVisible = true
                        homeMasterRecycler.isGone = true
                        homeLoadingError.isGone = true
                    }
                    is Resource.Failure -> {
                        homeLoadingShimmer.stopShimmer()
                        homeLoading.isGone = true
                        homeLoadingError.isVisible = true
                        resultErrorText.text = data.errorString
                    }
                }
            }
        }
        
        binding.homeSearchIcon.setOnClickListener {
            binding.homeStickyLogo.isGone = true
            binding.homeHeaderSpacer.isGone = true
            binding.homeSearchIcon.isGone = true
            binding.homeAvatar.isGone = true
            
            binding.homeSearchBar.isVisible = true
            binding.homeSearchEdittext.requestFocus()
            showInputMethod(binding.homeSearchEdittext)
        }

        binding.homeSearchClose.setOnClickListener {
            binding.homeSearchEdittext.text = null
            binding.homeSearchBar.isGone = true
            hideKeyboard()
            
            binding.homeStickyLogo.isVisible = true
            binding.homeHeaderSpacer.isVisible = true
            binding.homeSearchIcon.isVisible = true
            binding.homeAvatar.isVisible = true
        }

        binding.homeSearchEdittext.addTextChangedListener { text ->
            liveStreamViewModel.search(text?.toString() ?: "")
        }

        loadAvatar(binding)
        MainActivity.reloadAccountEvent += ::reloadAvatarObserver

        liveStreamViewModel.load(false)
    }

    private fun reloadAvatarObserver(reload: Boolean) {
        loadAvatar(binding ?: return)
    }

    override fun onDestroyView() {
        MainActivity.reloadAccountEvent -= ::reloadAvatarObserver
        super.onDestroyView()
    }

    private fun loadAvatar(binding: FragmentHomeBinding) {
        val context = context ?: return
        ioSafe {
            try {
                val email = ZetFlixAuthPrefs.getStoredEmail(context) ?: ""
                val userId = if (email.isNotEmpty()) email.substringBefore("@") else ""
                val account = DataStoreHelper.getCurrentAccount() ?: DataStoreHelper.getDefaultAccount(context)

                main {
                    val avatarView = binding.homeAvatar
                    if (account.customImage != null) {
                        avatarView.loadImage(account.image)
                        avatarView.background = null
                    } else {
                        val backgrounds = listOf(
                            R.drawable.profile_bg_blue,
                            R.drawable.profile_bg_dark_blue,
                            R.drawable.profile_bg_orange,
                            R.drawable.profile_bg_pink,
                            R.drawable.profile_bg_purple,
                            R.drawable.profile_bg_red,
                            R.drawable.profile_bg_teal
                        )
                        val bgIndex = if (userId.isNotEmpty()) userId.hashCode().absoluteValue % backgrounds.size else 0
                        avatarView.setBackgroundResource(backgrounds[bgIndex])
                        avatarView.setImageResource(R.drawable.ic_outline_account_circle_24)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
