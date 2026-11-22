package com.macos.launcher;

import android.content.Context;
import android.view.View;
import android.widget.ScrollView;

public class MaxScroll extends ScrollView {
    private final int maxH;
    public MaxScroll(Context c, int maxH) { super(c); this.maxH = maxH; setVerticalScrollBarEnabled(false); }
    @Override protected void onMeasure(int w, int h) {
        super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
    }
}
