package com.wmods.wppenhacer.preference

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.widget.EditText
import androidx.preference.EditTextPreference

/**
 * EditTextPreference that masks input like a password field
 */
class PasswordEditTextPreference : EditTextPreference {

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        // Show masked summary
        summaryProvider = SummaryProvider<PasswordEditTextPreference> { preference ->
            val text = preference.text
            if (text.isNullOrBlank()) {
                "Not set"
            } else {
                "••••••••"
            }
        }
    }
}
