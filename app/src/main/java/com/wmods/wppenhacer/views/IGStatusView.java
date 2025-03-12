package com.wmods.wppenhacer.views;

import android.content.Context;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.adapter.IGStatusAdapter;

public class IGStatusView extends FrameLayout {
    public HorizontalListView mStatusListView;

    public IGStatusAdapter mStatusAdapter;
    private int mFragmentId;

    public IGStatusView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public int getFragmentId() {
        return mFragmentId;
    }

    public void setFragmentId(int id) {
        mFragmentId = id;
    }

    private void init(Context context) {
        mStatusListView = new HorizontalListView(context);
        var layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
//        layoutParams.setMargins(Utils.dipToPixels(4), Utils.dipToPixels(10), 0, 0);
        mStatusListView.setLayoutParams(layoutParams);
        addView(mStatusListView);
    }

    @Override
    public void setTranslationY(float f) {
        if (this.getHeight() > 0) {
            int v = f > ((float) this.getHeight()) ? GONE : VISIBLE;
            if (v == VISIBLE) {
                super.setTranslationY(f);
            }
            this.setVisibility(v);
        }
    }


    public void updateList() {
        post(() -> {
            if (mStatusAdapter == null) return;
            mStatusAdapter.notifyDataSetChanged();
            this.invalidate();
        });
    }

    public void setAdapter(IGStatusAdapter mStatusAdapter) {
        this.mStatusAdapter = mStatusAdapter;
        mStatusListView.setAdapter(mStatusAdapter);
    }

    public IGStatusAdapter getAdapter() {
        return mStatusAdapter;
    }
}
