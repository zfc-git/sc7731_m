/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server.lights;

import com.android.server.SystemService;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.util.Slog;

public class LightsService extends SystemService {
    static final String TAG = "LightsService";
    static final boolean DEBUG = false;

    final LightImpl mLights[] = new LightImpl[LightsManager.LIGHT_ID_COUNT];
    private boolean isBatteryLightOpen = false;
    private final class LightImpl extends Light {

        private LightImpl(int id) {
            mId = id;
        }

        @Override
        public void setBrightness(int brightness) {
            setBrightness(brightness, BRIGHTNESS_MODE_USER);
        }

        @Override
        public void setBrightness(int brightness, int brightnessMode) {
            synchronized (this) {
                int color = brightness & 0x000000ff;
                color = 0xff000000 | (color << 16) | (color << 8) | color;
                setLightLocked(color, LIGHT_FLASH_NONE, 0, 0, brightnessMode);
            }
        }

        @Override
        public void setColor(int color) {
            synchronized (this) {
                setLightLocked(color, LIGHT_FLASH_NONE, 0, 0, 0);
            }
        }

        @Override
        public void setFlashing(int color, int mode, int onMS, int offMS) {
            synchronized (this) {
                setLightLocked(color, mode, onMS, offMS, BRIGHTNESS_MODE_USER);
            }
        }

        @Override
        public void pulse() {
            pulse(0x00ffffff, 7);
        }

        @Override
        public void pulse(int color, int onMS) {
            synchronized (this) {
                if (mColor == 0 && !mFlashing) {
                    setLightLocked(color, LIGHT_FLASH_HARDWARE, onMS, 1000, BRIGHTNESS_MODE_USER);
                    mColor = 0;
                    mH.sendMessageDelayed(Message.obtain(mH, 1, this), onMS);
                }
            }
        }

        @Override
        public void turnOff() {
            synchronized (this) {
                setLightLocked(0, LIGHT_FLASH_NONE, 0, 0, 0);
            }
        }

        private void stopFlashing() {
            synchronized (this) {
                setLightLocked(mColor, LIGHT_FLASH_NONE, 0, 0, BRIGHTNESS_MODE_USER);
            }
        }

        private void setLightLocked(int color, int mode, int onMS, int offMS, int brightnessMode) {
            if(!((mId == LightsManager.LIGHT_ID_NOTIFICATIONS)&&(isBatteryLightOpen))){
                if (DEBUG) Slog.v(TAG, "isBatteryLightOpen = " + isBatteryLightOpen);
                if (color != mColor || mode != mMode || onMS != mOnMS || offMS != mOffMS) {
                    if (DEBUG) Slog.v(TAG, "setLight #" + mId + ": color=#"
                        + Integer.toHexString(color));
                    mColor = color;
                    mMode = mode;
                    mOnMS = onMS;
                    mOffMS = offMS;
                    Trace.traceBegin(Trace.TRACE_TAG_POWER, "setLight(" + mId + ", 0x"
                        + Integer.toHexString(color) + ")");
                    try {
                        setLight_native(mNativePointer, mId, color, mode, onMS, offMS, brightnessMode);
                    } finally {
                        Trace.traceEnd(Trace.TRACE_TAG_POWER);
                    }
                }
            } else {
                Slog.d(TAG, "mid = " + mId +"; isBatteryLightOpen = " + isBatteryLightOpen);
            }
            if(mId == LightsManager.LIGHT_ID_BATTERY){
                if(color == 0 ){
                    isBatteryLightOpen = false;
                } else
                    isBatteryLightOpen = true;
            }
        }

        private int mId;
        private int mColor;
        private int mMode;
        private int mOnMS;
        private int mOffMS;
        private boolean mFlashing;
    }

    public LightsService(Context context) {
        super(context);

        mNativePointer = init_native();

        for (int i = 0; i < LightsManager.LIGHT_ID_COUNT; i++) {
            mLights[i] = new LightImpl(i);
        }
    }

    @Override
    public void onStart() {
        publishLocalService(LightsManager.class, mService);
    }

    private final LightsManager mService = new LightsManager() {
        @Override
        public Light getLight(int id) {
            if (id < LIGHT_ID_COUNT) {
                return mLights[id];
            } else {
                return null;
            }
        }
    };

    @Override
    protected void finalize() throws Throwable {
        finalize_native(mNativePointer);
        super.finalize();
    }

    private Handler mH = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LightImpl light = (LightImpl)msg.obj;
            light.stopFlashing();
        }
    };

    private static native long init_native();
    private static native void finalize_native(long ptr);

    static native void setLight_native(long ptr, int light, int color, int mode,
            int onMS, int offMS, int brightnessMode);

    private long mNativePointer;
}
