package com.flymeauto.phonewidget

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flymeauto.phonewidget.databinding.DialogContactPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class ContactPickerDialog(
    private val activity: AppCompatActivity,
    private val onContactPicked: (PickedContact) -> Unit
) {

    companion object {
        private const val TAG = "ContactPickerDialog"
    }

    fun show() {
        val contacts = ContactRepository.loadPhoneContacts(activity)
        val dialog = BottomSheetDialog(activity)
        val binding = DialogContactPickerBinding.inflate(activity.layoutInflater)
        dialog.setContentView(binding.root)

        val hasContacts = contacts.isNotEmpty()
        binding.contactSearchLayout.isVisible = hasContacts

        val adapter = ContactAdapter(contacts) { contact ->
            Log.d(TAG, "Contact selected: name=${contact.name}, phone=${contact.phoneNumber}")
            ContactPickerHelper.logPickedContact(contact)
            onContactPicked(contact)
            dialog.dismiss()
        }

        if (hasContacts) {
            binding.contactsList.layoutManager = LinearLayoutManager(activity)
            binding.contactsList.adapter = adapter

            binding.contactSearchInput.doAfterTextChanged { text ->
                val query = text?.toString().orEmpty()
                adapter.filter(query)
                updateEmptyState(binding, contacts.isEmpty(), adapter.itemCount == 0, query.isNotBlank())
            }
        }

        updateEmptyState(binding, !hasContacts, false, false)

        binding.dialogCancelButton.setOnClickListener {
            Log.d(TAG, "Contact picker canceled")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateEmptyState(
        binding: DialogContactPickerBinding,
        noContactsAtAll: Boolean,
        noSearchResults: Boolean,
        isSearching: Boolean
    ) {
        binding.contactsList.isVisible = !noContactsAtAll && !noSearchResults
        binding.contactsEmpty.isVisible = noContactsAtAll || noSearchResults
        binding.contactsEmpty.text = when {
            noContactsAtAll -> activity.getString(R.string.contacts_empty)
            isSearching && noSearchResults -> activity.getString(R.string.contacts_not_found)
            else -> activity.getString(R.string.contacts_empty)
        }
    }

    private class ContactAdapter(
        private val allContacts: List<PickedContact>,
        private val onClick: (PickedContact) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

        private var filteredContacts: List<PickedContact> = allContacts

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.contact_item_name)
            val phone: TextView = view.findViewById(R.id.contact_item_phone)
        }

        fun filter(query: String) {
            val trimmed = query.trim()
            filteredContacts = if (trimmed.isEmpty()) {
                allContacts
            } else {
                val digitsQuery = trimmed.filter { it.isDigit() }
                allContacts.filter { contact -> matchesQuery(contact, trimmed, digitsQuery) }
            }
            notifyDataSetChanged()
        }

        private fun matchesQuery(
            contact: PickedContact,
            query: String,
            digitsQuery: String
        ): Boolean {
            if (contact.name.contains(query, ignoreCase = true)) {
                return true
            }
            if (contact.phoneNumber.contains(query, ignoreCase = true)) {
                return true
            }
            if (digitsQuery.isNotEmpty()) {
                val phoneDigits = contact.phoneNumber.filter { it.isDigit() }
                if (phoneDigits.contains(digitsQuery)) {
                    return true
                }
            }
            return false
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contact, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contact = filteredContacts[position]
            holder.name.text = contact.name.ifBlank { contact.phoneNumber }
            holder.phone.text = contact.phoneNumber
            holder.itemView.setOnClickListener { onClick(contact) }
        }

        override fun getItemCount(): Int = filteredContacts.size
    }
}
