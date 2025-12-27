package com.bodekjan.soundmeter.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.appcompat.widget.AppCompatButton;

public class ProgressButton extends AppCompatButton {

    private enum State {
        IDLE, LOADING, SUCCESS, FAILED
    }

    private State currentState = State.IDLE;
    private GradientDrawable background;
    private int initialWidth;
    private CharSequence buttonText;

    private Paint progressPaint;
    private RectF progressRect;
    private float progressSweepAngle;

    public ProgressButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        background = new GradientDrawable();
        int[] colors = {Color.parseColor("#5DADE2"), Color.parseColor("#2E86C1")};
        background.setColors(colors);
        background.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        background.setCornerRadius(15f);
        setBackground(background);

        buttonText = getText();

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(Color.WHITE);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(8);

        progressRect = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentState == State.LOADING) {
            float size = getHeight() * 0.6f;
            float left = (getWidth() - size) / 2;
            float top = (getHeight() - size) / 2;
            progressRect.set(left, top, left + size, top + size);
            canvas.drawArc(progressRect, -90, progressSweepAngle, false, progressPaint);
        }
    }

    public void startLoading() {
        if (currentState != State.IDLE) return;

        currentState = State.LOADING;
        initialWidth = getWidth();
        setText("");

        ValueAnimator widthAnimator = ValueAnimator.ofInt(initialWidth, getHeight());
        widthAnimator.setDuration(300);
        widthAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        widthAnimator.addUpdateListener(animation -> {
            getLayoutParams().width = (int) animation.getAnimatedValue();
            requestLayout();
        });

        ValueAnimator cornerAnimator = ValueAnimator.ofFloat(background.getCornerRadius(), getHeight() / 2f);
        cornerAnimator.setDuration(300);
        cornerAnimator.addUpdateListener(animation -> background.setCornerRadius((float) animation.getAnimatedValue()));

        widthAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startProgressAnimation();
            }
        });

        widthAnimator.start();
        cornerAnimator.start();
    }

    private void startProgressAnimation() {
        ValueAnimator progressAnimator = ValueAnimator.ofFloat(0, 360);
        progressAnimator.setDuration(1000);
        progressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        progressAnimator.setRepeatCount(ValueAnimator.INFINITE);
        progressAnimator.addUpdateListener(animation -> {
            progressSweepAngle = (float) animation.getAnimatedValue();
            invalidate();
        });
        progressAnimator.start();
    }

    public void stopMorphAnimation() {
        // This would be expanded to handle success/failure animations
        currentState = State.IDLE;
        // Animate back to original state
        ValueAnimator widthAnimator = ValueAnimator.ofInt(getHeight(), initialWidth);
        widthAnimator.setDuration(300);
        widthAnimator.addUpdateListener(animation -> {
            getLayoutParams().width = (int) animation.getAnimatedValue();
            requestLayout();
        });

        ValueAnimator cornerAnimator = ValueAnimator.ofFloat(getHeight() / 2f, 15f); // Assuming original corner radius
        cornerAnimator.setDuration(300);
        cornerAnimator.addUpdateListener(animation -> background.setCornerRadius((float) animation.getAnimatedValue()));

        widthAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setText(buttonText);
            }
        });

        widthAnimator.start();
        cornerAnimator.start();
    }
}
