package com.sprd.fileexplorer;

import java.io.File;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.sprd.fileexplorer.util.StorageUtil;

public class StorageReceiver extends BroadcastReceiver {
    
    private static final String TAG = "StorageReceiver";
    // SPRD: Modify for bug509242.
    private boolean mSdcard = false;
    /*
     * 
     * <receiver android:name="test.spreadtrum.storagemodule.StorageReceiver" >
     * <intent-filter> <action android:name="android.intent.action.MEDIA_EJECT"
     * /> <action android:name="android.intent.action.MEDIA_MOUNTED" /> <action
     * android:name="android.intent.action.MEDIA_BAD_REMOVAL" /> <action
     * android:name="android.intent.action.MEDIA_UNMOUNTED" />
     * 
     * <data android:scheme="file" /> </intent-filter> </receiver>
     */

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive, mSdcard = " + mSdcard + "; intent: " + intent);

        File storageFile = whichStorage(intent.getData(), intent);
        if(storageFile == null) {
            return;
        }
        String path = storageFile.getAbsolutePath();
        String action  = intent.getAction();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean preAvailable = settings.getBoolean(path, false);
        Log.d(TAG, "onReceive, preAvailable = " + preAvailable + "; mSdcard = " + mSdcard + "; path = " + path);
        if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            if(!preAvailable) {
                Log.d(TAG, "onReceive, SD card was mounted.");
                settings.edit().putBoolean(path, true).commit();
                // SPRD: Modify for bug509242.
                StorageUtil.notifyStorageChanged(path, true, mSdcard);
            }
        } else if (Intent.ACTION_MEDIA_EJECT.equals(action)
                || Intent.ACTION_MEDIA_UNMOUNTED.equals(action)
                || Intent.ACTION_MEDIA_BAD_REMOVAL.equals(action)) {
            // SPRD: Modify for bug503699.
            //if(preAvailable && Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
            if(preAvailable && Intent.ACTION_MEDIA_EJECT.equals(action)) {
                Log.d(TAG, "onReceive, SD card was ejected.");
                settings.edit().putBoolean(path, false).commit();
                // SPRD: Modify for bug509242.
                StorageUtil.notifyStorageChanged(path, false, mSdcard);
            }
        }

    }

    private File whichStorage(Uri uri, Intent intent) {
        if(uri == null || uri.getPath() == null) {
            Log.e(TAG, "whichStorage, intent.data.path is null");
            return null;
        }
        String path = uri.getPath();
        Log.e(TAG, "whichStorage, path = " + path + "; getExternalStorage() = " + StorageUtil.getExternalStorage());
        /* SPRD: Modify for bug509242. @{ */
        if(StorageUtil.isInExternalStorage(path)) {
            mSdcard = true;
            return StorageUtil.getExternalStorage();
        } else if(StorageUtil.isInInternalStorage(path)) {
            mSdcard = false;
            return StorageUtil.getInternalStorage();
        } else if(StorageUtil.isInUSBStorage(path, intent)) {
            mSdcard = false;
            return StorageUtil.getUSBStorage();
        } else {
            mSdcard = false;
            Log.e(TAG, "whichStorage, can not found the uri: " + uri + " in which storage");
            return null;
        }
        /* @} */
    }
}
