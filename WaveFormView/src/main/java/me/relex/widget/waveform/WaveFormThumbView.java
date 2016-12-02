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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import java.util.List;
import me.relex.waveformview.R;
import me.relex.widget.waveform.gesture.DragDetector;
import me.relex.widget.waveform.gesture.OnDragGestureListener;

public class WaveFormThumbView extends View implements OnDragGestureListener {
    private Paint mWaveFormPaint;
    private Paint mWaveFormHighLightPaint;
    @Nullable private WaveFormInfo mBean;
    private double mTotalSecond = 0d;
    private float mThumbScale;
    private double mThumbStartSecond = 0d;
    private double mThumbDuration = 0d;
    private DragDetector mDragDetector;
    private int mThumbStartTimePixel;
    private int mThumbEndTimePixel;

    private OnDragThumbListener mOnDragThumbListener;

    public WaveFormThumbView(Context context) {
        super(context);
        init(context, null);
    }

    public WaveFormThumbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public WaveFormThumbView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public WaveFormThumbView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        int waveformColor = Color.BLACK;
        int highlightColor = Color.GRAY;

        if (attrs != null) {
            TypedArray typedArray =
                    context.obtainStyledAttributes(attrs, R.styleable.WaveFormThumbView);
            waveformColor = typedArray.getColor(R.styleable.WaveFormThumbView_wf_waveform_color,
                    Color.BLACK);
            highlightColor =
                    typedArray.getColor(R.styleable.WaveFormThumbView_wf_waveform_highlight_color,
                            Color.GRAY);
            typedArray.recycle();
        }

        mWaveFormPaint = new Paint();
        mWaveFormPaint.setColor(waveformColor);
        mWaveFormPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mWaveFormPaint.setStrokeWidth(0);

        mWaveFormHighLightPaint = new Paint();
        mWaveFormHighLightPaint.setColor(highlightColor);
        mWaveFormHighLightPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mWaveFormHighLightPaint.setStrokeWidth(0);

        mDragDetector = new DragDetector(context, this);
    }

    public void setWave(WaveFormInfo bean) {
        if (bean == null) {
            return;
        }

        mBean = bean;
        initWave(bean);
        invalidate();
    }

    public void initWave(@NonNull WaveFormInfo bean) {
        int sampleRate = bean.getSample_rate();
        int samplesPerPixel = bean.getSamples_per_pixel();
        int length = bean.getLength();
        mTotalSecond = WaveUtil.dataPixelsToSecond(length, sampleRate, samplesPerPixel);
        computerMinScaleFactor();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        computerMinScaleFactor();
    }

    private void computerMinScaleFactor() {
        int width = getMeasuredWidth();

        if (mBean == null || width <= 0) {
            return;
        }

        mThumbScale = (float) width / mBean.getLength();
        configScalePaint(mThumbScale);
    }

    private void configScalePaint(float scale) {
        float smokeWidth = (int) Math.ceil(scale);
        smokeWidth = smokeWidth < 0 ? 0 : smokeWidth;
        mWaveFormPaint.setStrokeWidth(smokeWidth);
        mWaveFormHighLightPaint.setStrokeWidth(smokeWidth);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
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

        return mDragDetector.onTouchEvent(event);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mBean == null) {
            return;
        }
        drawWave(canvas, mBean);
    }

    private void drawWave(Canvas canvas, @NonNull WaveFormInfo bean) {

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        List<Integer> data = bean.getData();
        int dataLength = bean.getLength();
        boolean is8Bit = bean.getBits() == 8;

        int dataPixel = 0;
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
                canvas.drawLine(finalAxisX, lowY, finalAxisX, highY,
                        dataPixel >= mThumbStartTimePixel && dataPixel <= mThumbEndTimePixel
                                ? mWaveFormHighLightPaint : mWaveFormPaint);
            }
            axisX += mThumbScale;
            dataPixel++;
        }
    }

    public void updateThumb(double thumbStartSecond, double thumbEndSecond) {
        if (mBean == null) {
            return;
        }
        mThumbStartSecond = thumbStartSecond;
        mThumbDuration = thumbEndSecond - thumbStartSecond;
        int sampleRate = mBean.getSample_rate();
        int samplesPerPixel = mBean.getSamples_per_pixel();
        mThumbStartTimePixel =
                WaveUtil.secondsToPixels(thumbStartSecond, sampleRate, samplesPerPixel, 1f);
        mThumbEndTimePixel =
                WaveUtil.secondsToPixels(thumbEndSecond, sampleRate, samplesPerPixel, 1f);
        float thumbRectLeft = mThumbStartTimePixel * mThumbScale;
        float thumbRectRight = mThumbEndTimePixel * mThumbScale;
        mDragDetector.setEnableRect(thumbRectLeft, 0, thumbRectRight, getHeight());

        invalidate();
    }

    @Override public void onDrag(float dx, float dy) {
        if (mBean == null) {
            return;
        }

        int sampleRate = mBean.getSample_rate();
        int samplesPerPixel = mBean.getSamples_per_pixel();

        mThumbStartSecond += WaveUtil.pixelsToSeconds(dx, sampleRate, samplesPerPixel, mThumbScale);

        // 右边界
        if (mThumbStartSecond + mThumbDuration > mTotalSecond) {
            mThumbStartSecond = mTotalSecond - mThumbDuration;
        }

        // 左边界
        if (mThumbStartSecond < 0) {
            mThumbStartSecond = 0d;
        }

        if (mOnDragThumbListener != null) {
            mOnDragThumbListener.onDrag(mThumbStartSecond);
        }
    }

    public void setOnDragThumbListener(OnDragThumbListener onDragThumbListener) {
        mOnDragThumbListener = onDragThumbListener;
    }

    public interface OnDragThumbListener {
        void onDrag(double startTime);
    }
}
