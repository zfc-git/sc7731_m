/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.OperatorInfo;
import com.sprd.phone.TeleServicePluginsHelper;
import com.android.internal.telephony.TeleUtils;

import java.util.HashMap;
import java.util.List;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;

/**
 * "Networks" settings UI for the Phone app.
 */
public class NetworkSetting extends PreferenceActivity
        implements DialogInterface.OnCancelListener {

    private static final String LOG_TAG = "phone";
    private static final boolean DBG = true;

    private static final int EVENT_NETWORK_SCAN_COMPLETED = 100;
    private static final int EVENT_NETWORK_SELECTION_DONE = 200;
    private static final int EVENT_AUTO_SELECT_DONE = 300;

    //dialog ids
    private static final int DIALOG_NETWORK_SELECTION = 100;
    private static final int DIALOG_NETWORK_LIST_LOAD = 200;
    private static final int DIALOG_NETWORK_AUTO_SELECT = 300;
    // SPRD: add for manual network query
    private static final int DIALOG_NETWORK_SELECTION_WARNING = 400;

    //String keys for preference lookup
    private static final String LIST_NETWORKS_KEY = "list_networks_key";
    private static final String BUTTON_SRCH_NETWRKS_KEY = "button_srch_netwrks_key";
    private static final String BUTTON_AUTO_SELECT_KEY = "button_auto_select_key";

    //map of network controls to the network data.
    private HashMap<Preference, OperatorInfo> mNetworkMap;

    int mPhoneId = SubscriptionManager.INVALID_PHONE_INDEX;
    protected boolean mIsForeground = false;

    private UserManager mUm;
    private boolean mUnavailable;

    /** message for network selection */
    String mNetworkSelectMsg;

    //preference objects
    private PreferenceGroup mNetworkList;
    private Preference mSearchButton;
    private Preference mAutoSelect;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_NETWORK_SCAN_COMPLETED:
                    networksListLoaded ((List<OperatorInfo>) msg.obj, msg.arg1);
                    break;

                case EVENT_NETWORK_SELECTION_DONE:
                    if (DBG) log("hideProgressPanel");
                    removeDialog(DIALOG_NETWORK_SELECTION);
                    getPreferenceScreen().setEnabled(true);

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) log("manual network selection: failed!");
                        displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) log("manual network selection: succeeded!");
                        // SPRD: Bug 519168 Wrong prompt message when change network selection mode
                        displayNetworkSelectionSucceeded(EVENT_NETWORK_SELECTION_DONE);
                    }

                    break;
                case EVENT_AUTO_SELECT_DONE:
                    if (DBG) log("hideProgressPanel");

                    // Always try to dismiss the dialog because activity may
                    // be moved to background after dialog is shown.
                    try {
                        dismissDialog(DIALOG_NETWORK_AUTO_SELECT);
                    } catch (IllegalArgumentException e) {
                        // "auto select" is always trigged in foreground, so "auto select" dialog
                        //  should be shown when "auto select" is trigged. Should NOT get
                        // this exception, and Log it.
                        Log.w(LOG_TAG, "[NetworksList] Fail to dismiss auto select dialog ", e);
                    }
                    getPreferenceScreen().setEnabled(true);

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) log("automatic network selection: failed!");
                        displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) log("automatic network selection: succeeded!");
                        // SPRD: Bug 519168 Wrong prompt message when change network selection mode
                        displayNetworkSelectionSucceeded(EVENT_AUTO_SELECT_DONE);
                    }

                    break;
            }

            return;
        }
    };

    /**
     * Service connection code for the NetworkQueryService.
     * Handles the work of binding to a local object so that we can make
     * the appropriate service calls.
     */

    /** Local service interface */
    private INetworkQueryService mNetworkQueryService = null;

    /** Service connection */
    private final ServiceConnection mNetworkQueryServiceConnection = new ServiceConnection() {

        /** Handle the task of binding the local object to the service */
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) log("connection created, binding local service.");
            mNetworkQueryService = ((NetworkQueryService.LocalBinder) service).getService();
            // SPRD Modified: Bug#474249
            // if you want auto-search network when you click
            // network_carrier preference, please open below
            // note as soon as it is bound, run a query.
            //loadNetworksList();
        }

        /** Handle the task of cleaning up the local binding */
        public void onServiceDisconnected(ComponentName className) {
            if (DBG) log("connection disconnected, cleaning local binding.");
            mNetworkQueryService = null;
        }
    };

    /**
     * This implementation of INetworkQueryServiceCallback is used to receive
     * callback notifications from the network query service.
     */
    private final INetworkQueryServiceCallback mCallback = new INetworkQueryServiceCallback.Stub() {

        /** place the message on the looper queue upon query completion. */
        public void onQueryComplete(List<OperatorInfo> networkInfoArray, int status) {
            if (DBG) log("notifying message loop of query completion.");
            Message msg = mHandler.obtainMessage(EVENT_NETWORK_SCAN_COMPLETED,
                    status, 0, networkInfoArray);
            msg.sendToTarget();
        }
    };

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean handled = false;

        if (preference == mSearchButton) {
            // SPRD: add for manual network query
            showDialog(DIALOG_NETWORK_SELECTION_WARNING);
            handled = true;
        } else if (preference == mAutoSelect) {
            selectNetworkAutomatic();
            handled = true;
        } else {
            Preference selectedCarrier = preference;

            String networkStr = selectedCarrier.getTitle().toString();
            if (DBG) log("selected network: " + networkStr);

            Message msg = mHandler.obtainMessage(EVENT_NETWORK_SELECTION_DONE);
            Phone phone = PhoneFactory.getPhone(mPhoneId);
            if (phone != null) {
                phone.selectNetworkManually(mNetworkMap.get(selectedCarrier), msg);
                displayNetworkSeletionInProgress(networkStr);
                handled = true;
            } else {
                log("Error selecting network. phone is null.");
            }


        }

        return handled;
    }

    //implemented for DialogInterface.OnCancelListener
    public void onCancel(DialogInterface dialog) {
        // request that the service stop the query with this callback object.
        try {
            mNetworkQueryService.stopNetworkQuery(mCallback);
        } catch (RemoteException e) {
            log("onCancel: exception from stopNetworkQuery " + e);
        }
        finish();
    }

    /* SPRD: add for manual network query @{ */
    @Override
    public void onStop() {
        super.onStop();
        // Always try to dismiss the dialog because activity may
        // be moved to background after dialog is shown.
        try {
            dismissDialog(DIALOG_NETWORK_LIST_LOAD);
        } catch (IllegalArgumentException e) {
            // It's not a error in following scenario, we just ignore it.
            // "Load list" dialog will not show, if NetworkQueryService is
            // connected after this activity is moved to background.
            if (DBG) log("Fail to dismiss network when onStop() " + e);
        }
        getPreferenceScreen().setEnabled(true);
        try {
            mNetworkQueryService.stopNetworkQuery(mCallback);
        } catch (RemoteException e) {
            log("onStop: exception from stopNetworkQuery " + e);
        }
    }
    /* @} */

    public String getNormalizedCarrierName(OperatorInfo ni) {
        if (ni != null) {
            return ni.getOperatorAlphaLong() + " (" + ni.getOperatorNumeric() + ")";
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mUm = (UserManager) getSystemService(Context.USER_SERVICE);

        if (mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            setContentView(R.layout.telephony_disallowed_preference_screen);
            mUnavailable = true;
            return;
        }

        addPreferencesFromResource(R.xml.carrier_select);

        int subId;
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            subId = intent.getExtras().getInt(GsmUmtsOptions.EXTRA_SUB_ID);
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                mPhoneId = SubscriptionManager.getPhoneId(subId);
            }
        }

        mNetworkList = (PreferenceGroup) getPreferenceScreen().findPreference(LIST_NETWORKS_KEY);
        mNetworkMap = new HashMap<Preference, OperatorInfo>();

        mSearchButton = getPreferenceScreen().findPreference(BUTTON_SRCH_NETWRKS_KEY);
        mAutoSelect = getPreferenceScreen().findPreference(BUTTON_AUTO_SELECT_KEY);

        // Start the Network Query service, and bind it.
        // The OS knows to start he service only once and keep the instance around (so
        // long as startService is called) until a stopservice request is made.  Since
        // we want this service to just stay in the background until it is killed, we
        // don't bother stopping it from our end.
        startService (new Intent(this, NetworkQueryService.class));
        bindService (new Intent(this, NetworkQueryService.class), mNetworkQueryServiceConnection,
                Context.BIND_AUTO_CREATE);

        /* SPRD: Add a dialog for bug 531844 in reliance case. @{ */
        if (TeleServicePluginsHelper.getInstance(this).showDataOffWarning()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(R.drawable.ic_dialog_alert)
                    .setTitle(R.string.dialog_network_selection_title)
                    .setMessage(R.string.data_off_warning)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            finish();
                        }
                    })
                    .setCancelable(false);
            AlertDialog dialog = builder.create();
            dialog.show();
        }
        /* @} */
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsForeground = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsForeground = false;
    }

    /**
     * Override onDestroy() to unbind the query service, avoiding service
     * leak exceptions.
     */
    @Override
    protected void onDestroy() {
        try {
            // used to un-register callback
            mNetworkQueryService.unregisterCallback(mCallback);
        } catch (RemoteException e) {
            log("onDestroy: exception from unregisterCallback " + e);
        }

        if (!mUnavailable) {
            // unbind the service.
            unbindService(mNetworkQueryServiceConnection);
        }
        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id) {

        if ((id == DIALOG_NETWORK_SELECTION) || (id == DIALOG_NETWORK_LIST_LOAD) ||
                (id == DIALOG_NETWORK_AUTO_SELECT)) {
            ProgressDialog dialog = new ProgressDialog(this);
            switch (id) {
                case DIALOG_NETWORK_SELECTION:
                    // It would be more efficient to reuse this dialog by moving
                    // this setMessage() into onPreparedDialog() and NOT use
                    // removeDialog().  However, this is not possible since the
                    // message is rendered only 2 times in the ProgressDialog -
                    // after show() and before onCreate.
                    dialog.setMessage(mNetworkSelectMsg);
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    break;
                case DIALOG_NETWORK_AUTO_SELECT:
                    dialog.setMessage(getResources().getString(R.string.register_automatically));
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    break;
                case DIALOG_NETWORK_LIST_LOAD:
                default:
                    // reinstate the cancelablity of the dialog.
                    dialog.setMessage(getResources().getString(R.string.load_networks_progress));
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setOnCancelListener(this);
                    break;
            }
            return dialog;
        } else if (id == DIALOG_NETWORK_SELECTION_WARNING) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(R.drawable.ic_dialog_alert)
                    .setTitle(R.string.dialog_network_selection_title)
                    .setMessage(R.string.dialog_network_selection_message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    loadNetworksList();
                                }
                            })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });
            AlertDialog dialog = builder.create();
            return dialog;
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if ((id == DIALOG_NETWORK_SELECTION) || (id == DIALOG_NETWORK_LIST_LOAD) ||
                (id == DIALOG_NETWORK_AUTO_SELECT)) {
            // when the dialogs come up, we'll need to indicate that
            // we're in a busy state to dissallow further input.
            getPreferenceScreen().setEnabled(false);
        }
    }

    private void displayEmptyNetworkList(boolean flag) {
        mNetworkList.setTitle(flag ? R.string.empty_networks_list : R.string.label_available);
    }

    private void displayNetworkSeletionInProgress(String networkStr) {
        // TODO: use notification manager?
        mNetworkSelectMsg = getResources().getString(R.string.register_on_network, networkStr);

        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_SELECTION);
        }
    }

    private void displayNetworkQueryFailed(int error) {
        String status = getResources().getString(R.string.network_query_error);

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    private void displayNetworkSelectionFailed(Throwable ex) {
        String status;

        if ((ex != null && ex instanceof CommandException) &&
                ((CommandException)ex).getCommandError()
                  == CommandException.Error.ILLEGAL_SIM_OR_ME)
        {
            status = getResources().getString(R.string.not_allowed);
        } else {
            status = getResources().getString(R.string.connect_later);
        }

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    /* SPRD: Bug 519168 Wrong prompt message when change network selection mode @{ */
    private void displayNetworkSelectionSucceeded(int event) {
        String status = "";
        if (event == EVENT_NETWORK_SELECTION_DONE) {
            status = getResources().getString(R.string.registration_done);
        } else if (event == EVENT_AUTO_SELECT_DONE) {
            status = getResources().getString(R.string.automatic_selection_done);
        }
    /* @} */

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);

        /** SPRD: modify for bug408921: no need to finish activity in 3 seconds.
        mHandler.postDelayed(new Runnable() {
            public void run() {
                finish();
            }
        }, 3000);
        **/
    }

    private void loadNetworksList() {
        if (DBG) log("load networks list...");

        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_LIST_LOAD);
        }

        // delegate query request to the service.
        try {
            mNetworkQueryService.startNetworkQuery(mCallback, mPhoneId);
        } catch (RemoteException e) {
            log("loadNetworksList: exception from startNetworkQuery " + e);
            if (mIsForeground) {
                try {
                    dismissDialog(DIALOG_NETWORK_LIST_LOAD);
                } catch (IllegalArgumentException e1) {
                    // do nothing
                }
            }
        }

        displayEmptyNetworkList(false);
    }

    /**
     * networksListLoaded has been rewritten to take an array of
     * OperatorInfo objects and a status field, instead of an
     * AsyncResult.  Otherwise, the functionality which takes the
     * OperatorInfo array and creates a list of preferences from it,
     * remains unchanged.
     */
    private void networksListLoaded(List<OperatorInfo> result, int status) {
        if (DBG) log("networks list loaded");

        // used to un-register callback
        try {
            mNetworkQueryService.unregisterCallback(mCallback);
        } catch (RemoteException e) {
            log("networksListLoaded: exception from unregisterCallback " + e);
        }

        // update the state of the preferences.
        if (DBG) log("hideProgressPanel");

        // Always try to dismiss the dialog because activity may
        // be moved to background after dialog is shown.
        try {
            dismissDialog(DIALOG_NETWORK_LIST_LOAD);
        } catch (IllegalArgumentException e) {
            // It's not a error in following scenario, we just ignore it.
            // "Load list" dialog will not show, if NetworkQueryService is
            // connected after this activity is moved to background.
            if (DBG) log("Fail to dismiss network load list dialog " + e);
        }

        getPreferenceScreen().setEnabled(true);
        clearList();

        if (status != NetworkQueryService.QUERY_OK) {
            if (DBG) log("error while querying available networks");
            displayNetworkQueryFailed(status);
            displayEmptyNetworkList(true);
        } else {
            if (result != null){
                displayEmptyNetworkList(false);

                // create a preference for each item in the list.
                // just use the operator name instead of the mildly
                // confusing mcc/mnc.
                for (OperatorInfo ni : result) {
                    Preference carrier = new Preference(this, null);
                    carrier.setTitle(getNetworkTitle(ni));
                    carrier.setPersistent(false);
                    /* SPRD Modify: bug#476003 */
                    carrier.setEnabled(TeleServicePluginsHelper.getInstance(this).getDisplayNetworkList(ni,mPhoneId));
                    mNetworkList.addPreference(carrier);
                    mNetworkMap.put(carrier, ni);

                    if (DBG) log("  " + ni);
                }
            } else {
                displayEmptyNetworkList(true);
            }
        }
    }

    /* SPRD Modified: Bug#474249 @{ */
    private String getNetworkTitle(OperatorInfo ni) {
        return formatNetworkTitle(ni);
    }
//    @Orig code:
//    /**
//     * Returns the title of the network obtained in the manual search.
//     *
//     * @param OperatorInfo contains the information of the network.
//     *
//     * @return Long Name if not null/empty, otherwise Short Name if not null/empty,
//     * else MCCMNC string.
//     */
//    private String getNetworkTitle(OperatorInfo ni) {
//        if (!TextUtils.isEmpty(ni.getOperatorAlphaLong())) {
//            return ni.getOperatorAlphaLong();
//        } else if (!TextUtils.isEmpty(ni.getOperatorAlphaShort())) {
//            return ni.getOperatorAlphaShort();
//        } else {
//            BidiFormatter bidiFormatter = BidiFormatter.getInstance();
//            return bidiFormatter.unicodeWrap(ni.getOperatorNumeric(), TextDirectionHeuristics.LTR);
//        }
//    }

    /**
     * SPRD Add: Format network title.
     * @param ni OperatorInfo contains the information of the network.
     * @return "Operator name + ACT + State"
     */
    private String formatNetworkTitle(OperatorInfo ni){
        String operatorAlphaLong = ni.getOperatorAlphaLong();
        String operatorAlphaShort = ni.getOperatorAlphaShort();
        String mccmnc = ni.getOperatorNumeric();
        String act = "";

        //SPRD Modified: Bug#507274
        //Do not use SPNWNAME. OperatorNumeric is reported in format "mccmnc act",
        //so firstly get mccmnc and act. Secondly, as long as mccmnc is reported,
        //updating operator name from XML.
        if(mccmnc != null && mccmnc.length() > 5){
            int index = mccmnc.lastIndexOf(" ");
            if(index != -1){
                //the operatorNumeric is reported in format "mccmnc act"
                act = mccmnc.substring(index + 1);
                mccmnc = mccmnc.substring(0, index);
            } else {
                Log.e(LOG_TAG,"ACT was not reported by RIL with PLMN " + mccmnc);
            }

            String mcc = mccmnc.substring(0, 3);
            String mnc = mccmnc.substring(3);
            //delete zero front of mnc is mncShort
            int mncShort = Integer.parseInt(mnc);
            String tmpMccMnc = "";
            tmpMccMnc = mcc + mncShort;

            //according to PLMN obtain operator name from XML
            String operatorLong = TeleUtils.updateOperator(tmpMccMnc, "numeric_to_operator");
            if (!tmpMccMnc.equals(operatorLong)) {
                operatorAlphaLong = operatorLong;
                operatorAlphaShort = operatorLong;
            } else {
                operatorAlphaLong = mccmnc;
                operatorAlphaShort = mccmnc;
            }
            //Translate operator name to specific language
            operatorAlphaLong = TeleUtils.updateOperator(operatorAlphaLong, "operator");
            operatorAlphaShort = TeleUtils.updateOperator(operatorAlphaShort, "operator");
        }else {
            //OperatorNumeric should by no means be null or empty
            Log.e(LOG_TAG,"invalid mcc mnc code " + mccmnc);
            return "Invalid Network";
        }

        return operatorAlphaShort + " " + getDisplayStringFromAct(act) + "" + getNetworkState(ni.getState());
    }

    /**
     * SPRD Add: Display Act as 2\3\4G
     * @param act 0-GSM\1-GSMCompact\2-UTRAN\7-E-UTRAN
     * @return 2\3\4G
     */
    private String getDisplayStringFromAct(String act) {
        if (act.equals("7")) {
            return "4G";
        } else if (act.equals("2")) {
            return "3G";
        } else if (act.equals("1") || act.equals("0")){
            return "2G";
        } else {
            Log.e(LOG_TAG, "Invalid network act:" + act);
            return act;
        }
    }

    /**
     * SPRD Add: Return network state string
     * @param state OperatorInfo.State
     * @return network state string
     */
    public String getNetworkState(OperatorInfo.State state) {
        Resources res = getResources();
        if(state == null) {
            return "";
        } else if(state == OperatorInfo.State.FORBIDDEN) {
            return ""+" (" + res.getText(R.string.network_inhibit) + ")";
        } else if(state == OperatorInfo.State.UNKNOWN) {
            return ""+" (" + res.getText(R.string.network_unknown) + ")";
        } else {
            return "";
        }
    }
    /*@}*/

    private void clearList() {
        for (Preference p : mNetworkMap.keySet()) {
            mNetworkList.removePreference(p);
        }
        mNetworkMap.clear();
    }

    private void selectNetworkAutomatic() {
        if (DBG) log("select network automatically...");
        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_AUTO_SELECT);
        }

        Message msg = mHandler.obtainMessage(EVENT_AUTO_SELECT_DONE);
        Phone phone = PhoneFactory.getPhone(mPhoneId);
        if (phone != null) {
            phone.setNetworkSelectionModeAutomatic(msg);
        }
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[NetworksList] " + msg);
    }
}
