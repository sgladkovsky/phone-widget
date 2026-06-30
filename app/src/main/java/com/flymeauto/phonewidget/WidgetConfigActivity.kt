package com.flymeauto.phonewidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.flymeauto.phonewidget.databinding.ActivityWidgetConfigBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WidgetConfigActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WidgetConfigActivity"
    }

    private lateinit var binding: ActivityWidgetConfigBinding
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedIconType: IconType = IconType.PHONE
    private var selectedCustomIconUri: Uri? = null

    private val requestCallPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.call_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openContactPicker()
        } else {
            showContactsPermissionSettingsDialog()
        }
    }

    private val pickCustomIcon = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedCustomIconUri = uri
            selectedIconType = IconType.CUSTOM
            binding.iconPreview.setImageURI(uri)
            binding.iconSpinner.setSelection(iconSpinnerIndex(IconType.CUSTOM))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED)
        setupIconSpinner()
        setupTransparencySlider()
        loadExistingConfig()
        setupListeners()
    }

    private fun setupIconSpinner() {
        val labels = IconType.entries.map { iconLabel(it) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.iconSpinner.adapter = adapter
        binding.iconSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val iconType = IconType.entries[position]
                if (iconType == IconType.CUSTOM) {
                    if (selectedCustomIconUri == null) {
                        pickCustomIcon.launch(arrayOf("image/*"))
                    } else {
                        binding.iconPreview.setImageURI(selectedCustomIconUri)
                    }
                } else {
                    selectedIconType = iconType
                    binding.iconPreview.setImageResource(WidgetIconMapper.drawableRes(iconType))
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun iconLabel(iconType: IconType): String = when (iconType) {
        IconType.PHONE -> getString(R.string.icon_phone)
        IconType.PERSON -> getString(R.string.icon_person)
        IconType.HOME -> getString(R.string.icon_home)
        IconType.CAR -> getString(R.string.icon_car)
        IconType.WORK -> getString(R.string.icon_work)
        IconType.STAR -> getString(R.string.icon_star)
        IconType.HEART -> getString(R.string.icon_heart)
        IconType.CUSTOM -> getString(R.string.icon_custom)
    }

    private fun iconSpinnerIndex(iconType: IconType): Int =
        IconType.entries.indexOf(iconType).coerceAtLeast(0)

    private fun loadExistingConfig() {
        val config = WidgetPreferences.load(this, appWidgetId) ?: return
        selectedIconType = config.iconType
        selectedCustomIconUri = config.customIconUri?.let(Uri::parse)

        binding.phoneInput.setText(config.phoneNumber)
        binding.contactNameInput.setText(config.contactName)
        binding.confirmCallSwitch.isChecked = config.confirmCall
        binding.transparencySlider.value = config.transparencyPercent.toFloat()
        updateTransparencyPreview(config.transparencyPercent)
        binding.iconSpinner.setSelection(iconSpinnerIndex(config.iconType))

        if (config.iconType == IconType.CUSTOM) {
            val previewUri = WidgetIconStorage.iconUri(this, appWidgetId) ?: selectedCustomIconUri
            if (previewUri != null) {
                binding.iconPreview.setImageURI(previewUri)
            }
        } else {
            binding.iconPreview.setImageResource(WidgetIconMapper.drawableRes(config.iconType))
        }
    }

    private fun setupTransparencySlider() {
        binding.transparencySlider.value = WidgetAppearance.DEFAULT_TRANSPARENCY_PERCENT.toFloat()
        updateTransparencyPreview(WidgetAppearance.DEFAULT_TRANSPARENCY_PERCENT)
        binding.transparencySlider.addOnChangeListener { _, value, _ ->
            updateTransparencyPreview(value.toInt())
        }
    }

    private fun updateTransparencyPreview(transparencyPercent: Int) {
        binding.transparencyValue.text = getString(
            R.string.transparency_value,
            transparencyPercent
        )
        binding.iconPreviewBackground.setBackgroundColor(
            WidgetAppearance.backgroundColor(transparencyPercent)
        )
    }

    private fun setupListeners() {
        Log.d(TAG, "setupListeners: start")

        binding.phoneInputLayout.editText?.doAfterTextChanged {
            binding.phoneInputLayout.error = null
        } ?: Log.w(TAG, "setupListeners: phone input is null")

        binding.pickCustomIconButton.setOnClickListener {
            pickCustomIcon.launch(arrayOf("image/*"))
        }

        binding.saveButton.setOnClickListener { saveWidget() }
        binding.cancelButton.setOnClickListener { finish() }
        binding.pickContactButton.setOnClickListener(::onPickContactClicked)

        Log.d(TAG, "setupListeners: done")
    }

    private fun onPickContactClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        Log.d(TAG, "onPickContactClicked")
        if (hasContactsPermission()) {
            openContactPicker()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            showContactsPermissionSettingsDialog()
        } else {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun showContactsPermissionSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.contacts_permission_title)
            .setMessage(R.string.contacts_permission_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun openContactPicker() {
        Log.d(TAG, "openContactPicker: showing in-app contact list")
        ContactPickerDialog(this) { contact ->
            applySelectedContact(contact)
        }.show()
    }

    private fun applySelectedContact(contact: PickedContact) {
        binding.phoneInput.setText(contact.phoneNumber)
        binding.contactNameInput.setText(contact.name)
        binding.phoneInputLayout.error = null
        Log.d(TAG, "applySelectedContact: phone=${contact.phoneNumber}, name=${contact.name}")
    }

    private fun saveWidget() {
        val phoneNumber = binding.phoneInput.text?.toString()?.trim().orEmpty()
        if (phoneNumber.isBlank()) {
            binding.phoneInputLayout.error = getString(R.string.enter_phone_first)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
            && !binding.confirmCallSwitch.isChecked
        ) {
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
        }

        var customIconUriString: String? = null
        if (selectedIconType == IconType.CUSTOM && selectedCustomIconUri != null) {
            customIconUriString = WidgetIconStorage.saveIconFromUri(
                this,
                appWidgetId,
                selectedCustomIconUri!!
            )
        }

        val config = WidgetConfig(
            contactName = binding.contactNameInput.text?.toString()?.trim().orEmpty(),
            phoneNumber = phoneNumber,
            iconType = selectedIconType,
            customIconUri = customIconUriString,
            confirmCall = binding.confirmCallSwitch.isChecked,
            transparencyPercent = binding.transparencySlider.value.toInt()
        )
        WidgetPreferences.save(this, appWidgetId, config)

        val manager = AppWidgetManager.getInstance(this)
        PhoneWidgetProvider.updateWidget(this, manager, appWidgetId)

        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }
}
