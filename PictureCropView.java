package com.msile.view.canvas;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * 图片裁剪
 * create by msilemsile
 */
public class PictureCropView extends View {

    private Bitmap mPicture, mForeground;
    private Paint mCropPaint;
    private Canvas mCanvas;
    private RectF mCannotTouchRect;
    private int mTouchX, mTouchY;
    private int mTouchMode;
    private double mLastDistance;
    private static final int SINGLE_TOUCH_MODE = 1;
    private static final int MUL_TOUCH_MODE = 2;

    private Matrix mMatrix;
    private float mTransX, mTransY;
    private float mScale;
    private int mPicWidth, mPicHeight;

    private float mMinScale;
    private float mMaxScale = 2.0f;
    private float mInitScale = 1.0f;
    private boolean canNotTouch;

    private int mRadius = 120;

    private Bitmap mCropBitmap;
    private boolean mFirstInitBitmap;

    public PictureCropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PictureCropView(Context context) {
        this(context, null);
    }

    private void init() {
        mMatrix = new Matrix();
        mCropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCropPaint.setARGB(128, 255, 0, 0);
        mCropPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    /**
     * 设置要裁剪的图片
     */
    public void setPicture(Bitmap mPicture) {
        if (mPicture == null) {
            return;
        }
        this.mPicture = mPicture;
        mPicWidth = mPicture.getWidth();
        mPicHeight = mPicture.getHeight();
        int minEdge = mPicWidth > mPicHeight ? mPicHeight : mPicWidth;
        int normalEdge = mRadius * 2;
        if (mPicWidth < normalEdge && mPicHeight < normalEdge) {
            canNotTouch = true;
        }
        mMinScale = normalEdge / (minEdge * 1.0f);
        if (mMinScale < 1) {
            mMinScale = 1;
            mMaxScale = 1;
            mInitScale = 1 / mMinScale;
        }
        mScale = mInitScale;
        mMatrix.postScale(mInitScale, mInitScale);
        mFirstInitBitmap = true;
    }

    /**
     * 设置圆形半径大小
     */
    public void setCircleRadius(int mRadius) {
        this.mRadius = mRadius;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mCannotTouchRect == null) {
            mCannotTouchRect = new RectF(getWidth() / 2 - mRadius, getHeight() / 2 - mRadius, getWidth() / 2 + mRadius, getHeight() / 2 + mRadius);
        }
        if (mCropBitmap != null) {
            canvas.drawBitmap(mCropBitmap, getWidth() / 2 - mRadius, getHeight() / 2 - mRadius, null);
            return;
        }
        if (mPicture == null) {
            return;
        }
        if (mFirstInitBitmap && mScale == 1) {
            //绘制到中心点
            mMatrix.postTranslate(-mPicWidth / 2 + getWidth() / 2, -mPicHeight / 2 + getHeight() / 2);
            mFirstInitBitmap = false;
        }
        canvas.drawBitmap(mPicture, mMatrix, null);
        if (mForeground == null) {
            mForeground = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mForeground);
            mCanvas.drawColor(Color.argb(88, 0, 0, 0));
        }
        canvas.drawBitmap(mForeground, 0, 0, null);

        mCanvas.drawCircle(getWidth() / 2, getHeight() / 2, mRadius, mCropPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (canNotTouch) {
            return false;
        }
        int action = MotionEventCompat.getActionMasked(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mTouchX = (int) event.getX();
                mTouchY = (int) event.getY();
                mTouchMode = SINGLE_TOUCH_MODE;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getActionIndex() > 1) {
                    break;
                }
                mTouchMode = MUL_TOUCH_MODE;
                mLastDistance = getDistance(event);
                break;
            case MotionEvent.ACTION_MOVE:
                float moveX = (int) event.getX();
                float moveY = (int) event.getY();
                if (mTouchMode == SINGLE_TOUCH_MODE) {
                    //位移
                    int distanceX = (int) (moveX - mTouchX);
                    int distanceY = (int) (moveY - mTouchY);
                    postTranslate(distanceX, distanceY);
                    invalidate();
                    mTouchX = (int) moveX;
                    mTouchY = (int) moveY;
                } else if (mTouchMode == MUL_TOUCH_MODE) {
                    //中心点
                    float centerX = getPointCenterX(event);
                    float centerY = getPointCenterY(event);

                    double currentDistance = getDistance(event);
                    if (mLastDistance > 0) {
                        //缩放
                        float scale = (float) (currentDistance / mLastDistance);
                        if (Math.abs(scale) > 0) {
                            mMatrix.postScale(scale, scale, centerX, centerY);
                            if (checkEdge()) {
                                mMatrix.postScale(1 / scale, 1 / scale, centerX, centerY);
                                mLastDistance = currentDistance;
                            } else {
                                invalidate();
                            }
                        }
                    }
                    mLastDistance = currentDistance;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                mTouchMode = 0;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchX = 0;
                mTouchY = 0;
                break;
        }
        return true;
    }

    /**
     * 重新裁剪
     */
    public void retryCrop() {
        if (mCropBitmap != null) {
            mCropBitmap.recycle();
            mCropBitmap = null;
            invalidate();
        }
    }

    /**
     * 裁剪图片
     */
    public Bitmap cropPicture() {
        if (mCropBitmap == null) {
            mCropBitmap = Bitmap.createBitmap(mRadius * 2, mRadius * 2, Bitmap.Config.ARGB_8888);
        }
        Canvas canvas = new Canvas(mCropBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader bitmapShader = new BitmapShader(mPicture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Matrix temp = new Matrix();
        getMatrixValues();

        temp.postScale(mScale, mScale);
        temp.postTranslate(mTransX - getWidth() / 2 + mRadius, mTransY - getHeight() / 2 + mRadius);

        bitmapShader.setLocalMatrix(temp);
        paint.setShader(bitmapShader);
        canvas.drawCircle(mRadius, mRadius, mRadius, paint);
        invalidate();
        return mCropBitmap;
    }

    /**
     * 平移
     */
    private void postTranslate(int distanceX, int distanceY) {
        getMatrixValues();
        Log.i("-----checkEdge---", "transX=" + mTransX + "---transY=" + mTransY);
        float currentWidth = mScale * mPicWidth;
        float currentHeight = mScale * mPicHeight;
        float tempTransX = mTransX + distanceX;
        float tempTransY = mTransY + distanceY;
        float tempRight = tempTransX + currentWidth;
        float tempBottom = tempTransY + currentHeight;
        float realDisX = distanceX, realDisY = distanceY;
        if (tempTransX >= mCannotTouchRect.left) {
            realDisX = mCannotTouchRect.left - tempTransX;
        }
        if (tempTransY >= mCannotTouchRect.top) {
            realDisY = mCannotTouchRect.top - tempTransY;
        }
        if (tempRight <= mCannotTouchRect.right) {
            realDisX = mCannotTouchRect.right - tempRight;
        }
        if (tempBottom <= mCannotTouchRect.bottom) {
            realDisY = mCannotTouchRect.bottom - tempBottom;
        }
        mMatrix.postTranslate(realDisX, realDisY);
    }

    /**
     * 边界检查
     */
    private boolean checkEdge() {
        getMatrixValues();
        Log.i("-----checkEdge---", "transX=" + mTransX + "---transY=" + mTransY);
        float currentWidth = mScale * mPicWidth;
        float currentHeight = mScale * mPicHeight;
        return mTransX >= mCannotTouchRect.left
                || mTransY >= mCannotTouchRect.top
                || (mTransX + currentWidth) <= mCannotTouchRect.right
                || (mTransY + currentHeight) <= mCannotTouchRect.bottom;
    }

    /**
     * 获取当前矩阵的值
     */
    private void getMatrixValues() {
        float[] v = new float[9];
        mMatrix.getValues(v);
        // translation is simple
        mTransX = v[Matrix.MTRANS_X];
        mTransY = v[Matrix.MTRANS_Y];
        // calculate real scale
        float scaleX = v[Matrix.MSCALE_X];
        float skewY = v[Matrix.MSKEW_Y];
        mScale = (float) Math.sqrt(scaleX * scaleX + skewY * skewY);
        // calculate the degree of rotation
//        float rAngle = Math.round(Math.atan2(v[Matrix.MSKEW_X], v[Matrix.MSCALE_X]) * (180 / Math.PI));
    }

    /**
     * 两点间距离
     */
    private double getDistance(MotionEvent event) {
        float dx = event.getX(1) - event.getX(0);
        float dy = event.getY(1) - event.getY(0);
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 两点X中心点
     */
    private float getPointCenterX(MotionEvent event) {
        return Math.abs((event.getX(1) + event.getX(0)) / 2);
    }

    /**
     * 两点Y中心点
     */
    private float getPointCenterY(MotionEvent event) {
        return Math.abs((event.getY(1) + event.getY(0)) / 2);
    }

}
