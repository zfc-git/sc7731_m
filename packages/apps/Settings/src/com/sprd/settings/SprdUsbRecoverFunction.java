/** Created by Spreadst */
package com.sprd.settings;

import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.util.Log;
import android.widget.Toast;

public class SprdUsbRecoverFunction extends BroadcastReceiver {
    private final String TAG = "SprdUsbRecoverFunction";

    private final static boolean mSupportRecoverFunction = true;

    private static Context mContext = null;

    @Override
    public void onReceive(Context context, Intent intent) {

        if (!mSupportRecoverFunction) return; // SPRD: Do nothing if RecoverFunction is not supported.

        // TODO Auto-generated method stub
        mContext = context;

        String action = intent.getAction();

        Log.d(TAG, "action = " + action);

        if (UsbManager.ACTION_USB_STATE.equals(action)) {
            if (intent.getBooleanExtra(UsbManager.USB_CONNECTED, false)) {

                if (SystemProperties.get("persist.sys.sprd.mtbf", "1").equals("0")) {
                    return;
                }
                /* To determine whether the boot unlock interface. @{ */
                //recoverFunction();
                ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                List<RunningTaskInfo> info=manager.getRunningTasks(1);
                String classname = null;
                 if( null != info && !(info.isEmpty())){
                    classname=info.get(0).topActivity.getClassName();
                    // add EmergencyDialer to avoid show usb setting when plugining usb line
                    if (classname.equals("com.android.settings.CryptKeeper") || "com.android.phone.EmergencyDialer".equals(classname)) {
                       return;
                    }else {
                        //recoverFunction();
                    }
                 }else{
                        //recoverFunction();
                }
              /* @} */
            }
        }
    }

    private void recoverFunction() {
        Intent sprdIntent = new Intent(
                "com.sprd.settings.APPLICATION_SPRD_USB_SETTINGS");
        sprdIntent.setClass(mContext, SprdUsbSettings.class);
        sprdIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mContext.startActivity(sprdIntent);
    }
}
