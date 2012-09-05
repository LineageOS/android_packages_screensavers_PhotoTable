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
import android.provider.MediaStore;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Loads images from the local store.
 */
public class LocalSource extends PhotoSource {
    private static final String TAG = "PhotoTable.LocalSource";

    private int mNextPosition;

    public static final int TYPE = 2;

    public LocalSource(Context context) {
        super(context);
        mSourceName = TAG;
        mNextPosition = -1;
        fillQueue();
    }

    @Override
    protected Collection<ImageData> findImages(int howMany) {
        log(TAG, "finding images");
        LinkedList<ImageData> foundImages = new LinkedList<ImageData>();

        String[] projection = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.ORIENTATION,
                MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME};
        String[] selectionArgs = {}; // settings go here
        String selection = "";
        for (String arg : selectionArgs) {
            if (selection.length() > 0) {
                selection += " OR ";
            }
            selection += MediaStore.Images.Media.BUCKET_ID + " = '" + arg + "'";
        }
        Cursor cursor = mResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, null);
        if (cursor != null) {
            if (cursor.getCount() > howMany && mNextPosition == -1) {
                mNextPosition = mRNG.nextInt() % (cursor.getCount() - howMany);
            }
            if (mNextPosition == -1) {
                mNextPosition = 0;
            }
            cursor.moveToPosition(mNextPosition);

            int dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
            int orientationIndex = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION);
            int bucketIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID);
            int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);

            if (dataIndex < 0) {
                log(TAG, "can't find the DATA column!");
            } else {
                while (foundImages.size() < howMany && !cursor.isAfterLast()) {
                    ImageData data = new ImageData();
                    data.type = TYPE;
                    data.url = cursor.getString(dataIndex);
                    data.orientation = cursor.getInt(orientationIndex);
                    foundImages.offer(data);
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
        log(TAG, "found " + foundImages.size() + " items.");
        return foundImages;
    }

    @Override
    protected InputStream getStream(ImageData data) {
        FileInputStream fis = null;
        try {
            log(TAG, "opening:" + data.url);
            fis = new FileInputStream(data.url);
        } catch (Exception ex) {
            log(TAG, ex.toString());
            fis = null;
        }

        return (InputStream) fis;
    }
}
