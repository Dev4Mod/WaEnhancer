package com.wmods.wppenhacer.views.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import androidx.core.graphics.toColorInt

class SimpleColorPickerDialog(
    context: Context,
    private val listener: OnColorSelectedListener?
) : AlertDialogWpp(context) {
    private val dialogContext = context
    private var selectedColor = Color.BLACK
    private var isUpdating = false

    override fun create(): Dialog {
        setTitle(dialogContext.getString(R.string.select_a_color))

        val layout = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val redSeekBar = SeekBar(dialogContext).apply { max = 255 }
        val greenSeekBar = SeekBar(dialogContext).apply { max = 255 }
        val blueSeekBar = SeekBar(dialogContext).apply { max = 255 }
        layout.addView(createSeekBarLayout("Red", redSeekBar))
        layout.addView(createSeekBarLayout("Green", greenSeekBar))
        layout.addView(createSeekBarLayout("Blue", blueSeekBar))

        val colorPreview = View(dialogContext).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 200)
        }
        val borderDrawable = GradientDrawable().apply {
            setColor(selectedColor)
            setStroke(1, DesignUtils.getPrimaryTextColor())
        }
        colorPreview.background = borderDrawable
        layout.addView(colorPreview)

        val hexInput = EditText(dialogContext).apply { hint = "#000000" }
        layout.addView(hexInput)

        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!isUpdating) {
                    isUpdating = true
                    updateColorPreview(borderDrawable, redSeekBar, greenSeekBar, blueSeekBar, hexInput)
                    isUpdating = false
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        }
        redSeekBar.setOnSeekBarChangeListener(seekBarListener)
        greenSeekBar.setOnSeekBarChangeListener(seekBarListener)
        blueSeekBar.setOnSeekBarChangeListener(seekBarListener)

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isUpdating && s?.length == 7 && s[0] == '#') {
                    try {
                        isUpdating = true
                        selectedColor = s.toString().toColorInt()
                        borderDrawable.setColor(selectedColor)
                        redSeekBar.progress = Color.red(selectedColor)
                        greenSeekBar.progress = Color.green(selectedColor)
                        blueSeekBar.progress = Color.blue(selectedColor)
                        isUpdating = false
                    } catch (_: IllegalArgumentException) {
                        // Keep the previous color while the input is invalid.
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        setPositiveButton("OK") { _, _ ->
            listener?.onColorSelected(selectedColor)
            dismiss()
        }
        setNegativeButton(dialogContext.getString(R.string.cancel)) { _, _ -> dismiss() }
        setView(layout)
        return super.create()
    }

    private fun updateColorPreview(
        borderDrawable: GradientDrawable,
        redSeekBar: SeekBar,
        greenSeekBar: SeekBar,
        blueSeekBar: SeekBar,
        hexInput: EditText
    ) {
        val red = redSeekBar.progress
        val green = greenSeekBar.progress
        val blue = blueSeekBar.progress
        selectedColor = Color.rgb(red, green, blue)
        borderDrawable.setColor(selectedColor)
        hexInput.setText(String.format("#%02X%02X%02X", red, green, blue))
    }

    private fun createSeekBarLayout(label: String, seekBar: SeekBar): LinearLayout =
        LinearLayout(dialogContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 10, 10, 10)
            addView(TextView(dialogContext).apply {
                text = label
                setTextColor(DesignUtils.getPrimaryTextColor())
            })
            addView(seekBar)
        }

    fun interface OnColorSelectedListener {
        fun onColorSelected(color: Int)
    }
}
