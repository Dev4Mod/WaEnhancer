package com.wmods.wppenhacer.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.databinding.ItemLogLineBinding
import com.wmods.wppenhacer.utils.RootDiagnostics

class LogLineAdapter : RecyclerView.Adapter<LogLineAdapter.ViewHolder>() {

    private val items = mutableListOf<RootDiagnostics.LogEntry>()

    fun add(entry: RootDiagnostics.LogEntry) {
        items.add(entry)
        notifyItemInserted(items.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLogLineBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val binding: ItemLogLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: RootDiagnostics.LogEntry) {
            binding.logText.text = entry.message
            binding.logText.setTextColor(resolveColor(entry.type))
            binding.root.setOnLongClickListener {
                copyToClipboard(entry.message)
                true
            }
            runEnterAnimation(binding.root)
        }

        private fun copyToClipboard(text: String) {
            val context = binding.root.context
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    context.getString(R.string.diag_dialog_title),
                    text
                )
            )
            Toast.makeText(context, R.string.diag_copied, Toast.LENGTH_SHORT).show()
        }

        private fun resolveColor(type: RootDiagnostics.LogType): Int {
            val context = binding.root.context
            return when (type) {
                RootDiagnostics.LogType.SUCCESS -> context.getColor(R.color.log_success)
                RootDiagnostics.LogType.WARNING -> context.getColor(R.color.log_warning)
                RootDiagnostics.LogType.ERROR -> context.getColor(R.color.log_error)
                else -> context.getColor(R.color.log_info)
            }
        }

        private fun runEnterAnimation(view: View) {
            val slide = TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0.3f,
                Animation.RELATIVE_TO_SELF, 0f
            )
            val fade = AlphaAnimation(0f, 1f)

            slide.duration = 250
            fade.duration = 250

            val set = android.view.animation.AnimationSet(false)
            set.addAnimation(slide)
            set.addAnimation(fade)
            view.startAnimation(set)
        }
    }
}
