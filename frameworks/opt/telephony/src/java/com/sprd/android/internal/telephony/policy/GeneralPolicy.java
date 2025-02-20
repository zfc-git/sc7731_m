
package com.android.internal.telephony.policy;

import android.util.Log;

import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.TeleUtils;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;

public class GeneralPolicy extends IccPolicy {

    private static final String TAG = "GeneralPolicy";

    public SimPriority getSimPriority(int phoneId) {

        SimPriority priority = SimPriority.NONE;
        UiccController uc = UiccController.getInstance();

        if (uc != null) {
            UiccCardApplication currentApp = uc.getUiccCardApplication(phoneId,
                    UiccController.APP_FAM_3GPP);
            if (currentApp != null) {
                if (currentApp.getState() == AppState.APPSTATE_READY) {
                    priority = currentApp.getType() == AppType.APPTYPE_USIM ?
                            SimPriority.HIGH_USIM : SimPriority.HIGH_SIM;
                } else {
                    priority = SimPriority.LOCKED;
                }
            }
        }
        Log.d(TAG, "get sim " + phoneId + " priority:" + priority.toString());
        return priority;
    }

    public boolean isPrimaryCardNeedManualSet() {
        if (TeleUtils.isSuppSetDataAndPrimaryCardBind()) {
            return false;
        } else {
            return mMaxPriorityCount >= 2 &&
                    (mSimPriorities[mMaxPriorityPhoneId] == SimPriority.HIGH_USIM);
        }
    }
}
