package com.wmods.wppenhacer.listeners;

import android.view.GestureDetector;
import android.view.MotionEvent;

public class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {
    private final OnDoubleClickListener listener;

    public DoubleTapListener(OnDoubleClickListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        listener.onDoubleClick();
        return true;
    }

    public interface OnDoubleClickListener {
        void onDoubleClick();
    }
}