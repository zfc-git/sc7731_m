package com.android.telephony;

import android.app.AddonManager;
import android.util.Log;
import android.content.Context;
import com.android.internal.R;

public class EmergencyAndLowBattaryCallUtils {

    private static final String TAG = "EmergencyAndLowBattaryCallUtils";
    static EmergencyAndLowBattaryCallUtils sInstance;

    public static EmergencyAndLowBattaryCallUtils getInstance() {
        Log.d(TAG, "enter EmergencyAndLowBattaryCallUtils");
        if (sInstance != null)
            return sInstance;
        sInstance = (EmergencyAndLowBattaryCallUtils) AddonManager.getDefault().getAddon(R.string.feature_AddonEmergencyAndLowBattaryCallUtils,
                EmergencyAndLowBattaryCallUtils.class);
        return sInstance;
    }

    public EmergencyAndLowBattaryCallUtils() {
    }

    public boolean isBatteryLow() {
        Log.d(TAG, "isBatteryLow = false");
        return false;
    }

    public String AddEmergencyNO(int key,String fastcall) {
        Log.d(TAG, "AddEmergencyNO");
        // SPRD: modify for bug526629
        return fastcall;
    }
}
