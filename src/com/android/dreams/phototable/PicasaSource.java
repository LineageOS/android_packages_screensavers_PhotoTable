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
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;

/**
 * Picks a random image from the local store.
 */
public class PicasaSource {
    private static final String TAG = "PhotoTable.PicasaSource";
    private static final boolean DEBUG = false;

    private static final String PICASA_ID = "_id";
    private static final String PICASA_URL = "content_url";
    private static final String PICASA_ROTATION = "rotation";
    private static final String PICASA_ALBUM_ID = "album_id";

    private static final String PICASA_URL_KEY = "content_url";
    private static final String PICASA_TYPE_KEY = "type";
    private static final String PICASA_TYPE_THUMB_VALUE = "full";

    // This should be large enough for BitmapFactory to decode the header so
    // that we can mark and reset the input stream to avoid duplicate network i/o
    private static final int BUFFER_SIZE = 128 * 1024;

    public static class ImageData {
        public String id;
        public String url;
        public String bucketId;
        public int orientation;
    }

    private final ContentResolver mResolver;
    private final Context mContext;
    private final LinkedList<ImageData> mImageQueue;
    private final float mImageRatio;
    private final int mMaxQueueSize;
    private final Random mRNG;
    private int mNextPosition;

    public PicasaSource(Context context) {
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
        String[] projection = {PICASA_ID, PICASA_URL, PICASA_ROTATION, PICASA_ALBUM_ID};
        String[] selectionArgs = {}; // settings go here
        String selection = "";
        for (String arg : selectionArgs) {
            if (selection.length() > 0) {
                selection += " OR ";
            }
            selection += PICASA_ALBUM_ID + " = '" + arg + "'";
        }
        Uri.Builder picasaUriBuilder = new Uri.Builder()
                .scheme("content")
                .authority("com.google.android.gallery3d.GooglePhotoProvider")
                .appendPath("photos");
        Cursor cursor = mResolver.query(picasaUriBuilder.build(),
                projection, selection, null, null);
        if (cursor != null) {
            if (cursor.getCount() > mMaxQueueSize && mNextPosition == -1) {
                log("getcount: " + cursor.getCount());
                log("mMaxQueueSize: " + mMaxQueueSize);
                mNextPosition =
                        (int) Math.abs(mRNG.nextInt() % (cursor.getCount() - mMaxQueueSize));
            }
            if (mNextPosition == -1) {
                mNextPosition = 0;
            }
            log("moving to position: " + mNextPosition);
            cursor.moveToPosition(mNextPosition);

            int idIndex = cursor.getColumnIndex(PICASA_ID);
            int urlIndex = cursor.getColumnIndex(PICASA_URL);
            int orientationIndex = cursor.getColumnIndex(PICASA_ROTATION);
            int bucketIndex = cursor.getColumnIndex(PICASA_ALBUM_ID);

            if (idIndex < 0) {
                log("can't find the ID column!");
            } else {
                while (mImageQueue.size() < mMaxQueueSize && !cursor.isAfterLast()) {
                    if (idIndex >= 0) {
                        ImageData data = new ImageData();
                        data.id = cursor.getString(idIndex);

                        if (bucketIndex >= 0) {
                            data.bucketId = cursor.getString(bucketIndex);
                        }

                        if (urlIndex >= 0) {
                            data.url = cursor.getString(urlIndex);
                        }

                        mImageQueue.offer(data);
                    }
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
        log("decoding a picasa resource to " +  longSide + ", " + shortSide);
        Bitmap image = null;

        if (mImageQueue.isEmpty()) {
            fillQueue();
        }


        if (!mImageQueue.isEmpty()) {
            ImageData data = mImageQueue.poll();
            InputStream is = null;
            try {
                log("bucket is: " + data.bucketId);

                options.inJustDecodeBounds = false;
                Uri.Builder photoUriBuilder = new Uri.Builder()
                        .scheme("content")
                        .authority("com.google.android.gallery3d.GooglePhotoProvider")
                        .appendPath("photos")
                        .appendPath(data.id)
                        .appendQueryParameter(PICASA_TYPE_KEY, PICASA_TYPE_THUMB_VALUE);
                if (data.url != null) {
                    photoUriBuilder.appendQueryParameter(PICASA_URL_KEY, data.url);
                }
                is = mResolver.openInputStream(photoUriBuilder.build());
                BufferedInputStream bis = new BufferedInputStream(is);
                bis.mark(BUFFER_SIZE);

                options.inJustDecodeBounds = true;
                options.inSampleSize = 1;
                BitmapFactory.decodeStream(new BufferedInputStream(bis), null, options);
                int rawLongSide = Math.max(options.outWidth, options.outHeight);
                int rawShortSide = Math.min(options.outWidth, options.outHeight);
                log("I see bounds of " +  rawLongSide + ", " + rawShortSide);

                float ratio = Math.max((float) longSide / (float) rawLongSide,
                                       (float) shortSide / (float) rawShortSide);
                while (ratio < 0.5) {
                    options.inSampleSize *= 2;
                    ratio *= 2;
                }

                log("decoding with inSampleSize " +  options.inSampleSize);
                bis.reset();
                options.inJustDecodeBounds = false;
                image = BitmapFactory.decodeStream(bis, null, options);
                rawLongSide = Math.max(options.outWidth, options.outHeight);
                rawShortSide = Math.max(options.outWidth, options.outHeight);
                ratio = Math.max((float) longSide / (float) rawLongSide,
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
                    matrix.setRotate(data.orientation,
                                     (float) image.getWidth() / 2,
                                     (float) image.getHeight() / 2);
                    image = Bitmap.createBitmap(image, 0, 0,
                                                options.outHeight, options.outWidth,
                                                matrix, true);
                }

                log("returning bitmap sized to " + image.getWidth() + ", " + image.getHeight());
            } catch (FileNotFoundException fnf) {
                log("file not found: " + fnf);
            } catch (IOException ioe) {
                log("i/o exception: " + ioe);
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (Throwable t) {
                    log("close fail: " + t.toString());
                }
            }
        } else {
            log("device has no picasa images.");
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
