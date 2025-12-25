package com.bodekjan.soundmeter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Created by bodekjan on 2016/8/8.
 */
public class Speedometer extends ImageView {

    private float scaleWidth, scaleHeight;
    private int newWidth, newHeight;
    private Matrix mMatrix = new Matrix();
    private Bitmap indicatorBitmap;
    private Paint paint = new Paint();
    private Typeface typeface;  // 添加 Typeface 成员变量
    static final long  ANIMATION_INTERVAL = 20;

    public Speedometer(Context context) {
        super(context);
    }

    public Speedometer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // 添加设置 Typeface 的方法
    public void setTypeface(Typeface tf) {
        this.typeface = tf;
    }

    private void init() {
        Bitmap myBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.noise_index);
        int bitmapWidth = myBitmap.getWidth();
        int bitmapHeight = myBitmap.getHeight();
        newWidth = getWidth();
        newHeight = getHeight();
        scaleWidth = ((float) newWidth) /(float) bitmapWidth;  // Get the zoom ratio
        scaleHeight = ((float) newHeight) /(float) bitmapHeight;  //Get the zoom ratio
        mMatrix.postScale(scaleWidth, scaleHeight);   //Set the scale of mMatrix
        indicatorBitmap = Bitmap.createBitmap(myBitmap, 0, 0, bitmapWidth, bitmapHeight, mMatrix,true);  //Get the same and background width and height of the pointer map bitmap

        paint = new Paint();
        paint.setTextSize(44);
        if (typeface != null) {
            paint.setTypeface(typeface);  // 使用传递的 Typeface
        }
        paint.setAntiAlias(true);  //Anti-aliasing
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
    }

    public void refresh() {
        postInvalidateDelayed(ANIMATION_INTERVAL); //Sub-thread refresh view
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (indicatorBitmap == null) {
            init();
        }
        mMatrix.setRotate(getAngle(World.dbCount), newWidth / 2, newHeight * 215 / 460);   //The relative position of the sheet
        canvas.drawBitmap(indicatorBitmap, mMatrix, paint);
        canvas.drawText((int)World.dbCount+" db", newWidth/2,newHeight*36/46, paint); //Picture relative position
    }

    private float getAngle(float db){
        return(db-85)*5/3;  //Say more are tears, online to find pictures. The They will not change the map, the code calculation
    }
}
