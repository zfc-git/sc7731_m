package com.sprd.android.fdn.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.ActivityThread;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;
import android.telephony.SubscriptionInfo;

import com.android.internal.telephony.IFdnService;

public class FdnServiceUtil {

    /**
     * @hide
     */
    public synchronized static IFdnService getIFdnService(Context context) {
        if (mService == null) {
            tryConnectingFdnService(context);
            return mService;
        }
        return mService;
    }

    /**
     * @hide
     */
    public static void tryConnectingFdnService(Context context) {

        synchronized (mObject) {
            Log.i(TAG, " SmsManager is going to Connect IFdnService");
            if (mService != null) {
                Log.i("FdnService", "SmsManager Already connected");
                return;
            }
            final Intent intent = new Intent();
            intent.putIntegerArrayListExtra("AcitivitySubIds",
                    getSubIds(SmsManager.getDefault().getActiveSubInfoList()));
            intent.setComponent(FDN_SERVICE_COMPONENT);
            try {
                if (!context.bindService(intent, mConnection,
                        Context.BIND_AUTO_CREATE)) {
                    Log.i(TAG, "SmsManager Failed to bind to FdnService");
                }
            } catch (SecurityException e) {
                Log.i(TAG, "Forbidden to bind to FdnService", e);
            }
            try {
                mObject.wait(3000);
            } catch (Exception e) {
                System.out.println("error happended when wait for FdnService");
                mObject.notify();
            }
        }
    }

    /**
     * 
     * @hide
     */
    private static ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "FdnService mConnection connected");
            synchronized(mObject){
                mService = IFdnService.Stub.asInterface(service);
                isConnected = true;
                mObject.notify();
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "FdnService mConnection unexpectedly disconnected");
            synchronized(mObject){
                mService = null;
                isConnected = false;
                mObject.notify();
            }

        }
    };


    private static ArrayList<Integer> getSubIds(
            List<SubscriptionInfo> mSubscriptionInfolist) {
        if (mSubscriptionInfolist == null) {
            Log.i(TAG, "mSubscriptionInfolist == null");
            return null;
        }
        Iterator<SubscriptionInfo> iterator = mSubscriptionInfolist.iterator();
        if (iterator == null) {
            Log.i(TAG, "mSubscriptionInfolist's iterator== null");
            return null;
        }

        ArrayList<Integer> arrayList = new ArrayList<Integer>(
                mSubscriptionInfolist.size());
        while (iterator.hasNext()) {
            SubscriptionInfo subInfo = iterator.next();
            int phoneId = subInfo.getSimSlotIndex();
            arrayList.add(subInfo.getSubscriptionId());
        }
        return arrayList;
    }

    /** @hide */
    private static IFdnService mService;

    private final static Object mObject = new Object();

    /** @hide */
    public static boolean isConnected = false;
    private static boolean isWaiting = false;

    public static final String TAG = "FdnService";
    /** @hide */
    private static final ComponentName FDN_SERVICE_COMPONENT = new ComponentName(
            "com.spread.cachefdn", "com.spread.cachefdn.SprdFdnService");

    private static FdnServiceManger mFdnServiceManger;

}
