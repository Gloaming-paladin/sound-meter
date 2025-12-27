package com.bodekjan.soundmeter.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ParticleView extends View {

    private List<Particle> particles = new ArrayList<>();
    private Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int PARTICLE_COUNT = 50;
    private ValueAnimator animator;

    public ParticleView(Context context) {
        super(context);
        init();
    }

    public ParticleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ParticleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        particlePaint.setStyle(Paint.Style.FILL);
        particlePaint.setColor(Color.WHITE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            particles.clear();
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                particles.add(new Particle(w, h));
            }
            startAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (canvas == null) return;



        for (Particle p : particles) {
            particlePaint.setAlpha(p.alpha);
            canvas.drawCircle(p.x, p.y, p.radius, particlePaint);
        }
    }

    private void startAnimation() {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration(10000); 
        animator.addUpdateListener(animation -> {
            for (Particle p : particles) {
                p.move(getWidth(), getHeight());
            }
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }
}
