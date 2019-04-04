package com.fxyan.draco;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.fxyan.draco.ui.BaseRenderer;

/**
 * @author fxYan
 */
public final class ThreeDSurfaceView
        extends GLSurfaceView
        implements View.OnTouchListener {

    private ScaleGestureDetector detector;
    private BaseRenderer renderer;

    private int lastPointerCount;
    private float downX, downY;

    public ThreeDSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        detector = new ScaleGestureDetector(context, new ScaleListener());
        setOnTouchListener(this);
    }

    public void setThreeDRenderer(BaseRenderer renderer) {
        this.renderer = renderer;
        super.setRenderer(renderer);
    }

    private boolean isMultiPointer = false;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        detector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE: {
                int pointerCount = event.getPointerCount();
                if (pointerCount > 1) {
                    isMultiPointer = true;
                } else if (pointerCount == 1 && !isMultiPointer) {
                    float moveX = event.getX();
                    float moveY = event.getY();
                    float degreeY = (moveX - downX) / 2;
                    renderer.addRotateY(degreeY);
                    float degreeX = (moveY - downY) / 2;
                    renderer.addRotateX(degreeX);
                    downX = moveX;
                    downY = moveY;
                }
            }
            break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isMultiPointer = false;
                break;
            default:
        }
        return true;
    }

    class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (renderer != null) {
                float scaleFactor = detector.getScaleFactor();

                float pivotX = detector.getFocusX();
                float pivotY = detector.getFocusY();

                float scale = renderer.getScale();
                scale *= scaleFactor;
                if (scale > 2) {
                    scale = 2f;
                }
                if (scale < 1) {
                    scale = 1.0f;
                }

                renderer.setScale(scale);
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {

        }
    }
}
