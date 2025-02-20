/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.internal.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IPowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.WindowManager;
import android.os.Handler;

import android.util.Log;


public class ShutdownActivity extends Activity {

    private static final String TAG = "ShutdownActivity";
    private boolean mReboot;
    private boolean mConfirm;

    private int mSeconds = 15;
    private AlertDialog mDialog;
    private IPowerManager mPm;

    private Handler mHandler = new Handler();
    private Runnable mShutdownAction = new Runnable() {
        @Override
        public void run() {
            mSeconds --;
            if(mDialog != null){
                if (mSeconds > 0) {
                    mDialog.setMessage(getResources().getQuantityString(
                            com.android.internal.R.plurals.shutdown_after_seconds_plurals, mSeconds,
                            mSeconds));
                } else {
                    mDialog.setMessage(getString(com.android.internal.R.string.shutdown_confirm));
                }
            }
            if(mSeconds > 0){
                mHandler.postDelayed(mShutdownAction, 1000);
            }else{
                mHandler.post(new Runnable() {
                    public void run() {
                        Slog.i(TAG,"ShutdownThread->shutdown");
                        try{
                            if(mDialog != null){
                                mDialog.dismiss();
                            }
                            mPm.shutdown(mConfirm, false);
                        }catch(RemoteException e){}
                    }
                });
            }
        }
    };

	private BroadcastReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mReboot = Intent.ACTION_REBOOT.equals(intent.getAction());
        mConfirm = intent.getBooleanExtra(Intent.EXTRA_KEY_CONFIRM, false);
        Slog.i(TAG, "onCreate(): confirm=" + mConfirm + ", reboot="+mReboot);

        mPm = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

		
		mReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if(Intent.ACTION_BATTERY_OKAY.equals(intent.getAction())||
					Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())){
					//ShutDownWakeLock.releaseCpuLock();
					mHandler.removeCallbacks(mShutdownAction);
					if(mReceiver != null) {
						try {
							unregisterReceiver(mReceiver);
						} catch (IllegalArgumentException e) {
							Slog.e(TAG, "Exception: " + e.getMessage());
							e.printStackTrace();
						}
					}
					finish();
				}
			}
		};

		
		IntentFilter filter=new IntentFilter(Intent.ACTION_POWER_CONNECTED);
		filter.addAction(Intent.ACTION_BATTERY_OKAY);
		registerReceiver(mReceiver, filter);

        if(!mConfirm && !mReboot){
            mDialog = new AlertDialog.Builder(this,android.R.style.Theme_Holo_Light_Dialog_MinWidth).create();
            mDialog.getWindow().setBackgroundDrawableResource(com.android.internal.R.color.transparent);
            mDialog.setTitle(com.android.internal.R.string.power_off);
            if (mSeconds > 0) {
                mDialog.setMessage(getResources().getQuantityString(
                    com.android.internal.R.plurals.shutdown_after_seconds_plurals, mSeconds, mSeconds));
            } else {
                mDialog.setMessage(getString(com.android.internal.R.string.shutdown_confirm));
            }
        /*
            mDialog.setButton(DialogInterface.BUTTON_NEUTRAL, getText(com.android.internal.R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mHandler.removeCallbacks(mShutdownAction);
                        dialog.cancel();
                        finish();
                    }});
	   	*/
            mDialog.setCancelable(false);
            mDialog.getWindow().getAttributes().setTitle("ShutdownTiming");
            mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            mDialog.show();
            mHandler.postDelayed(mShutdownAction, 1000);
        }else{
            Thread thr = new Thread("ShutdownActivity") {
                @Override
                public void run() {
                    try {
                        if (mReboot) {
                            mPm.reboot(mConfirm, null, false);
                        } else {
                            mPm.shutdown(mConfirm, false);
                        }
                    } catch (RemoteException e) {
                    }
                }
            };
            thr.start();
            // Wait for us to tell the power manager to shutdown.
            try {
                thr.join();
            } catch (InterruptedException e) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mDialog != null){
            mDialog.dismiss();
            mDialog = null;
        }
    }

}
