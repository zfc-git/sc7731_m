package com.zediel.pcbtest;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import java.util.Iterator;
import java.util.Stack;

/**
 * activity生命周期管理
 */
public class ActivityLifecycleHelper implements Application.ActivityLifecycleCallbacks {
    private final static String TAG = "ActivityLifecycleHelper";

    private final Stack<Activity> mActivityStack = new Stack<>();
    private final Object mLock = new Object();

    public ActivityLifecycleHelper() {

    }

    public static String getName(final Object object) {
        return object != null ? object.getClass().getName() : "NULL";
    }

    public static boolean equals(final CharSequence a, final CharSequence b) {
        if (a == b) {
            return true;
        }
        int length;
        if (a != null && b != null && (length = a.length()) == b.length()) {
            if (a instanceof String && b instanceof String) {
                return a.equals(b);
            } else {
                for (int i = 0; i < length; i++) {
                    if (a.charAt(i) != b.charAt(i)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        Log.v(TAG, "[onActivityCreated]:" + getName(activity));
        addActivity(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        Log.v(TAG, "[onActivityStarted]:" + getName(activity));
    }

    @Override
    public void onActivityResumed(Activity activity) {
        Log.v(TAG, "[onActivityResumed]:" + getName(activity));
    }

    @Override
    public void onActivityPaused(Activity activity) {
        Log.v(TAG, "[onActivityPaused]:" + getName(activity));
    }

    @Override
    public void onActivityStopped(Activity activity) {
        Log.v(TAG, "[onActivityStopped]:" + getName(activity));
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        Log.v(TAG, "[onActivitySaveInstanceState]:" + getName(activity));
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        Log.v(TAG, "[onActivityDestroyed]:" + getName(activity));
        removeActivity(activity);
    }

    /**
     * 添加Activity到堆栈
     */
    private void addActivity(Activity activity) {
        mActivityStack.add(activity);
    }

    /**
     * 将Activity移出堆栈
     *
     * @param activity
     */
    private void removeActivity(Activity activity) {
        if (activity != null) {
            mActivityStack.remove(activity);
        }
    }

    /**
     * 获取当前Activity（堆栈中最后一个压入的）
     */
    public Activity getCurrentActivity() {
        return mActivityStack.lastElement();
    }

    /**
     * 获取上一个Activity
     *
     * @return 上一个Activity
     */
    public Activity getPreActivity() {
        int size = mActivityStack.size();
        if (size < 2) {
            return null;
        }
        return mActivityStack.elementAt(size - 2);
    }

    /**
     * 某一个Activity是否存在
     *
     * @return true : 存在, false: 不存在
     */
    public boolean isActivityExist(Class<? extends Activity> clazz) {
        for (Activity activity : mActivityStack) {
            if (activity.getClass().equals(clazz)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 结束当前Activity（堆栈中最后一个压入的）
     */
    public void finishCurrentActivity() {
        finishActivity(getCurrentActivity());
    }

    /**
     * 结束上一个Activity
     */
    public void finishPreActivity() {
        finishActivity(getPreActivity());
    }

    /**
     * 结束指定的Activity
     */
    public void finishActivity(Activity activity) {
        if (activity != null) {
            mActivityStack.remove(activity);
            activity.finish();
            activity = null;
        }
    }

    /**
     * 结束指定的Activity
     *
     * @param clazz activity的类
     */
    public void finishActivity(Class<? extends Activity> clazz) {
        Iterator<Activity> it = mActivityStack.iterator();
        synchronized (mLock) {
            while (it.hasNext()) {
                Activity activity = it.next();
                if (equals(clazz.getCanonicalName(), activity.getClass().getCanonicalName())) {
                    if (!activity.isFinishing()) {
                        it.remove();
                        activity.finish();
                    }
                }
            }
        }
    }

    /**
     * 结束所有Activity
     */
    public void finishAllActivity() {
        for (int i = 0, size = mActivityStack.size(); i < size; i++) {
            Activity activity = mActivityStack.get(i);
            if (activity != null) {
                if (!activity.isFinishing()) {
                    Log.v(TAG, "[FinishActivity]:" + getName(activity));
                    activity.finish();
                }
            }
        }
        mActivityStack.clear();
    }

    /**
     * 获取当前Activity的活动栈
     *
     * @return 当前Activity的活动栈
     */
    public Stack<Activity> getActivityStack() {
        return mActivityStack;
    }

    /**
     * 退出
     */
    public void exit() {
        finishAllActivity();
    }
}

