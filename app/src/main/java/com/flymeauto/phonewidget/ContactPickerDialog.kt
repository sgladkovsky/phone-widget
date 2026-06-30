package com.flymeauto.phonewidget

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
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

        binding.contactsEmpty.isVisible = contacts.isEmpty()
        binding.contactsList.isVisible = contacts.isNotEmpty()

        if (contacts.isNotEmpty()) {
            binding.contactsList.layoutManager = LinearLayoutManager(activity)
            binding.contactsList.adapter = ContactAdapter(contacts) { contact ->
                Log.d(TAG, "Contact selected: name=${contact.name}, phone=${contact.phoneNumber}")
                ContactPickerHelper.logPickedContact(contact)
                onContactPicked(contact)
                dialog.dismiss()
            }
        }

        binding.dialogCancelButton.setOnClickListener {
            Log.d(TAG, "Contact picker canceled")
            dialog.dismiss()
        }

        dialog.show()
    }

    private class ContactAdapter(
        private val contacts: List<PickedContact>,
        private val onClick: (PickedContact) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.contact_item_name)
            val phone: TextView = view.findViewById(R.id.contact_item_phone)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contact, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contact = contacts[position]
            holder.name.text = contact.name.ifBlank { contact.phoneNumber }
            holder.phone.text = contact.phoneNumber
            holder.itemView.setOnClickListener { onClick(contact) }
        }

        override fun getItemCount(): Int = contacts.size
    }
}
