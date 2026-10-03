package com.wmods.wppenhacer.adapter

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils

class CustomPrivacyAdapter(
    context: Context,
    private val prefs: SharedPreferences,
    private val items: MutableList<Item>,
    private val contactClass: Class<*>,
    private val groupClass: Class<*>
) : ArrayAdapter<CustomPrivacyAdapter.Item>(context, 0) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = items[position]
        val holder: ViewHolder
        val row: View
        if (convertView == null) {
            holder = ViewHolder()
            row = createLayout(holder)
            row.tag = holder
        } else {
            row = convertView
            holder = row.tag as ViewHolder
        }

        holder.textView.text = item.name
        row.setOnClickListener {
            if (item.number.length > 15) {
                val intent = Intent(context, groupClass).putExtra("gid", item.number + "@g.us")
                context.startActivity(intent)
            } else {
                val intent =
                    Intent(context, contactClass).putExtra("jid", item.number + "@s.whatsapp.net")
                context.startActivity(intent)
            }
        }
        holder.button.setOnClickListener {
            items.removeAt(position)
            prefs.edit { remove(item.key) }
            notifyDataSetChanged()
        }
        return row
    }

    override fun getCount(): Int = items.size

    private fun createLayout(holder: ViewHolder): ViewGroup {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Utils.dipToPixels(25), 0, Utils.dipToPixels(25), 0)
        }

        holder.textView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f }
        }
        layout.addView(holder.textView)

        holder.button = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                Utils.dipToPixels(40),
                Utils.dipToPixels(40)
            )
            val drawable = DesignUtils.createDrawable("stroke_border", Color.BLACK)
            background = DesignUtils.alphaDrawable(drawable, DesignUtils.getPrimaryTextColor(), 25)
            text = "X"
        }
        layout.addView(holder.button)
        return layout
    }

    private class ViewHolder {
        lateinit var textView: TextView
        lateinit var button: Button
    }

    class Item {
        @JvmField
        var name: String = ""
        @JvmField
        var number: String = ""
        @JvmField
        var key: String = ""
    }
}
