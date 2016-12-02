package me.relex.widget.waveform;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.widget.ScrollerCompat;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import java.util.List;
import me.relex.waveformview.R;
import me.relex.widget.waveform.gesture.OnScaleDragGestureListener;
import me.relex.widget.waveform.gesture.ScaleDragDetector;

public class WaveFormView extends View implements OnScaleDragGestureListener {
    private final static int[] SECOND_STEPS = { 1, 2, 5, 10, 20, 30 }; //  秒数间隔区间

    private float mMaxScale = 3f;
    private float mMinScale = 1f;

    @Nullable private WaveFormInfo mWaveFormInfo;
    private Paint mWaveFormPaint;
    private Paint mTimeLabelPaint;
    private TextPaint mTimeTextPaint;
    private float mTimeTextHeight;

    private int mTimeLabelHeight = 24;
    private int mTimeLabelMinSpace = 72;

    private double mTotalSecond = 0d;
    private double mStartSecond = 0d;
    private float mScale = 1f;

    @Nullable private WaveFormListener mWaveFormListener;
    private ScaleDragDetector mScaleDragDetector;
    private GestureDetectorCompat mGestureDetector;
    private FlingRunnable mCurrentFlingRunnable;
    private AnimatedZoomRunnable mAnimatedZoomRunnable;

    public WaveFormView(Context context) {
        super(context);
        init(context, null);
    }

    public WaveFormView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public WaveFormView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public WaveFormView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        int waveformColor = Color.BLACK;
        int textColor = Color.BLACK;
        int textSize = 24;
        int labelColor = Color.BLACK;
        int labelWidth = 2;

        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.WaveFormView);
            mMaxScale = typedArray.getFloat(R.styleable.WaveFormView_wf_waveform_max_scale, 3f);
            waveformColor =
                    typedArray.getColor(R.styleable.WaveFormView_wf_waveform_color, Color.BLACK);
            textColor =
                    typedArray.getColor(R.styleable.WaveFormView_wf_time_text_color, Color.BLACK);
            textSize = typedArray.getDimensionPixelSize(R.styleable.WaveFormView_wf_time_text_size,
                    24);
            labelColor =
                    typedArray.getColor(R.styleable.WaveFormView_wf_time_label_color, Color.BLACK);
            labelWidth =
                    typedArray.getDimensionPixelSize(R.styleable.WaveFormView_wf_time_label_width,
                            2);
            mTimeLabelHeight =
                    typedArray.getDimensionPixelSize(R.styleable.WaveFormView_wf_time_label_height,
                            24);
            mTimeLabelMinSpace = typedArray.getDimensionPixelSize(
                    R.styleable.WaveFormView_wf_time_label_min_space, 72);
            typedArray.recycle();
        }

        mWaveFormPaint = new Paint();
        mWaveFormPaint.setColor(waveformColor);
        mWaveFormPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mWaveFormPaint.setStrokeWidth(0);

        mTimeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTimeTextPaint.setColor(textColor);
        mTimeTextPaint.setTextSize(textSize);
        mTimeTextHeight = Math.abs(mTimeTextPaint.descent() - mTimeTextPaint.ascent());

        mTimeLabelPaint = new Paint();
        mTimeLabelPaint.setColor(labelColor);
        mTimeLabelPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mTimeLabelPaint.setStrokeWidth(labelWidth);

        mScaleDragDetector = new ScaleDragDetector(context, this);
        mGestureDetector =
                new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener());
        mGestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                return false;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                float x = e.getX();
                float maxScale = mMaxScale;
                float minScale = 1;
                float mediumScale = (maxScale + minScale) / 2;
                if (mScale < mediumScale) {
                    setScale(mediumScale, x, true);
                } else if (mScale >= mediumScale && mScale < maxScale) {
                    setScale(maxScale, x, true);
                } else {
                    setScale(minScale, x, true);
                }
                return true;
            }

            @Override public boolean onDoubleTapEvent(MotionEvent e) {
                return false;
            }
        });
    }

    public void setWave(WaveFormInfo info) {
        if (info == null) {
            return;
        }
        mWaveFormInfo = info;
        initWave(info);

        int width = getMeasuredWidth();
        if (mWaveFormListener != null && width > 0) {
            double viewSeconds = WaveUtil.pixelsToSeconds(width, info.getSample_rate(),
                    info.getSamples_per_pixel(), mScale);
            mWaveFormListener.onScrollChanged(mStartSecond, mStartSecond + viewSeconds);
        }

        invalidate();
    }

    public void initWave(@NonNull WaveFormInfo info) {
        int sampleRate = info.getSample_rate();
        int samplesPerPixel = info.getSamples_per_pixel();
        int length = info.getLength();
        mTotalSecond = WaveUtil.dataPixelsToSecond(length, sampleRate, samplesPerPixel);
        computerMinScaleFactor();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        computerMinScaleFactor();

        if (mWaveFormListener != null && w > 0 && mWaveFormInfo != null) {
            double viewSeconds =
                    WaveUtil.pixelsToSeconds(getMeasuredWidth(), mWaveFormInfo.getSample_rate(),
                            mWaveFormInfo.getSamples_per_pixel(), mScale);
            mWaveFormListener.onScrollChanged(mStartSecond, mStartSecond + viewSeconds);
        }
    }

    private void computerMinScaleFactor() {
        int width = getMeasuredWidth();

        if (mWaveFormInfo == null || width <= 0) {
            return;
        }
        mMinScale = (float) width / mWaveFormInfo.getLength();
        mScale = verifyScale(mScale);
    }

    // 计算 缩放的时间间距。 根据 sampleRate 和 samplesPerPixel 计算合适的区间内，选择最小秒(1s ,2s,5s,10s,20s,30s)
    private int getSecondScale(int sampleRate, int samplesPerPixel) {
        int baseSecs = 1; // seconds
        int index = 0;
        int second;
        while (true) {
            second = baseSecs * SECOND_STEPS[index];
            int pixels = WaveUtil.secondsToPixels(second, sampleRate, samplesPerPixel, mScale);
            if (pixels < mTimeLabelMinSpace) {
                if (index++ == SECOND_STEPS.length) {
                    baseSecs *= 60; // seconds -> minutes -> hours
                    index = 0;
                }
            } else {
                // Spacing OK
                break;
            }
        }
        return second;
    }

    @Override public void onDrag(float dx, float dy) {
        if (mWaveFormInfo == null || mScaleDragDetector.isScaling()) {
            return;
        }

        int sampleRate = mWaveFormInfo.getSample_rate();
        int samplesPerPixel = mWaveFormInfo.getSamples_per_pixel();

        if (dx == 0) {
            return;
        }

        double dxSeconds = WaveUtil.pixelsToSeconds(-dx, sampleRate, samplesPerPixel, mScale);
        double viewSeconds =
                WaveUtil.pixelsToSeconds(getMeasuredWidth(), sampleRate, samplesPerPixel, mScale);
        mStartSecond += dxSeconds;
        if (mStartSecond + viewSeconds > mTotalSecond) {
            mStartSecond = mTotalSecond - viewSeconds;
        }
        if (mStartSecond < 0) {
            mStartSecond = 0d;
        }

        if (mWaveFormListener != null) {
            mWaveFormListener.onScrollChanged(mStartSecond, mStartSecond + viewSeconds);
        }

        invalidate();
    }

    @Override public void onFling(float startX, float startY, float velocityX, float velocityY) {
        mCurrentFlingRunnable = new FlingRunnable(getContext());
        mCurrentFlingRunnable.fling((int) startX, (int) velocityX);
        post(mCurrentFlingRunnable);
    }

    private double mLastStartSecond;
    private boolean mResetScaleStartSecond;

    @Override public void onScaleBegin() {
        mResetScaleStartSecond = true;
    }

    @Override public void onScale(float scaleFactor, float focusX, float focusY) {

        if (mWaveFormInfo == null) {
            return;
        }
        float tempScale = verifyScale(mScale * scaleFactor);

        if (tempScale != mScale) {
            mScale = tempScale;
            int sampleRate = mWaveFormInfo.getSample_rate();
            int samplesPerPixel = mWaveFormInfo.getSamples_per_pixel();
            float dx = focusX - (focusX - 0) * mScale;
            double ds = WaveUtil.pixelsToSeconds(dx, sampleRate, samplesPerPixel, mScale);
            if (mResetScaleStartSecond) {
                mLastStartSecond = mStartSecond + ds;
                mResetScaleStartSecond = false;
            }

            mStartSecond = verifyStartSecond(mLastStartSecond - ds, sampleRate, samplesPerPixel);

            if (mWaveFormListener != null) {
                double viewSeconds =
                        WaveUtil.pixelsToSeconds(getMeasuredWidth(), sampleRate, samplesPerPixel,
                                mScale);
                mWaveFormListener.onScrollChanged(mStartSecond, mStartSecond + viewSeconds);
            }

            invalidate();
        }
    }

    @Override public void onScaleEnd() {
        if (mWaveFormInfo == null) {
            return;
        }
        mStartSecond = verifyStartSecond(mStartSecond, mWaveFormInfo.getSample_rate(),
                mWaveFormInfo.getSamples_per_pixel());
        invalidate();
    }

    public void setScale(float scale) {
        if (mWaveFormInfo == null) {
            return;
        }
        int sampleRate = mWaveFormInfo.getSample_rate();
        int samplesPerPixel = mWaveFormInfo.getSamples_per_pixel();

        float tempScale = verifyScale(scale);
        if (mScale != tempScale) {
            mScale = tempScale;
            mStartSecond = verifyStartSecond(mStartSecond, sampleRate, samplesPerPixel);
            if (mWaveFormListener != null) {
                double viewSeconds =
                        WaveUtil.pixelsToSeconds(getMeasuredWidth(), sampleRate, samplesPerPixel,
                                mScale);
                mWaveFormListener.onScrollChanged(mStartSecond, mStartSecond + viewSeconds);
            }
            invalidate();
        }
    }

    public void setScale(float scale, float focusX, boolean animate) {
        if (mWaveFormInfo == null) {
            return;
        }

        if (animate) {
            mAnimatedZoomRunnable = new AnimatedZoomRunnable(mScale, scale, focusX);
            post(mAnimatedZoomRunnable);
        } else {
            setScale(scale);
        }
    }

    private double verifyStartSecond(double startSecond, int sampleRate, int samplesPerPixel) {
        double viewSeconds =
                WaveUtil.pixelsToSeconds(getMeasuredWidth(), sampleRate, samplesPerPixel, mScale);
        if (startSecond + viewSeconds > mTotalSecond) {
            startSecond = mTotalSecond - viewSeconds;
        }
        if (startSecond < 0) {
            startSecond = 0d;
        }

        return startSecond;
    }

    private float verifyScale(float scale) {
        if (scale < mMinScale) {
            scale = mMinScale;
        } else if (scale > mMaxScale) {
            scale = mMaxScale;
        }

        float smokeWidth = (int) Math.ceil(scale);
        smokeWidth = smokeWidth < 0 ? 0 : smokeWidth;
        mWaveFormPaint.setStrokeWidth(smokeWidth);
        return scale;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                cancelFling();
                cancelAnimation();
            }
            break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(false);
                }
            }
            break;
        }

        boolean handled = mScaleDragDetector.onTouchEvent(event);
        if (mGestureDetector.onTouchEvent(event)) {
            handled = true;
        }
        return handled;
    }

    @Override protected void onDetachedFromWindow() {
        cancelFling();
        cancelAnimation();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mWaveFormInfo == null) {
            return;
        }
        drawWave(canvas, mWaveFormInfo);
        drawTimeLabel(canvas, mWaveFormInfo);
    }

    private void drawWave(Canvas canvas, @NonNull WaveFormInfo info) {

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        int sampleRate = info.getSample_rate();
        int samplesPerPixel = info.getSamples_per_pixel();
        List<Integer> data = info.getData();
        int dataLength = info.getLength();
        boolean is8Bit = info.getBits() == 8;

        // 改变 x 的 叠加值，放大或缩小波形

        int startTimePixel = (int) (mStartSecond * sampleRate / samplesPerPixel);

        int dataPixel = startTimePixel;
        float axisX = 0;
        int finalAxisX = -1;
        int low;
        int high;
        int lowY;
        int highY;

        while (axisX < width) {
            if (dataPixel < 0 || dataPixel >= dataLength) {
                break;
            }

            int nearestAxisX = (int) axisX;
            if (nearestAxisX != finalAxisX) {
                finalAxisX = nearestAxisX;
                low = (is8Bit ? data.get(dataPixel * 2) * 256 : data.get(dataPixel * 2)) + 32768;
                high = (is8Bit ? data.get(dataPixel * 2 + 1) * 256 : data.get(dataPixel * 2 + 1))
                        + 32768;
                lowY = height - low * height / 65536;
                highY = height - high * height / 65536;
                canvas.drawLine(finalAxisX, lowY, finalAxisX, highY, mWaveFormPaint);
            }

            axisX += mScale;
            dataPixel++;
        }
    }

    private void drawTimeLabel(Canvas canvas, @NonNull WaveFormInfo info) {
        int sampleRate = info.getSample_rate();
        int samplesPerPixel = info.getSamples_per_pixel();

        int width = getMeasuredWidth();

        int intervalSecond = getSecondScale(sampleRate, samplesPerPixel);

        //  第一轴标时间戳
        int firstAxisLabelSecond = WaveUtil.roundUpToNearest(mStartSecond, intervalSecond);

        //  第一轴标时间戳与起始时间戳的时间差
        double firstAxisLabelOffsetSecond = firstAxisLabelSecond - mStartSecond;

        // 起始点到第一轴标时间戳的距离
        int firstAxisLabelOffsetWidth =
                WaveUtil.secondsToPixels(firstAxisLabelOffsetSecond, sampleRate, samplesPerPixel,
                        mScale);

        int second = firstAxisLabelSecond;

        while (true) {
            int x = firstAxisLabelOffsetWidth + WaveUtil.secondsToPixels(
                    (second - firstAxisLabelSecond), sampleRate, samplesPerPixel, mScale);
            if (x >= width) {
                break;
            }

            if (second != 0) {
                canvas.drawLine(x, 0, x, mTimeLabelHeight, mTimeLabelPaint);
                String timeString = second + "s";
                float timeWidth = mTimeTextPaint.measureText(timeString);
                canvas.drawText(timeString, x - timeWidth / 2f, mTimeLabelHeight + mTimeTextHeight,
                        mTimeTextPaint);
            }
            second += intervalSecond;
        }
    }

    private void cancelFling() {
        if (mCurrentFlingRunnable != null) {
            mCurrentFlingRunnable.cancelFling();
            mCurrentFlingRunnable = null;
        }
    }

    private void cancelAnimation() {
        if (mAnimatedZoomRunnable != null) {
            mAnimatedZoomRunnable.cancel();
            mAnimatedZoomRunnable = null;
        }
    }

    public void setWaveFormListener(@Nullable WaveFormListener waveFormListener) {
        mWaveFormListener = waveFormListener;
    }

    public void setStartTime(double startSecond) {
        mStartSecond = startSecond;

        if (mWaveFormInfo != null) {
            if (mWaveFormListener != null) {
                double viewSeconds =
                        WaveUtil.pixelsToSeconds(getMeasuredWidth(), mWaveFormInfo.getSample_rate(),
                                mWaveFormInfo.getSamples_per_pixel(), mScale);
                mWaveFormListener.onScrollChanged(mStartSecond, mStartSecond + viewSeconds);
            }

            invalidate();
        }
    }

    private class FlingRunnable implements Runnable {
        private final ScrollerCompat scroller;
        private int tempStartX;

        public FlingRunnable(Context context) {
            scroller = ScrollerCompat.create(context);
        }

        public void fling(int startX, int velocityX) {
            if (mWaveFormInfo == null) {
                return;
            }
            int sampleRate = mWaveFormInfo.getSample_rate();
            int samplesPerPixel = mWaveFormInfo.getSamples_per_pixel();
            int totalPixels = (int) (mWaveFormInfo.getLength() * mScale);

            int minPixels = startX;
            if (minPixels < 0) {
                minPixels = 0;
            }

            int maxPixels = totalPixels - (getMeasuredWidth() - startX);
            if (maxPixels < 0) {
                maxPixels = 0;
            }

            tempStartX = startX;
            int flingStartX =
                    WaveUtil.secondsToPixels(mStartSecond, sampleRate, samplesPerPixel, mScale)
                            + startX;
            scroller.fling(flingStartX, 0, velocityX, 0, minPixels, maxPixels, 0, 0);
        }

        @Override public void run() {
            if (scroller.isFinished() || mWaveFormInfo == null) {
                return;
            }

            if (scroller.computeScrollOffset()) {
                final int currentX = scroller.getCurrX();

                int sampleRate = mWaveFormInfo.getSample_rate();
                int samplesPerPixel = mWaveFormInfo.getSamples_per_pixel();

                double startSecond =
                        WaveUtil.pixelsToSeconds(currentX - tempStartX, sampleRate, samplesPerPixel,
                                mScale);
                if (startSecond < 0) {
                    startSecond = 0;
                }

                if (mStartSecond != startSecond) {
                    mStartSecond = startSecond;
                    if (mWaveFormListener != null) {
                        double viewSeconds =
                                WaveUtil.pixelsToSeconds(getMeasuredWidth(), sampleRate,
                                        samplesPerPixel, mScale);
                        mWaveFormListener.onScrollChanged(mStartSecond, mStartSecond + viewSeconds);
                    }

                    invalidate();
                }
                postOnAnimationCompat(this);
            }
        }

        public void cancelFling() {
            scroller.abortAnimation();
        }
    }

    private class AnimatedZoomRunnable implements Runnable {
        private static final long ZOOM_DURATION = 200L;
        private final float focalX;
        private final long startTime;
        private final float startScale, targetScale;
        private boolean canceled;
        private final Interpolator scaleInterpolator = new AccelerateDecelerateInterpolator();

        public AnimatedZoomRunnable(final float startScale, final float targetScale,
                final float focalX) {
            startTime = System.currentTimeMillis();
            this.focalX = focalX;
            this.startScale = startScale;
            this.targetScale = targetScale;

            onScaleBegin();
        }

        @Override public void run() {
            float t = interpolate();
            float scale = startScale + t * (targetScale - startScale);
            float deltaScale = scale / mScale;

            onScale(deltaScale, focalX, 0);
            if (t < 1f && !canceled) {
                postOnAnimationCompat(this);
            } else {
                onScaleEnd();
            }
        }

        public void cancel() {
            canceled = true;
        }

        private float interpolate() {
            float t = 1f * (System.currentTimeMillis() - startTime) / ZOOM_DURATION;
            t = Math.min(1f, t);
            t = scaleInterpolator.getInterpolation(t);
            return t;
        }
    }

    private void postOnAnimationCompat(Runnable runnable) {
        if (Build.VERSION.SDK_INT >= 16) {
            postOnAnimation(runnable);
        } else {
            postDelayed(runnable, 16L);
        }
    }
}
