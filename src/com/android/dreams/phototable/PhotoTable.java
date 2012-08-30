/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.dreams.phototable;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.service.dreams.Dream;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

/**
 * Example interactive screen saver.
 */
public class PhotoTable extends Dream {
    private static final String TAG = "PhotoTable";
    private static final boolean DEBUG = false;
    private static final int[] PHOTOS = {R.drawable.photo_044_002,
        R.drawable.photo_039_002,
        R.drawable.photo_059_003,
        R.drawable.photo_070_004,
        R.drawable.photo_072_001,
        R.drawable.photo_077_002,
        R.drawable.photo_098_002,
        R.drawable.photo_119_003,
        R.drawable.photo_119_004,
        R.drawable.photo_126_001,
        R.drawable.photo_147_002,
        R.drawable.photo_175_004
    };

    private Table mTable;

    static class PhotoTouchListener implements View.OnTouchListener,
            GestureDetector.OnGestureListener {
        private static final int INVALID_POINTER = -1;
        private static final int MAX_POINTER_COUNT = 10;
        private final int mTouchSlop;
        private final int mTapTimeout;
        private final Table mTable;
        private final GestureDetector mDetector;
        private final float mBeta;
        private final float mTableRatio;
        private final boolean mEnableFling;
        private View mTarget;
        private float mInitialTouchX;
        private float mInitialTouchY;
        private float mInitialTouchA;
        private long mInitialTouchTime;
        private float mInitialTargetX;
        private float mInitialTargetY;
        private float mInitialTargetA;
        private int mA = INVALID_POINTER;
        private int mB = INVALID_POINTER;
        private float[] pts = new float[MAX_POINTER_COUNT];

        public PhotoTouchListener(Context context, Table table) {
            mTable = table;
            mDetector = new GestureDetector(context, this);
            final ViewConfiguration configuration = ViewConfiguration.get(context);
            mTouchSlop = configuration.getScaledTouchSlop();
            mTapTimeout = configuration.getTapTimeout();
            final Resources resources = context.getResources();
            mBeta = resources.getInteger(R.integer.table_damping) / 1000000f;
            mTableRatio = resources.getInteger(R.integer.table_ratio) / 1000000f;
            mEnableFling = resources.getBoolean(R.bool.enable_fling);
        }

        /** Get angle defined by first two touches, in degrees */
        private float getAngle(View target, MotionEvent ev) {
            float alpha = 0f;
            int a = ev.findPointerIndex(mA);
            int b = ev.findPointerIndex(mB);
            if (a >=0 && b >=0) {
                alpha = (float) (Math.atan2(pts[2*a + 1] - pts[2*b + 1],
                                            pts[2*a] - pts[2*b]) *
                                 180f / Math.PI);
            }
            return alpha;
        }

        private void resetTouch(View target) {
            mInitialTouchX = -1;
            mInitialTouchY = -1;
            mInitialTouchA = 0f;
            mInitialTargetX = (float) target.getX();
            mInitialTargetY = (float) target.getY();
            mInitialTargetA = (float) target.getRotation();
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float deltaX, float deltaY) {
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float dX, float dY) {
            if (!mEnableFling) {
                return false;
            }
            pts[0] = dX;
            pts[1] = dY;
            mTarget.getMatrix().mapVectors(pts);
            // velocity components in global coordinate frame
            dX = - pts[0];
            dY = - pts[1];

            if (DEBUG) {
                Log.i(TAG, "fling " + dX + ", " + dY);
            }

            final int idx = e2.getActionIndex();
            pts[0] = e2.getX(idx);
            pts[1] = e2.getY(idx);
            mTarget.getMatrix().mapPoints(pts);
            // starting position compionents in global corrdinate frame
            final float x0 = pts[0];
            final float y0 = pts[1];

            // velocity
            final float v = (float) Math.hypot(dX, dY);
            // number of steps to come to a stop
            final float n = (float) (- Math.log(v) / Math.log(mBeta));
            // distance travelled before stopping
            final float s = (float) (v * (1f - Math.pow(mBeta, n)) / (1f - mBeta));

            // ending posiiton after stopping
            final float x1 = x0 + s * dX / v;
            final float y1 = y0 + s * dY / v;

            if (DEBUG) {
                Log.i(TAG, "fling v = " + v);
                Log.i(TAG, "fling n = " + n);
                Log.i(TAG, "fling s = " + n);
                Log.i(TAG, "fling x0 = " + x0);
                Log.i(TAG, "fling y0 = " + y0);
                Log.i(TAG, "fling x1 = " + x1);
                Log.i(TAG, "fling y1 = " + y1);
            }

            final float photoWidth = ((Integer) mTarget.getTag(R.id.photo_width)).floatValue();
            final float photoHeight = ((Integer) mTarget.getTag(R.id.photo_height)).floatValue();
            final float tableWidth = mTable.getWidth();
            final float tableHeight = mTable.getHeight();

            pts[0] = 0f;
            pts[1] = 0f;
            pts[2] = photoHeight;
            pts[3] = photoWidth;
            mTarget.getMatrix().mapPoints(pts);
            pts[0] += x1;
            pts[1] += y1;
            pts[2] += x1;
            pts[3] += y1;

            boolean xOut = true;
            boolean yOut = true;
            for (int i = 0; i < 2; i++) {
                if(pts[2 * i] >= 0f && pts[2 * i] < tableWidth) {
                    xOut = false;
                    if (DEBUG) {
                        Log.i(TAG, "fling x in: " + pts[2 * i]);
                    }
                }
                if(pts[2 * i + 1] >= 0f && pts[2 * i + 1] < tableHeight) {
                    yOut = false;
                    if (DEBUG) {
                        Log.i(TAG, "fling y in: " + pts[2 * i + 1]);
                    }
                }
            }
            final View photo = mTarget;
            ViewPropertyAnimator animator = photo.animate()
                    .withLayer()
                    .x(x1)
                    .y(y1)
                    .setDuration((int) (100f * n));

            if (xOut || yOut) {
                if (DEBUG) {
                    Log.i(TAG, "fling away");
                }
                animator.withEndAction(new Runnable() {
                        @Override
                            public void run() {
                                mTable.fadeAway(photo);
                                mTable.launch();
                            }
                    });
            }

            return true;
        }

        @Override
        public boolean onTouch(View target, MotionEvent ev) {
            mTarget = target;
            if (mDetector.onTouchEvent(ev)) {
                return true;
            }
            final int action = ev.getActionMasked();

            // compute raw coordinates
            for(int i = 0; i < 10 && i < ev.getPointerCount(); i++) {
                pts[i*2] = ev.getX(i);
                pts[i*2 + 1] = ev.getY(i);
            }
            target.getMatrix().mapPoints(pts);

            switch (action) {
            case MotionEvent.ACTION_DOWN:
                mTable.moveToBackOfQueue(target);
                mInitialTouchTime = ev.getEventTime();
                mA = ev.getPointerId(ev.getActionIndex());
                resetTouch(target);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (mB == INVALID_POINTER) {
                    mB = ev.getPointerId(ev.getActionIndex());
                    mInitialTouchA = getAngle(target, ev);
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (mB == ev.getPointerId(ev.getActionIndex())) {
                    mB = INVALID_POINTER;
                    mInitialTargetA = (float) target.getRotation();
                }
                if (mA == ev.getPointerId(ev.getActionIndex())) {
                    mA = mB;
                    resetTouch(target);
                    mB = INVALID_POINTER;
                }
                break;

            case MotionEvent.ACTION_MOVE: {
                    if (mA != INVALID_POINTER) {
                        int idx = ev.findPointerIndex(mA);
                        float x = pts[2 * idx];
                        float y = pts[2 * idx + 1];
                        if (mInitialTouchX == -1 && mInitialTouchY == -1) {
                            mInitialTouchX = x;
                            mInitialTouchY = y;
                        }
                        if (mTable.getSelected() != target) {
                            target.animate().cancel();

                            target.setX((int) (mInitialTargetX + x - mInitialTouchX));
                            target.setY((int) (mInitialTargetY + y - mInitialTouchY));
                            if (mTable.mManualImageRotation && mB != INVALID_POINTER) {
                                float a = getAngle(target, ev);
                                target.setRotation(
                                        (int) (mInitialTargetA + a - mInitialTouchA));
                            }
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP: {
                    if (mA != INVALID_POINTER) {
                        int idx = ev.findPointerIndex(mA);
                        float x = pts[2 * idx];
                        float y = pts[2 * idx + 1];
                        if (mInitialTouchX == -1 && mInitialTouchY == -1) {
                            mInitialTouchX = x;
                            mInitialTouchY = y;
                        }
                        double distance = Math.hypot(x - mInitialTouchX,
                                                     y - mInitialTouchY);
                        if (mTable.getSelected() == target) {
                            mTable.dropOnTable(target);
                            mTable.clearSelection();
                        } else if ((ev.getEventTime() - mInitialTouchTime) < mTapTimeout &&
                                   distance < mTouchSlop) {
                            // tap
                            target.animate().cancel();
                            mTable.setSelection(target);
                        }
                        mA = INVALID_POINTER;
                        mB = INVALID_POINTER;
                    }
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                break;
            }
            return true;
        }
    }

    public static class Table extends FrameLayout {
        class Launcher implements Runnable {
            private final Table mTable;
            public Launcher(Table table) {
                mTable = table;
            }

            @Override
            public void run() {
                mTable.launch();
            }
        }

        private static final long MAX_SELECTION_TIME = 10000L;
        private static Random sRNG = new Random();

        private final Launcher mLauncher;
        private final LinkedList<View> mOnTable;
        private final Dream mDream;
        private final int mDropPeriod;
        private final float mImageRatio;
        private final float mTableRatio;
        private final float mImageRotationLimit;
        private final boolean mManualImageRotation;
        private final boolean mTapToExit;
        private final int mTableCapacity;
        private final int mInset;
        private final LocalSource mLocalSource;
        private final Resources mResources;
        private boolean mStarted;
        private boolean mIsLandscape;
        private BitmapFactory.Options mOptions;
        private int mLongSide;
        private int mShortSide;
        private int mWidth;
        private int mHeight;
        private View mSelected;
        private long mSelectedTime;

        public Table(Dream dream, AttributeSet as) {
            super(dream, as);
            mDream = dream;
            mResources = getResources();
            setBackground(mResources.getDrawable(R.drawable.table));
            mInset = mResources.getDimensionPixelSize(R.dimen.photo_inset);
            mDropPeriod = mResources.getInteger(R.integer.drop_period);
            mImageRatio = mResources.getInteger(R.integer.image_ratio) / 1000000f;
            mTableRatio = mResources.getInteger(R.integer.table_ratio) / 1000000f;
            mImageRotationLimit = (float) mResources.getInteger(R.integer.max_image_rotation);
            mTableCapacity = mResources.getInteger(R.integer.table_capacity);
            mManualImageRotation = mResources.getBoolean(R.bool.enable_manual_image_rotation);
            mTapToExit = mResources.getBoolean(R.bool.enable_tap_to_exit);
            mOnTable = new LinkedList<View>();
            mOptions = new BitmapFactory.Options();
            mOptions.inTempStorage = new byte[32768];
            mLocalSource = new LocalSource(getContext());
            mLauncher = new Launcher(this);
            mStarted = false;
        }

        public boolean hasSelection() {
            return mSelected != null;
        }

        public View getSelected() {
            return mSelected;
        }

        public void clearSelection() {
            mSelected = null;
        }

        public void setSelection(View selected) {
            assert(selected != null);
            if (mSelected != null) {
                dropOnTable(mSelected);
            }
            mSelected = selected;
            mSelectedTime = System.currentTimeMillis();
            bringChildToFront(selected);
            pickUp(selected);
        }

        static float lerp(float a, float b, float f) {
            return (b-a)*f + a;
        }

        static float randfrange(float a, float b) {
            return lerp(a, b, sRNG.nextFloat());
        }

        static PointF randFromCurve(float t, PointF[] v) {
            PointF p = new PointF();
            if (v.length == 4 && t >= 0f && t <= 1f) {
                float a = (float) Math.pow(1f-t, 3f);
                float b = (float) Math.pow(1f-t, 2f) * t;
                float c = (1f-t) * (float) Math.pow(t, 2f);
                float d = (float) Math.pow(t, 3f);

                p.x = a * v[0].x + 3 * b * v[1].x + 3 * c * v[2].x + d * v[3].x;
                p.y = a * v[0].y + 3 * b * v[1].y + 3 * c * v[2].y + d * v[3].y;
            }
            return p;
        }

        private static PointF randInCenter(float i, float j, int width, int height) {
            log("randInCenter (" + i + ", " + j + ", " + width + ", " + height + ")");
            PointF p = new PointF();
            p.x = 0.5f * width + 0.15f * width * i;
            p.y = 0.5f * height + 0.15f * height * j;
            log("randInCenter returning " + p.x + "," + p.y);
            return p;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (hasSelection()) {
                    dropOnTable(getSelected());
                    clearSelection();
                } else  {
                    if (mTapToExit) {
                        mDream.finish();
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            log("onLayout (" + left + ", " + top + ", " + right + ", " + bottom + ")");

            mHeight = bottom - top;
            mWidth = right - left;

            mLongSide = (int) (mImageRatio * Math.max(mWidth, mHeight));
            mShortSide = (int) (mImageRatio * Math.min(mWidth, mHeight));

            boolean isLandscape = mWidth > mHeight;
            if (mIsLandscape != isLandscape) {
                for (View photo: mOnTable) {
                    if (photo == getSelected()) {
                        pickUp(photo);
                    } else {
                        dropOnTable(photo);
                    }
                }
                mIsLandscape = isLandscape;
            }
            start();
        }

        @Override
        public boolean isOpaque() {
            return true;
        }

        @SuppressWarnings("deprecation")
        private void launch() {
            scheduleNext();

            log("launching");
            setSystemUiVisibility(View.STATUS_BAR_HIDDEN);
            if (hasSelection() &&
                    (System.currentTimeMillis() - mSelectedTime) > MAX_SELECTION_TIME) {
                dropOnTable(getSelected());
                clearSelection();
            } else {
                log("inflate it");
                AsyncTask<Void, Void, View> task = new AsyncTask<Void, Void, View>() {
                    @Override
                    public View doInBackground(Void... unused) {
                        log("load a new photo");
                        LayoutInflater inflater = (LayoutInflater) getContext()
                               .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        View photo = inflater.inflate(R.layout.photo, null);
                        ImageView image = (ImageView) photo;
                        Drawable[] layers = new Drawable[2];
                        Bitmap decodedPhoto = null;
                        decodedPhoto = mLocalSource.next(mOptions, mLongSide, mShortSide);
                        if (decodedPhoto == null) {
                            decodedPhoto = nextStockPhoto(mOptions, mLongSide, mShortSide);
                        }
                        int photoWidth = mOptions.outWidth;
                        int photoHeight = mOptions.outHeight;
                        if (mOptions.outWidth <= 0 || mOptions.outHeight <= 0) {
                            photo = null;
                        } else {
                            layers[0] = new BitmapDrawable(mResources, decodedPhoto);
                            layers[1] = mResources.getDrawable(R.drawable.frame);
                            LayerDrawable layerList = new LayerDrawable(layers);
                            layerList.setLayerInset(0, mInset, mInset, mInset, mInset);
                            image.setImageDrawable(layerList);

                            photo.setTag(R.id.photo_width, new Integer(photoWidth));
                            photo.setTag(R.id.photo_height, new Integer(photoHeight));
                        }

                        return photo;
                    }

                    @Override
                    public void onPostExecute(View photo) {
                        if (photo != null) {
                            addView(photo, new LayoutParams(LayoutParams.WRAP_CONTENT,
                                                            LayoutParams.WRAP_CONTENT));
                            if (hasSelection()) {
                                bringChildToFront(getSelected());
                            }
                            int width = ((Integer) photo.getTag(R.id.photo_width)).intValue();
                            int height = ((Integer) photo.getTag(R.id.photo_height)).intValue();

                            log("drop it");
                            throwOnTable(photo);
                        }
                    }
                };
                task.execute();
            }
        }

        private Bitmap nextStockPhoto(BitmapFactory.Options options, int longSide, int shortSide) {
            log("decoding a local resource to " +  longSide + ", " + shortSide);
            int photo = PHOTOS[Math.abs(sRNG.nextInt() % PHOTOS.length)];

            options.inJustDecodeBounds = true;
            options.inSampleSize = 1;
            BitmapFactory.decodeResource(mResources, photo, options);
            int rawLongSide = Math.max(options.outWidth, options.outHeight);
            int rawShortSide = Math.max(options.outWidth, options.outHeight);
            log("I see bounds of " +  rawLongSide + ", " + rawShortSide);
            float ratio = Math.min((float) longSide / (float) rawLongSide,
                                   (float) shortSide / (float) rawShortSide);
            while (ratio < 0.5) {
                options.inSampleSize *= 2;
                ratio *= 2;
            }
            log("decoding with inSampleSize " +  options.inSampleSize);
            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeResource(mResources, photo, options);
            rawLongSide = Math.max(options.outWidth, options.outHeight);
            rawShortSide = Math.max(options.outWidth, options.outHeight);
            ratio = Math.min((float) longSide / (float) rawLongSide,
                             (float) shortSide / (float) rawShortSide);

            if (ratio < 1.0f) {
                log("still too big, scaling down by " + ratio);
                int photoWidth = (int) (ratio * options.outWidth);
                int photoHeight = (int) (ratio * options.outHeight);
                bitmap = Bitmap.createScaledBitmap(bitmap, photoWidth, photoHeight, true);
            }

            log("returning bitmap sized to " + bitmap.getWidth() + ", " + bitmap.getHeight());
            return bitmap;
        }

        private void fadeAway(final View photo) {
            // fade out of view
            photo.animate().cancel();
            photo.animate()
                    .withLayer()
                    .alpha(0f)
                    .setDuration(1000)
                    .withEndAction(new Runnable() {
                            @Override
                                public void run() {
                                removeView(photo);
                                recycle(photo);
                            }
                        });
        }

        private void moveToBackOfQueue(View photo) {
            // make this photo the last to be removed.
            bringChildToFront(photo);
            invalidate();
            mOnTable.remove(photo);
            mOnTable.offer(photo);
        }

        private void throwOnTable(final View photo) {
            mOnTable.offer(photo);
            log("start offscreen");
            int width = ((Integer) photo.getTag(R.id.photo_width));
            int height = ((Integer) photo.getTag(R.id.photo_height));
            photo.setRotation(-100.0f);
            photo.setX(-mLongSide);
            photo.setY(-mLongSide);
            dropOnTable(photo);
        }

        private void dropOnTable(final View photo) {
            float angle = randfrange(-mImageRotationLimit, mImageRotationLimit);
            PointF p = randInCenter((float) sRNG.nextGaussian(), (float) sRNG.nextGaussian(),
                                    mWidth, mHeight);
            float x = p.x;
            float y = p.y;

            log("drop it at " + x + ", " + y);

            float x0 = photo.getX();
            float y0 = photo.getY();
            float width = (float) ((Integer) photo.getTag(R.id.photo_width)).intValue();
            float height = (float) ((Integer) photo.getTag(R.id.photo_height)).intValue();

            x -= mTableRatio * mLongSide / 2f;
            y -= mTableRatio * mLongSide / 2f;
            log("fixed offset is " + x + ", " + y);

            float dx = x - x0;
            float dy = y - y0;

            float dist = (float) (Math.sqrt(dx * dx + dy * dy));
            int duration = (int) (1000f * dist / 400f);
            duration = Math.max(duration, 1000);

            log("animate it");
            // toss onto table
            photo.animate()
                    .withLayer()
                    .scaleX(mTableRatio / mImageRatio)
                    .scaleY(mTableRatio / mImageRatio)
                    .rotation(angle)
                    .x(x)
                    .y(y)
                    .setDuration(duration)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(new Runnable() {
                            @Override
                                public void run() {
                                while (mOnTable.size() > mTableCapacity) {
                                    fadeAway(mOnTable.poll());
                                }
                            }
                        });

            photo.setOnTouchListener(new PhotoTouchListener(getContext(), this));
        }

        /** wrap all orientations to the interval [-180, 180). */
        private float wrapAngle(float angle) {
            float result = angle + 180;
            result = ((result % 360) + 360) % 360; // catch negative numbers
            result -= 180;
            return result;
        }

        private void pickUp(final View photo) {
            float photoWidth = photo.getWidth();
            float photoHeight = photo.getHeight();

            float scale = Math.min(getHeight() / photoHeight, getWidth() / photoWidth);

            log("target it");
            float x = (getWidth() - photoWidth) / 2f;
            float y = (getHeight() - photoHeight) / 2f;

            float x0 = photo.getX();
            float y0 = photo.getY();
            float dx = x - x0;
            float dy = y - y0;

            float dist = (float) (Math.sqrt(dx * dx + dy * dy));
            int duration = (int) (1000f * dist / 1000f);
            duration = Math.max(duration, 500);

            photo.setRotation(wrapAngle(photo.getRotation()));

            log("animate it");
            // toss onto table
            photo.animate()
                    .withLayer()
                    .rotation(0f)
                    .scaleX(scale)
                    .scaleY(scale)
                    .x(x)
                    .y(y)
                    .setDuration(duration)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(new Runnable() {
                            @Override
                                public void run() {
                                log("endtimes: " + photo.getX());
                            }
                        });
        }

        private static void log(String message) {
            if (DEBUG) {
                Log.i(TAG, message);
            }
        }

        private void recycle(View photo) {
            ImageView image = (ImageView) photo;
            LayerDrawable layers = (LayerDrawable) image.getDrawable();
            BitmapDrawable bitmap = (BitmapDrawable) layers.getDrawable(0);
            bitmap.getBitmap().recycle();
        }

        public void recycleAll() {
            new AsyncTask<Void, Void, Void>() {
                @Override
                public void onPreExecute() {
                    for (View photo: mOnTable) {
                        removeView(photo);
                    }
                }

                @Override
                public Void doInBackground(Void... unused) {
                    while (!mOnTable.isEmpty()) {
                        recycle(mOnTable.poll());
                    }
                    return null;
                }
            }.execute();
        }

        public void start() {
            if (!mStarted) {
                log("kick it");
                mStarted = true;
                for (int i = 0; i < mResources.getInteger(R.integer.initial_drop); i++) {
                    launch();
                }
            }
        }

        public void scheduleNext() {
            removeCallbacks(mLauncher);
            postDelayed(mLauncher, mDropPeriod);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        setInteractive(true);
        mTable = new Table(this, null);
        setContentView(mTable);
        lightsOut();
    }

    @Override
    public void onDestroy() {
        mTable.recycleAll();
        super.onDestroy();
    }
}
