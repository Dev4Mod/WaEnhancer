package com.wmods.wppenhacer.views.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;

public class SimpleColorPickerDialog extends AlertDialogWpp {

    private final OnColorSelectedListener listener;
    private int selectedColor = Color.BLACK;
    private boolean isUpdating = false;

    public SimpleColorPickerDialog(Context context, OnColorSelectedListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    public Dialog create() {

        setTitle(getContext().getString(ResId.string.select_a_color));

        // Configura o layout principal do diálogo
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        // Cria os SeekBars para ajustar as cores
        final SeekBar redSeekBar = new SeekBar(getContext());
        final SeekBar greenSeekBar = new SeekBar(getContext());
        final SeekBar blueSeekBar = new SeekBar(getContext());

        redSeekBar.setMax(255);
        greenSeekBar.setMax(255);
        blueSeekBar.setMax(255);

        // Adiciona os SeekBars ao layout
        layout.addView(createSeekBarLayout("Red", redSeekBar));
        layout.addView(createSeekBarLayout("Green", greenSeekBar));
        layout.addView(createSeekBarLayout("Blue", blueSeekBar));

        // Cria a visualização da cor selecionada
        final View colorPreview = new View(getContext());
        colorPreview.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 200));
        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setColor(selectedColor);
        borderDrawable.setStroke(1, DesignUtils.getPrimaryTextColor()); // Borda branca de 5 pixels

        colorPreview.setBackground(borderDrawable);
        layout.addView(colorPreview);

        // Cria o EditText para entrada de cor hexadecimal
        final EditText hexInput = new EditText(getContext());
        hexInput.setHint("#000000");
        layout.addView(hexInput);

        // Atualiza a visualização da cor conforme os SeekBars são ajustados
        redSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!isUpdating) {
                    isUpdating = true;
                    updateColorPreview(borderDrawable, redSeekBar, greenSeekBar, blueSeekBar, hexInput);
                    isUpdating = false;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        greenSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!isUpdating) {
                    isUpdating = true;
                    updateColorPreview(borderDrawable, redSeekBar, greenSeekBar, blueSeekBar, hexInput);
                    isUpdating = false;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        blueSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!isUpdating) {
                    isUpdating = true;
                    updateColorPreview(borderDrawable, redSeekBar, greenSeekBar, blueSeekBar, hexInput);
                    isUpdating = false;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Atualiza a cor conforme o valor hexadecimal é inserido
        hexInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isUpdating && s.length() == 7 && s.charAt(0) == '#') {
                    try {
                        isUpdating = true;
                        selectedColor = Color.parseColor(s.toString());
                        borderDrawable.setColor(selectedColor);
                        redSeekBar.setProgress(Color.red(selectedColor));
                        greenSeekBar.setProgress(Color.green(selectedColor));
                        blueSeekBar.setProgress(Color.blue(selectedColor));
                        isUpdating = false;
                    } catch (IllegalArgumentException e) {
                        // Cor inválida, não faz nada
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        setPositiveButton("OK", (dialogInterface, i) -> {
            if (listener != null) {
                listener.onColorSelected(selectedColor);
            }
            dismiss();
        });

        setNegativeButton(getContext().getString(ResId.string.cancel), (dialogInterface, i) -> {
            dismiss();
        });

        // Define o layout do diálogo
        setView(layout);

        return super.create();
    }

    private void updateColorPreview(GradientDrawable borderDrawable, SeekBar redSeekBar, SeekBar greenSeekBar, SeekBar blueSeekBar, EditText hexInput) {
        int red = redSeekBar.getProgress();
        int green = greenSeekBar.getProgress();
        int blue = blueSeekBar.getProgress();
        selectedColor = Color.rgb(red, green, blue);
        borderDrawable.setColor(selectedColor);
        hexInput.setText(String.format("#%02X%02X%02X", red, green, blue));
    }

    private LinearLayout createSeekBarLayout(String label, SeekBar seekBar) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(10, 10, 10, 10);

        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextColor(DesignUtils.getPrimaryTextColor());

        layout.addView(labelView);
        layout.addView(seekBar);

        return layout;
    }

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }
}
