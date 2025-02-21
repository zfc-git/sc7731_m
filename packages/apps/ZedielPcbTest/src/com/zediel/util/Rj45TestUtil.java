
package com.zediel.util;

import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.os.SystemClock;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Iterator;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import java.io.File;

import com.zediel.pcbtest.R;

public class Rj45TestUtil {
    private Context mContext = null;

	public void startTest(Context context) {
        mContext = context;
    }

    public void stopTest() {
    }

    // rj45是否插入@return false 未插入 true 插入
	public  String getrjStatus() {
        String rj45status = mContext.getResources().getString(R.string.ethnet_init);// 默认值
        String status ="0";
        try {
			 File file = new File("sys/class/net/eth0/carrier");
        if (file == null || !file.exists()) {
            return mContext.getResources().getString(R.string.ethnet_init);
        }
            BufferedReader reader = new BufferedReader(new FileReader("sys/class/net/eth0/carrier"));
            status = reader.readLine();
            if("0".equals(status)){
                return mContext.getResources().getString(R.string.ethnet_init);
            }else{
				//Log.i("qyh","ipV6:"+getDefaultIpAddresses(mContext));
                return "ipV6地址 || ipv4地址 "+getDefaultIpAddresses(mContext);
            }
        } catch (IOException e) {
            e.printStackTrace();
			Log.i("error",e+"");
        }

        return rj45status;
    }

	/**
     * Returns the default link's IP addresses, if any, taking into account IPv4 and IPv6 style
     * addresses.
     * @param context the application context
     * @return the formatted and newline-separated IP addresses, or null if none.
     */
    public  String getDefaultIpAddresses(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties prop = cm.getActiveLinkProperties();
        return formatIpAddresses(prop);
    }

    private  String formatIpAddresses(LinkProperties prop) {
        if (prop == null) return null;
        Iterator<InetAddress> iter = prop.getAllAddresses().iterator();
        // If there are no entries, return null
        if (!iter.hasNext()) return null;
        // Concatenate all available addresses, comma separated
        String addresses = "";
        while (iter.hasNext()) {
            addresses += iter.next().getHostAddress();
            if (iter.hasNext()) addresses += "  ||  ";
        }
        if (TextUtils.isEmpty(addresses) || "null".equals(addresses)) {
            return mContext.getResources().getString(R.string.fail);
        }
        return addresses;
    }

}
