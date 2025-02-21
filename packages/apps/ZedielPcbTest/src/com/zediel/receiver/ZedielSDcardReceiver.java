package com.zediel.receiver;

import com.zediel.pcbtest.ZedielTools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;

public class ZedielSDcardReceiver extends BroadcastReceiver {
	ZedielTools guanhongtools;
	public static final int USB_STATE_MSG = 0x00020;  
    public static final int USB_STATE_ON = 0x00021;  
    public static final int USB_STATE_OFF = 0x00022;  
    public IntentFilter filter = new IntentFilter();
	public ZedielSDcardReceiver(Context context) {
		super();
		guanhongtools = (ZedielTools)context;
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_EJECT);
		filter.addAction(Intent.ACTION_MEDIA_REMOVED);
		filter.addDataScheme("file"); 	
	}

	public Intent registerReceiver() {
		return guanhongtools.registerReceiver(this, filter);
	}

	public void unregisterReceiver() {
		guanhongtools.unregisterReceiver(this);
	}

	
	@Override
	public void onReceive(Context arg0, Intent intent) {
		// TODO Auto-generated method stub
		if(guanhongtools.mhandler == null){
			return;
		}
		
		Message msg = new Message();
	    msg.what = USB_STATE_MSG;  
	      
	    if( intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED ) ||  
	    		intent.getAction().equals(Intent.ACTION_MEDIA_CHECKING)){  
	        msg.arg1 = USB_STATE_ON;  
	    }else{  
	        msg.arg1 = USB_STATE_OFF;  
	    }  
	    guanhongtools.mhandler.sendMessage(msg);  

	};
	

}
