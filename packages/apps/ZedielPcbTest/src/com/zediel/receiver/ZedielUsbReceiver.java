package com.zediel.receiver;

import com.zediel.pcbtest.ZedielTools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Message;
import android.util.Log;

public class ZedielUsbReceiver extends BroadcastReceiver {
	ZedielTools mContext;
	public static final int USB_STATE_MSG = 0x00020;
	public static final int USB_STATE_ON = 0x00021;
	public static final int USB_STATE_OFF = 0x00022;

	public static final int CHARGE_IN = 0x00030;
	public static final int AC_CHARGE_IN = 0x00031;
	public static final int USB_CHARGE_IN = 0x00032;
	private static final String TAG = "guanhongUsbReceiver";
	
	
	public IntentFilter filter = new IntentFilter();

	public ZedielUsbReceiver(Context context) {
		super();
		mContext  = (ZedielTools) context;
		filter.addAction(Intent.ACTION_MEDIA_CHECKING);
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_EJECT);
		filter.addAction(Intent.ACTION_MEDIA_REMOVED);
		filter.addDataScheme("file");

		filter.addAction(Intent.ACTION_BATTERY_CHANGED);
	}

	public Intent registerReceiver() {
		//return guanhongtools.registerReceiver(this, filter);
		return mContext.registerReceiver(ZedielUsbReceiver.this, filter);
	}

	public void unregisterReceiver() {
		//guanhongtools.unregisterReceiver(this);
		mContext.unregisterReceiver(ZedielUsbReceiver.this);
	}

	@Override
	public void onReceive(Context arg0, Intent intent) {
		// TODO Auto-generated method stub
		if (mContext.mhandler == null) {
			return;
		}

		Message msg = new Message();
		msg.what = USB_STATE_MSG;

		if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)
				|| intent.getAction().equals(Intent.ACTION_MEDIA_CHECKING)) {
			msg.arg1 = USB_STATE_ON;
		} else {
			msg.arg1 = USB_STATE_OFF;
		}
		mContext.mhandler.sendMessage(msg);
		
		Message msgcharg = new Message();
		msgcharg.what = CHARGE_IN;
		if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
			int plugged = intent.getIntExtra("plugged", 0); //
			switch (plugged) {
			case BatteryManager.BATTERY_PLUGGED_AC:
				msgcharg.arg1 = AC_CHARGE_IN;
				break;
			case BatteryManager.BATTERY_PLUGGED_USB:
				msgcharg.arg1 = USB_CHARGE_IN;
				break;
			}
			
			int voltage = intent.getIntExtra("voltage", 0);
			msgcharg.arg2 = voltage;
			Log.i(TAG,"<<<<<<< the voltage is >>>>>"+voltage);
		}
		mContext.mhandler.sendMessage(msgcharg);

	}

}
