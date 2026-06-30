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
import com.flymeauto.phonewidget.databinding.ActivityWidgetConfigBinding

class WidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedPhone: String = ""
    private var selectedName: String = ""
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
            Toast.makeText(this, R.string.contacts_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    private val pickContact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            return@registerForActivityResult
        }
        val data = result.data ?: return@registerForActivityResult
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
        selectedName = config.contactName
        selectedPhone = config.phoneNumber
        selectedIconType = config.iconType
        selectedCustomIconUri = config.customIconUri?.let(Uri::parse)

        binding.contactNameText.text = config.displayName
        binding.contactPhoneText.text = config.phoneNumber
        binding.contactPhoneText.visibility =
            if (config.phoneNumber.isBlank()) View.GONE else View.VISIBLE
        binding.confirmCallSwitch.isChecked = config.confirmCall
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

    private fun setupListeners() {
        binding.pickContactButton.setOnClickListener { ensureContactsPermissionAndPick() }
        binding.pickCustomIconButton.setOnClickListener {
            pickCustomIcon.launch(arrayOf("image/*"))
        }
        binding.saveButton.setOnClickListener { saveWidget() }
        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun ensureContactsPermissionAndPick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openContactPicker()
        } else {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun openContactPicker() {
        val intent = Intent(
            Intent.ACTION_PICK,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        )
        if (intent.resolveActivity(packageManager) == null) {
            val fallback = Intent(Intent.ACTION_PICK).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
            }
            pickContact.launch(fallback)
            return
        }
        pickContact.launch(intent)
    }

    private fun parseContactResult(data: Intent) {
        val uri = data.data
        if (uri == null) {
            Toast.makeText(this, R.string.contact_pick_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val contact = ContactPickerHelper.parsePickedContact(this, uri)
        if (contact == null) {
            Toast.makeText(this, R.string.contact_pick_failed, Toast.LENGTH_SHORT).show()
            return
        }

        selectedName = contact.name
        selectedPhone = contact.phoneNumber
        binding.contactNameText.text = contact.name.ifBlank { contact.phoneNumber }
        binding.contactPhoneText.text = contact.phoneNumber
        binding.contactPhoneText.visibility = View.VISIBLE
    }

    private fun saveWidget() {
        if (selectedPhone.isBlank()) {
            Toast.makeText(this, R.string.select_contact_first, Toast.LENGTH_SHORT).show()
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
            contactName = selectedName,
            phoneNumber = selectedPhone,
            iconType = selectedIconType,
            customIconUri = customIconUriString,
            confirmCall = binding.confirmCallSwitch.isChecked
        )
        WidgetPreferences.save(this, appWidgetId, config)

        val manager = AppWidgetManager.getInstance(this)
        PhoneWidgetProvider.updateWidget(this, manager, appWidgetId)

        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }
}
