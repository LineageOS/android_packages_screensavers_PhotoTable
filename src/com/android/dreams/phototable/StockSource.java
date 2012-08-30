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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.util.Random;

/**
 * Picks a random image from the local store.
 */
public class StockSource {
    private static final String TAG = "PhotoTable.StockSource";
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
    private static Random sRNG = new Random();

    private final Context mContext;
    private final Resources mResources;
    public StockSource(Context context) {
        mContext = context;
        mResources = context.getResources();
    }

    public Bitmap next(BitmapFactory.Options options, int longSide, int shortSide) {
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

    private static void log(String message) {
        if (DEBUG) {
            Log.i(TAG, message);
        }
    }

}
