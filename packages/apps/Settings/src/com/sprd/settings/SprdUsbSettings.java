/** Created by Spreadst */
package com.sprd.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.location.RadioButtonPreference;

public class SprdUsbSettings extends PreferenceActivity {

    private final String LOG_TAG = "SprdUsbSettings";
    private final static boolean DBG = true;

    private static final String KEY_CHARGE_ONLY = "usb_charge_only";
    private static final String KEY_MTP = "usb_mtp";
    private static final String KEY_PTP = "usb_ptp";
    private static final String KEY_CDROM = "usb_virtual_drive";
    private static final String KEY_MIDI = "usb_midi";
    private static final String KEY_UMS = "usb_storage";

    private RadioButtonPreference mUsbChargeOnly;
    private RadioButtonPreference mMtp;
    private RadioButtonPreference mPtp;
    private RadioButtonPreference mMidi;
    private RadioButtonPreference mCdrom;
    private RadioButtonPreference mUms;

    private UsbManager mUsbManager = null;
    private BroadcastReceiver mPowerDisconnectReceiver = null;

    private final static boolean SUPPORT_UMS = SystemProperties.getBoolean("persist.sys.usb.support_ums", false);

    private boolean SUPPORT_CTA = SystemProperties.getBoolean("ro.usb.support_cta", false);
    private boolean mIsUnlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent i = getBaseContext().registerReceiver(null, new IntentFilter(UsbManager.ACTION_USB_STATE));
        mIsUnlocked = i.getBooleanExtra(UsbManager.USB_DATA_UNLOCKED, false);

        super.onCreate(savedInstanceState);
        if (DBG) Log.d(LOG_TAG, "on Create");
        setContentView(R.layout.sprd_usb_screen);
        addPreferencesFromResource(R.xml.sprd_usb_settings);

        mUsbChargeOnly = (RadioButtonPreference) findPreference(KEY_CHARGE_ONLY);
        mMtp = (RadioButtonPreference) findPreference(KEY_MTP);
        mPtp = (RadioButtonPreference) findPreference(KEY_PTP);
        mCdrom = (RadioButtonPreference) findPreference(KEY_CDROM);
        mMidi = (RadioButtonPreference) findPreference(KEY_MIDI);
        mUms = (RadioButtonPreference) findPreference(KEY_UMS);
        if (!SUPPORT_UMS) {
            getPreferenceScreen().removePreference(mUms);
        }
	getPreferenceScreen().removePreference(mCdrom);
	getPreferenceScreen().removePreference(mMidi);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        boolean isFileTransferRestricted = ((UserManager) getSystemService(Context.USER_SERVICE))
                .hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER);
        if (isFileTransferRestricted) {
            getPreferenceScreen().removePreference(mMtp);
            getPreferenceScreen().removePreference(mPtp);
        }

        mPowerDisconnectReceiver = new PowerDisconnectReceiver();
        registerReceiver(mPowerDisconnectReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (DBG) Log.d(LOG_TAG, "on onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DBG) Log.d(LOG_TAG, "on Resume");
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mPowerDisconnectReceiver);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {

        if (Utils.isMonkeyRunning()) {
            return false;
        }

        if (preference == mUsbChargeOnly) {
            if (mUsbChargeOnly.isChecked()) return false;
            if (SUPPORT_CTA) {
                mUsbManager.setCurrentFunction(UsbManager.USB_FUNCTION_NONE);
            } else {
                mUsbManager.setCurrentFunction(null);
                mUsbManager.setUsbDataUnlocked(false);
                finish();
                return true;
            }
        } else if (preference == mMtp) {
            if (mMtp.isChecked()) return false;
            mUsbManager.setCurrentFunction(UsbManager.USB_FUNCTION_MTP);
        } else if (preference == mPtp) {
            if (mPtp.isChecked()) return false;
            mUsbManager.setCurrentFunction(UsbManager.USB_FUNCTION_PTP);
        } else if (preference == mMidi) {
            if (mMidi.isChecked()) return false;
            mUsbManager.setCurrentFunction(UsbManager.USB_FUNCTION_MIDI);
        } else if (preference == mCdrom) {
            if (mCdrom.isChecked()) return false;
            mUsbManager.setCurrentFunction(UsbManager.USB_FUNCTION_CDROM);
        } else if (preference == mUms) {
            if (mUms.isChecked()) return false;
            mUsbManager.setCurrentFunction(UsbManager.USB_FUNCTION_MASS_STORAGE);
        }
        mUsbManager.setUsbDataUnlocked(true);
        finish();
        return true;
    }

    private void updateUI() {
        String mCurrentfunction = getCurrentFunction();
        Log.i(LOG_TAG, "mCurrentfunction = " + mCurrentfunction);
        uncheckAllUI();

        if (SUPPORT_CTA) {
            if (UsbManager.USB_FUNCTION_NONE.equals(mCurrentfunction)) {
                mUsbChargeOnly.setChecked(true);
            } else if (UsbManager.USB_FUNCTION_MTP.equals(mCurrentfunction)) {
                mMtp.setChecked(true);
            } else if (UsbManager.USB_FUNCTION_PTP.equals(mCurrentfunction)) {
                mPtp.setChecked(true);
            } else if (UsbManager.USB_FUNCTION_MIDI.equals(mCurrentfunction)) {
                mMidi.setChecked(true);
            } else if (UsbManager.USB_FUNCTION_CDROM.equals(mCurrentfunction)) {
                mCdrom.setChecked(true);
            } else if (UsbManager.USB_FUNCTION_MASS_STORAGE.equals(mCurrentfunction)) {
                mUms.setChecked(true);
            }
        } else {
            if (!mIsUnlocked) {
                mUsbChargeOnly.setChecked(true);
            } else if (UsbManager.USB_FUNCTION_CDROM.equals(mCurrentfunction)) {
                mCdrom.setChecked(true);
            } else if (UsbManager.USB_FUNCTION_MASS_STORAGE.equals(mCurrentfunction)) {
                mUms.setChecked(true);
            } else if (UsbManager.USB_FUNCTION_MTP.equals(mCurrentfunction)) {
                mMtp.setChecked(true);
            } else if (UsbManager.USB_FUNCTION_PTP.equals(mCurrentfunction)) {
                mPtp.setChecked(true);
            } else if (UsbManager.USB_FUNCTION_MIDI.equals(mCurrentfunction)) {
                mMidi.setChecked(true);
            }
        }
    }

    private void uncheckAllUI() {
        mUsbChargeOnly.setChecked(false);
        mMtp.setChecked(false);
        mPtp.setChecked(false);
        mMidi.setChecked(false);
        mCdrom.setChecked(false);
        mUms.setChecked(false);
    }

    public String getCurrentFunction() {
        String functions = SystemProperties.get("sys.usb.config", "");
        int commaIndex = functions.indexOf(',');
        if (commaIndex > 0) {
            return functions.substring(0, commaIndex);
        } else {
            return functions;
        }
    }

    private class PowerDisconnectReceiver extends BroadcastReceiver {
        public void onReceive(Context content, Intent intent) {
            int plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            if (DBG) Log.d(LOG_TAG, "plugType = " + plugType);
            if (plugType == 0) {
                SprdUsbSettings.this.finish();
            }
        }
    }

}
