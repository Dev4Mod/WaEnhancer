package com.wmods.wppenhacer.activities.syntax;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;

import androidx.core.content.ContextCompat;

import com.amrdeveloper.codeview.CodeView;
import com.wmods.wppenhacer.R;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CSSLanguage {

    private static final Pattern PATTERN_SELECTORS = Pattern.compile("(?!#?[0-9a-fA-F]{3,6})([.#]?[a-zA-Z_][a-zA-Z0-9_-]*)");
    private static final Pattern PATTERN_NTH_CHILD = Pattern.compile(":nth-child\\([^\\)]*\\)");
    private static final Pattern PATTERN_CSS_PROPERTIES = Pattern.compile("\\b(color-tint|color-filter|color|background-image|background-size|background|margin|padding|font-size|border|display|position|top|bottom|left|right|width|height|background-color|border-radius)\\b");
    private static final Pattern PATTERN_HEX = Pattern.compile("#[a-fA-F0-9]{3,6}");
    private static final Pattern PATTERN_UNITS = Pattern.compile("[0-9]+\\.?[0-9]*(em|px|%)");
    private static final Pattern PATTERN_IMPORTANTS = Pattern.compile("!(important)");
    private static final Pattern PATTERN_PROPERTIES = Pattern.compile("[{}]");
    private static final Pattern PATTERN_STRINGS = Pattern.compile("\"[^\"]*\"");


    public static void applyCSSHighlighting(Context context, CodeView codeView) {
        codeView.resetSyntaxPatternList();
        codeView.resetHighlighter();
        // View Background
        codeView.setBackgroundColor(ContextCompat.getColor(context, R.color.codeview_background));

        // Syntax Colors
        var mapSyntaxColor = new HashMap<Pattern, Integer>();
        mapSyntaxColor.put(PATTERN_SELECTORS, ContextCompat.getColor(context, R.color.codeview_selector));
        mapSyntaxColor.put(PATTERN_PROPERTIES, ContextCompat.getColor(context, R.color.codeview_properties));
        mapSyntaxColor.put(PATTERN_CSS_PROPERTIES, ContextCompat.getColor(context, R.color.codeview_css_properties));
        mapSyntaxColor.put(PATTERN_STRINGS, ContextCompat.getColor(context, R.color.codeview_strings));
        mapSyntaxColor.put(PATTERN_NTH_CHILD, ContextCompat.getColor(context, R.color.codeview_nth_child));
        mapSyntaxColor.put(PATTERN_UNITS, ContextCompat.getColor(context, R.color.codeview_units));
        mapSyntaxColor.put(PATTERN_IMPORTANTS, ContextCompat.getColor(context, R.color.codeview_important));
        codeView.setSyntaxPatternsMap(mapSyntaxColor);
        codeView.addPairCompleteItem('{', '}');
        codeView.addPairCompleteItem('"', '"');
        codeView.enablePairComplete(true);
        codeView.enablePairCompleteCenterCursor(true);
        // Default Color
        codeView.setTextColor(ContextCompat.getColor(context, R.color.codeview_text));
        codeView.reHighlightSyntax();
        applyColorHighlight(codeView.getText());

        codeView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // N/A
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // N/A
            }

            @Override
            public void afterTextChanged(Editable editable) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> applyColorHighlight(editable), 1000);
            }
        });
    }

    private static void applyColorHighlight(Editable editable) {
        // Padrão para detectar valores hexadecimais (#rrggbb)
        Matcher matcher = PATTERN_HEX.matcher(editable);
        while (matcher.find()) {
            String colorText = matcher.group();
            try {
                int color = Color.parseColor(colorText);
                editable.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), 0);
            } catch (IllegalArgumentException e) {
                // Ignorar cores inválidas
            }
        }
    }
}