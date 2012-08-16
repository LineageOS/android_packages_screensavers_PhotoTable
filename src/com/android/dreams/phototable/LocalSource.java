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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.provider.MediaStore;
import android.util.Log;

import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;

/**
 * Picks a random image from the local store.
 */
public class LocalSource {
    private static final String TAG = "PhotoTable.LocalSource";
    static final boolean DEBUG = false;

    public static class ImageData {
        public String path;
        public int orientation;
    }

    private final ContentResolver mResolver;
    private final Context mContext;
    private final LinkedList<ImageData> mImageQueue;
    private final float mImageRatio;
    private final int mMaxQueueSize;
    private final Random mRNG;
    private int mNextPosition;

    public LocalSource(Context context) {
        mContext = context;
        mResolver = mContext.getContentResolver();
        mNextPosition = -1;
        mImageQueue = new LinkedList<ImageData>();
        mImageRatio = context.getResources().getInteger(R.integer.image_ratio) / 1000000f;
        mMaxQueueSize = context.getResources().getInteger(R.integer.image_queue_size);
        mRNG = new Random();
        fillQueue();
    }

    private void fillQueue() {
        log("filling queue");
        String[] projection = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.ORIENTATION};
        Cursor cursor = mResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
               projection, null, null, null);
        if (cursor != null) {
            if (cursor.getCount() > mMaxQueueSize && mNextPosition == -1) {
                mNextPosition = mRNG.nextInt() % (cursor.getCount() - mMaxQueueSize);
            }
            if (mNextPosition == -1) {
                mNextPosition = 0;
            }
            cursor.moveToPosition(mNextPosition);

            int dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
            int orientationIndex = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION);
            if (dataIndex < 0) {
                log("can't find the DATA column!");
            } else {
                while (mImageQueue.size() < mMaxQueueSize && !cursor.isAfterLast()) {
                    ImageData data = new ImageData();
                    data.path = cursor.getString(dataIndex);
                    data.orientation = cursor.getInt(orientationIndex);
                    mImageQueue.offer(data);
                    if (cursor.moveToNext()) {
                        mNextPosition++;
                    }
                }
                if (cursor.isAfterLast()) {
                    mNextPosition = 0;
                }
            }
            cursor.close();
        }
        Collections.shuffle(mImageQueue);
        log("queue contains: " + mImageQueue.size() + " items.");
    }

    public Bitmap next(BitmapFactory.Options options, int longSide, int shortSide) {
        log("decoding a local resource to " +  longSide + ", " + shortSide);
        Bitmap image = null;

        if (mImageQueue.isEmpty()) {
            fillQueue();
        }

        if (!mImageQueue.isEmpty()) {
            ImageData data = mImageQueue.poll();

            FileInputStream fis = null;
            try {
                log("decoding:" + data.path);
                fis = new FileInputStream(data.path);
                options.inJustDecodeBounds = true;
                options.inSampleSize = 1;
                BitmapFactory.decodeFileDescriptor(fis.getFD(), null, options);
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
                image = BitmapFactory.decodeFileDescriptor(fis.getFD(), null, options);
                rawLongSide = Math.max(options.outWidth, options.outHeight);
                rawShortSide = Math.max(options.outWidth, options.outHeight);
                ratio = Math.min((float) longSide / (float) rawLongSide,
                             (float) shortSide / (float) rawShortSide);

                if (ratio < 1.0f) {
                    log("still too big, scaling down by " + ratio);
                    options.outWidth = (int) (ratio * options.outWidth);
                    options.outHeight = (int) (ratio * options.outHeight);
                    image = Bitmap.createScaledBitmap(image,
                                                      options.outWidth, options.outHeight,
                                                      true);
                }

                if (data.orientation != 0) {
                    log("rotated by " + data.orientation + ": fixing");
                    if (data.orientation == 90 || data.orientation == 270) {
                        int tmp = options.outWidth;
                        options.outWidth = options.outHeight;
                        options.outHeight = tmp;
                    }
                    Matrix matrix = new Matrix();
                    matrix.setRotate(- data.orientation,
                                     (float) image.getWidth() / 2,
                                     (float) image.getHeight() / 2);
                    image = Bitmap.createBitmap(image, 0, 0,
                                                options.outWidth, options.outHeight,
                                                matrix, true);
                }

                log("returning bitmap sized to " + image.getWidth() + ", " + image.getHeight());
            } catch (Exception ex) {
                log(ex.toString());
                return null;
            } finally {
                try {
                    if (fis != null) { 
                        fis.close();
                    }
                } catch (Throwable t) {
                    log("close fail: " + t.toString());
                }
            }
        } else {
            log("device has no local images.");
        }

        return image;
    }

    public void setSeed(long seed) {
        mRNG.setSeed(seed);
    }

    private void log(String message) {
        if (DEBUG) {
            Log.i(TAG, message);
        }
    }
}
