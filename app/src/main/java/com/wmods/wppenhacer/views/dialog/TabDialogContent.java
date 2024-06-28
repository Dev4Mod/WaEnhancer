package com.wmods.wppenhacer.views.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.utils.DesignUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

public class TabDialogContent extends LinearLayout {

    private TextView mTitle;
    private LinearLayout contentLinear;

    public TabDialogContent(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        var params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        setLayoutParams(params);
        setOrientation(VERTICAL);
        setBackground(DesignUtils.createDrawable("rc_dialog_bg", DesignUtils.getPrimarySurfaceColor()));
        setPadding(Utils.dipToPixels(16), Utils.dipToPixels(12), Utils.dipToPixels(16), Utils.dipToPixels(16));

        // linha inicial
        LinearLayout layoutLine = new LinearLayout(context);
        layoutLine.setOrientation(LinearLayout.HORIZONTAL);
        var layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        layoutParams.setMargins(0, Utils.dipToPixels(5), 0, Utils.dipToPixels(5));
        layoutLine.setLayoutParams(layoutParams);

        ImageView imageView = new ImageView(context);
        var paramsImageView = new LinearLayout.LayoutParams(Utils.dipToPixels(70), LinearLayout.LayoutParams.MATCH_PARENT);
        imageView.setLayoutParams(paramsImageView);
        imageView.setImageDrawable(DesignUtils.createDrawable("rc_dotline_dialog", DesignUtils.getPrimaryTextColor()));
        layoutLine.addView(imageView);
        addView(layoutLine);

        mTitle = new TextView(context);
        var paramsTitle = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsTitle.gravity = Gravity.CENTER;
        paramsTitle.setMargins(Utils.dipToPixels(6), 0, 0, 0);
        mTitle.setLayoutParams(paramsTitle);
        mTitle.setGravity(Gravity.CENTER);
        mTitle.setTextColor(DesignUtils.getPrimaryTextColor());
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mTitle.setTypeface(null, Typeface.BOLD);
        addView(mTitle);

        var view = new View(context);
        var paramsView = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Utils.dipToPixels(0.9f));
        paramsView.setMargins(0, Utils.dipToPixels(10), 0, 0);
        view.setLayoutParams(paramsView);
        view.setBackgroundColor(Color.GRAY);
        addView(view);

        contentLinear = new LinearLayout(context);
        contentLinear.setOrientation(LinearLayout.HORIZONTAL);
        contentLinear.setGravity(Gravity.CENTER_HORIZONTAL);
        var paramsContent = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsContent.setMargins(0, Utils.dipToPixels(5), 0, Utils.dipToPixels(5));
        contentLinear.setLayoutParams(paramsContent);
        addView(contentLinear);

    }

    public void setTitle(String title) {
        mTitle.setText(title);
    }

    public void addTab(String title, @Nullable Drawable image, @Nullable View.OnClickListener listener) {
        var linearLayout = new LinearLayout(getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setGravity(Gravity.CENTER);
        var params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        params.setMargins(Utils.dipToPixels(1.5f), 0, Utils.dipToPixels(1.5f), 0);
        linearLayout.setLayoutParams(params);
        linearLayout.setPadding(Utils.dipToPixels(10), Utils.dipToPixels(20), Utils.dipToPixels(10), Utils.dipToPixels(10));
        linearLayout.setBackground(DesignUtils.createDrawable("stroke_border", DesignUtils.getPrimaryTextColor()));
        linearLayout.setOnClickListener(listener);

        var imageView = new ImageView(getContext());
        var paramsImageView = new LinearLayout.LayoutParams(Utils.dipToPixels(40), Utils.dipToPixels(40));
        imageView.setLayoutParams(paramsImageView);
        imageView.setImageDrawable(image);
        linearLayout.addView(imageView);

        var textView = new TextView(getContext());
        var paramsTextView = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsTextView.setMargins(0, Utils.dipToPixels(5), 0, 0);
        textView.setLayoutParams(paramsTextView);
        textView.setText(title);
        textView.setTextColor(DesignUtils.getPrimaryTextColor());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textView.setTypeface(null, Typeface.BOLD);
        linearLayout.addView(textView);
        contentLinear.addView(linearLayout);

    }


}
