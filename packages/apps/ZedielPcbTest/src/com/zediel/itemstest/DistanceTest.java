package com.zediel.itemstest;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class DistanceTest {
    private static final String TAG = "DistanceTest";
    private static final int ACTIVITY_TRIGGER_COUNT = 3;
    private static final float DEFAULT_VALUE = -1.0f;
    private final float[] mHits = new float[ACTIVITY_TRIGGER_COUNT];
    private static DistanceTest sDistanceTest;
    private SensorManager mSensorManager;
    private LightEventListener mLightListener;
    private Sensor mSensor;
    private CallBack mCallBack;

    public static DistanceTest getInstance() {
        if (sDistanceTest == null) {
            synchronized (DistanceTest.class) {
                if (sDistanceTest == null) {
                    sDistanceTest = new DistanceTest();
                }
            }
        }
        return sDistanceTest;
    }

    public void registerListener(Context context) {
        if (mSensorManager == null) {
            mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }
        if (mLightListener == null) {
            mLightListener = new LightEventListener();
        }
        if (mSensor == null) {
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        }
        for (int i = 0; i < mHits.length; i++) {
            mHits[i] = DEFAULT_VALUE;
        }
        mSensorManager.registerListener(mLightListener, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void unregisterListener() {
        if (mSensorManager != null && mLightListener != null && mSensor != null) {
            mSensorManager.unregisterListener(mLightListener, mSensor);
        }
    }

    public void setCallBack(CallBack callBack) {
        mCallBack = callBack;
    }

    private final class LightEventListener implements SensorEventListener {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //nothing
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            Log.d(TAG, "onSensorChanged event.values[0]=" + event.values[0]);
            System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
            mHits[mHits.length - 1] = event.values[0];
            if (mHits[0] != DEFAULT_VALUE && mHits[1] != DEFAULT_VALUE && mHits[2] != DEFAULT_VALUE
                && mHits[0] != mHits[1] && mHits[0] == mHits[2] && mHits[1] != mHits[2]) {
                if (mCallBack != null) {
                    mCallBack.onSuccess();
                }
            }
        }
    }

    public interface CallBack {
        void onSuccess();
    }
}