package com.zediel.pcbtest;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.text.TextUtils;

import java.util.List;

public class MyApp extends Application {
    private final static String TAG = "MyApp";
    private static MyApp sMyApp;
    private static Handler sHandler;
    private static ActivityLifecycleHelper sLifecycleHelper = new ActivityLifecycleHelper() {

        @Override
        public void onActivityStopped(Activity activity) {
            super.onActivityStopped(activity);
            Log.d(TAG, "[onActivityStopped]:" + ActivityLifecycleHelper.getName(activity));
            if (sHandler != null) {
                sHandler.sendEmptyMessageDelayed(0, 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sMyApp = this;
        sHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (sLifecycleHelper != null) {
		            Log.d(TAG, "[handleMessage] size=" +sLifecycleHelper.getActivityStack().size());
	                if (sLifecycleHelper.getActivityStack().size() > 0) {
		            	Log.d(TAG, "[handleMessage] isTopActivity=" + isTopActivity(sMyApp.getPackageName()));
		                if (!isTopActivity(sMyApp.getPackageName())) {
		                    startActivity(sLifecycleHelper.getCurrentActivity().getIntent());
		                }
	                } else {
	                	//onExit();
	                }
                }
            }
        };
        registerActivityLifecycleCallbacks(sLifecycleHelper);
    }

    public static MyApp getMyApp() {
        return sMyApp;
    }

    public static void onExit() {
		Log.d(TAG, "[onExit]");
        if (sMyApp != null) {
            sMyApp.unregisterActivityLifecycleCallbacks(sLifecycleHelper);
        }
        if (sHandler != null) {
            sHandler.removeCallbacksAndMessages(null);
        }
    }

    public static boolean isTopActivity(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        ComponentName topActivity = getTopActivityComponent();
        return topActivity != null && packageName.equals(topActivity.getPackageName());
    }

    public static ComponentName getTopActivityComponent() {
        ComponentName topActivity = null;
        ActivityManager activityManager = (ActivityManager) sMyApp.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTaskInfos = activityManager
                .getRunningTasks(1);
        if (runningTaskInfos != null) {
            topActivity = runningTaskInfos.get(0).topActivity;
        }
        return topActivity;
    }
}