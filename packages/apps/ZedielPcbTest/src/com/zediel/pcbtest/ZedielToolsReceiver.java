package com.zediel.pcbtest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class ZedielToolsReceiver extends BroadcastReceiver {

	public ZedielToolsReceiver(){
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
            Uri uri = intent.getData();
            String host = uri.getHost();
            
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
			if("0".equals(host)){
	                i.setClass(context, ZedielTools.class);
	                context.startActivity(i);
			}	
	}
}
