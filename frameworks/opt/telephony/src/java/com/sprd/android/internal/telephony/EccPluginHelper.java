package com.android.internal.telephony;

import android.app.ActivityThread;
import android.app.AddonManager;
import android.content.Context;
import android.util.Log;

public class EccPluginHelper {

    private static EccPluginHelper mInstance;

    public EccPluginHelper() {
    }

    public static EccPluginHelper getInstance() {
        if (mInstance != null)
            return mInstance;
        mInstance = (EccPluginHelper) AddonManager.getDefault().getAddon(getFeatureId(),
                EccPluginHelper.class);
        return mInstance;
    }

    private static int getFeatureId() {
        int featureId = 0;
        Context app = ActivityThread.currentApplication().getApplicationContext();
        if (app != null) {
            featureId = app.getResources().getIdentifier("feature_ecc_list_customized_by_operator",
                    "string", app.getOpPackageName());
        }
        return featureId;
    }

    public void customizedEccList(String[] simEccs) {
        // Do nothing.
    }
}
