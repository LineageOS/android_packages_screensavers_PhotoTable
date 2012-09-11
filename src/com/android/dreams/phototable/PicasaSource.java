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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Loads images from Picasa.
 */
public class PicasaSource extends PhotoSource {
    private static final String TAG = "PhotoTable.PicasaSource";

    private static final String PICASA_AUTHORITY =
            "com.google.android.gallery3d.GooglePhotoProvider";

    private static final String PICASA_PHOTO_PATH = "photos";
    private static final String PICASA_ALBUM_PATH = "albums";

    private static final String PICASA_ID = "_id";
    private static final String PICASA_URL = "content_url";
    private static final String PICASA_ROTATION = "rotation";
    private static final String PICASA_ALBUM_ID = "album_id";
    private static final String PICASA_TITLE = "title";
    private static final String PICASA_THUMB = "thumbnail_url";
    private static final String PICASA_ALBUM_TYPE = "album_type";
    private static final String PICASA_ALBUM_UPDATED = "date_updated";

    private static final String PICASA_URL_KEY = "content_url";
    private static final String PICASA_TYPE_KEY = "type";
    private static final String PICASA_TYPE_FULL_VALUE = "full";
    private static final String PICASA_TYPE_SCREEN_VALUE = "screennail";
    private static final String PICASA_TYPE_THUMB_VALUE = "thumbnail";
    private static final String PICASA_TYPE_IMAGE_VALUE = "image";
    private static final String PICASA_BUZZ_TYPE = "Buzz";

    private final int mMaxPostAblums;

    private int mNextPosition;

    public PicasaSource(Context context, SharedPreferences settings) {
        super(context, settings);
        mSourceName = TAG;
        mNextPosition = -1;
        mMaxPostAblums = mResources.getInteger(R.integer.max_post_albums);
        log(TAG, "mSettings: " + mSettings);
        fillQueue();
    }

    @Override
    protected Collection<ImageData> findImages(int howMany) {
        log(TAG, "finding images");
        LinkedList<ImageData> foundImages = new LinkedList<ImageData>();
        String[] projection = {PICASA_ID, PICASA_URL, PICASA_ROTATION, PICASA_ALBUM_ID};
        StringBuilder selection = new StringBuilder();
        boolean usePosts = false;
        for (String id : AlbumSettings.getEnabledAlbums(mSettings)) {
            if (id.startsWith(TAG)) {
                String[] parts = id.split(":");
                if (parts.length > 1) {
                    if (PICASA_BUZZ_TYPE.equals(parts[1])) {
                        usePosts = true;
                    } else {
                        if (selection.length() > 0) {
                            selection.append(" OR ");
                        }
                        log(TAG, "adding on: " + parts[1]);
                        selection.append(PICASA_ALBUM_ID + " = '" + parts[1] + "'");
                    }
                }
            }
        }
        if (usePosts) {
            for (String id : findPostIds()) {
                if (selection.length() > 0) {
                    selection.append(" OR ");
                }
                log(TAG, "adding on: " + id);
                selection.append(PICASA_ALBUM_ID + " = '" + id + "'");
            }
        }

        if (selection.length() == 0) {
            return foundImages;
        }
        log(TAG, "selection is: " + selection.toString());

        Uri.Builder picasaUriBuilder = new Uri.Builder()
                .scheme("content")
                .authority(PICASA_AUTHORITY)
                .appendPath(PICASA_PHOTO_PATH);
        Cursor cursor = mResolver.query(picasaUriBuilder.build(),
                projection, selection.toString(), null, null);
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

    private Collection<String> findPostIds() {
        LinkedList<String> postIds = new LinkedList<String>();
        String[] projection = {PICASA_ID, PICASA_ALBUM_TYPE, PICASA_ALBUM_UPDATED};
        String order = PICASA_ALBUM_UPDATED + " DESC";
        Uri.Builder picasaUriBuilder = new Uri.Builder()
                .scheme("content")
                .authority(PICASA_AUTHORITY)
                .appendPath(PICASA_ALBUM_PATH)
                .appendQueryParameter(PICASA_TYPE_KEY, PICASA_TYPE_IMAGE_VALUE);
        Cursor cursor = mResolver.query(picasaUriBuilder.build(),
                projection, null, null, order);
        if (cursor != null) {
            cursor.moveToFirst();

            int idIndex = cursor.getColumnIndex(PICASA_ID);
            int typeIndex = cursor.getColumnIndex(PICASA_ALBUM_TYPE);

            if (idIndex < 0) {
                log(TAG, "can't find the ID column!");
            } else {
                while (postIds.size() < mMaxPostAblums && !cursor.isAfterLast()) {
                    if (typeIndex >= 0 && PICASA_BUZZ_TYPE.equals(cursor.getString(typeIndex))) {
                        postIds.add(cursor.getString(idIndex));
                    }
                    cursor.moveToNext();
                }
            }
            cursor.close();
        }
        return postIds;
    }

    @Override
    public Collection<AlbumData> findAlbums() {
        log(TAG, "finding albums");
        HashMap<String, AlbumData> foundAlbums = new HashMap<String, AlbumData>();
        String[] projection = {PICASA_ID, PICASA_TITLE, PICASA_THUMB, PICASA_ALBUM_TYPE,
                               PICASA_ALBUM_UPDATED};
        Uri.Builder picasaUriBuilder = new Uri.Builder()
                .scheme("content")
                .authority(PICASA_AUTHORITY)
                .appendPath(PICASA_ALBUM_PATH)
                .appendQueryParameter(PICASA_TYPE_KEY, PICASA_TYPE_IMAGE_VALUE);
        Cursor cursor = mResolver.query(picasaUriBuilder.build(),
                projection, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();

            int idIndex = cursor.getColumnIndex(PICASA_ID);
            int thumbIndex = cursor.getColumnIndex(PICASA_THUMB);
            int titleIndex = cursor.getColumnIndex(PICASA_TITLE);
            int typeIndex = cursor.getColumnIndex(PICASA_ALBUM_TYPE);
            int updatedIndex = cursor.getColumnIndex(PICASA_ALBUM_UPDATED);

            if (idIndex < 0) {
                log(TAG, "can't find the ID column!");
            } else {
                while (!cursor.isAfterLast()) {
                    String id = TAG + ":" + cursor.getString(idIndex);
                    boolean isBuzz = (typeIndex >= 0 &&
                                      PICASA_BUZZ_TYPE.equals(cursor.getString(typeIndex)));

                    if (isBuzz) {
                        id = TAG + ":" + PICASA_BUZZ_TYPE;
                    }
                    AlbumData data = foundAlbums.get(id);
                    if (data == null) {
                        data = new AlbumData();
                        data.id = id;

                        if (isBuzz) {
                            data.title =
                                    mResources.getString(R.string.posts_album_name, "Posts");
                        } else if (titleIndex >= 0) {
                            data.title = cursor.getString(titleIndex);
                        } else {
                            data.title =
                                    mResources.getString(R.string.unknown_album_name, "Unknown");
                        }

                        if (thumbIndex >= 0) {
                            data.thumbnailUrl = cursor.getString(thumbIndex);
                        }

                        log(TAG, "found " + data.title + "(" + data.id + ")");
                        foundAlbums.put(id, data);
                    }

                    if (updatedIndex >= 0) {
                        data.updated = (long) Math.max(data.updated,
                                                       cursor.getLong(updatedIndex));
                    }

                    cursor.moveToNext();
                }
            }
            cursor.close();

        }
        log(TAG, "found " + foundAlbums.size() + " items.");
        return foundAlbums.values();
    }

    @Override
    protected InputStream getStream(ImageData data) {
        InputStream is = null;
        try {
            Uri.Builder photoUriBuilder = new Uri.Builder()
                    .scheme("content")
                    .authority(PICASA_AUTHORITY)
                    .appendPath(PICASA_PHOTO_PATH)
                    .appendPath(data.id)
                    .appendQueryParameter(PICASA_TYPE_KEY, PICASA_TYPE_FULL_VALUE);
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
