package com.projecttango.examples.java.hellomotiontracking;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;


import com.almeros.android.multitouch.MoveGestureDetector;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MapView extends View implements GestureDetector.OnGestureListener {

    final String TAG = this.getClass().getName();

    public Bitmap map;
    public Boolean isInitialized = false;

    private GestureDetector gestureDetector;
    private MoveGestureDetector moveGestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private ArrayList<LongPressListener> longPressListeners = new ArrayList<>();

    private float moveX;
    private float moveY;
    private float scaleFactor;
    private float scaleFocusX;
    private float scaleFocusY;


    private Bitmap mark;
    private Matrix matrix;
    private float[] markPointInMeter;
    private float[] markPointInPixel;
    private float markDegree;
    private float orientationChange;
    private ArrayList<float[]> track;
    private double[] position;
    //private float METER_2_PIXEL = 33.7f;
    //Parameter values
    //How many pixels equals to one meter on the map
    final static float METER_2_PIXEL = 63;
    //Forward noise is +/- FORWARD_NOISE, unit meter.
    final static double FORWARD_NOISE = 0.3;
    //Turn noise equals to +/- TURN_NOISE, unit rad.
    final static double TURN_NOISE = Math.PI / 36;
    //Sense noise standard deviation equals to SENSE_NOISE, unit meter.
    final static double SENSE_NOISE = 2;
    //Number of particles in particle filter
    final static int NUM_PARTICLES = 200;
    //Original point on the map.
    final static float[] ORIGINAL_POINT = new float[]{0f, 0f};
    ;
    //Diameter of the initialization area
    final static float DIAMETER = 2;

    private Paint paint;

    public MapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        moveX = 0.f;
        moveY = 0.f;
        scaleFocusX = 0.f;
        scaleFocusY = 0.f;
        scaleFactor = 1.0f;
        gestureDetector = new GestureDetector(context, this);
        moveGestureDetector = new MoveGestureDetector(context, new MoveListener());
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        mark = BitmapFactory.decodeResource(this.getResources(), R.drawable.mark, options);
        matrix = new Matrix();

        markPointInMeter = new float[2];

        //map = BitmapFactory.decodeResource(this.getResources(), R.drawable.glodon6, null);
        map = BitmapFactory.decodeResource(this.getResources(), R.drawable.floor6_bw, null);


        //Initialization parameter values
        //Initial orientation of the mark
        markDegree = 180;

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(20);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();

        //Scale, move canvas

        canvas.scale(scaleFactor, scaleFactor, scaleFocusX, scaleFocusY);
        canvas.translate(moveX, moveY);

        //Draw Map
        canvas.drawBitmap(map, 0, 0, null);


        if (markPointInPixel != null) {
            matrix.setTranslate(markPointInPixel[0] - mark.getWidth() / 2, markPointInPixel[1] - mark.getHeight() / 2);
            matrix.preRotate(markDegree, mark.getWidth() / 2, mark.getHeight() / 2);
            canvas.drawBitmap(mark, matrix, null);

            paint.setTextSize(35);
            paint.setColor(Color.BLACK);
            //reverse x and y axis
            canvas.drawText(new DecimalFormat("#.00").format(markPointInMeter[1]), markPointInPixel[0] - mark.getWidth() / 2 - 100, markPointInPixel[1] - mark.getHeight() / 2 - 5, paint);
            canvas.drawText(new DecimalFormat("#.00").format(markPointInMeter[0]), markPointInPixel[0] - mark.getWidth() / 2 + 100, markPointInPixel[1] - mark.getHeight() / 2 - 5, paint);
        }

        if (track != null) {
            paint.setColor(Color.RED);
            for (int i = 0; i < track.size(); i++) {
                canvas.drawCircle(track.get(i)[0], track.get(i)[1], 5, paint);
            }
        }
        if (position != null) {
            paint.setColor(Color.BLUE);
            canvas.drawCircle((float) position[0], (float) position[1], 2, paint);
            paint.setTextSize(40);
            //canvas.drawText("X=" + decimalFormat.format(position[0]) + ", Y=" + decimalFormat.format(position[1]), 100, 150, paint);
        }

        canvas.restore();
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        moveGestureDetector.onTouchEvent(event);
        scaleGestureDetector.onTouchEvent(event);
        return true;
    }


    private class MoveListener extends MoveGestureDetector.SimpleOnMoveGestureListener {
        @Override
        public boolean onMove(MoveGestureDetector detector) {
                moveX += detector.getFocusDelta().x / scaleFactor;
                moveY += detector.getFocusDelta().y / scaleFactor;
                invalidate();
            return super.onMove(detector);
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {


            scaleFactor *= detector.getScaleFactor();
            scaleFocusX = detector.getFocusX();
            scaleFocusY = detector.getFocusY();

            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 1.5f));
            invalidate();
            return true;
        }
    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {
        markPointInPixel = point2pixel(motionEvent.getX(), motionEvent.getY());
        markPointInMeter[0] = (markPointInPixel[0] - ORIGINAL_POINT[0]) / METER_2_PIXEL;
        markPointInMeter[1] = (markPointInPixel[1] - ORIGINAL_POINT[1]) / METER_2_PIXEL;
        for (LongPressListener longPressListener : longPressListeners) {
            longPressListener.onLongPressInit(markPointInMeter[0], markPointInMeter[1]);
        }
        isInitialized=true;
        invalidate();
    }

    @Override
    public boolean onDown(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    private float[] point2pixel(float x, float y) {
        return new float[]{(x - (scaleFactor * moveX + scaleFocusX * (1 - scaleFactor))) / scaleFactor, (y - (scaleFactor * moveY + scaleFocusY * (1 - scaleFactor))) / scaleFactor};
    }

    private float[] point2meter(float x, float y) {
        return new float[]{(x - (scaleFactor * moveX + scaleFocusX * (1 - scaleFactor))) / scaleFactor / METER_2_PIXEL, (y - (scaleFactor * moveY + scaleFocusY * (1 - scaleFactor))) / scaleFactor / METER_2_PIXEL};
    }


    public void setTrack() {
        if (track == null) {
            this.track = new ArrayList<>();
        } else {
            this.track = null;
        }
        invalidate();
    }


    public void moveMark(double strideLength) {
        if (markPointInPixel != null) {
            if (track != null) {
                track.add(markPointInPixel.clone());
            }
            markPointInMeter[0] = (float) (markPointInMeter[0] + strideLength * Math.sin(markDegree / 180 * Math.PI));
            markPointInMeter[1] = (float) (markPointInMeter[1] - strideLength * Math.cos(markDegree / 180 * Math.PI));
            markPointInPixel[0] = markPointInMeter[0] * METER_2_PIXEL + ORIGINAL_POINT[0];
            markPointInPixel[1] = markPointInMeter[1] * METER_2_PIXEL + ORIGINAL_POINT[1];
            invalidate();
        }
    }

    public void moveMark(float angle, float strength) {
        if (markPointInPixel != null) {
            markPointInPixel[0] = (float) (markPointInPixel[0] + strength * Math.sin(angle / 180 * Math.PI));
            markPointInPixel[1] = (float) (markPointInPixel[1] - strength * Math.cos(angle / 180 * Math.PI));
            markPointInMeter[0] = (markPointInPixel[0] - ORIGINAL_POINT[0]) / METER_2_PIXEL;
            markPointInMeter[1] = (markPointInPixel[1] - ORIGINAL_POINT[1]) / METER_2_PIXEL;
            invalidate();
        }
    }

    public void moveMarkByCoordinates(float x, float y) {
        if (markPointInPixel != null) {
            markPointInPixel[0] = markPointInPixel[0] + x;
            markPointInPixel[1] = markPointInPixel[1] + y;
            markPointInMeter[0] = (markPointInPixel[0] - ORIGINAL_POINT[0]) / METER_2_PIXEL;
            markPointInMeter[1] = (markPointInPixel[1] - ORIGINAL_POINT[1]) / METER_2_PIXEL;
            invalidate();
        }
    }

    public void setMarkPointInPixel(float[] markPointInPixel) {
        this.markPointInPixel = markPointInPixel;
        invalidate();
    }

    public void setMarkDegree(float markDegree) {
        this.markDegree = markDegree;
        invalidate();
    }

    public double getMarkDegree() {
        return markDegree;
    }

    public double getOrienDegreeChange() {
        return orientationChange;
    }

    public void setMarkDegreeChange(float markDegreeChange) {
        this.markDegree += markDegreeChange;
        this.orientationChange = markDegreeChange;
        Log.d("Mark", "Orientation:" + Double.toString(markDegree));
        invalidate();
    }

    public void addLongPressListener(LongPressListener lpl) {
        longPressListeners.add(lpl);
    }

    public float[] getMarkPointInPixel() {
        return markPointInPixel;
    }

    public float[] getMarkPointInMeter() {
        return markPointInMeter;
    }

    public void setPosition(double[] position) {
        this.position = position;
        invalidate();
    }

    public Bitmap getMap() {
        return map;
    }




    public double getMeter2Pixel() {
        return METER_2_PIXEL;
    }

    public double getForwardNoise() {
        return FORWARD_NOISE;
    }

    public double getTurnNoise() {
        return TURN_NOISE;
    }

    public double getSenseNoise() {
        return SENSE_NOISE;
    }


}
