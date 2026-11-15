package com.wmods.wppenhacer.xposed.features.media

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.utils.Utils

class StatusDownloadProgress(
    private val activity: Activity?,
    private val transfer: StatusMediaDownloader.Transfer,
    private val title: String
) : StatusMediaDownloader.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var fileText: TextView? = null
    private var fileBar: ProgressBar? = null

    @Volatile
    private var lastPercent = -1

    fun show() {
        val host = activity ?: return
        handler.post {
            if (host.isFinishing || host.isDestroyed) return@post
            val padding = Utils.dipToPixels(20f)
            val root = LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, 0)
            }
            fileText = TextView(host).apply { visibility = View.GONE }.also(root::addView)
            fileBar = ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                visibility = View.GONE
            }.also(root::addView)
            dialog = AlertDialogWpp(host)
                .setTitle(title)
                .setView(root)
                .setNegativeButton(
                    Utils.getString(R.string.cancel),
                    DialogInterface.OnClickListener { _, _ -> transfer.cancel() }
                )
                .create()
                .apply {
                    setCancelable(false)
                    setCanceledOnTouchOutside(false)
                    show()
                }
        }
    }

    override fun onDownloadProgress(read: Long, total: Long) {
        val percent = if (total > 0) (read * 100 / total).toInt() else -1
        if (percent == lastPercent && total > 0) return
        lastPercent = percent
        handler.post {
            val host = activity ?: return@post
            val label = Utils.getString(R.string.downloading).trim()
            val readSize = Formatter.formatShortFileSize(host, read)
            fileText?.apply {
                text = if (total > 0) {
                    val totalSize = Formatter.formatShortFileSize(host, total)
                    "$label $percent%\n$readSize / $totalSize"
                } else {
                    "$label\n$readSize"
                }
                visibility = View.VISIBLE
            }
            fileBar?.apply {
                isIndeterminate = total <= 0
                if (total > 0) progress = percent
                visibility = View.VISIBLE
            }
        }
    }

    override fun onDecrypting() {
        handler.post {
            fileText?.apply {
                text = Utils.getString(R.string.decrypting)
                visibility = View.VISIBLE
            }
            fileBar?.apply {
                isIndeterminate = true
                visibility = View.VISIBLE
            }
        }
    }

    fun dismiss() {
        handler.post {
            runCatching { dialog?.dismiss() }
            dialog = null
        }
    }
}
