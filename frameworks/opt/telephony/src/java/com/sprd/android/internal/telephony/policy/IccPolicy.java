
package com.android.internal.telephony.policy;

import android.app.ActivityThread;
import android.telephony.TelephonyManager;
import android.util.Log;

public abstract class IccPolicy {
    public enum SimPriority {
        NONE,
        DISABLE,
        LOWER_USIM_SIM,
        LOW_SIM,
        LOW_USIM,
        HIGH_SIM,
        LOCKED,
        HIGH_USIM;

        public boolean isAllowedPowerRadioOn() {
            if (this.compareTo(NONE) > 0
                    && this != LOCKED) {
                return true;
            }
            return false;
        }
    }

    private static final String TAG = "IccPolicy";

    public static final int INVALID_PRIMARY_PHONE_ID = -1;
    protected SimPriority[] mSimPriorities;
    protected int mPhoneCount = 1;
    protected int mMaxPriorityCount;
    protected int mMaxPriorityPhoneId;
    private int mNoneOrLockedCount = 0;
    private StringBuilder mMaxPriorityPhoneIds;

    public IccPolicy() {
        mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
        mSimPriorities = new SimPriority[mPhoneCount];
        updateSimPriorities();
    }

    public void updateSimPriorities() {
        Log.d(TAG, "updateSimPriorities");
        initMaxPriorityValues();

        for (int i = 0; i < mPhoneCount; i++) {
            mSimPriorities[i] = getSimPriority(i);
            if (!TelephonyManager.from(ActivityThread.currentApplication()).isSimStandby(i)) {
                mSimPriorities[i] = SimPriority.DISABLE;
            }
            if (mSimPriorities[i] == SimPriority.NONE
                    || mSimPriorities[i] == SimPriority.LOCKED) {
                mNoneOrLockedCount++;
            }
        }

        updateMaxPriorityValues();
    }

    public abstract SimPriority getSimPriority(int phoneId);

    public abstract boolean isPrimaryCardNeedManualSet();

    private void initMaxPriorityValues() {
        mMaxPriorityPhoneId = 0;
        mMaxPriorityCount = 1;
        mMaxPriorityPhoneIds = new StringBuilder("0");
        mNoneOrLockedCount = 0;
    }

    private void updateMaxPriorityValues() {
        for (int i = 1; i < mPhoneCount; i++) {
            if (mSimPriorities[mMaxPriorityPhoneId].compareTo(mSimPriorities[i]) < 0) {
                mMaxPriorityPhoneId = i;
                mMaxPriorityCount = 1;
                mMaxPriorityPhoneIds = new StringBuilder(i);
            } else if (mSimPriorities[mMaxPriorityPhoneId] == mSimPriorities[i]) {
                mMaxPriorityCount++;
                mMaxPriorityPhoneIds.append("," + i);
            }
        }

        if (mSimPriorities[mMaxPriorityPhoneId] == SimPriority.NONE
                ||
                (mSimPriorities[mMaxPriorityPhoneId] == SimPriority.LOCKED && mNoneOrLockedCount == mPhoneCount)) {
            mSimPriorities[mMaxPriorityPhoneId] = SimPriority.HIGH_USIM;
            mMaxPriorityCount = 1;
            Log.d(TAG, "No sim is available,need power on default radio for emergency call.");
        }

        Log.d(TAG, "mMaxPriorityPhoneId = " + mMaxPriorityPhoneId + " mMaxPriorityCount = " +
                mMaxPriorityCount + " mMaxPriorityPhoneIds = " + mMaxPriorityPhoneIds.toString());
    }

    protected int getPrimaryCardAccordingToPolicy() {
        return mMaxPriorityPhoneId;
    }

    public boolean hasUsimCard(int phoneId) {
        if (phoneId >= 0 && phoneId < mPhoneCount) {
            return mSimPriorities[phoneId] == SimPriority.HIGH_USIM
                    || mSimPriorities[phoneId] == SimPriority.LOW_USIM;
        } else {
            return false;
        }
    }

    public boolean hasSimLocked() {
        boolean hasSimLocked = false;
        for (int i = 0; i < mPhoneCount; i++) {
            hasSimLocked |= (mSimPriorities[i] == SimPriority.LOCKED);
        }
        return hasSimLocked;
    }

    public SimPriority[] getSimPriorities() {
        return mSimPriorities;
    }
}
