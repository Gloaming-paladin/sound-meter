// app/src/main/java/com/bodekjan/soundmeter/SpectrumView.java
package com.bodekjan.soundmeter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SpectrumView extends View {
    private Paint paint;
    private double[] spectrumData;
    private int barCount = 64; // 显示的频谱条数
    private float[] barHeights;
    private float maxHeight = 200f;

    public SpectrumView(Context context) {
        super(context);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        barHeights = new float[barCount];
    }

    public void setSpectrumData(double[] data) {
        this.spectrumData = data;
        updateBarHeights();
        invalidate();
    }

    private void updateBarHeights() {
        if (spectrumData == null || spectrumData.length == 0) return;

        // 计算每个频段的平均值
        int segmentSize = spectrumData.length / barCount;
        if (segmentSize == 0) segmentSize = 1;

        for (int i = 0; i < barCount; i++) {
            double sum = 0;
            int count = 0;
            int start = i * segmentSize;
            int end = Math.min(start + segmentSize, spectrumData.length);

            for (int j = start; j < end; j++) {
                sum += spectrumData[j];
                count++;
            }

            if (count > 0) {
                double avg = sum / count;
                // 对数缩放，增强视觉效果
                barHeights[i] = (float) Math.log10(1 + avg * 100) * maxHeight / 2;
            } else {
                barHeights[i] = 0;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (barHeights == null) return;

        int width = getWidth();
        int height = getHeight();
        float barWidth = (float) width / barCount;
        float baseY = height;

        for (int i = 0; i < barCount; i++) {
            // 根据频率设置颜色（低频-中频-高频）
            float hue = 240f - (i * 240f / barCount); // 从蓝色到红色
            paint.setColor(Color.HSVToColor(new float[]{hue, 1.0f, 1.0f}));

            float barHeight = barHeights[i];
            canvas.drawRect(
                    i * barWidth,
                    baseY - barHeight,
                    (i + 1) * barWidth,
                    baseY,
                    paint
            );
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        maxHeight = h * 0.8f; // 频谱图最大高度为视图的80%
    }
}
