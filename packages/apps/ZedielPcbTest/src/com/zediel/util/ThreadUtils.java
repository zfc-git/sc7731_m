package com.zediel.util;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

public class ThreadUtils {
    private HandlerThread mHandlerThread;
    private Handler mMainHandler;
    private Handler mBgHandler;
    private static ThreadUtils sThreadUtils;

    public static ThreadUtils getInstance() {
        if (sThreadUtils == null) {
            synchronized (ThreadUtils.class) {
                if (sThreadUtils == null) {
                    sThreadUtils = new ThreadUtils();
                }
            }
        }
        return sThreadUtils;
    }

    public void execInBg(Runnable runnable) {
        if (mBgHandler == null) {
            mHandlerThread = new HandlerThread("BG");
            mHandlerThread.start();
            mBgHandler = new Handler(mHandlerThread.getLooper());
        }
        mBgHandler.post(runnable);
    }

    public void execInMain(Runnable runnable) {
        if (mMainHandler == null) {
            mMainHandler = new Handler(Looper.getMainLooper());
        }
        mMainHandler.post(runnable);
    }

    public void shutdown() {
        if (mBgHandler != null) {
            mBgHandler.removeCallbacksAndMessages(null);
        }
        if (mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
        }
    }
}
