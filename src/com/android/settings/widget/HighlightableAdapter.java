package com.android.settings.widget;

import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.AppBarLayout;

public interface HighlightableAdapter {
    void requestHighlight(View root, RecyclerView recyclerView, AppBarLayout appBarLayout);
    boolean isHighlightRequested();
}
