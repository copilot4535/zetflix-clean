package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.databinding.BottomSheetQueueBinding
import java.util.Collections

@UnstableApi
class QueueBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetQueueBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var queueAdapter: MusicSearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeQueue()
    }

    private fun setupRecyclerView() {
        queueAdapter = MusicSearchAdapter({ index ->
            // Jump to song in queue
            (activity as? MusicActivity)?.getMediaControllerMedia3()?.seekTo(index, 0)
            dismiss()
        }, { _, _ -> })

        binding.rvQueueTracks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = queueAdapter
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                
                // Move in MediaController
                (activity as? MusicActivity)?.getMediaControllerMedia3()?.moveMediaItem(fromPos, toPos)
                
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                // Remove from MediaController
                (activity as? MusicActivity)?.getMediaControllerMedia3()?.removeMediaItem(pos)
            }
        })

        itemTouchHelper.attachToRecyclerView(binding.rvQueueTracks)
    }

    private fun observeQueue() {
        // Sync with MediaController queue
        val controller = (activity as? MusicActivity)?.getMediaControllerMedia3()
        controller?.let { c ->
            val items = mutableListOf<MusicSearchResponse>()
            for (i in 0 until c.mediaItemCount) {
                val item = c.getMediaItemAt(i)
                items.add(
                    MusicSearchResponse(
                        item.mediaMetadata.title.toString(),
                        item.mediaMetadata.artist.toString(),
                        item.mediaId,
                        item.mediaMetadata.artworkUri?.toString()
                    )
                )
            }
            queueAdapter.submitList(items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
