/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.Log;

/**
 * This class stores information about a single receiving file. It will only be
 * used for inbounds share, e.g. receive a file to determine a correct save file
 * name
 */
public class BluetoothOppReceiveFileInfo {
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;
    private static String sDesiredStoragePath = null;
    private static boolean mUseSecondCard = false;

    /** absolute store file name */
    public String mFileName;

    public long mLength;

    public FileOutputStream mOutputStream;

    public int mStatus;

    public String mData;

    public BluetoothOppReceiveFileInfo(String data, long length, int status) {
        mData = data;
        mStatus = status;
        mLength = length;
    }

    public BluetoothOppReceiveFileInfo(String filename, long length, FileOutputStream outputStream,
            int status) {
        mFileName = filename;
        mOutputStream = outputStream;
        mStatus = status;
        mLength = length;
    }

    public BluetoothOppReceiveFileInfo(int status) {
        this(null, 0, null, status);
    }

    // public static final int BATCH_STATUS_CANCELED = 4;
    public static BluetoothOppReceiveFileInfo generateFileInfo(Context context, int id) {

        ContentResolver contentResolver = context.getContentResolver();
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + id);
        String filename = null, hint = null, mimeType = null;
        long length = 0;
        Cursor metadataCursor = contentResolver.query(contentUri, new String[] {
                BluetoothShare.FILENAME_HINT, BluetoothShare.TOTAL_BYTES, BluetoothShare.MIMETYPE
        }, null, null, null);
        if (metadataCursor != null) {
            try {
                if (metadataCursor.moveToFirst()) {
                    hint = metadataCursor.getString(0);
                    length = metadataCursor.getLong(1);
                    mimeType = metadataCursor.getString(2);
                }
            } finally {
                metadataCursor.close();
            }
        }

        File base = null;
        StatFs stat = null;
        mUseSecondCard = false;

        if (Environment.getExternalStoragePathState().equals(Environment.MEDIA_MOUNTED)) {
            String root = getStoragePath();

            base = new File(root + Constants.DEFAULT_STORE_SUBDIR);
            if (!base.isDirectory() && !base.mkdir()) {
                if (D) Log.d(Constants.TAG, "Receive File aborted - can't create base directory on external card "
                            + base.getPath());

                if (Environment.getInternalStoragePathState().equals(Environment.MEDIA_MOUNTED)) {
                    base = new File(Environment.getInternalStoragePath() + Constants.DEFAULT_STORE_SUBDIR);
                    if (!base.isDirectory() && !base.mkdir()) {
                        if (D) Log.d(Constants.TAG, "Receive File aborted - can't create base directory on internal card "
                                + base.getPath());
                        return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
                    }
                    stat = new StatFs(base.getPath());
                    mUseSecondCard = true;
                    if (stat.getBlockSize() * ((long)stat.getAvailableBlocks() - 4) < length) {
                        Log.d(Constants.TAG, "Receive File aborted - internal card also not enough free space");
                        return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_ERROR_SDCARD_FULL);
                    }
                } else {
                    if (D) Log.d(Constants.TAG, "Receive File aborted - no external and internal storage");
                    return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_ERROR_NO_SDCARD);
                }
            } else {
                stat = new StatFs(base.getPath());
            }
        } else if (Environment.getInternalStoragePathState().equals(Environment.MEDIA_MOUNTED)) {
            base = new File(getStoragePath() + Constants.DEFAULT_STORE_SUBDIR);
            if (!base.isDirectory() && !base.mkdir()) {
                if (D) Log.d(Constants.TAG, "Receive File aborted - can't create base directory on internal storage"
                            + base.getPath());
                return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
            }
            stat = new StatFs(base.getPath());
            mUseSecondCard = true;
            if (stat.getBlockSize() * ((long)stat.getAvailableBlocks() - 4) < length) {
                Log.d(Constants.TAG, "Receive File aborted - second card also not enough free space");
                return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_ERROR_SDCARD_FULL);
            }
        } else {
            if (D) Log.d(Constants.TAG, "Receive File aborted - no external storage");
            return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_ERROR_NO_SDCARD);
        }

        /*
         * Check whether there's enough space on the target filesystem to save
         * the file. Put a bit of margin (in case creating the file grows the
         * system by a few blocks).
         */
        if (!mUseSecondCard && stat.getBlockSize() * ((long)stat.getAvailableBlocks() - 4) < length) {
            if (Environment.getInternalStoragePathState().equals(Environment.MEDIA_MOUNTED)) {
                // access sequence: SD card first(if mounted), then internal card, so second card means internal storages
                String root = Environment.getInternalStoragePath().getPath();
                Log.d(Constants.TAG,"use the second storage path: " + root);

                base = new File(root + Constants.DEFAULT_STORE_SUBDIR);
                if (!base.isDirectory() && !base.mkdir()) {
                    if (D) Log.d(Constants.TAG, "Receive File aborted - can't create base directory on external storage "
                                + base.getPath());
                    return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
                }
                stat = new StatFs(base.getPath());
                if (stat.getBlockSize() * ((long)stat.getAvailableBlocks() - 4) < length) {
                    Log.d(Constants.TAG, "Receive File aborted - second card also not enough free space");
                    return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_ERROR_SDCARD_FULL);
                }
                mUseSecondCard = true;
            } else {
                if (D) Log.d(Constants.TAG, "Receive File aborted - not enough free space");
                return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_ERROR_SDCARD_FULL);
            }
        }

        filename = choosefilename(hint);
        if (filename == null) {
            // should not happen. It must be pre-rejected
            return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
        }
        String extension = null;
        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex < 0) {
            if (mimeType == null) {
                // should not happen. It must be pre-rejected
                return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
            } else {
                extension = "";
            }
        } else {
            extension = filename.substring(dotIndex);
            filename = filename.substring(0, dotIndex);
        }
        filename = base.getPath() + File.separator + filename;
        // Generate a unique filename, create the file, return it.
        String fullfilename = chooseUniquefilename(filename, extension);

        if (!safeCanonicalPath(fullfilename)) {
            // If this second check fails, then we better reject the transfer
            return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
        }
        if (V) Log.v(Constants.TAG, "Generated received filename " + fullfilename);

        if (fullfilename != null) {
            try {
                new FileOutputStream(fullfilename).close();
                int index = fullfilename.lastIndexOf('/') + 1;
                // update display name
                if (index > 0) {
                    String displayName = fullfilename.substring(index);
                    if (V) Log.v(Constants.TAG, "New display name " + displayName);
                    ContentValues updateValues = new ContentValues();
                    updateValues.put(BluetoothShare.FILENAME_HINT, displayName);
                    context.getContentResolver().update(contentUri, updateValues, null, null);

                }
                return new BluetoothOppReceiveFileInfo(fullfilename, length, new FileOutputStream(
                        fullfilename), 0);
            } catch (IOException e) {
                if (D) Log.e(Constants.TAG, "Error when creating file " + fullfilename);
                return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
            }
        } else {
            return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
        }

    }

    private static String getStoragePath() {
        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment
                    .getExternalStoragePathState())) {
                return Environment.getExternalStoragePath().getCanonicalPath();
            } else {
                return Environment.getInternalStoragePath().getCanonicalPath();
            }
        } catch (IOException e) {
            Log.e(Constants.TAG, "Error when getStoragePath ");
            return null;
        }
    }

    private static boolean safeCanonicalPath(String uniqueFileName) {
        try {
            File receiveFile = new File(uniqueFileName);
            if (mUseSecondCard) {
                sDesiredStoragePath = Environment.getInternalStoragePath().getPath() +
                    Constants.DEFAULT_STORE_SUBDIR;
            } else {
                sDesiredStoragePath = getStoragePath() +
                        Constants.DEFAULT_STORE_SUBDIR;
            }
            String canonicalPath = receiveFile.getCanonicalPath();
            Log.v(Constants.TAG, "safeCanonicalPath, mUseSecondCard:" + mUseSecondCard + "  ,sDesiredStoragePath: " +sDesiredStoragePath + ",canonicalPath: " +canonicalPath);

            // Check if canonical path is complete - case sensitive-wise
            if (!canonicalPath.startsWith(sDesiredStoragePath)) {
                return false;
            }

	    	return true;
        } catch (IOException ioe) {
            // If an exception is thrown, there might be something wrong with the file.
            return false;
        }
    }

    private static String chooseUniquefilename(String filename, String extension) {
        String fullfilename = filename + extension;
        if (!new File(fullfilename).exists()) {
            return fullfilename;
        }
        filename = filename + Constants.filename_SEQUENCE_SEPARATOR;
        /*
         * This number is used to generate partially randomized filenames to
         * avoid collisions. It starts at 1. The next 9 iterations increment it
         * by 1 at a time (up to 10). The next 9 iterations increment it by 1 to
         * 10 (random) at a time. The next 9 iterations increment it by 1 to 100
         * (random) at a time. ... Up to the point where it increases by
         * 100000000 at a time. (the maximum value that can be reached is
         * 1000000000) As soon as a number is reached that generates a filename
         * that doesn't exist, that filename is used. If the filename coming in
         * is [base].[ext], the generated filenames are [base]-[sequence].[ext].
         */
        Random rnd = new Random(SystemClock.uptimeMillis());
        int sequence = 1;
        for (int magnitude = 1; magnitude < 1000000000; magnitude *= 10) {
            for (int iteration = 0; iteration < 9; ++iteration) {
                fullfilename = filename + sequence + extension;
                if (!new File(fullfilename).exists()) {
                    return fullfilename;
                }
                if (V) Log.v(Constants.TAG, "file with sequence number " + sequence + " exists");
                sequence += rnd.nextInt(magnitude) + 1;
            }
        }
        return null;
    }

    private static String choosefilename(String hint) {
        String filename = null;

        // First, try to use the hint from the application, if there's one
        if (filename == null && !(hint == null) && !hint.endsWith("/") && !hint.endsWith("\\")) {
            // Prevent abuse of path backslashes by converting all backlashes '\\' chars
            // to UNIX-style forward-slashes '/'
            hint = hint.replace('\\', '/');
            // Convert all whitespace characters to spaces.
            hint = hint.replaceAll("\\s", " ");
            // Replace illegal fat filesystem characters from the
            // filename hint i.e. :"<>*?| with something safe.
            hint = hint.replaceAll("[:\"<>*?|]", "_");
            if (V) Log.v(Constants.TAG, "getting filename from hint");
            int index = hint.lastIndexOf('/') + 1;
            if (index > 0) {
                filename = hint.substring(index);
            } else {
                filename = hint;
            }
        }
        return filename;
    }
}
