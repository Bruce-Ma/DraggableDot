package com.lsxiao.library;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * author:lsxiao
 * date:2015/12/25 17:02
 */
public class DraggableLayout extends FrameLayout implements ValueAnimator.AnimatorUpdateListener {
    DotView mTouchedDot;
    Circle mDragCircle;
    Circle mFixedCircle;
    Circle mOriginCircle;
    //intersection on Fixed Circle
    PointF mPointA;
    //intersection on Fixed Circle
    PointF mPointB;
    //intersection on Drag Circle
    PointF mPointC;
    //intersection on Drag Circle
    PointF mPointD;
    //mid point
    PointF mPointMid;
    Paint mPaint;
    Path mPath;
    private List<DotView> mDotViewList;

    float mStartPosX;
    float mStartPosY;

    float mLastPosX;
    float mLastPosY;

    float mLastLengthBetweenCenter;


    private State mState = State.IDLE;

    /**
     * control the circle center point animation.
     */
    ValueAnimator mPointAnimator;

    PointFEvaluator mPointFEvaluator;

    public static boolean mCanProcess = false;

    private int mCurAnimateCircle = -1;

    private enum State {
        IDLE,//停止状态
        STRETCH,//拉伸状态
        FIXED_MOVE_TO_DRAG,//固定圆圈移动到拖动圆圈
        FIXED_MOVE_TO_ORIGIN,//固定圆圈移动到原点
        DRAG_MOVE_TO_ORIGIN,//拖动圆圈移动到原点
        DRAG,//拖动状态
        DISMISSING,//消失中状态
        DISMISSED//消失状态
    }

    public void addDot(DotView dotView) {
        if (mDotViewList == null) {
            mDotViewList = new ArrayList<>();
        }
        mDotViewList.add(dotView);
    }

    public void removeDot(DotView dotView) {
        if (mDotViewList == null) {
            mDotViewList = new ArrayList<>();
        }
        mDotViewList.remove(dotView);
    }

    public DraggableLayout(Context context) {
        this(context, null);
    }

    public DraggableLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DraggableLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public static void bind(Activity activity) {
        DraggableLayout draggableLayout = new DraggableLayout(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        draggableLayout.setLayoutParams(params);

        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();

        for (int i = 0; i < decorView.getChildCount(); i++) {
            View view = decorView.getChildAt(i);
            if (view instanceof DraggableLayout) {
                return;
            }
        }

        for (int i = 0; i < decorView.getChildCount(); i++) {
            View v = decorView.getChildAt(i);
            decorView.removeView(v);
            draggableLayout.addView(v);
        }

        decorView.addView(draggableLayout, 0);
    }

    public interface onStateChangedListener {
        void onDragStart(DotView dotView);

        void onDragEnd(DotView dotView);

        void onRoboundStart(DotView dotView);

        void onRoboundEnd(DotView dotView);
    }

    public abstract class SimpleStateChangedListener implements onStateChangedListener {

    }

    private void init() {
        //the paint which used to draw bezierCurve
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Paint.Style.FILL);

        //the path which used to draw bezierCurve
        mPath = new Path();

        if (mDotViewList == null) {
            mDotViewList = new ArrayList<>();
        }
    }

    private DotView getTouchedDotView(MotionEvent event) {
        for (DotView dotView : mDotViewList) {
            int[] location = new int[2];
            dotView.getLocationOnScreen(location);
            final int w = dotView.getMeasuredWidth();
            final int h = dotView.getMeasuredHeight();
            int posX = (int) event.getRawX();
            int posY = (int) event.getRawY();

            int lowerX = location[0];
            int upperX = w + location[0];

            int lowerY = location[1];
            int upperY = h + location[1];

            final boolean isTouched = (posX > lowerX && posX < upperX && posY > lowerY && posY < upperY);
            if (isTouched) {
                return dotView;
            }
        }
        return null;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        init();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        //after super.dispatchDraw(canvas) make sure draw drag effect above children view.
        if (mTouchedDot == null) {
            return;
        }
        if (mFixedCircle == null || mDragCircle == null) {
            initCircle();
        }

        if (getState() == State.STRETCH
                || getState() == State.FIXED_MOVE_TO_DRAG
                || getState() == State.FIXED_MOVE_TO_ORIGIN
                || getState() == State.DRAG_MOVE_TO_ORIGIN) {
            calculatePoint();
            drawBezierCurve(canvas, mPaint);
            mFixedCircle.draw(canvas, mPaint);
        }

        if (getState() == State.STRETCH
                || getState() == State.FIXED_MOVE_TO_DRAG
                || getState() == State.FIXED_MOVE_TO_ORIGIN
                || getState() == State.DRAG_MOVE_TO_ORIGIN
                || getState() == State.DRAG
                || getState() == State.DISMISSING) {
            if (getState() == State.DISMISSING) {
                mDragCircle.draw(canvas, mPaint);
            } else {
                canvas.drawBitmap(mTouchedDot.getDrawingCache(), mDragCircle.mCenter.x - mDragCircle.mRadius, mDragCircle.mCenter.y - mDragCircle.mRadius, mPaint);
            }
        }
    }

    /**
     * draw Bezier curve
     *
     * @param canvas Canvas
     * @param paint  Paint
     */
    public void drawBezierCurve(Canvas canvas, Paint paint) {
        mPath.reset();
        mPath.moveTo(mPointA.x, mPointA.y);
        mPath.quadTo(mPointMid.x, mPointMid.y, mPointC.x, mPointC.y);
        mPath.lineTo(mPointD.x, mPointD.y);
        mPath.quadTo(mPointMid.x, mPointMid.y, mPointB.x, mPointB.y);
        mPath.lineTo(mPointA.x, mPointA.y);
        mPath.close();
        canvas.drawPath(mPath, paint);
    }

    /**
     * calculate 5 points that we used to draw Bezier curve.
     * <p/>
     * if the circle's property has changed,you need to recalculate the position of these points.
     */
    public void calculatePoint() {
        mPointA = mFixedCircle.getCutPoint(mDragCircle.mCenter, true);
        mPointC = mDragCircle.getCutPoint(mFixedCircle.mCenter, false);

        mPointB = mFixedCircle.getCutPoint(mDragCircle.mCenter, false);
        mPointD = mDragCircle.getCutPoint(mFixedCircle.mCenter, true);

        float midX = (mFixedCircle.mCenter.x + mDragCircle.mCenter.x) / 2;
        float midY = (mFixedCircle.mCenter.y + mDragCircle.mCenter.y) / 2;
        mPointMid = new PointF(midX, midY);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return handleIntercept(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return processTouchEvent(ev);
    }

    /**
     * handle whether intercept the event.only intercept event when dot is visible and touched.
     *
     * @param event MotionEvent
     * @return true, if need to intercept the event.
     */
    private boolean handleIntercept(MotionEvent event) {
        if (mTouchedDot == null) {
            mTouchedDot = getTouchedDotView(event);
        } else {
            DotView curTouchDot = getTouchedDotView(event);
            if (curTouchDot != null && curTouchDot != mTouchedDot) {
                setState(State.IDLE);
            }
            mTouchedDot = curTouchDot;
        }

        if (mTouchedDot == null || mTouchedDot.getVisibility() != VISIBLE) {
            return super.onInterceptTouchEvent(event);
        }
        mCanProcess = true;
        initCircle();
        return true;
    }

    /**
     * when over max stretch length,needing to start fixedCircle dismissed animation.
     *
     * @return true, if over the max stretch length
     */
    private boolean isOverMaxDistance() {
        final float length = mOriginCircle.distanceToOtherCircle(mDragCircle);
        return length > mTouchedDot.getMaxStretchLength() - 50;
    }

    /**
     * 获取到圆心之间的长度
     *
     * @return float
     */
    private float getLengthBetweenCenter() {
        return mFixedCircle.distanceToOtherCircle(mDragCircle);
    }

    /**
     * 状态
     *
     * @param state State
     */
    private void setState(State state) {
        mState = state;
    }

    private State getState() {
        return mState;
    }

    private void showDotView() {
        if (mTouchedDot.getVisibility() == VISIBLE) {
            return;
        }
        mTouchedDot.setVisibility(VISIBLE);
    }

    private void hideDotView() {
        if (mTouchedDot.getVisibility() == INVISIBLE) {
            return;
        }
        mTouchedDot.setVisibility(INVISIBLE);
    }

    private boolean isOutLayout(MotionEvent ev) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        displayMetrics = getResources().getDisplayMetrics();
        final float x = ev.getRawX();
        final float y = ev.getRawY();
        final float width = displayMetrics.widthPixels;
        final float height = displayMetrics.heightPixels;
        return x < 10 || x > width - 10 || y < 10 || y > height - 10;
    }

    /**
     * process the drag event.
     *
     * @param ev MotionEvent
     * @return true, if consume the event,and start to drag.
     */
    private boolean processTouchEvent(MotionEvent ev) {
        Log.d("xls", ev.getAction() + "");
        boolean processed;
        if (!mCanProcess) {
            return false;
        }
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                processed = processActionDown(ev);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                processed = processMove(ev);
                break;
            }
            case MotionEvent.ACTION_UP: {
                processed = processActionUp(ev);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                processed = processActionCancel(ev);
                break;
            }
            default: {
                processed = false;
            }

        }
        invalidate();
        return processed;
    }


    private boolean isAnimating() {
        return mPointAnimator != null && mPointAnimator.isRunning();
    }


    /**
     * 处理ActionDown事件
     *
     * @param ev MotionEvent
     */
    private boolean processActionDown(MotionEvent ev) {
        saveStartMotion(ev);
        saveLastMotion(ev);
        mLastLengthBetweenCenter = 0;
        return true;
    }


    /**
     * 处理move事件
     *
     * @param ev MotionEvent
     */
    private boolean processMove(MotionEvent ev) {
        final float x = ev.getX();
        final float y = ev.getY();
        final float dx = (x - mLastPosX);
        final float dy = (y - mLastPosY);
        mDragCircle.mCenter.x += dx;
        mDragCircle.mCenter.y += dy;
        updateFixedCircleRadius();

        switch (mState) {
            case IDLE: {
                /**do prepare for ready to stretch*/
                hideDotView();
                setState(State.STRETCH);
                break;
            }
            //拉伸状态
            case STRETCH: {
                //超过了可以拉动的区间
                if (isOverMaxDistance()) {
                    setState(State.FIXED_MOVE_TO_DRAG);
                    animate(State.FIXED_MOVE_TO_DRAG);
                }
                break;
            }
            case DRAG: {
                //在拖动状态触发
                if (!isOverMaxDistance()) {
                    setState(State.FIXED_MOVE_TO_ORIGIN);
                    animate(State.FIXED_MOVE_TO_ORIGIN);
                }
                break;
            }
            case DISMISSED: {
                //已经dismiss完成
                break;
            }
        }
        saveLastMotion(ev);
        return true;
    }


    /**
     * 处理ActionUp事件
     *
     * @param ev MotionEvent
     */
    private boolean processActionUp(MotionEvent ev) {
        switch (mState) {
            case IDLE: {
                break;
            }
            case STRETCH: {
                setState(State.DRAG_MOVE_TO_ORIGIN);
                animate(State.DRAG_MOVE_TO_ORIGIN);
                break;
            }
            case DRAG: {
                setState(State.DISMISSING);
                animate(State.DISMISSING);
                break;
            }
            case DISMISSED: {
                //回调事件
                setState(State.IDLE);
                break;
            }
            default: {

            }
        }
        return false;
    }

    /**
     * 处理ActionCancel事件
     *
     * @param ev MotionEvent
     */
    private boolean processActionCancel(MotionEvent ev) {
        switch (mState) {
            case IDLE: {
                break;
            }
            case STRETCH: {
                setState(State.DRAG_MOVE_TO_ORIGIN);
                break;
            }
            case DRAG: {
                setState(State.DISMISSING);
                break;
            }
            case DISMISSED: {
                setState(State.IDLE);
                break;
            }
        }
        return false;
    }


    /**
     * save the last motion
     *
     * @param ev MotionEvent
     */
    private void saveLastMotion(MotionEvent ev) {
        mLastPosX = ev.getX();
        mLastPosY = ev.getY();
    }

    /**
     * save the start motion
     *
     * @param ev MotionEvent
     */
    private void saveStartMotion(MotionEvent ev) {
        mStartPosX = ev.getX();
        mStartPosY = ev.getY();
    }

    private void initCircle() {
        //the dot location in window
        final int[] dotLocation = new int[2];
        //the layout location in window
        final int[] layoutLocation = new int[2];

        /*get the location instance*/
        mTouchedDot.getLocationInWindow(dotLocation);
        getLocationInWindow(layoutLocation);

        /*calculate the dx and dy*/
        int dx = -layoutLocation[0] + dotLocation[0];
        int dy = -layoutLocation[1] + dotLocation[1];

        mFixedCircle = new Circle(dx - getLeft() + mTouchedDot.getWidth() / 2, dy - getTop() + mTouchedDot.getWidth() / 2, mTouchedDot.getWidth() / 2);
        mDragCircle = Circle.copy(mFixedCircle);
        mOriginCircle = Circle.copy(mFixedCircle);
    }

    /**
     * the dot will be back the origin position by animate.
     */
    private void animate(State state) {
        if (mPointAnimator != null && mPointAnimator.isRunning()) {
            return;
        }

        if (mPointFEvaluator == null) {
            mPointFEvaluator = new PointFEvaluator();
        }

        switch (state) {
            case FIXED_MOVE_TO_DRAG: {
                //fixed move to drag
                mPointAnimator = ValueAnimator.ofObject(mPointFEvaluator, mFixedCircle.mCenter, mDragCircle.mCenter);
                mPointAnimator.setEvaluator(mPointFEvaluator);
                mPointAnimator.setDuration(200);
                mPointAnimator.setInterpolator(new FastOutSlowInInterpolator());
                mPointAnimator.addUpdateListener(this);
                mPointAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setState(State.DRAG);
                        invalidate();
                    }
                });
                break;
            }
            case FIXED_MOVE_TO_ORIGIN: {
                mFixedCircle = Circle.copy(mDragCircle);
                mPointAnimator = ValueAnimator.ofObject(mPointFEvaluator, mFixedCircle.mCenter, mOriginCircle.mCenter);
                mPointAnimator.setEvaluator(mPointFEvaluator);
                mPointAnimator.setDuration(200);
                mPointAnimator.setInterpolator(new FastOutSlowInInterpolator());
                mPointAnimator.addUpdateListener(this);
                mPointAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setState(State.STRETCH);
                        invalidate();
                    }
                });
                break;
            }
            case DRAG_MOVE_TO_ORIGIN: {
                mPointAnimator = ValueAnimator.ofObject(mPointFEvaluator, mDragCircle.mCenter, mOriginCircle.mCenter);
                mPointAnimator.setEvaluator(mPointFEvaluator);
                mPointAnimator.setDuration(200);
                mPointAnimator.setInterpolator(new FastOutSlowInInterpolator());
                mPointAnimator.addUpdateListener(this);
                mPointAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setState(State.IDLE);
                        invalidate();
                        showDotView();
                    }
                });
                break;
            }
            case DISMISSING: {
                mPointAnimator = ValueAnimator.ofFloat(mDragCircle.mRadius, 0);
                mPointAnimator.setDuration(200);
                mPointAnimator.setInterpolator(new AnticipateOvershootInterpolator());
                mPointAnimator.addUpdateListener(this);
                mPointAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setState(State.DISMISSED);
                        invalidate();
                    }
                });
                //开始dismiss动画
                break;
            }
        }

        mPointAnimator.start();
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        Log.d("xls", mState.name());
        switch (mState) {
            case FIXED_MOVE_TO_DRAG: {
                mFixedCircle.mCenter = (PointF) valueAnimator.getAnimatedValue();
                updateFixedCircleRadius();
                break;
            }
            case FIXED_MOVE_TO_ORIGIN: {
                mFixedCircle.mCenter = (PointF) valueAnimator.getAnimatedValue();
                updateFixedCircleRadius();
                break;
            }
            case DRAG_MOVE_TO_ORIGIN: {
                mDragCircle.mCenter = (PointF) valueAnimator.getAnimatedValue();
                updateFixedCircleRadius();
                break;
            }
            case DISMISSING: {
                mDragCircle.mRadius = (float) valueAnimator.getAnimatedValue();
                //开始dismiss动画
                break;
            }
        }
        invalidate();
    }

    /**
     * the fixed circle's radius is depend on the distance from fixedCircle's center to DragCircle's center.
     * you need invoke this method to update the fixedCircle's radius when the distance is changed.
     */
    private void updateFixedCircleRadius() {
        final float dLength = Math.max(mTouchedDot.getMaxStretchLength() - getLengthBetweenCenter(), 0);
        final float fraction = dLength / mTouchedDot.getMaxStretchLength();
        mFixedCircle.mRadius = fraction * mDragCircle.mRadius;
    }

    /**
     * reset fixed circle's radius and center point to default.
     */
    private void resetFixedCircle() {
        //the dot location in window
        final int[] dotLocation = new int[2];
        //the layout location in window
        final int[] layoutLocation = new int[2];

        /*get the location instance*/
        mTouchedDot.getLocationInWindow(dotLocation);
        getLocationInWindow(layoutLocation);

        /*calculate the dx and dy*/
        int dx = -layoutLocation[0] + dotLocation[0];
        int dy = -layoutLocation[1] + dotLocation[1];

        mFixedCircle = new Circle(dx - getLeft() + mTouchedDot.getWidth() / 2, dy - getTop() + mTouchedDot.getWidth() / 2, mTouchedDot.getWidth() / 2);
    }

    private Circle getOriginCircle() {
        //the dot location in window
        final int[] dotLocation = new int[2];
        //the layout location in window
        final int[] layoutLocation = new int[2];

        /*get the location instance*/
        mTouchedDot.getLocationInWindow(dotLocation);
        getLocationInWindow(layoutLocation);

        /*calculate the dx and dy*/
        int dx = -layoutLocation[0] + dotLocation[0];
        int dy = -layoutLocation[1] + dotLocation[1];

        return new Circle(dx - getLeft() + mTouchedDot.getWidth() / 2, dy - getTop() + mTouchedDot.getWidth() / 2, mTouchedDot.getWidth() / 2);
    }
}
