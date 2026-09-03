package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.databinding.DialogMusicSleepTimerBinding

class SleepTimerBottomSheetFragment : BottomSheetDialogFragment() {
    private var _binding: DialogMusicSleepTimerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMusicSleepTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.timerOff.setOnClickListener {
            viewModel.startSleepTimer(0)
            dismiss()
        }

        binding.timer15.setOnClickListener {
            viewModel.startSleepTimer(15)
            dismiss()
        }

        binding.timer30.setOnClickListener {
            viewModel.startSleepTimer(30)
            dismiss()
        }

        binding.timer60.setOnClickListener {
            viewModel.startSleepTimer(60)
            dismiss()
        }

        binding.timerCustomSet.setOnClickListener {
            val minutes = binding.customTimerInput.text.toString().toIntOrNull()
            if (minutes != null && minutes > 0) {
                viewModel.startSleepTimer(minutes)
                dismiss()
            } else {
                Toast.makeText(context, "Please enter valid minutes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
