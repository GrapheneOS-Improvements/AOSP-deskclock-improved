/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.deskclock.settings;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SeekBarPreference;

import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;

import com.android.deskclock.R;
import com.android.deskclock.RingtonePreviewKlaxon;
import com.android.deskclock.data.DataModel;

import static android.content.Context.AUDIO_SERVICE;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.media.AudioManager.STREAM_ALARM;

public class AlarmVolumePreference extends SeekBarPreference {

    private static final long ALARM_PREVIEW_DURATION_MS = 2000;

    private boolean mPreviewPlaying;
    private ContentObserver mVolumeObserver;
    private final AudioManager mAudioManager;

    public AlarmVolumePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAudioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        setMax(mAudioManager.getStreamMaxVolume(STREAM_ALARM));
        setValue(mAudioManager.getStreamVolume(STREAM_ALARM));
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(false);

        Context context = getContext();
        SeekBar localSeekBar = (SeekBar) holder.findViewById(R.id.seekbar);

        mVolumeObserver =
                new ContentObserver(localSeekBar.getHandler()) {
                    @Override
                    public void onChange(boolean selfChange) {
                        // Volume was changed elsewhere, update our slider.
                        localSeekBar.setProgress(mAudioManager.getStreamVolume(STREAM_ALARM));
                    }
                };

        context.getContentResolver()
                .registerContentObserver(Settings.System.CONTENT_URI, true, mVolumeObserver);

        localSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            mAudioManager.setStreamVolume(STREAM_ALARM, progress, 0);
                        }
                        setValue(progress);
                        onSeekbarChanged();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if (!mPreviewPlaying && seekBar.getProgress() != 0) {
                            // If we are not currently playing and progress is set tonon-zero,
                            // start.
                            RingtonePreviewKlaxon.start(
                                    context, DataModel.getDataModel().getDefaultAlarmRingtoneUri());
                            mPreviewPlaying = true;
                            seekBar.postDelayed(
                                    () -> {
                                        RingtonePreviewKlaxon.stop(context);
                                        mPreviewPlaying = false;
                                    },
                                    ALARM_PREVIEW_DURATION_MS);
                        }
                    }
                });
    }

    @Override
    public void onDetached() {
        super.onDetached();
        getContext().getContentResolver().unregisterContentObserver(mVolumeObserver);
    }

    private void onSeekbarChanged() {
        setEnabled(doesDoNotDisturbAllowAlarmPlayback());
        setIcon(getValue() == 0 ? R.drawable.ic_alarm_off : R.drawable.ic_alarm_small);
    }

    private boolean doesDoNotDisturbAllowAlarmPlayback() {
        final NotificationManager notificationManager =
                (NotificationManager) getContext().getSystemService(NOTIFICATION_SERVICE);
        return notificationManager.getCurrentInterruptionFilter()
                != NotificationManager.INTERRUPTION_FILTER_NONE;
    }
}
