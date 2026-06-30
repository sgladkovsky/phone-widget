package com.flymeauto.phonewidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.flymeauto.phonewidget.databinding.ActivityWidgetConfigBinding

class WidgetConfigActivity : AppCompatActivity() {

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

    private val pickContact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK && !hasContactResult(data)) {
            return@registerForActivityResult
        }
        parseContactResult(data)
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
        updateContactPickerVisibility()
    }

    override fun onResume() {
        super.onResume()
        updateContactPickerVisibility()
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
        binding.pickContactButton.setOnClickListener { openContactPicker() }
        binding.phoneInput.doAfterTextChanged {
            binding.phoneInputLayout.error = null
        }
        binding.pickCustomIconButton.setOnClickListener {
            pickCustomIcon.launch(arrayOf("image/*"))
        }
        binding.saveButton.setOnClickListener { saveWidget() }
        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun updateContactPickerVisibility() {
        binding.pickContactButton.isVisible = hasContactsPermission()
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun openContactPicker() {
        val intents = listOf(
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
            Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI),
            Intent(Intent.ACTION_PICK).apply {
                type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
            },
            Intent(Intent.ACTION_PICK).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
            }
        )
        val intent = intents.firstOrNull { it.resolveActivity(packageManager) != null }
            ?: intents.first()
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        pickContact.launch(intent)
    }

    private fun hasContactResult(data: Intent): Boolean {
        if (data.data != null) {
            return true
        }
        if (data.clipData != null && data.clipData!!.itemCount > 0) {
            return true
        }
        return ContactPickerHelper.hasContactData(data)
    }

    private fun parseContactResult(data: Intent) {
        val contact = ContactPickerHelper.parseContactFromIntent(this, data)
        if (contact == null) {
            Toast.makeText(this, R.string.contact_pick_failed, Toast.LENGTH_SHORT).show()
            return
        }

        binding.phoneInput.setText(contact.phoneNumber)
        binding.contactNameInput.setText(contact.name)
        binding.phoneInputLayout.error = null
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
