package com.wmods.wppenhacer.views.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.utils.Utils;

public class BottomDialogWpp {

    private final Dialog dialog;

    public BottomDialogWpp(@NonNull Dialog dialog) {
        this.dialog = dialog;
    }

    public void dismissDialog() {
        dialog.dismiss();
    }

    public void showDialog() {
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(null);
            dialog.getWindow().setDimAmount(0);
            var view = dialog.getWindow().getDecorView();
            view.findViewById(Utils.getID("design_bottom_sheet", "id")).setBackgroundColor(Color.TRANSPARENT);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
    }

    public void setContentView(View view) {
        dialog.setContentView(view);
    }


    public void setCanceledOnTouchOutside(boolean b) {
        dialog.setCanceledOnTouchOutside(b);
    }
}
