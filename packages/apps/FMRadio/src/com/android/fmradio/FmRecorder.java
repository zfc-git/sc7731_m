/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.fmradio;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * This class provider interface to recording, stop recording, save recording
 * file, play recording file
 */
public class FmRecorder implements MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener {
    private static final String TAG = "FmRecorder";
    // file prefix
    public static final String RECORDING_FILE_PREFIX = "FM";
    // file extension
    public static final String RECORDING_FILE_EXTENSION = ".3gpp";
    /**
     * SPRD: bug474741, recording format selection.
     * @{
     */
    public static final String RECORDING_FILE_EXTENSION_AMR = ".amr";
    /**
     * @}
     */

    // recording file folder
    public static final String FM_RECORD_FOLDER = "FM Recording";
    private static final String RECORDING_FILE_TYPE = "audio/3gpp";
    private static final String RECORDING_FILE_SOURCE = "FM Recordings";
    // error type no sdcard
    public static final int ERROR_SDCARD_NOT_PRESENT = 0;
    // error type sdcard not have enough space
    public static final int ERROR_SDCARD_INSUFFICIENT_SPACE = 1;
    // error type can't write sdcard
    public static final int ERROR_SDCARD_WRITE_FAILED = 2;
    // error type recorder internal error occur
    public static final int ERROR_RECORDER_INTERNAL = 3;

    public static final int ERROR_RECORD_FAILED = 4;

    // FM Recorder state not recording and not playing
    public static final int STATE_IDLE = 5;
    // FM Recorder state recording
    public static final int STATE_RECORDING = 6;
    // FM Recorder state playing
    public static final int STATE_PLAYBACK = 7;
    // FM Recorder state invalid, need to check
    public static final int STATE_INVALID = -1;
    /**
     * SPRD: bug474741, recording format selection.
     *
     * 0:amr;
     * 1:3gpp
     * @{
     */
    public  static int GLOBAL_RECORD_FORMAT_FLAG = 0;
    /**
     * @}
     */

    /**
     * SPRD: bug509670, recording time is too short, causing music player anr.
     * @{
     */
    public static final String FM_RECORDING_TIME_KEY = "FM_RECORDING_TIME_KEY";
    /**
     * @}
     */

    // use to record current FM recorder state
    public int mInternalState = STATE_IDLE;
    // the recording time after start recording
    private long mRecordTime = 0;
    // record start time
    private long mRecordStartTime = 0;
    // current record file
    private File mRecordFile = null;
    // record current record file is saved by user
    private boolean mIsRecordingFileSaved = false;
    // listener use for notify service the record state or error state
    private OnRecorderStateChangedListener mStateListener = null;
    // recorder use for record file
    private MediaRecorder mRecorder = null;

    /**
     * Start recording the voice of FM, also check the pre-conditions, if not
     * meet, will return an error message to the caller. if can start recording
     * success, will set FM record state to recording and notify to the caller
     */
    public void startRecording(Context context) {
        mRecordTime = 0;

        /**
         * SPRD:Bug529776 Check the main card state
         * Original Android code:
         * @{
        // Check external storage
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Log.e(TAG, "startRecording, no external storage available");
            setError(ERROR_SDCARD_NOT_PRESENT);
            return;
        }
        /**
         * @}
         */

        String recordingSdcard = FmUtils.getDefaultStoragePath();

        /**
         * SPRD: bug474747, recording path selection.
         * @{
         */
        if (recordingSdcard == null || recordingSdcard.isEmpty()
                || (1 == FmUtils.FM_RECORD_STORAGE_PATH && !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStoragePathState()))) {
            Log.e(TAG, "startRecording, no sdcard storage available");
            setError(ERROR_SDCARD_NOT_PRESENT);
            return;
        }
        /**
         * @}
         */

        // check whether have sufficient storage space, if not will notify
        // caller error message
        if (!FmUtils.hasEnoughSpace(recordingSdcard)) {
            setError(ERROR_SDCARD_INSUFFICIENT_SPACE);
            Log.e(TAG, "startRecording, SD card does not have sufficient space!!");
            return;
        }

        // get external storage directory
        File sdDir = new File(recordingSdcard);
        File recordingDir = new File(sdDir, FM_RECORD_FOLDER);
        // exist a file named FM Recording, so can't create FM recording folder
        if (recordingDir.exists() && !recordingDir.isDirectory()) {
            Log.e(TAG, "startRecording, a file with name \"FM Recording\" already exists!!");
            setError(ERROR_SDCARD_WRITE_FAILED);
            return;
        } else if (!recordingDir.exists()) { // try to create recording folder
            boolean mkdirResult = recordingDir.mkdir();
            if (!mkdirResult) { // create recording file failed
                setError(ERROR_RECORDER_INTERNAL);
                return;
            }
        }
        // create recording temporary file
        long curTime = System.currentTimeMillis();
        Date date = new Date(curTime);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMddyyyy_HHmmss",
                Locale.ENGLISH);
        String time = simpleDateFormat.format(date);
        StringBuilder stringBuilder = new StringBuilder();
        /**
         * SPRD: bug474741, recording format selection.
         * @{
         */
        if(GLOBAL_RECORD_FORMAT_FLAG == 1){
            stringBuilder.append(time).append(RECORDING_FILE_EXTENSION);
        }else{
            stringBuilder.append(time).append(RECORDING_FILE_EXTENSION_AMR);
        }
        /**
         * @}
         */
        String name = stringBuilder.toString();
        mRecordFile = new File(recordingDir, name);
        try {
            if (mRecordFile.createNewFile()) {
                Log.d(TAG, "startRecording, createNewFile success with path "
                        + mRecordFile.getPath());
            }
        } catch (IOException e) {
            Log.e(TAG, "startRecording, IOException while createTempFile: " + e);
            e.printStackTrace();
            setError(ERROR_SDCARD_WRITE_FAILED);
            return;
        }
        // set record parameter and start recording
        try {
            mRecorder = new MediaRecorder();
            mRecorder.setOnErrorListener(this);
            mRecorder.setOnInfoListener(this);
            //mRecorder.setAudioSource(MediaRecorder.AudioSource.RADIO_TUNER);
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            /**
             * SPRD: bug474741, recording format selection.
             *
             * Original Android code:
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            final int samplingRate = 44100;
            mRecorder.setAudioSamplingRate(samplingRate);
            final int bitRate = 128000;
            mRecorder.setAudioEncodingBitRate(bitRate);
            final int audiochannels = 2;
            mRecorder.setAudioChannels(audiochannels);
             * @{
             */
            if (1 == GLOBAL_RECORD_FORMAT_FLAG) {
                Log.e(TAG, "global_record_format: 3gpp");
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mRecorder.setAudioSamplingRate(44100);
                mRecorder.setAudioEncodingBitRate(128000);
            } else {
                Log.e(TAG, "global_record_format: amr_nb");
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mRecorder.setAudioSamplingRate(8000);
                mRecorder.setAudioEncodingBitRate(5900);
            }
            /**
             * @}
             */
            mRecorder.setOutputFile(mRecordFile.getAbsolutePath());
            mRecorder.prepare();
            mRecordStartTime = SystemClock.elapsedRealtime();
            mRecorder.start();
            mIsRecordingFileSaved = false;
        } catch (IllegalStateException e) {
            Log.e(TAG, "startRecording, IllegalStateException while starting recording!", e);
            setError(ERROR_RECORDER_INTERNAL);
            return;
        } catch (IOException e) {
            Log.e(TAG, "startRecording, IOException while starting recording!", e);
            setError(ERROR_RECORDER_INTERNAL);
            return;
        }
        setState(STATE_RECORDING);
    }

    /**
     * Stop recording, compute recording time and update FM recorder state
     */
    public void stopRecording() {
        if (STATE_RECORDING != mInternalState) {
            Log.w(TAG, "stopRecording, called in wrong state!!");
            return;
        }

        mRecordTime = SystemClock.elapsedRealtime() - mRecordStartTime;
        stopRecorder();
        setState(STATE_IDLE);
    }

    /**
     * Compute the current record time
     *
     * @return The current record time
     */
    public long getRecordTime() {
        if (STATE_RECORDING == mInternalState) {
            mRecordTime = SystemClock.elapsedRealtime() - mRecordStartTime;
        }
        return mRecordTime;
    }

    /**
     * Get FM recorder current state
     *
     * @return FM recorder current state
     */
    public int getState() {
        return mInternalState;
    }

    /**
     * Get current record file name
     *
     * @return The current record file name
     */
    public String getRecordFileName() {
        if (mRecordFile != null) {
            String fileName = mRecordFile.getName();
            /**
             * SPRD: bug474741, recording format selection.
             *
             * Original Android code:
            int index = fileName.indexOf(RECORDING_FILE_EXTENSION);
            if (index > 0) {
                fileName = fileName.substring(0, index);
            }
             * @{
             */
            int index = 0;
            if(GLOBAL_RECORD_FORMAT_FLAG == 1){
                index = fileName.indexOf(RECORDING_FILE_EXTENSION);
                if (index > 0) {
                    fileName = fileName.substring(0, index);
                }
            }else {
                index = fileName.indexOf(RECORDING_FILE_EXTENSION_AMR);
                if (index > 0) {
                    fileName = fileName.substring(0, index);
                }
            }
            /**
             * @}
             */
            return fileName;
        }
        return null;
    }

    /**
     * Save recording file with the given name, and insert it's info to database
     *
     * @param context The context
     * @param newName The name to override default recording name
     */
    public void saveRecording(Context context, String newName) {
        if (mRecordFile == null) {
            Log.e(TAG, "saveRecording, recording file is null!");
            return;
        }

        /**
         * SPRD: bug474741, recording format selection.
         *
         * Original Android code:
        File newRecordFile = new File(mRecordFile.getParent(), newName + RECORDING_FILE_EXTENSION);
         * @{
         */
        File newRecordFile = null;
        Log.e(TAG, "saveRecoring:newName :" + newName);
        if (1 == GLOBAL_RECORD_FORMAT_FLAG) {
            newRecordFile = new File(mRecordFile.getParent(), newName + RECORDING_FILE_EXTENSION);
        } else {
            newRecordFile = new File(mRecordFile.getParent(), newName
                    + RECORDING_FILE_EXTENSION_AMR);
        }
        /**
         * @}
         */
        boolean succuss = mRecordFile.renameTo(newRecordFile);
        if (succuss) {
            mRecordFile = newRecordFile;
        }
        mIsRecordingFileSaved = true;
        // insert recording file info to database
        addRecordingToDatabase(context);
    }

    /**
     * Discard current recording file, release recorder and player
     */
    public void discardRecording() {
        if ((STATE_RECORDING == mInternalState) && (null != mRecorder)) {
            stopRecorder();
        }

        if (mRecordFile != null && !mIsRecordingFileSaved) {
            if (!mRecordFile.delete()) {
                // deletion failed, possibly due to hot plug out SD card
                Log.d(TAG, "discardRecording, delete file failed!");
            }
            mRecordFile = null;
            mRecordStartTime = 0;
            mRecordTime = 0;
        }
        setState(STATE_IDLE);
    }

    /**
     * Set the callback use to notify FM recorder state and error message
     *
     * @param listener the callback
     */
    public void registerRecorderStateListener(OnRecorderStateChangedListener listener) {
        mStateListener = listener;
    }

    /**
     * Interface to notify FM recorder state and error message
     */
    public interface OnRecorderStateChangedListener {
        /**
         * notify FM recorder state
         *
         * @param state current FM recorder state
         */
        void onRecorderStateChanged(int state);

        /**
         * notify FM recorder error message
         *
         * @param error error type
         */
        void onRecorderError(int error);
    }

    /**
     * When recorder occur error, release player, notify error message, and
     * update FM recorder state to idle
     *
     * @param mr The current recorder
     * @param what The error message type
     * @param extra The error message extra
     */
    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.e(TAG, "onError, what = " + what + ", extra = " + extra);
        stopRecorder();
        setError(ERROR_RECORDER_INTERNAL);
        if (STATE_RECORDING == mInternalState) {
            setState(STATE_IDLE);
        }
    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        Log.d(TAG, "onInfo: what=" + what + ", extra=" + extra);
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || 
            what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            onError(mr, what, extra);
        }
    }

    /**
     * Reset FM recorder
     */
    public void resetRecorder() {
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
        mRecordFile = null;
        mRecordStartTime = 0;
        mRecordTime = 0;
        mInternalState = STATE_IDLE;
    }

    /**
     * Notify error message to the callback
     *
     * @param error FM recorder error type
     */
    private void setError(int error) {
        if (mStateListener != null) {
            mStateListener.onRecorderError(error);
        }
    }

    /**
     * Notify FM recorder state message to the callback
     *
     * @param state FM recorder current state
     */
    private void setState(int state) {
        mInternalState = state;
        if (mStateListener != null) {
            mStateListener.onRecorderStateChanged(state);
        }
    }

    /**
     * Save recording file info to database
     *
     * @param context The context
     */
    private void addRecordingToDatabase(final Context context) {
        long curTime = System.currentTimeMillis();
        long modDate = mRecordFile.lastModified();
        Date date = new Date(curTime);

        java.text.DateFormat dateFormatter = DateFormat.getDateFormat(context);
        java.text.DateFormat timeFormatter = DateFormat.getTimeFormat(context);
        String title = getRecordFileName();
        StringBuilder stringBuilder = new StringBuilder()
                .append(FM_RECORD_FOLDER)
                .append(" ")
                .append(dateFormatter.format(date))
                .append(" ")
                .append(timeFormatter.format(date));
        String artist = stringBuilder.toString();

        final int size = 9;
        ContentValues cv = new ContentValues(size);
        cv.put(MediaStore.Audio.Media.IS_MUSIC, 1);
        cv.put(MediaStore.Audio.Media.TITLE, title);
        cv.put(MediaStore.Audio.Media.DATA, mRecordFile.getAbsolutePath());
        final int oneSecond = 1000;
        cv.put(MediaStore.Audio.Media.DATE_ADDED, (int) (curTime / oneSecond));
        cv.put(MediaStore.Audio.Media.DATE_MODIFIED, (int) (modDate / oneSecond));
        cv.put(MediaStore.Audio.Media.MIME_TYPE, RECORDING_FILE_TYPE);
        cv.put(MediaStore.Audio.Media.ARTIST, artist);
        cv.put(MediaStore.Audio.Media.ALBUM, RECORDING_FILE_SOURCE);
        cv.put(MediaStore.Audio.Media.DURATION, mRecordTime);

        int recordingId = addToAudioTable(context, cv);
        if (recordingId < 0) {
            // insert failed
            return;
        }
        int playlistId = getPlaylistId(context);
        if (playlistId < 0) {
            // play list not exist, create FM Recording play list
            playlistId = createPlaylist(context);
        }
        if (playlistId < 0) {
            // insert playlist failed
            return;
        }
        // insert item to FM recording play list
        addToPlaylist(context, playlistId, recordingId);
        // scan to update duration
        MediaScannerConnection.scanFile(context, new String[] { mRecordFile.getPath() },
                null, null);
    }

    /**
     * Get the play list ID
     * @param context Current passed in Context instance
     * @return The play list ID
     */
    public static int getPlaylistId(final Context context) {
        Cursor playlistCursor = context.getContentResolver().query(
                MediaStore.Audio.Playlists.getContentUri("external"),
                new String[] {
                    MediaStore.Audio.Playlists._ID
                },
                /**
                 * SPRD: bug498427, FM recording, repeat create playlists
                 * Original Android code:
                MediaStore.Audio.Playlists.DATA + "=?",
                new String[] {
                    FmUtils.getPlaylistPath(context) + RECORDING_FILE_SOURCE
                 * @{
                 */
                MediaStore.Audio.Playlists.DATA + " like ? AND " + MediaStore.Audio.Playlists.NAME + "=?",
                new String[] {
                    "%" + FmUtils.getPlaylistPath(context) + RECORDING_FILE_SOURCE + "%", RECORDING_FILE_SOURCE
                },
                null);
                /**
                 * @}
                 */
        int playlistId = -1;
        if (null != playlistCursor) {
            try {
                if (playlistCursor.moveToFirst()) {
                    playlistId = playlistCursor.getInt(0);
                }
            } finally {
                playlistCursor.close();
            }
        }
        return playlistId;
    }

    private int createPlaylist(final Context context) {
        final int size = 1;
        ContentValues cv = new ContentValues(size);
        cv.put(MediaStore.Audio.Playlists.NAME, RECORDING_FILE_SOURCE);
        Uri newPlaylistUri = context.getContentResolver().insert(
                MediaStore.Audio.Playlists.getContentUri("external"), cv);
        if (newPlaylistUri == null) {
            Log.d(TAG, "createPlaylist, create playlist failed");
            return -1;
        }
        return Integer.valueOf(newPlaylistUri.getLastPathSegment());
    }

    private int addToAudioTable(final Context context, final ContentValues cv) {
        ContentResolver resolver = context.getContentResolver();
        int id = -1;

        Cursor cursor = null;

        try {
            cursor = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[] { MediaStore.Audio.Media._ID },
                    MediaStore.Audio.Media.DATA + "=?",
                    new String[] { mRecordFile.getPath() },
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                // Exist in database, just update it
                id = cursor.getInt(0);
                resolver.update(ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        cv,
                        null,
                        null);
            } else {
                // insert new entry to database
                Uri uri = context.getContentResolver().insert(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    id = Integer.valueOf(uri.getLastPathSegment());
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return id;
    }

    private void addToPlaylist(final Context context, final int playlistId, final int recordingId) {
        ContentResolver resolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        int order = 0;
        Cursor cursor = null;
        try {
            cursor = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[] { MediaStore.Audio.Media._ID },
                    MediaStore.Audio.Media.DATA + "=?",
                    new String[] { mRecordFile.getPath() },
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                // Exist in database, just update it
                order = cursor.getCount();
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        ContentValues cv = new ContentValues(2);
        cv.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, recordingId);
        cv.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, order);
        context.getContentResolver().insert(uri, cv);
    }

    private void stopRecorder() {
        synchronized (this) {
            if (mRecorder != null) {
                try {
                    mRecorder.stop();
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "stopRecorder, IllegalStateException ocurr " + ex);
                    setError(ERROR_RECORDER_INTERNAL);
                /**
                 * SPRD: bug474747, recording path selection.
                 * @{
                 */
                } catch (RuntimeException exception) {
                    Log.e(TAG, "stopRecorder, RuntimeException ocurr " + exception);
                /**
                 * @}
                 */
                } finally {
                    mRecorder.release();
                    mRecorder = null;
                }
            }
        }
    }
}
