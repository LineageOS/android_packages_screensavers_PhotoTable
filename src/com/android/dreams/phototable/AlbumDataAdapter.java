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

import android.content.SharedPreferences;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Settings panel for photo flipping dream.
 */
public class AlbumDataAdapter extends ArrayAdapter<PhotoSource.AlbumData> {
    private static final String TAG = "AlbumDataAdapter";

    public static final String ALBUM_SET = "Enabled Album Set";

    private final SharedPreferences mSettings;
    private final LayoutInflater mInflater;
    private final int mLayout;
    private final ItemClickListener mListener;

    private Set<String> mEnabledAlbums;

    public AlbumDataAdapter(Context context, SharedPreferences settings,
            int resource, List<PhotoSource.AlbumData> objects) {
        super(context, resource, objects);
        mSettings = settings;
        mLayout = resource;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mListener = new ItemClickListener();
        mEnabledAlbums = AlbumSettings.getEnabledAlbums(mSettings);
    }

    @Override
    public View getView (int position, View convertView, ViewGroup parent) {
        View item = convertView;
        if (item == null) {
            item = mInflater.inflate(mLayout, parent, false);
        } else {
        }
        PhotoSource.AlbumData data = getItem(position);

        View vCheckBox = item.findViewById(R.id.enabled);
        if (vCheckBox != null && vCheckBox instanceof CheckBox) {
            CheckBox checkBox = (CheckBox) vCheckBox;
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(mEnabledAlbums.contains(data.id));
            checkBox.setText(data.toString());
            checkBox.setTag(R.id.data_payload, data);
            checkBox.setOnCheckedChangeListener(mListener);
        }

        return item;
    }

    public static class RecencyComparator implements Comparator<PhotoSource.AlbumData> {
        @Override
        public int compare(PhotoSource.AlbumData a, PhotoSource.AlbumData b) {
            if (a.updated == b.updated) {
                return a.title.compareTo(b.title);
            } else {
                return (int) Math.signum(b.updated - a.updated);
            }
        }
    }

    public static class AlphabeticalComparator implements Comparator<PhotoSource.AlbumData> {
        @Override
        public int compare(PhotoSource.AlbumData a, PhotoSource.AlbumData b) {
            return a.title.compareTo(b.title);
        }
    }

    private class ItemClickListener implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            PhotoSource.AlbumData data =
                    (PhotoSource.AlbumData) buttonView.getTag(R.id.data_payload);

            if (isChecked) {
                mEnabledAlbums.add(data.id);
            } else {
                mEnabledAlbums.remove(data.id);
            }

            AlbumSettings.setEnabledAlbums(mSettings , mEnabledAlbums);

            Log.i("adaptor", data.title + " is " + (isChecked ? "" : "not") + " enabled");
        }
    }
}
