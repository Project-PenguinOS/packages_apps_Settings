package com.android.settings.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;

public class TopLevelRevertAdapter extends HighlightablePreferenceGroupAdapter {

    private final int mNormalBackgroundRes;
    private final int mHighlightColor;

    public TopLevelRevertAdapter(PreferenceGroup preferenceGroup, String key, boolean highlightRequested) {
        super(preferenceGroup, key, highlightRequested);

        final Context context = preferenceGroup.getContext();
        final TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                outValue, true);
        mNormalBackgroundRes = outValue.resourceId;
        mHighlightColor = context.getColor(R.color.preference_highlight_color);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);

        updateOldBackground(holder, position);
    }

    private void updateOldBackground(PreferenceViewHolder holder, int position) {
        View v = holder.itemView;
        Preference currentPreference = getItem(position);

        if (currentPreference == null) {
            return;
        }

        if (position == mHighlightPosition
                && (mHighlightKey != null
                && TextUtils.equals(mHighlightKey, currentPreference.getKey()))) {
            addOldHighlightBackground(holder, !mFadeInAnimated, position);
        }
        else if (Boolean.TRUE.equals(v.getTag(R.id.preference_highlighted))) {
            removeOldHighlightBackground(holder, false /* animate */);
        }
    }

    private void addOldHighlightBackground(PreferenceViewHolder holder, boolean animate, int position) {
        final View v = holder.itemView;
        v.setTag(R.id.preference_highlighted, true);

        if (!animate) {
            v.setBackgroundColor(mHighlightColor);
            requestRemoveHighlightDelayed(holder, position);
            return;
        }

        mFadeInAnimated = true;
        final int colorFrom = mNormalBackgroundRes;
        final int colorTo = mHighlightColor;
        final ValueAnimator fadeInLoop = ValueAnimator.ofObject(
                new ArgbEvaluator(), colorFrom, colorTo);

        fadeInLoop.setDuration(HIGHLIGHT_FADE_IN_DURATION);
        fadeInLoop.addUpdateListener(
                animator -> v.setBackgroundColor((int) animator.getAnimatedValue()));
        fadeInLoop.setRepeatMode(ValueAnimator.REVERSE);
        fadeInLoop.setRepeatCount(4);
        fadeInLoop.start();
        holder.setIsRecyclable(false);
        requestRemoveHighlightDelayed(holder, position);
    }

    private void removeOldHighlightBackground(PreferenceViewHolder holder, boolean animate) {
        final View v = holder.itemView;

        if (!animate) {
            v.setTag(R.id.preference_highlighted, false);
            v.setBackgroundResource(mNormalBackgroundRes);
            return;
        }

        if (!Boolean.TRUE.equals(v.getTag(R.id.preference_highlighted))) {
            return;
        }
        v.setTag(R.id.preference_highlighted, false);

        int colorFrom = mHighlightColor;
        int colorTo = mNormalBackgroundRes;
        final ValueAnimator colorAnimation = ValueAnimator.ofObject(
                new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(HIGHLIGHT_FADE_OUT_DURATION);
        colorAnimation.addUpdateListener(
                animator -> v.setBackgroundColor((int) animator.getAnimatedValue()));
        colorAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                v.setBackgroundResource(mNormalBackgroundRes);
                holder.setIsRecyclable(true);
            }
        });
        colorAnimation.start();
    }
}
