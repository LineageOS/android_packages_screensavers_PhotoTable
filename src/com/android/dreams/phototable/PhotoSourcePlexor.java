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
import android.database.Cursor;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Loads images from a variety of sources.
 */
public class PhotoSourcePlexor extends PhotoSource {
    private static final String TAG = "PhotoTable.PhotoSourcePlexor";

    private final PhotoSource mPicasaSource;
    private final PhotoSource mLocalSource;
    private final PhotoSource mStockSource;

    public PhotoSourcePlexor(Context context) {
        super(context);
        mSourceName = TAG;
        mPicasaSource = new PicasaSource(context);
        mLocalSource = new LocalSource(context);
        mStockSource = new StockSource(context);
    }

    @Override
    protected Collection<ImageData> findImages(int howMany) {
        log(TAG, "finding images");
        LinkedList<ImageData> foundImages = new LinkedList<ImageData>();

        foundImages.addAll(mPicasaSource.findImages(howMany));
        log(TAG, "found " + foundImages.size() + " network images");

        foundImages.addAll(mLocalSource.findImages(howMany));
        log(TAG, "found " + foundImages.size() + " user images");

        if (foundImages.isEmpty()) {
            foundImages.addAll(mStockSource.findImages(howMany));
        }
        log(TAG, "found " + foundImages.size() + " images");

        return foundImages;
    }

    @Override
    protected InputStream getStream(ImageData data) {
        switch (data.type) {
        case PicasaSource.TYPE:
            return mPicasaSource.getStream(data);

        case LocalSource.TYPE:
            return mLocalSource.getStream(data);

        case StockSource.TYPE:
            return mStockSource.getStream(data);

        default:
            return null;
        }
    }
}
