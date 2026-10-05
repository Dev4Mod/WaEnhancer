package com.wmods.wppenhacer.preference

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import com.wmods.wppenhacer.R
import kotlin.math.roundToInt

class FloatSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.seekBarPreferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes),
    Slider.OnChangeListener,
    Slider.OnSliderTouchListener {

    private var minValue = 0f
    private var maxValue = 1f
    private var valueSpacing = 0.1f
    private var format = "%3.1f"
    private var seekbar: Slider? = null
    private var textView: TextView? = null
    private var defaultValue = 0f
    private var newValue = 0f

    init {
        layoutResource = R.layout.preference_feature_seekbar
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.FloatSeekBarPreference,
            defStyleAttr,
            defStyleRes
        )
        minValue = typedArray.getFloat(R.styleable.FloatSeekBarPreference_minValue, 0f)
        maxValue = typedArray.getFloat(R.styleable.FloatSeekBarPreference_maxValue, 1f)
        valueSpacing = typedArray.getFloat(R.styleable.FloatSeekBarPreference_valueSpacing, 0.1f)
        format = typedArray.getString(R.styleable.FloatSeekBarPreference_format) ?: "%3.1f"
        typedArray.recycle()
    }

    override fun onGetDefaultValue(typedArray: TypedArray, index: Int): Any =
        typedArray.getFloat(index, 0f).also { defaultValue = it }

    override fun onSetInitialValue(defaultValue: Any?) {
        newValue = getPersistedFloat(defaultValue as? Float ?: this.defaultValue)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.isClickable = false

        val slider = holder.findViewById(R.id.seekbar) as Slider
        val valueView = holder.findViewById(R.id.seekbar_value) as TextView
        seekbar = slider
        textView = valueView

        slider.removeOnChangeListener(this)
        slider.clearOnSliderTouchListeners()
        slider.valueFrom = minValue
        slider.valueTo = maxValue
        slider.stepSize = valueSpacing
        slider.value = clampValue(newValue)
        slider.isEnabled = isEnabled
        slider.addOnChangeListener(this)
        slider.addOnSliderTouchListener(this)

        valueView.text = String.format(format, slider.value)
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (fromUser) textView?.text = String.format(format, value)
    }

    override fun onStartTrackingTouch(slider: Slider) = Unit

    override fun onStopTrackingTouch(slider: Slider) {
        newValue = slider.value
        persistFloat(newValue)
    }

    fun getValue(): Float = seekbar?.value ?: newValue

    fun setValue(value: Float) {
        newValue = clampValue(value)
        persistFloat(newValue)
        seekbar?.value = newValue
        notifyChanged()
    }

    private fun clampValue(value: Float): Float {
        val clamped = value.coerceIn(minValue, maxValue)
        if (valueSpacing <= 0f) return clamped
        val steps = ((clamped - minValue) / valueSpacing).roundToInt()
        return (minValue + steps * valueSpacing).coerceIn(minValue, maxValue)
    }
}
