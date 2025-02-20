/**
 * Manages carrier configs for DcTracker
 *
 * Add by Spreadtrum.
 */

package com.android.internal.telephony.dataconnection;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;

import static android.telephony.CarrierConfigManager.KEY_DCT_DATA_KEEP_ALIVE_DURATION_INT;

public final class DctConfig {
    private static DctConfig sInstance;

    private Context mContext;
    private CarrierConfigManager mConfigManager;
    private PersistableBundle mPersistableBundle;

    /**
     * Get the singleton instance of DctConfig
     */
    public synchronized static DctConfig getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DctConfig(context);
        }
        return sInstance;
    }

    /**
     * Get the delay before tearing down data
     */
    public int getDataKeepAliveDuration(int defValue) {
        ensureConfig();
        if (mPersistableBundle != null) {
            return mPersistableBundle.getInt(KEY_DCT_DATA_KEEP_ALIVE_DURATION_INT, defValue);
        }

        return defValue;
    }

    public void dispose() {
        mConfigManager = null;
        mPersistableBundle = null;
    }

    private DctConfig(Context context) {
        mContext = context;
        mConfigManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
    }

    private void ensureConfig() {
        if (mPersistableBundle == null) {
            if (mConfigManager == null) {
                mConfigManager = (CarrierConfigManager) mContext.getSystemService(
                        Context.CARRIER_CONFIG_SERVICE);
            }

            if (mConfigManager != null) {
                mPersistableBundle = mConfigManager.getConfigForDefaultPhone();
            }
        }
    }

    /* SPRD: National Data Roaming. @{ */
    public boolean isNationalDataRoamingEnabled() {
        ensureConfig();

        boolean retVal = false;
        if (mPersistableBundle != null) {
            retVal = mPersistableBundle.getBoolean(
                    CarrierConfigManager.KEY_NATIONAL_DATA_ROAMING_BOOL);
        }
        return retVal;
    }
    /* @} */
}
