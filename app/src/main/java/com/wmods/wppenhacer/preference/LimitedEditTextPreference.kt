package com.wmods.wppenhacer.preference

import android.content.Context
import android.text.InputFilter
import android.util.AttributeSet
import android.widget.EditText
import androidx.preference.EditTextPreference
import com.wmods.wppenhacer.R

class LimitedEditTextPreference : EditTextPreference {
    private var maxLength = DEFAULT_MAX_LENGTH

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        maxLength = if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.LimitedEditTextPreference)
            typedArray.getInt(R.styleable.LimitedEditTextPreference_maxLength, DEFAULT_MAX_LENGTH)
                .also { typedArray.recycle() }
        } else {
            DEFAULT_MAX_LENGTH
        }
        setOnBindEditTextListener(::setMaxLength)
    }

    private fun setMaxLength(editText: EditText) {
        editText.filters = arrayOf(InputFilter.LengthFilter(maxLength))
    }

    private companion object {
        const val DEFAULT_MAX_LENGTH = 10
    }
}
