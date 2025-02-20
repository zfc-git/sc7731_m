package com.android.internal.telephony.plugin;

import android.app.AddonManager;
import android.app.AlertDialog;
import android.content.Context;
import android.telephony.ServiceState;
import android.util.Log;


import com.android.internal.R;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.dataconnection.DcFailCause;

public class ClearCodeAndRAUUtils {
    private static final String TAG = "ClearCodeAndRAUUtils";
    private static ClearCodeAndRAUUtils sInstance;

    public static final int RETRY_DELAY_LONG  = 45000;
    public static final int RETRY_DELAY_SHORT = 10000;
    public static final int RETRY_FROM_FAILURE_DELAY = 2 * 3600 * 1000; // 2 hours

    public ClearCodeAndRAUUtils() {
    }

    public static ClearCodeAndRAUUtils getInstance() {
        Log.d(TAG, "ClearCodeUtils getInstance");
        if (sInstance != null) return sInstance;

        AddonManager addonManager = AddonManager.getDefault();
        sInstance = (ClearCodeAndRAUUtils) addonManager.getAddon(
                R.string.feature_clearcode_and_rau,
                ClearCodeAndRAUUtils.class);
        Log.d(TAG, "ClearCodeUtils getInstance: " + sInstance);
        return sInstance;
    }

    /* ===== To be override in sub class ===== */
    public boolean supportSpecialClearCode() {
        Log.d(TAG, "supportSpecialClearCode = false");
        return false;
    }

    public boolean isSpecialCode(DcFailCause fc) {
        return false;
    }

    public AlertDialog getErrorDialog(DcFailCause cause) {
        return null;
    }
}
