package com.artemfilatov.criminalintent

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.artemfilatov.criminalintent.databinding.ZoomPictureBinding

class ZoomInDialogFragment : DialogFragment() {
    private var _binding: ZoomPictureBinding? = null
    private val binding: ZoomPictureBinding
        get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ZoomPictureBinding.inflate(inflater, container, false)

        val photoFileName = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arguments?.getSerializable(
                "PHOTO_URI",
                String::class.java
            )
            else -> @Suppress("DEPRECATION") arguments?.getSerializable("PHOTO_URI") as? String
        }

        binding.zoomImage.setImageBitmap(
            BitmapFactory.decodeFile(requireContext().filesDir.path + "/" + photoFileName))

        return binding.root
    }

    companion object {
        fun newInstance(photoFileName: String): ZoomInDialogFragment {
            val frag = ZoomInDialogFragment()
            val args = Bundle()
            args.putSerializable("PHOTO_URI", photoFileName)
            frag.arguments = args
            return frag
        }
    }
}