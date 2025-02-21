/** Created by Spreadst */
package com.sprd.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.util.Log;
import android.widget.Toast;

public class SprdUsbReceiver extends BroadcastReceiver {
    private final String TAG = "SprdUsbReceiver";

    private final static boolean SUPPORT_UMS = SystemProperties.getBoolean("persist.sys.usb.support_ums", false);

    private static Context mContext = null;

    private static UsbManager mUsbManager = null;
    private static StorageManager mStorageManager = null;

    private static boolean mUsbConfiged = false;
    private static boolean mBootCompleted = false;

    @Override
    public void onReceive(Context context, Intent intent) {

        if (!SUPPORT_UMS) return; // SPRD: Do nothing if ums is not supported.

        // TODO Auto-generated method stub
        mContext = context;
        if (mUsbManager == null)
            mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (mStorageManager == null)
            mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);

        String action = intent.getAction();

        Log.d(TAG, "action = " + action);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            mBootCompleted = true;
        } else if (UsbManager.ACTION_USB_STATE.equals(action)) {
            mUsbConfiged = intent.getBooleanExtra(UsbManager.USB_CONFIGURED, false);
        }
		if (mBootCompleted == false) {
             mBootCompleted = SystemProperties.getBoolean("sys.boot_completed", false);
        }
		Log.d(TAG,"mBootCompleted = " + mBootCompleted + ", mUsbConfiged = " + mUsbConfiged);
        if (mBootCompleted && mUsbConfiged) {
            enableUsbFunction();
        }
    }

    private void enableUsbFunction() {
        String currentFunction = getCurrentFunction();
        Log.d(TAG,"currentFunction = " + currentFunction);
        if (UsbManager.USB_FUNCTION_MASS_STORAGE.equals(currentFunction)) {
            //usb mass_storage
            if (isUmsAvailable()) {
                Log.d(TAG,"enable Ums function");
                enableUmsFunction();
            } else {
                Log.d(TAG,"SD Card is not exist. reset the function to none");
                mUsbManager.setCurrentFunction(null);
                Toast.makeText(mContext, "Sdcards are not avalible for USB storage, reset to USB default function", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void enableUmsFunction() {
        new Thread() {
            public void run() {
                mStorageManager.enableUsbMassStorage();
            }
        }.start();
    }

    private boolean isUmsAvailable() {
        if (!mUsbConfiged) {
            return false;
        }

        if (isSdcardAvailable()) {
            String mSdcardState = getSdcardState();
            if (!Environment.MEDIA_MOUNTED.equals(mSdcardState)
                    && !Environment.MEDIA_UNMOUNTED.equals(mSdcardState)) {
                return false;
            }
        } else if (isInternalSdcardAvailable()) {
            String mInternalSdcardState = getInternalSdcardState();
            if (!Environment.MEDIA_MOUNTED.equals(mInternalSdcardState)
                    && !Environment.MEDIA_UNMOUNTED
                            .equals(mInternalSdcardState)) {
                return false;
            }
        } else {
            // Both sdcard and internal sdcard are unavailable.
            Log.d(TAG,"Both sdcard and internal sdcard are unavailable.");
            return false;
        }
        return true;
    }

    private boolean isSdcardAvailable() {
        String mSdcardState = getSdcardState();
        if (!Environment.MEDIA_REMOVED.equals(mSdcardState)
                && !Environment.MEDIA_BAD_REMOVAL.equals(mSdcardState)
                && !Environment.MEDIA_NOFS.equals(mSdcardState)
                && !Environment.MEDIA_UNMOUNTABLE.equals(mSdcardState)) {
            return true;
        }

        return false;
    }

    private String getSdcardState() {
        return Environment.getExternalStoragePathState();
    }

    private boolean isInternalSdcardAvailable() {
        String interSdcardState = getInternalSdcardState();

        Log.d(TAG, "interSdcardState = " + interSdcardState
                    + ", isEmulated = " + Environment.internalIsEmulated());
        if (!Environment.MEDIA_REMOVED.equals(interSdcardState)
                && !Environment.MEDIA_BAD_REMOVAL.equals(interSdcardState)
                && !Environment.MEDIA_NOFS.equals(interSdcardState)
                && !Environment.MEDIA_UNMOUNTABLE.equals(interSdcardState)
                && !Environment.internalIsEmulated() ) {
            return true;
        }
        return false;
    }

    /* SPRD: add for internal T card  @{ */
    private String getInternalSdcardState() {
        return Environment.getInternalStoragePathState();
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
}
