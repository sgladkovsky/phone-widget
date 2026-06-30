package com.flymeauto.phonewidget

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

object ContactRepository {

    private const val TAG = "ContactRepository"

    fun loadPhoneContacts(context: Context): List<PickedContact> {
        val contacts = linkedMapOf<String, PickedContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex < 0) {
                    return emptyList()
                }
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex)?.trim().orEmpty()
                    if (number.isBlank()) {
                        continue
                    }
                    val name = if (nameIndex >= 0) {
                        cursor.getString(nameIndex).orEmpty()
                    } else {
                        ""
                    }
                    contacts.putIfAbsent(number, PickedContact(name, number))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load contacts", e)
            return emptyList()
        }

        val result = contacts.values.toList()
        Log.d(TAG, "Loaded ${result.size} contacts")
        return result
    }
}
