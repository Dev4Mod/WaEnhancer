package com.wmods.wppenhacer.preference;

import android.content.Context;
import android.text.InputFilter;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.preference.EditTextPreference;

import com.wmods.wppenhacer.R;

public class LimitedEditTextPreference extends EditTextPreference {

    private static final int DEFAULT_MAX_LENGTH = 10;  // Limite padrão de caracteres
    private int maxLength;

    public LimitedEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public LimitedEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        if (attrs != null) {
            var a = getContext().obtainStyledAttributes(attrs, com.wmods.wppenhacer.R.styleable.LimitedEditTextPreference);
            maxLength = a.getInt(R.styleable.LimitedEditTextPreference_maxLength, DEFAULT_MAX_LENGTH);
            a.recycle();
        } else {
            maxLength = DEFAULT_MAX_LENGTH;
        }

        setOnBindEditTextListener(this::setMaxLength);
    }

    private void setMaxLength(EditText editText) {
        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(maxLength);
        editText.setFilters(filters);
    }
}