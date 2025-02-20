package com.android.internal.telephony.plugin;

import android.app.AddonManager;
import android.database.Cursor;
import android.net.NetworkUtils;
import android.provider.Telephony;
import android.util.Log;
import com.android.internal.R;
import com.android.internal.telephony.dataconnection.ApnSetting;

public class SupportTrafficClassUtils {

    private static SupportTrafficClassUtils mInstance;
    private static final String LOG_TAG = "SupportTrafficClassUtils";

    public SupportTrafficClassUtils() {
    }

    public static SupportTrafficClassUtils getInstance() {
        if (mInstance != null) {
            return mInstance;
        }
        AddonManager addonManager = AddonManager.getDefault();
        mInstance = (SupportTrafficClassUtils) addonManager.getAddon(
                R.string.feature_add_trafficClass,
                SupportTrafficClassUtils.class);
        Log.d(LOG_TAG, "mInstance = " + mInstance);
        return mInstance;
    }

    public boolean isSupportTracfficClass() {
        Log.d(LOG_TAG, "isSupportTracfficClass : false");
        return false;
    }

    public ApnSetting getApnSettingForTrafficClass(Cursor cursor, String[] types) {
        ApnSetting apn = new ApnSetting(
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NUMERIC)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.APN)),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.PROXY))),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PORT)),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSC))),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPROXY))),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPORT)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.USER)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PASSWORD)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.AUTH_TYPE)),
                types,
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROTOCOL)),
                cursor.getString(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.ROAMING_PROTOCOL)),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.CARRIER_ENABLED)) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.BEARER)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.BEARER_BITMASK)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROFILE_ID)),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.MODEM_COGNITIVE)) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MAX_CONNS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.WAIT_TIME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MAX_CONNS_TIME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MTU)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_TYPE)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_MATCH_DATA)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.TRAFFIC_CLASS)));
        return apn;
    }
}
