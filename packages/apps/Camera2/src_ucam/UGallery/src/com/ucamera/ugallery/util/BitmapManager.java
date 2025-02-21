/*
 * Copyright (C) 2011,2012 Thundersoft Corporation
 * All rights Reserved
 *
 * Copyright (C) 2009 The Android Open Source Project
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

package com.ucamera.ugallery.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.util.Log;

import java.io.FileDescriptor;
import java.util.WeakHashMap;


/**
 * Provides utilities to decode bitmap, get thumbnail, and cancel the
 * operations.
 *
 * <p>The function {@link #decodeFileDescriptor(FileDescriptor,
 * BitmapFactory.Options)} is used to decode a bitmap. During decoding another
 * thread can cancel it using the function {@link #cancelThreadDecoding(Thread,
 * ContentResolver)} specifying the {@code Thread} which is in decoding.
 *
 * <p>{@code cancelThreadDecoding(Thread,ContentResolver)} is sticky until
 * {@code allowThreadDecoding(Thread) } is called.
 */
public class BitmapManager {
    private static final String TAG = "BitmapManager";
    private static enum State {CANCEL, ALLOW}
    private static class ThreadStatus {
        public State mState = State.ALLOW;
        public BitmapFactory.Options mOptions;
        public boolean mThumbRequesting;

        @Override
        public String toString() {
            String s;
            if (mState == State.CANCEL) {
                s = "Cancel";
            } else if (mState == State.ALLOW) {
                s = "Allow";
            } else {
                s = "?";
            }
            s = "thread state = " + s + ", options = " + mOptions;
            return s;
        }
    }

    private final WeakHashMap<Thread, ThreadStatus> mThreadStatus =
            new WeakHashMap<Thread, ThreadStatus>();

    private static BitmapManager sManager = null;

    private BitmapManager() {
    }

    /**
     * Get thread status and create one if specified.
     */
    private synchronized ThreadStatus getOrCreateThreadStatus(Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        if (status == null) {
            status = new ThreadStatus();
            mThreadStatus.put(t, status);
        }
        return status;
    }

    /**
     * The following three methods are used to keep track of
     * BitmapFaction.Options used for decoding and cancelling.
     */
    private synchronized void setDecodingOptions(Thread t, BitmapFactory.Options options) {
        getOrCreateThreadStatus(t).mOptions = options;
    }

    synchronized void removeDecodingOptions(Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        status.mOptions = null;
    }

    public synchronized boolean canThreadDecoding(Thread t) {
        ThreadStatus status = mThreadStatus.get(t);
        if (status == null) {
            // allow decoding by default
            return true;
        }

        boolean result = (status.mState != State.CANCEL);
        return result;
    }

    public synchronized void allowThreadDecoding(Thread t) {
        getOrCreateThreadStatus(t).mState = State.ALLOW;
    }

    public synchronized void cancelThreadDecoding(Thread t, ContentResolver cr) {
        ThreadStatus status = getOrCreateThreadStatus(t);
        status.mState = State.CANCEL;
        if (status.mOptions != null) {
            status.mOptions.requestCancelDecode();
        }

        // Wake up threads in waiting list
        notifyAll();

        // Since our cancel request can arrive MediaProvider earlier than
        // getThumbnail request,
        // we use mThumbRequesting flag to make sure our request does cancel the
        // request.
        try {
            synchronized (status) {
                while (status.mThumbRequesting) {
                    Images.Thumbnails.cancelThumbnailRequest(cr, -1, t.getId());
                    Video.Thumbnails.cancelThumbnailRequest(cr, -1, t.getId());
                    status.wait(200);
                }
            }
        } catch (InterruptedException ex) {
            // ignore it.
        }
    }

    /**
     * Gets the thumbnail of the given ID of the original image.
     *
     * <p> This method wraps around @{code getThumbnail} in {@code
     * android.provider.MediaStore}. It provides the ability to cancel it.
     */
    public Bitmap getThumbnail(ContentResolver cr, long origId, int kind,
            BitmapFactory.Options options, boolean isVideo) {
        Thread t = Thread.currentThread();
        ThreadStatus status = getOrCreateThreadStatus(t);

        if (!canThreadDecoding(t)) {
            Log.d(TAG, "Thread " + t + " is not allowed to decode.");
            return null;
        }

        try {
            synchronized (status) {
                status.mThumbRequesting = true;
            }
            if (isVideo) {
                /* FIX BUG : 4439
                 * BUG CAUSE : some devices can not supported "setOrientationHint" function of MediaRecorder
                 * BUG COMMENT : set correct orientation to thumbnail for this devices
                 * DATE : 2013-07-01
                 */
                Bitmap bitmap = Video.Thumbnails.getThumbnail(cr, origId, t.getId(),kind, options);
                if(!Compatible.instance().getOrientationRecordable()){
                    bitmap = Util.rotate(bitmap, 90);
                }
                return bitmap;
            } else {
                return Images.Thumbnails.getThumbnail(cr, origId, t.getId(),
                        kind, options);
            }
        } catch (NullPointerException ex) {
            /*
             * FIX BUG: 1453
             * BUG CAUSE: frameworks layer has null pointer;
             * FIX COMMENT: App layer catch this excption to avoid crash.
             * DATE: 2012-08-16
             */
            Log.w(TAG, "Frameworks layer throws this excption.");
        }finally {
            synchronized (status) {
                status.mThumbRequesting = false;
                status.notifyAll();
            }
        }

        return null;
    }

    public static synchronized BitmapManager instance() {
        if (sManager == null) {
            sManager = new BitmapManager();
        }
        return sManager;
    }

    /**
     * The real place to delegate bitmap decoding to BitmapFactory.
     */
    public Bitmap decodeFileDescriptor(FileDescriptor fd, BitmapFactory.Options options) {
        if (options.mCancel) {
            return null;
        }

        Thread thread = Thread.currentThread();
        if (!canThreadDecoding(thread)) {
            Log.d(TAG, "Thread " + thread + " is not allowed to decode.");
            return null;
        }

        setDecodingOptions(thread, options);
        Bitmap b = BitmapFactory.decodeFileDescriptor(fd, null, options);

        removeDecodingOptions(thread);
        return b;
    }
}
