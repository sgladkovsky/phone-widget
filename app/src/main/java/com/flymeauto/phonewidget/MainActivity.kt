package com.flymeauto.phonewidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flymeauto.phonewidget.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.widgetsList.layoutManager = LinearLayoutManager(this)
        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshWidgetList()
    }

    private fun ensurePermissions() {
        val missing = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.CALL_PHONE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.READ_CONTACTS)
        }
        if (missing.isNotEmpty()) {
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    private fun refreshWidgetList() {
        val manager = AppWidgetManager.getInstance(this)
        val component = ComponentName(this, PhoneWidgetProvider::class.java)
        val activeIds = manager.getAppWidgetIds(component).toSet()
        val savedIds = WidgetPreferences.getAllWidgetIds(this).filter { it in activeIds }

        if (savedIds.isEmpty()) {
            binding.widgetsList.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            return
        }

        binding.widgetsList.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.widgetsList.adapter = WidgetAdapter(savedIds) { widgetId ->
            openWidgetConfig(widgetId)
        }
    }

    private fun openWidgetConfig(widgetId: Int) {
        val intent = Intent(this, WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        startActivity(intent)
    }

    private class WidgetAdapter(
        private val widgetIds: List<Int>,
        private val onEdit: (Int) -> Unit
    ) : RecyclerView.Adapter<WidgetAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.widget_item_title)
            val subtitle: TextView = view.findViewById(R.id.widget_item_subtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_widget, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val widgetId = widgetIds[position]
            val config = WidgetPreferences.load(holder.itemView.context, widgetId)
            holder.title.text = config?.displayName
                ?: holder.itemView.context.getString(R.string.widget_not_configured)
            holder.subtitle.text = config?.phoneNumber.orEmpty()
            holder.itemView.setOnClickListener { onEdit(widgetId) }
        }

        override fun getItemCount(): Int = widgetIds.size
    }
}
