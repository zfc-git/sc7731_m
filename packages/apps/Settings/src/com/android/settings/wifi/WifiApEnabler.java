/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.wifi;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.widget.Toast;
import android.os.SystemProperties;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;

import com.android.settings.R;
import com.android.settingslib.TetherUtil;

import java.util.ArrayList;

public class WifiApEnabler {
    private final Context mContext;
    private final SwitchPreference mSwitch;
    private final CharSequence mOriginalSummary;

    private WifiManager mWifiManager;
    private final IntentFilter mIntentFilter;

    ConnectivityManager mCm;
    private String[] mWifiRegexs;

    private boolean supportBtWifiSoftApCoexit = true;
    private BluetoothAdapter mBluetoothAdapter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED);
                if (state == WifiManager.WIFI_AP_STATE_FAILED) {
                    int reason = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_FAILURE_REASON,
                            WifiManager.SAP_START_FAILURE_GENERAL);
                    handleWifiApStateChanged(state, reason);
                } else {
                    handleWifiApStateChanged(state, WifiManager.SAP_START_FAILURE_GENERAL);
                }
            } else if (ConnectivityManager.ACTION_TETHER_STATE_CHANGED.equals(action)) {
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
                updateTetherState(available.toArray(), active.toArray(), errored.toArray());
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                enableWifiSwitch();
            }
        }
    };

    public WifiApEnabler(Context context, SwitchPreference switchPreference) {
        mContext = context;
        mSwitch = switchPreference;
        mOriginalSummary = switchPreference.getSummary();
        switchPreference.setPersistent(false);

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mCm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mWifiRegexs = mCm.getTetherableWifiRegexs();

        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);

        if (SystemProperties.get("ro.btwifisoftap.coexist", "true").equals(
                "false")) {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            supportBtWifiSoftApCoexit = false;
        }
    }

    public void resume() {
        mContext.registerReceiver(mReceiver, mIntentFilter);
        enableWifiSwitch();
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);
    }

    private void enableWifiSwitch() {
        boolean isAirplaneMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        if(!isAirplaneMode) {
            mSwitch.setEnabled(true);
            if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLING
                    || mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED) {
                mSwitch.setChecked(true);
            }
        } else {
            mSwitch.setSummary(mOriginalSummary);
            mSwitch.setEnabled(false);
        }
    }

    public void setSoftapEnabled(boolean enable) {
        final ContentResolver cr = mContext.getContentResolver();
        if (!supportBtWifiSoftApCoexit) {
            int btState = mBluetoothAdapter.getState();
            if (enable
                    && (btState != BluetoothAdapter.STATE_OFF)) {
                Toast.makeText(this.mContext, R.string.softap_bt_cannot_coexist, Toast.LENGTH_SHORT)
                .show();
                return;
            }
        }

        if (enable && !mCm.getMobileDataEnabled()) {
            showAlertForMobileDataNeedEnabled();
        }

        if (enable) {
            Settings.Global.putInt(cr, Settings.Global.SOFTAP_ENABLING_OR_ENABLED, 1);
        }

        if (TetherUtil.setWifiTethering(enable, mContext)) {
            /* Disable here, enabled on receiving success broadcast */
            mSwitch.setEnabled(false);
        } else {
            mSwitch.setSummary(R.string.wifi_error);
        }

    }

    public void updateConfigSummary(WifiConfiguration wifiConfig) {
        String s = mContext.getString(
                com.android.internal.R.string.wifi_tether_configure_ssid_default);
        mSwitch.setSummary(String.format(
                    mContext.getString(R.string.wifi_tether_enabled_subtext),
                    (wifiConfig == null) ? s : wifiConfig.SSID));
    }

    private void updateTetherState(Object[] available, Object[] tethered, Object[] errored) {
        boolean wifiTethered = false;
        boolean wifiErrored = false;

        for (Object o : tethered) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) wifiTethered = true;
            }
        }
        for (Object o: errored) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) wifiErrored = true;
            }
        }

        if (wifiTethered) {
            WifiConfiguration wifiConfig = mWifiManager.getWifiApConfiguration();
            updateConfigSummary(wifiConfig);
        } else if (wifiErrored) {
            mSwitch.setSummary(R.string.wifi_error);
        }
    }

    private void handleWifiApStateChanged(int state, int reason) {
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                mSwitch.setSummary(R.string.wifi_tether_starting);
                mSwitch.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                /**
                 * Summary on enable is handled by tether
                 * broadcast notice
                 */
                mSwitch.setChecked(true);
                /* Doesnt need the airplane check */
                mSwitch.setEnabled(true);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                mSwitch.setSummary(R.string.wifi_tether_stopping);
                mSwitch.setChecked(false);
                mSwitch.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                mSwitch.setChecked(false);
                mSwitch.setSummary(mOriginalSummary);
                enableWifiSwitch();
                break;
            default:
                mSwitch.setChecked(false);
                if (reason == WifiManager.SAP_START_FAILURE_NO_CHANNEL) {
                    mSwitch.setSummary(R.string.wifi_sap_no_channel_error);
                } else {
                    mSwitch.setSummary(R.string.wifi_error);
                }
                enableWifiSwitch();
        }
    }

    /* SPRD: Modify Bug 316222 show tip for wifi hotspot by mobile data disabled @{ */
    private void showAlertForMobileDataNeedEnabled() {
        Toast.makeText(mContext, R.string.softap_need_mobile_data_enabled, Toast.LENGTH_LONG)
                .show();
    }
    /* @} */
}
