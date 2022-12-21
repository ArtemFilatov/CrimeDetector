package com.artemfilatov.criminalintent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.doOnLayout
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.artemfilatov.criminalintent.databinding.FragmentCrimeDetailBinding
import com.artemfilatov.criminalintent.model.Crime
import com.artemfilatov.criminalintent.utils.getScaledBitmap
import com.artemfilatov.criminalintent.viewmodel.CrimeDetailViewModel
import com.artemfilatov.criminalintent.viewmodel.CrimeDetailViewModelFactory
import kotlinx.coroutines.launch
import java.io.File
import java.util.*


private const val DATE_FORMAT = "EEE, d MMMM yyyy HH:mm"

class CrimeDetailFragment : Fragment() {
    private var _binding: FragmentCrimeDetailBinding? = null
    private val binding
        get() = checkNotNull(_binding) {
            "Cannot access binding because it is null. Is the view visible?"
        }

    private val args: CrimeDetailFragmentArgs by navArgs()

    private val crimeDetailViewModel: CrimeDetailViewModel by viewModels {
        CrimeDetailViewModelFactory(args.crimeId)
    }

    private val selectSuspect = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let {
            parseContactSelection(it)
        }
    }

    private val takePhoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { didTakePhoto ->
        if (didTakePhoto && photoName != null) {
            crimeDetailViewModel.updateCrime { oldCrime ->
                oldCrime.copy(photoFileName = photoName)
            }
        } else {
            Toast.makeText(requireContext(), "Crash", Toast.LENGTH_LONG).show()
        }
    }

    private var photoName: String? = null

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {
                selectSuspect.launch(null)
            } else {
                Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            onBackButtonPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCrimeDetailBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            crimeTitle.doOnTextChanged { text, _, _, _ ->
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(title = text.toString())
                }
            }

            crimeSolved.setOnCheckedChangeListener { _, isChecked ->
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(isSolved = isChecked)
                }
            }

            crimeSuspect.setOnClickListener {
                activityResultLauncher.launch(Manifest.permission.READ_CONTACTS)
            }

            val selectSuspectIntent = selectSuspect.contract.createIntent(
                requireContext(),
                null
            )
            crimeSuspect.isEnabled = canResolveIntent(selectSuspectIntent)

            callSuspend.setOnClickListener {
                if (crimeDetailViewModel.crime.value?.phone?.isNotBlank() == true) {
                    selectSuspect.launch(null)
                    val callContactIntent =
                        Intent(Intent.ACTION_DIAL).apply {

                            val phone = crimeDetailViewModel.crime.value?.phone
                            data = Uri.parse("tel:$phone")

                        }
                    // this intent will call the phone number given in Uri.parse("tel:$phone")
                    startActivity(callContactIntent)
                } else {
                    Toast.makeText(requireContext(), "Choose suspect first", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            crimeCamera.setOnClickListener {
                photoName = "IMG_${Date()}.JPG"
                val photoFile = File(requireContext().applicationContext.filesDir, photoName)
                val photoUri = FileProvider.getUriForFile(
                    requireContext(),
                    "com.artemfilatov.criminalintent.fileprovider",
                    photoFile
                )
                takePhoto.launch(photoUri)

                val captureImageIntent = takePhoto.contract.createIntent(
                    requireContext(),
                    photoUri
                )
                crimeCamera.isEnabled = canResolveIntent(captureImageIntent)
            }
        }

        showMenu()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                crimeDetailViewModel.crime.collect { crime ->
                    crime?.let { updateUi(crime) }
                }
            }
        }

        setFragmentResultListener(
            DatePickerFragment.REQUEST_KEY_DATE
        ) { _, bundle ->
            val newDate =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bundle.getSerializable(DatePickerFragment.BUNDLE_KEY_DATE, Date::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    bundle.getSerializable(DatePickerFragment.BUNDLE_KEY_DATE) as Date
                }
            crimeDetailViewModel.updateCrime { it.copy(date = newDate!!) }
        }
    }

    private fun updateUi(crime: Crime) {
        binding.apply {
            if (crimeTitle.text.toString() != crime.title) {
                crimeTitle.setText(crime.title)
            }

            crimeDate.text = DateFormat.format(DATE_FORMAT, crime.date)
            crimeDate.setOnClickListener {
                findNavController().navigate(
                    CrimeDetailFragmentDirections.selectDate(crime.date)
                )
            }
            crimeSolved.isChecked = crime.isSolved
            crimeSolved.contentDescription =
                if (crime.isSolved) getString(R.string.crime_solved) else getString(R.string.not_solved)

            crimeReport.setOnClickListener {
                val reportIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getCrimeReport(crime))
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crime_report_subject))
                }
                val chooserIntent =
                    Intent.createChooser(reportIntent, getString(R.string.send_report))
                startActivity(chooserIntent)
            }

            crimeSuspect.text = crime.suspect.ifEmpty {
                getString(R.string.crime_suspect_text)
            }

            updatePhoto(crime.photoFileName)

            crimePhoto.setOnClickListener {
                if (crime.photoFileName != null) {
                    val zoomDialog = ZoomInDialogFragment.newInstance(crime.photoFileName)
                    zoomDialog.show(parentFragmentManager, null)
                }
            }
        }
    }

    private fun showMenu() {
        (requireActivity() as MenuHost).addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.fragment_crime_detail, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.delete_crime -> {
                        deleteCrime()
                        true
                    }
                    else -> false
                }

            }
        }, viewLifecycleOwner)
    }

    private fun deleteCrime() {
        viewLifecycleOwner.lifecycleScope.launch {
            val crimeToDelete = crimeDetailViewModel.crime.value
            crimeDetailViewModel.deleteCrime(crimeToDelete!!)
            findNavController().navigate(
                CrimeDetailFragmentDirections.showCrimeList()
            )
        }
    }

    private fun getCrimeReport(crime: Crime): String {
        val solvedString = if (crime.isSolved) {
            getString(R.string.crime_report_solved)
        } else {
            getString(R.string.crime_report_unsolved)
        }

        val dateString = DateFormat.format(DATE_FORMAT, crime.date).toString()
        val suspectText = if (crime.suspect.isBlank()) {
            getString(R.string.crime_report_no_suspect)
        } else {
            getString(R.string.crime_report_suspect, crime.suspect)
        }

        return getString(
            R.string.crime_report,
            crime.title, dateString, solvedString, suspectText
        )
    }

    private fun parseContactSelection(contactUri: Uri) {
        val queryFields = arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts._ID
        )

        val queryCursor = requireActivity().contentResolver
            .query(
                contactUri,
                queryFields,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
        queryCursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val suspect =
                    cursor.getString(0)

                val contactId =
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))

                // This is the Uri to get a Phone number
                val phoneURI = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

                // phoneNumberQueryFields: a List to return the PhoneNumber Column Only

                val phoneNumberQueryFields = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)

                // phoneWhereClause: A filter declaring which rows to return, formatted as an SQL WHERE clause (excluding the WHERE itself)
                val phoneWhereClause = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"

                // This val replace the question mark in the phoneWhereClause  val
                val phoneQueryParameters = arrayOf(contactId)

                val phoneCursor = requireActivity().contentResolver
                    .query(
                        phoneURI,
                        phoneNumberQueryFields,
                        phoneWhereClause,
                        phoneQueryParameters,
                        null
                    )

                phoneCursor?.use { cursorPhone ->
                    if (cursorPhone.moveToFirst()) {
                        val phoneNumValue = cursorPhone.getString(0)
                        crimeDetailViewModel.updateCrime { oldCrime ->
                            oldCrime.copy(suspect = suspect, phone = phoneNumValue)
                        }
                    } else {
                        crimeDetailViewModel.updateCrime { oldCrime ->
                            oldCrime.copy(suspect = "", phone = "")
                        }
                        Toast.makeText(
                            requireContext(),
                            "Incorrect Phone Number",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun canResolveIntent(intent: Intent): Boolean {
        val packageManager: PackageManager = requireActivity().packageManager
        val resolveActivity: ResolveInfo? =
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

        return resolveActivity != null
    }

    private fun updatePhoto(photoFileName: String?) {
        if (binding.crimePhoto.tag != photoFileName) {
            val photoFile = photoFileName?.let {
                File(requireContext().applicationContext.filesDir, it)
            }
            if (photoFile?.exists() == true) {
                binding.crimePhoto.doOnLayout { measuredView ->
                    val scaledBitmap = getScaledBitmap(
                        photoFile.path,
                        measuredView.width,
                        measuredView.height
                    )
                    binding.crimePhoto.setImageBitmap(scaledBitmap)
                    binding.crimePhoto.tag = photoFileName
                    binding.crimePhoto.contentDescription =
                        getString(R.string.crime_photo_image_description)
                }
            }
        } else {
            binding.crimePhoto.setImageBitmap(null)
            binding.crimePhoto.tag = null
            binding.crimePhoto.contentDescription =
                getString(R.string.crime_photo_no_image_description)
        }
    }

    private fun onBackButtonPressed() {
        if (binding.crimeTitle.text.isBlank()) {
            Toast.makeText(
                requireContext(),
                "The title of the crime can't blank!",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            findNavController().navigate(
                CrimeDetailFragmentDirections.showCrimeList()
            )
            onBackPressedCallback.isEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
