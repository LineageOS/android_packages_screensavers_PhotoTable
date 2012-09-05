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
 * Loads images from Picasa.
 */
public class PicasaSource extends PhotoSource {
    private static final String TAG = "PhotoTable.PicasaSource";

    private static final String PICASA_ID = "_id";
    private static final String PICASA_URL = "content_url";
    private static final String PICASA_ROTATION = "rotation";
    private static final String PICASA_ALBUM_ID = "album_id";

    private static final String PICASA_URL_KEY = "content_url";
    private static final String PICASA_TYPE_KEY = "type";
    private static final String PICASA_TYPE_THUMB_VALUE = "full";

    private int mNextPosition;

    public static final int TYPE = 3;

    public PicasaSource(Context context) {
        super(context);
        mSourceName = TAG;
        mNextPosition = -1;
        fillQueue();
    }

    @Override
    protected Collection<ImageData> findImages(int howMany) {
        log(TAG, "finding images");
        LinkedList<ImageData> foundImages = new LinkedList<ImageData>();
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
            if (cursor.getCount() > howMany && mNextPosition == -1) {
                mNextPosition =
                        (int) Math.abs(mRNG.nextInt() % (cursor.getCount() - howMany));
            }
            if (mNextPosition == -1) {
                mNextPosition = 0;
            }
            log(TAG, "moving to position: " + mNextPosition);
            cursor.moveToPosition(mNextPosition);

            int idIndex = cursor.getColumnIndex(PICASA_ID);
            int urlIndex = cursor.getColumnIndex(PICASA_URL);
            int orientationIndex = cursor.getColumnIndex(PICASA_ROTATION);
            int bucketIndex = cursor.getColumnIndex(PICASA_ALBUM_ID);

            if (idIndex < 0) {
                log(TAG, "can't find the ID column!");
            } else {
                while (foundImages.size() < howMany && !cursor.isAfterLast()) {
                    if (idIndex >= 0) {
                        ImageData data = new ImageData();
                        data.type = TYPE;
                        data.id = cursor.getString(idIndex);

                        if (urlIndex >= 0) {
                            data.url = cursor.getString(urlIndex);
                        }

                        foundImages.offer(data);
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
        log(TAG, "found " + foundImages.size() + " items.");
        return foundImages;
    }

    @Override
    protected InputStream getStream(ImageData data) {
        InputStream is = null;
        try {
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
        } catch (FileNotFoundException fnf) {
            log(TAG, "file not found: " + fnf);
            is = null;
        } catch (IOException ioe) {
            log(TAG, "i/o exception: " + ioe);
            is = null;
        }

        return is;
    }
}
