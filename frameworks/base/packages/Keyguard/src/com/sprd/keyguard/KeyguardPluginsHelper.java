package com.sprd.keyguard;

import android.app.AddonManager;
import android.content.Context;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.sprd.keyguard.KeyguardConfig;
import com.android.keyguard.R;

public class KeyguardPluginsHelper {
    static KeyguardPluginsHelper mInstance;
    public static final String TAG = "KeyguardPluginsHelper";
    /* SPRD: modify by BUG 540847 @{ */
    protected String RAT_4G = "4G";
    protected String RAT_3G = "3G";
    protected String RAT_2G = "2G";
    protected String RAT_LTE = "4GLTE";
    /* @} */

    public KeyguardPluginsHelper() {
    }

    public static KeyguardPluginsHelper getInstance() {
        if (mInstance != null)
            return mInstance;
        mInstance = (KeyguardPluginsHelper) AddonManager.getDefault()
                .getAddon(R.string.plugin_keyguard_operator, KeyguardPluginsHelper.class);
        return mInstance;
    }

    public String updateNetworkName(Context context, boolean showSpn, String spn, boolean showPlmn,
            String plmn) {
        return "";
    }

    public boolean makeEmergencyVisible() {
        return true;
    }

    /* Bug 532196 modify for flight mode name display in reliance. @{ */
    public boolean showFlightMode() {
        Log.d(TAG, "ShowFlightMode = " + false);
        return false;
    }
    /* @} */

    /* SPRD: modify by BUG 540847 @{ */
    protected String getNetworkTypeToString(Context context, int ratInt) {
        String ratClassName = "";
        switch (ratInt) {
            case TelephonyManager.NETWORK_CLASS_2_G:
                boolean showRat2G = getBoolShowRatFor2G();
                Log.d(TAG, "showRat2G : " + showRat2G);
                ratClassName = showRat2G ? RAT_2G : "";
                break;
            case TelephonyManager.NETWORK_CLASS_3_G:
                ratClassName = RAT_3G;
                break;
            case TelephonyManager.NETWORK_CLASS_4_G:
                Log.d(TAG, "showLteFor4G : " + showLteFor4G());
                ratClassName = showLteFor4G() ? RAT_LTE : RAT_4G;
                break;
        }
        return ratClassName;
    }

    protected String appendRatToNetworkName(Context context, ServiceState state, String operator) {
        String operatorName = operator;
        KeyguardConfig config = KeyguardConfig.getInstance(context);
        if (context == null || state == null || !getBoolAppendRAT()) {
            return operatorName;
        }

        /* SPRD: add for BUG 536878 @{ */
        if (operator != null && operator.matches(".*[2-4]G$")) {
            return operator;
        }
        /* @} */

        if (state.getDataRegState() == ServiceState.STATE_IN_SERVICE
                || state.getVoiceRegState() == ServiceState.STATE_IN_SERVICE) {
            int voiceNetType = state.getVoiceNetworkType();
            int dataNetType = state.getDataNetworkType();
            int chosenNetType = ((dataNetType == TelephonyManager.NETWORK_TYPE_UNKNOWN)
                    ? voiceNetType : dataNetType);
            TelephonyManager tm = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            int ratInt = tm.getNetworkClass(chosenNetType);
            String networktypeString = getNetworkTypeToString(context, ratInt);
            operatorName = new StringBuilder().append(operator).append(" ")
                    .append(networktypeString).toString();
            return operatorName;
        } else {
            operatorName = operator;
        }
        return operatorName;

    }

    public boolean getBoolAppendRAT() {
        return true;
    }

    public boolean getBoolShowRatFor2G() {
        return false;
    }

    protected boolean showLteFor4G() {
        return false;
    }
    /* @} */
}
