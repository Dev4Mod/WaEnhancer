package com.wmods.wppenhacer.views;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.ListView;

public class NoScrollListView extends ListView {
    public NoScrollListView(Context context) {
        super(context);
    }

    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0x1FFFFFFF, MeasureSpec.AT_MOST));
        ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
        layoutParams.height = this.getMeasuredHeight();
    }
}
