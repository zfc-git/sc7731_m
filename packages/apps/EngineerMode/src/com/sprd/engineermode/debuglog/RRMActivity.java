
package com.sprd.engineermode.debuglog;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.telephony.TelephonyManager;
import com.sprd.engineermode.telephony.TelephonyManagerSprd;
import android.util.Log;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.HashSet;

import com.sprd.engineermode.R;
import com.sprd.engineermode.engconstents;
import com.sprd.engineermode.utils.IATUtils;

import android.content.Context;
import android.provider.Settings;
import android.provider.Settings.System;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

public class RRMActivity extends PreferenceActivity implements
OnSharedPreferenceChangeListener,Preference.OnPreferenceClickListener {
    private static final String TAG = "RRMActivity";
    private static final String KEY_SUPPLSERVICEQUERY = "supplementary_service_query";
    private static final String KEY_NETMODE_SELECT = "network_mode";
    private static final String KEY_SIM_INDEX = "simindex";

    private static final String CFU_CONTROL = "persist.sys.callforwarding";

    private static final int SET_CFU = 1;

    private Handler mUiThread = new Handler();
    private RRMHandler mRRMHandler;
    private Context mContext;
    private int mCardCount = 0;
    private int mPhoneId = 0;
    private boolean[] mIsCardExit;

    private Preference mNetModeSelect;
    private ListPreference mSupplementaryServiceQuery;
    private int mPhoneCount;
    private int[] mModemType;
    private TelephonyManager[] mTelephonyManager;

    private boolean isSupportCSFB = SystemProperties.get("persist.radio.ssda.mode").equals("tdd-csfb")
            || SystemProperties.get("persist.radio.ssda.mode").equals("fdd-csfb")
            || SystemProperties.get("persist.radio.ssda.mode").equals("csfb");

    private boolean isSupportLTE = SystemProperties.get("persist.radio.ssda.mode").equals("svlte")
            || SystemProperties.get("persist.radio.ssda.mode").equals("tdd-csfb")
            || SystemProperties.get("persist.radio.ssda.mode").equals("fdd-csfb")
            || SystemProperties.get("persist.radio.ssda.mode").equals("csfb");

    private BroadcastReceiver mMobileReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String action = intent.getAction();
            Log.d(TAG, "action = " + action);
            if (action.startsWith(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                updateModeType();
            }
            /* @} */
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_rrm);
        HandlerThread ht = new HandlerThread(TAG);
        ht.start();
        mRRMHandler = new RRMHandler(ht.getLooper());
        mContext = this;
        mSupplementaryServiceQuery = (ListPreference) findPreference(KEY_SUPPLSERVICEQUERY);
        mNetModeSelect = (Preference)findPreference(KEY_NETMODE_SELECT);
        mNetModeSelect.setOnPreferenceClickListener(this);
        SharedPreferences sharePref = PreferenceManager.getDefaultSharedPreferences(this);
        sharePref.registerOnSharedPreferenceChangeListener(this);
        
        /** Modify NetMode Select Feature 355098 start
         *  NetMode disabled options
         *  1) GSM Modem Type Product
         *  2) LTE Modem Type Product, with 0/2 sim
         */ 
        mPhoneCount = TelephonyManager.from(this).getPhoneCount();
        mIsCardExit = new boolean[mPhoneCount];
        mModemType = new int[mPhoneCount];
        mTelephonyManager = new TelephonyManager[mPhoneCount];
        for (int i = 0; i < mPhoneCount; i++) {
            mModemType[i] = TelephonyManagerSprd.getModemType();
            mTelephonyManager[i] = TelephonyManager.from(this);
            if (mModemType[i] == TelephonyManagerSprd.MODEM_TYPE_TDSCDMA) {
                mNetModeSelect.setSummary(R.string.input_cmcc_card);
            } else if (mModemType[i] == TelephonyManagerSprd.MODEM_TYPE_WCDMA) {
                mNetModeSelect.setSummary(R.string.input_cucc_card);
            }

            if (mTelephonyManager[i].getSimState(i) == TelephonyManager.SIM_STATE_READY) {
                mIsCardExit[i] = true;
            } else {
                mIsCardExit[i] = false;
            }
            if (mIsCardExit[i]) {
                mPhoneId = i;
                mCardCount++;
            }
            Log.d(TAG, "mIsCardExit[" + i + "] = " + mIsCardExit[i]
                    + " mCardCount = " + mCardCount);
        }
        registerReceiver();
        if (TelephonyManagerSprd.getModemType() == TelephonyManagerSprd.MODEM_TYPE_GSM) {
            Log.d(TAG,"GSM production, net mode select not support");
            mNetModeSelect.setEnabled(false);
            mNetModeSelect.setSummary(R.string.feature_not_support);
        }

    }

    @Override
    protected void onStart() {
        mSupplementaryServiceQuery.setSummary(mSupplementaryServiceQuery.getEntry());
        updateModeType();
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        if(mRRMHandler != null){
            Log.d(TAG,"HandlerThread has quit");
            mRRMHandler.getLooper().quit();
        } 
        unregisterReceiver();
        super.onDestroy();
    }

    public void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mContext.registerReceiver(mMobileReceiver, filter);
    }

    public void unregisterReceiver() {
        try {
            mContext.unregisterReceiver(mMobileReceiver);
        } catch (IllegalArgumentException iea) {
            // Ignored.
        }
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        /**
         * NetMode Select dynamic loading Activity
         * 1) LTE:LTEActivity
         * 2) Other:NetModeSelectActivity
         */
        if (pref == mNetModeSelect) {
            if (isSupportLTE) {
                AlertDialog alertDialog = new AlertDialog.Builder(mContext)
                        .setTitle(getString(R.string.network_mode))
                        .setMessage(
                                getString(R.string.switch_mode_warn_message))
                        .setPositiveButton(getString(R.string.alertdialog_ok),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        Intent intent = new Intent(
                                                "android.intent.action.LTEActivity");
                                        intent.putExtra(KEY_SIM_INDEX, mPhoneId);
                                        mContext.startActivity(intent);
                                    }
                                })
                        .setNegativeButton(R.string.alertdialog_cancel,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                    }
                                }).create();
                alertDialog.show();
            } else {
                Intent intent = new Intent("android.intent.action.NetModeSelection");
                intent.putExtra(KEY_SIM_INDEX, 0);
                startActivity(intent);
            }
        }
        return false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key){
        if (key.equals(KEY_SUPPLSERVICEQUERY)){
            mSupplementaryServiceQuery.setSummary(mSupplementaryServiceQuery.getEntry());
            String re = sharedPreferences.getString(key, "");
            Message mSupplService = mRRMHandler.obtainMessage(SET_CFU,re);
            mRRMHandler.sendMessage(mSupplService);  
        }
    }

    private void updateModeType() {
        boolean isStandby = Settings.System.getInt(
                mContext.getContentResolver(), Settings.Global.SIM_STANDBY + 0,
                1) == 1;
        boolean isAirplane = Settings.Global.getInt(
                mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        Log.d(TAG, "isSupportLTE = " + isSupportLTE + " isStandby = "
                + isStandby + " isAirplane = " + isAirplane);
        boolean isEnable = false;
        CharSequence summary = null;
        if (isSupportLTE) {
            if (TelephonyManager.from(mContext).getPhoneCount() == 1) {
                if (mIsCardExit[0]) {
                    if (!isStandby || isAirplane) {
                        isEnable = false;
                        summary = mContext.getString(R.string.open_card_warn);
                    } else {
                        isEnable = true;
                        summary = null;
                    }
                } else {
                    isEnable = false;
                    summary = mContext.getString(R.string.input_sim__warn);
                }
            } else {
                if (mCardCount == 0) {
                    isEnable = false;
                    summary = mContext.getString(R.string.no_sim1_warn);
                } else if (mIsCardExit[1]) {
                    isEnable = false;
                    summary = mContext.getString(R.string.input_sim_warn);
                } else if (mIsCardExit[0]) {
                    if (!isStandby || isAirplane) {
                        isEnable = false;
                        summary = mContext.getString(R.string.open_card_warn);
                    } else {
                        isEnable = true;
                        summary = null;
                    }
                }
            }
            mNetModeSelect.setEnabled(isEnable);
            mNetModeSelect.setSummary(summary);

        }
    }

    class RRMHandler extends Handler {

        public RRMHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg){
            switch (msg.what){
                case SET_CFU:{
                    String valueStr = (String) msg.obj;
                    if (isSupportCSFB) {
                        if (valueStr.equals("1")) {
                            SystemProperties.set(CFU_CONTROL, "1");
                        } else if (valueStr.equals("0") || valueStr.equals("2")) {
                            SystemProperties.set(CFU_CONTROL, "0");
                        } 
                    } else {
                        if (valueStr.equals("0") || valueStr.equals("1")) {
                            SystemProperties.set(CFU_CONTROL, "1");
                        } else if (valueStr.equals("2")) {
                            SystemProperties.set(CFU_CONTROL, "0");
                        } 
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }
}
