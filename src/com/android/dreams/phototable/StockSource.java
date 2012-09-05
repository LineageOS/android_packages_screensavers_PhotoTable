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
import android.util.Log;

import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Picks a random image from the local store.
 */
public class StockSource extends PhotoSource {
    private static final String TAG = "PhotoTable.StockSource";
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

    private final LinkedList<ImageData> mImageList;
    private int mNextPosition;

    public static final int TYPE = 1;

    public StockSource(Context context) {
        super(context);
        mSourceName = TAG;
        mImageList = new LinkedList<ImageData>();
        fillQueue();
    }

    @Override
    protected Collection<ImageData> findImages(int howMany) {
        if (mImageList.isEmpty()) {
            for (int i = 0; i < PHOTOS.length; i++) {
                ImageData data = new ImageData();
                data.type = TYPE;
                data.id = Integer.toString(PHOTOS[i]);
                mImageList.offer(data);
            }
        }
        return mImageList;
    }

    @Override
    protected InputStream getStream(ImageData data) {
        InputStream is = null;
        try {
            log(TAG, "opening:" + data.id);
            is = mResources.openRawResource(Integer.valueOf(data.id));
        } catch (Exception ex) {
            log(TAG, ex.toString());
            is = null;
        }

        return is;
    }
}
