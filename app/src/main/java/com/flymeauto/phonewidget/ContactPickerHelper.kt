package com.flymeauto.phonewidget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

data class PickedContact(
    val name: String,
    val phoneNumber: String
)

object ContactPickerHelper {

    private const val TAG = "ContactPicker"

    fun logPickerResult(resultCode: Int, intent: Intent) {
        Log.d(TAG, "========== Выбор контакта ==========")
        Log.d(TAG, "resultCode: $resultCode")
        Log.d(TAG, "action: ${intent.action}")
        Log.d(TAG, "data URI: ${intent.data}")
        intent.extras?.let { bundle ->
            if (bundle.isEmpty) {
                Log.d(TAG, "extras: (пусто)")
            } else {
                for (key in bundle.keySet()) {
                    Log.d(TAG, "extra[$key] = ${bundle.get(key)}")
                }
            }
        } ?: Log.d(TAG, "extras: null")
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                val item = clip.getItemAt(index)
                Log.d(TAG, "clipData[$index] uri=${item.uri} text=${item.text}")
            }
        } ?: Log.d(TAG, "clipData: null")
        collectUris(intent).forEachIndexed { index, uri ->
            Log.d(TAG, "collectedUri[$index]: $uri")
        }
    }

    fun logPickedContact(contact: PickedContact?) {
        if (contact == null) {
            Log.w(TAG, "Распознанный контакт: не удалось получить")
        } else {
            Log.d(TAG, "Распознанный контакт:")
            Log.d(TAG, "  name: ${contact.name}")
            Log.d(TAG, "  phoneNumber: ${contact.phoneNumber}")
        }
        Log.d(TAG, "====================================")
    }

    fun hasContactData(intent: Intent): Boolean {
        if (intent.data != null) {
            return true
        }
        if (intent.clipData != null && intent.clipData!!.itemCount > 0) {
            return true
        }
        return parseFromExtras(intent) != null
    }

    fun parseContactFromIntent(context: Context, intent: Intent): PickedContact? {
        parseFromExtras(intent)?.let {
            Log.d(TAG, "Источник данных: extras")
            return it
        }

        for (uri in collectUris(intent)) {
            parsePickedContact(context, uri)?.let {
                Log.d(TAG, "Источник данных: URI $uri")
                return it
            }
        }
        return null
    }

    fun parsePickedContact(context: Context, uri: Uri): PickedContact? {
        if (uri.scheme == "tel") {
            val number = Uri.decode(uri.schemeSpecificPart)?.trim().orEmpty()
            if (number.isNotBlank()) {
                return PickedContact("", number)
            }
        }

        parseAsPhoneRow(context, uri)?.let { return it }
        parseAsDataRow(context, uri)?.let { return it }
        parseAsContactRow(context, uri)?.let { return it }

        val contactId = resolveContactId(context, uri) ?: return null
        return queryPhoneByContactId(context, contactId)
    }

    private fun parseFromExtras(intent: Intent): PickedContact? {
        val phoneKeys = listOf(
            ContactsContract.Intents.Insert.PHONE,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            "phone",
            "phoneNumber",
            "phone_number",
            "android.intent.extra.PHONE_NUMBER"
        )
        val nameKeys = listOf(
            ContactsContract.Intents.Insert.NAME,
            ContactsContract.Contacts.DISPLAY_NAME,
            "name",
            "display_name",
            "contact_name"
        )

        var phone: String? = null
        var name: String? = null

        intent.extras?.let { bundle ->
            for (key in phoneKeys) {
                phone = bundle.getString(key)?.trim()?.takeIf { it.isNotEmpty() }
                if (phone != null) break
            }
            for (key in nameKeys) {
                name = bundle.getString(key)?.trim()?.takeIf { it.isNotEmpty() }
                if (name != null) break
            }
        }

        if (!phone.isNullOrBlank()) {
            return PickedContact(name.orEmpty(), phone!!)
        }
        return null
    }

    private fun collectUris(intent: Intent): List<Uri> {
        val uris = linkedSetOf<Uri>()
        intent.data?.let { uris.add(it) }

        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let { uris.add(it) }
            }
        }

        val extraKeys = listOf(
            "android.provider.extra.PICKED_URI",
            ContactsContract.Intents.Insert.DATA,
            Intent.EXTRA_STREAM
        )
        for (key in extraKeys) {
            intent.getParcelableExtra<Uri>(key)?.let { uris.add(it) }
        }
        return uris.toList()
    }

    private fun parseAsPhoneRow(context: Context, uri: Uri): PickedContact? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return queryContact(context, uri, projection) { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (numberIndex < 0) {
                return@queryContact null
            }
            val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
            val number = cursor.getString(numberIndex).orEmpty()
            contactFromNumber(name, number)
        }
    }

    private fun parseAsDataRow(context: Context, uri: Uri): PickedContact? {
        val projection = arrayOf(
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return queryContact(context, uri, projection) { cursor ->
            val mimeIndex = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""

            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val dataIndex = cursor.getColumnIndex(ContactsContract.Data.DATA1)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)

            val number = when {
                numberIndex >= 0 && !cursor.isNull(numberIndex) -> cursor.getString(numberIndex)
                mimeType == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE &&
                    dataIndex >= 0 -> cursor.getString(dataIndex)
                dataIndex >= 0 && mimeType.contains("phone", ignoreCase = true) -> cursor.getString(dataIndex)
                else -> null
            }.orEmpty()

            val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
            contactFromNumber(name, number)
        }
    }

    private fun parseAsContactRow(context: Context, uri: Uri): PickedContact? {
        val projection = arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts._ID
        )
        return queryContact(context, uri, projection) { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            if (idIndex < 0) {
                return@queryContact null
            }
            val contactId = cursor.getLong(idIndex)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
            queryPhoneByContactId(context, contactId)?.let { phoneContact ->
                PickedContact(
                    name = name.ifBlank { phoneContact.name },
                    phoneNumber = phoneContact.phoneNumber
                )
            }
        }
    }

    private fun resolveContactId(context: Context, uri: Uri): Long? {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Data.CONTACT_ID
        )
        queryContact(context, uri, projection) { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            if (idIndex >= 0 && !cursor.isNull(idIndex)) {
                return@queryContact cursor.getLong(idIndex)
            }
            val contactIdIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            if (contactIdIndex >= 0 && !cursor.isNull(contactIdIndex)) {
                return@queryContact cursor.getLong(contactIdIndex)
            }
            null
        }?.let { return it }

        val segments = uri.pathSegments
        if (segments.size >= 2 && segments[0] == "contacts" && segments[1] != "lookup") {
            segments[1].toLongOrNull()?.let { return it }
        }

        val contactUri = try {
            ContactsContract.Contacts.lookupContact(context.contentResolver, uri)
        } catch (_: Exception) {
            null
        } ?: return null

        queryContact(
            context,
            contactUri,
            arrayOf(ContactsContract.Contacts._ID)
        ) { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            if (idIndex >= 0 && !cursor.isNull(idIndex)) cursor.getLong(idIndex) else null
        }?.let { return it }

        return contactUri.lastPathSegment?.toLongOrNull()
    }

    private fun queryPhoneByContactId(context: Context, contactId: Long): PickedContact? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
        return queryContact(
            context,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf(contactId.toString()),
            sortOrder
        ) { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (numberIndex < 0) {
                return@queryContact null
            }
            val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
            val number = cursor.getString(numberIndex).orEmpty()
            contactFromNumber(name, number)
        }
    }

    private inline fun <T> queryContact(
        context: Context,
        uri: Uri,
        projection: Array<String>,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
        block: (android.database.Cursor) -> T?
    ): T? {
        return try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                block(cursor)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun contactFromNumber(name: String, number: String): PickedContact? {
        val normalized = number.trim()
        if (normalized.isBlank()) {
            return null
        }
        return PickedContact(name.trim(), normalized)
    }
}
