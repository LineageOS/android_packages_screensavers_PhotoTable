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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.service.dreams.Dream;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.HashMap;

/**
 * A FrameLayout that holds two photos, back to back.
 */
public class PhotoCarousel extends FrameLayout {
    private static final String TAG = "PhotoCarousel";

    private final Flipper mFlipper;
    private final PhotoSourcePlexor mPhotoSource;
    private final GestureDetector mGestureDetector;
    private final View[] mPanel;
    private final BitmapFactory.Options mOptions;
    private final int mFlipDuration;
    private final int mDropPeriod;
    private boolean mOnce;
    private int mLongSide;
    private int mShortSide;
    private final HashMap<View, Bitmap> mBitmapStore;

    class Flipper implements Runnable {
        @Override
        public void run() {
            PhotoCarousel.this.flip(1f);
        }
    }

    public PhotoCarousel(Context context, AttributeSet as) {
        super(context, as);
        final Resources resources = getResources();
        mDropPeriod = resources.getInteger(R.integer.drop_period);
        mFlipDuration = resources.getInteger(R.integer.flip_duration);
        mOptions = new BitmapFactory.Options();
        mOptions.inTempStorage = new byte[32768];
        mPhotoSource = new PhotoSourcePlexor(context);
        mBitmapStore = new HashMap<View, Bitmap>();

        mPanel = new View[2];
        mFlipper = new Flipper();
        mGestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                        Log.i(TAG, "fling with " + vX);
                        flip(Math.signum(vX));
                        return true;
                    }
                });
    }

    private float lockTo180(float a) {
        return 180f * (float) Math.floor(a / 180f);
    }

    private float wrap360(float a) {
        return a - 360f * (float) Math.floor(a / 360f);
    }

    private class PhotoLoadTask extends AsyncTask<Void, Void, Bitmap> {
        private int mTries;
        private ImageView mDestination;

        public PhotoLoadTask(View destination) {
            mTries = 0;
            mDestination = (ImageView) destination;
        }

        @Override
        public Bitmap doInBackground(Void... unused) {
            Bitmap decodedPhoto = mPhotoSource.next(PhotoCarousel.this.mOptions,
                    PhotoCarousel.this.mLongSide, PhotoCarousel.this.mShortSide);
            return decodedPhoto;
        }

        @Override
        public void onPostExecute(Bitmap photo) {
            if (photo != null) {
                Bitmap old = mBitmapStore.get(mDestination);
                mDestination.setImageBitmap(photo);
                mBitmapStore.put(mDestination, photo);
                if (old != null) {
                    old.recycle();
                }
                PhotoCarousel.this.requestLayout();
            } else if (mTries < 3) {
                mTries++;
                this.execute();
            }
        }
    };

    public void flip(float sgn) {
        mPanel[0].animate().cancel();
        mPanel[1].animate().cancel();

        float frontY = mPanel[0].getRotationY();
        float backY = mPanel[1].getRotationY();

        frontY = wrap360(frontY);
        backY = wrap360(backY);

        mPanel[0].setRotationY(frontY);
        mPanel[1].setRotationY(backY);

        frontY = lockTo180(frontY + sgn * 180f);
        backY = lockTo180(backY + sgn * 180f);

        float frontA = 1f;
        float backA = 0f;
        if (frontY == 180f || frontY == -180f) {
            frontA = 0f;
            backA = 1f;
        } else {
            frontA = 1f;
            backA = 0f;
        }

        ViewPropertyAnimator frontAnim = mPanel[0].animate()
                .rotationY(frontY)
                .alpha(frontA)
                .setDuration(mFlipDuration);
        ViewPropertyAnimator backAnim = mPanel[1].animate()
                .rotationY(backY)
                .alpha(backA)
                .setDuration(mFlipDuration);

        int replaceIdx = 1;
        ViewPropertyAnimator replaceAnim = backAnim;
        if (frontA == 0f) {
            replaceAnim = frontAnim;
            replaceIdx = 0;
        }

        final View replaceView = mPanel[replaceIdx];
        replaceAnim.withEndAction(new Runnable() {
                @Override
                public void run() {
                    new PhotoLoadTask(replaceView)
                            .execute();
                }
            });

        frontAnim.start();
        backAnim.start();

        scheduleNext(mDropPeriod);
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int height = bottom - top;
        int width = right - left;

        mLongSide = (int) Math.max(width, height);
        mShortSide = (int) Math.min(width, height);

        if (!mOnce) {
            mOnce = true;

            mPanel[0] = findViewById(R.id.front);
            mPanel[1] = findViewById(R.id.back);

            new PhotoLoadTask(mPanel[0]).execute();
            new PhotoLoadTask(mPanel[1]).execute();

            scheduleNext(mDropPeriod);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return true;
    }

    public void scheduleNext(int delay) {
        removeCallbacks(mFlipper);
        postDelayed(mFlipper, delay);
    }
}
