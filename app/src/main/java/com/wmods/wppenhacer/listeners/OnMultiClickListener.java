package com.wmods.wppenhacer.listeners;

import android.view.View;

public abstract class OnMultiClickListener implements View.OnClickListener {

    private final int targetClicks;
    private final long delay;
    private long lastClick = 0;
    private int clicks = 0;

    public OnMultiClickListener(int targetClicks, long delay) {
        if (targetClicks < 2) {
            throw new IllegalArgumentException("targetClicks must be greater than 1");
        }
        this.targetClicks = targetClicks;
        this.delay = delay;
    }

    @Override
    public void onClick(View v) {
        if (this.lastClick == 0 || System.currentTimeMillis() - this.lastClick < this.delay) {
            this.lastClick = System.currentTimeMillis();
            this.clicks++;
        } else {
            this.lastClick = 0;
            this.clicks = 0;
        }
        if (this.clicks >= this.targetClicks) {
            this.clicks = 0;
            this.lastClick = 0;
            onMultiClick(v);
        }
    }

    abstract public void onMultiClick(View v);

}