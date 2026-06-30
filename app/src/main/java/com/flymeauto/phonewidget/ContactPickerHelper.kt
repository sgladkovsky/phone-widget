package com.flymeauto.phonewidget

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

data class PickedContact(
    val name: String,
    val phoneNumber: String
)

object ContactPickerHelper {

    fun parsePickedContact(context: Context, uri: Uri): PickedContact? {
        parseAsPhoneRow(context, uri)?.let { return it }
        val contactId = resolveContactId(context, uri) ?: return null
        return queryPhoneByContactId(context, contactId)
    }

    private fun parseAsPhoneRow(context: Context, uri: Uri): PickedContact? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex < 0) {
                    return@use null
                }
                val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                val number = cursor.getString(numberIndex).orEmpty()
                if (number.isBlank()) null else PickedContact(name, number)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveContactId(context: Context, uri: Uri): Long? {
        val segments = uri.pathSegments
        if (segments.isNotEmpty()) {
            when (segments[0]) {
                "contacts" -> {
                    if (segments.size >= 2 && segments[1] != "lookup") {
                        segments[1].toLongOrNull()?.let { return it }
                    }
                }
                "data" -> {
                    context.contentResolver.query(
                        uri,
                        arrayOf(ContactsContract.Data.CONTACT_ID),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                            if (index >= 0) {
                                return cursor.getLong(index)
                            }
                        }
                    }
                }
            }
        }

        val contactUri = try {
            ContactsContract.Contacts.lookupContact(context.contentResolver, uri)
        } catch (_: Exception) {
            null
        } ?: return null
        return contactUri.lastPathSegment?.toLongOrNull()
    }

    private fun queryPhoneByContactId(context: Context, contactId: Long): PickedContact? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                arrayOf(contactId.toString()),
                sortOrder
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex < 0) {
                    return@use null
                }
                val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                val number = cursor.getString(numberIndex).orEmpty()
                if (number.isBlank()) null else PickedContact(name, number)
            }
        } catch (_: Exception) {
            null
        }
    }
}
