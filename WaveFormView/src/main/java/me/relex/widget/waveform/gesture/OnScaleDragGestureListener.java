package me.relex.widget.waveform.gesture;

public interface OnScaleDragGestureListener {
    void onDrag(float dx, float dy);

    void onFling(float startX, float startY, float velocityX, float velocityY);

    void onScaleBegin();

    void onScale(float scaleFactor, float focusX, float focusY);

    void onScaleEnd();
}