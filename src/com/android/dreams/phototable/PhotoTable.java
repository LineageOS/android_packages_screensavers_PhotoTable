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
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
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
        private boolean mStarted;
        private LinkedList<View> mOnTable;
        private Dream mDream;
        private int mDropPeriod;
        private float mImageRatio;
        private int mTableCapacity;
        private int mInset;
        private BitmapFactory.Options mOptions;
        private int mLongSide;
        private int mShortSide;
        private int mWidth;
        private int mHeight;
        private View mSelected;
        private long mSelectedTime;
        private LocalSource mLocalSource;
        private Resources mResources;
        private PointF[] mDropZone;

        public Table(Dream dream, AttributeSet as) {
            super(dream, as);
            mDream = dream;
            mResources = getResources();
            setBackgroundColor(mResources.getColor(R.color.tabletop));
            mInset = mResources.getDimensionPixelSize(R.dimen.photo_inset);
            mDropPeriod = mResources.getInteger(R.integer.drop_period);
            mImageRatio = mResources.getInteger(R.integer.image_ratio) / 1000000f;
            mTableCapacity = mResources.getInteger(R.integer.table_capacity);
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
            mSelected = selected;
            mSelectedTime = System.currentTimeMillis();
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
                    mDream.finish();
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

            if (mDropZone == null) {
                mDropZone = new PointF[4];
                mDropZone[0] = new PointF();
                mDropZone[1] = new PointF();
                mDropZone[2] = new PointF();
                mDropZone[3] = new PointF();
            }
            mDropZone[0].x = 0f;
            mDropZone[0].y = 0.75f * mHeight;
            mDropZone[1].x = 0f;
            mDropZone[1].y = 0f;
            mDropZone[2].x = 0f;
            mDropZone[2].y = 0f;
            mDropZone[3].x = 0.75f * mWidth;
            mDropZone[3].y = 0f;

            mLongSide = Math.max(mWidth, mHeight);
            mShortSide = Math.min(mWidth, mHeight);

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

        private void throwOnTable(final View photo) {
            mOnTable.offer(photo);
            log("start offscreen");
            int width = ((Integer) photo.getTag(R.id.photo_width));
            int height = ((Integer) photo.getTag(R.id.photo_height));
            photo.setRotation(-100.0f);
            photo.setX(-width);
            photo.setY(-height);
            dropOnTable(photo);
        }

        private void dropOnTable(final View photo) {
            float angle = randfrange(-60, 60f);
            PointF p = randInCenter((float) sRNG.nextGaussian(), (float) sRNG.nextGaussian(),
                                    mWidth, mHeight);
            float x = p.x;
            float y = p.y;

            log("drop it at " + x + ", " + y);

            float x0 = photo.getX();
            float y0 = photo.getY();
            float width = (float) ((Integer) photo.getTag(R.id.photo_width)).intValue();
            float height = (float) ((Integer) photo.getTag(R.id.photo_height)).intValue();

            x -= width / 2f;
            y -= height / 2f;
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
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .rotation(angle)
                    .x(x)
                    .y(y)
                    .setDuration(duration)
                    .withEndAction(new Runnable() {
                            @Override
                                public void run() {
                                while (mOnTable.size() > mTableCapacity) {
                                    fadeAway(mOnTable.poll());
                                }
                            }
                        });
            
            photo.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View target) {
                    if (hasSelection()) {
                        dropOnTable(getSelected());
                        clearSelection();
                    }
                    target.animate().cancel();
                    bringChildToFront(target);
                    setSelection(target);
                    pickUp(getSelected());
                }
            });
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
            
            photo.setOnClickListener(new OnClickListener() {
                  @Override
                  public void onClick(View target) {
                      if (getSelected() == photo) {
                          dropOnTable(photo);
                          clearSelection();
                      }
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
