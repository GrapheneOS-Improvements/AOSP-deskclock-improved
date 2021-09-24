/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.deskclock.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Context.NOTIFICATION_SERVICE
import android.database.ContentObserver
import android.media.AudioManager
import android.media.AudioManager.STREAM_ALARM
import android.provider.Settings
import android.util.AttributeSet
import android.widget.SeekBar
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference

import com.android.deskclock.R
import android.widget.SeekBar.OnSeekBarChangeListener
import com.android.deskclock.RingtonePreviewKlaxon.start
import com.android.deskclock.RingtonePreviewKlaxon.stop
import com.android.deskclock.data.DataModel.Companion.dataModel


class AlarmVolumePreference(context: Context?, attrs: AttributeSet?) : SeekBarPreference(context, attrs) {
    private var mPreviewPlaying = false

    private var mVolumeObserver: ContentObserver? = null
    private var mAudioManager: AudioManager = context?.getSystemService(AUDIO_SERVICE) as AudioManager

    override fun onAttached() {
        super.onAttached()
        max = mAudioManager.getStreamMaxVolume(STREAM_ALARM)
        value = mAudioManager.getStreamVolume(STREAM_ALARM)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.isClickable = false

        val context = context
        val localSeekBar = holder.findViewById(R.id.seekbar) as SeekBar

        mVolumeObserver = object : ContentObserver(localSeekBar.handler) {
            override fun onChange(selfChange: Boolean) {
                // Volume was changed elsewhere, update our slider.
                localSeekBar.progress = mAudioManager.getStreamVolume(STREAM_ALARM)
            }
        }

        context.contentResolver
                .registerContentObserver(Settings.System.CONTENT_URI, true, mVolumeObserver as ContentObserver)

        localSeekBar.setOnSeekBarChangeListener(
                object : OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            mAudioManager.setStreamVolume(STREAM_ALARM, progress, 0)
                        }
                        value = progress
                        onSeekbarChanged()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        if (!mPreviewPlaying && seekBar.progress != 0) {
                            // If we are not currently playing and progress is set tonon-zero,
                            // start.
                            start(
                                    context, dataModel.defaultAlarmRingtoneUri)
                            mPreviewPlaying = true
                            seekBar.postDelayed(
                                    {
                                        stop(context)
                                        mPreviewPlaying = false
                                    },
                                    ALARM_PREVIEW_DURATION_MS)
                        }
                    }
                })
    }

    override fun onDetached() {
        super.onDetached()
        context.contentResolver.unregisterContentObserver(mVolumeObserver!!)
    }

    private fun onSeekbarChanged() {
        isEnabled = doesDoNotDisturbAllowAlarmPlayback()
        this.setIcon(if (value == 0) R.drawable.ic_alarm_off else R.drawable.ic_alarm_small)
    }

    private fun doesDoNotDisturbAllowAlarmPlayback(): Boolean {
        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return (notificationManager.currentInterruptionFilter
                != NotificationManager.INTERRUPTION_FILTER_NONE)
    }

    companion object {
        private const val ALARM_PREVIEW_DURATION_MS: Long = 2000
    }
}