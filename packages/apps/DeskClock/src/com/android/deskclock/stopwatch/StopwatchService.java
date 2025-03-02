package com.android.deskclock.stopwatch;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.view.View;
import android.widget.RemoteViews;

import com.android.deskclock.CircleTimerView;
import com.android.deskclock.DeskClock;
import com.android.deskclock.HandleDeskClockApiCalls;
import com.android.deskclock.R;
import com.android.deskclock.Utils;

/**
 * TODO: Insert description here. (generated by sblitz)
 */
public class StopwatchService extends Service {
    // Member fields
    private int mNumLaps;
    private long mElapsedTime;
    private long mStartTime;
    private boolean mLoadApp;
    private NotificationManagerCompat mNotificationManager;

    // Constants for intent information
    // Make this a large number to avoid the alarm ID's which seem to be 1, 2, ...
    // Must also be different than TimerReceiver.IN_USE_NOTIFICATION_ID
    private static final int NOTIFICATION_ID = Integer.MAX_VALUE - 1;
    // SPRD: bug 473433 add stopwatch stop function
    public static boolean toApps = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        mNumLaps = 0;
        mElapsedTime = 0;
        mStartTime = 0;
        mLoadApp = false;
        mNotificationManager = NotificationManagerCompat.from(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return Service.START_NOT_STICKY;
        }

        if (mStartTime == 0 || mElapsedTime == 0 || mNumLaps == 0) {
            // May not have the most recent values.
            readFromSharedPrefs();
        }

        String actionType = intent.getAction();
        long actionTime = intent.getLongExtra(Stopwatches.MESSAGE_TIME, Utils.getTimeNow());
        boolean showNotif = intent.getBooleanExtra(Stopwatches.SHOW_NOTIF, true);
        // Update the stopwatch circle when the app is open or is being opened.
        boolean updateCircle = !showNotif
                || intent.getAction().equals(Stopwatches.RESET_AND_LAUNCH_STOPWATCH);
        switch(actionType) {
            case HandleDeskClockApiCalls.ACTION_START_STOPWATCH:
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this) ;
                prefs.edit().putBoolean(Stopwatches.NOTIF_CLOCK_RUNNING, true).apply();
                /* SPRD: bug 473433 add stopwatch stop function @{ */
                if(toApps) {
                    mLoadApp = true ;
                    writeSharedPrefsReset(!updateCircle);
                    clearSavedNotification();
                    closeNotificationShade();
                    stopSelf();
                    toApps = false;
                } else {
                    boolean flag = intent.getBooleanExtra("reset_and_start", false);
                    if(flag){
                        clearSavedNotification();
                        closeNotificationShade();
                        mNumLaps = 0;
                        mElapsedTime =0;
                    }
                    mStartTime = actionTime;
                    writeSharedPrefsStarted(mStartTime, updateCircle);
                    if (showNotif) {
                        setNotification(mStartTime - mElapsedTime, true, mNumLaps);
                    } else {
                        saveNotification(mStartTime - mElapsedTime, true, mNumLaps);
                    }
                    /* @} */
                }
                break;
            case HandleDeskClockApiCalls.ACTION_LAP_STOPWATCH:
                /* SPRD Bug 495353 when mNumLaps more than MAX_LAPS, do nothing @{ */
                if (mNumLaps < Stopwatches.MAX_LAPS - 1) {
                    mNumLaps++;
                    long lapTimeElapsed = actionTime - mStartTime + mElapsedTime;
                    writeSharedPrefsLap(lapTimeElapsed, updateCircle);
                    if (showNotif) {
                        setNotification(mStartTime - mElapsedTime, true, mNumLaps);
                    } else {
                        saveNotification(mStartTime - mElapsedTime, true, mNumLaps);
                    }
                }
                /* @} */
                break;
            case HandleDeskClockApiCalls.ACTION_STOP_STOPWATCH:
                prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit().putBoolean(Stopwatches.NOTIF_CLOCK_RUNNING, false).apply();
                /* SPRD: bug 473433 add stopwatch stop function @{ */
                toApps = intent.getBooleanExtra(StopwatchFragment.STOP_BUTTON_CLICK, false);
                if(toApps){
                    StopwatchFragment.mShowContinue = false;
                } else {
                    StopwatchFragment.mShowContinue = true;
                }
                /* @} */

                mElapsedTime = mElapsedTime + (actionTime - mStartTime);
                writeSharedPrefsStopped(mElapsedTime, updateCircle);
                if (showNotif) {
                    setNotification(actionTime - mElapsedTime, false, mNumLaps);
                } else {
                    saveNotification(mElapsedTime, false, mNumLaps);
                }
                break;
            case HandleDeskClockApiCalls.ACTION_RESET_STOPWATCH:
                mLoadApp = false;
                writeSharedPrefsReset(updateCircle);
                clearSavedNotification();
                stopSelf();
                break;
            case Stopwatches.RESET_AND_LAUNCH_STOPWATCH:
                mLoadApp = true;
                writeSharedPrefsReset(updateCircle);
                clearSavedNotification();
                closeNotificationShade();
                stopSelf();
                break;
            case Stopwatches.SHARE_STOPWATCH:
                if  (mElapsedTime > 0) {
                    closeNotificationShade();
                    Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, Stopwatches.getShareTitle(
                            getApplicationContext()));
                    shareIntent.putExtra(Intent.EXTRA_TEXT, Stopwatches.buildShareResults(
                            getApplicationContext(), mElapsedTime, readLapsFromPrefs()));
                    Intent chooserIntent = Intent.createChooser(shareIntent, null);
                    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getApplication().startActivity(chooserIntent);
                }
                break;
            case Stopwatches.SHOW_NOTIF:
                // SHOW_NOTIF sent from the DeskClock.onPause
                // If a notification is not displayed, this service's work is over
                if (!showSavedNotification()) {
                    stopSelf();
                }
                break;
            case Stopwatches.KILL_NOTIF:
                mNotificationManager.cancel(NOTIFICATION_ID);
                break;

        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mNotificationManager.cancel(NOTIFICATION_ID);
        clearSavedNotification();
        mNumLaps = 0;
        mElapsedTime = 0;
        mStartTime = 0;
        if (mLoadApp) {
            Intent activityIntent = new Intent(getApplicationContext(), DeskClock.class);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activityIntent.putExtra(
                    DeskClock.SELECT_TAB_INTENT_EXTRA, DeskClock.STOPWATCH_TAB_INDEX);
            startActivity(activityIntent);
            mLoadApp = false;
        }
    }

    private void setNotification(long clockBaseTime, boolean clockRunning, int numLaps) {
        Context context = getApplicationContext();
        // Intent to load the app for a non-button click.
        Intent intent = new Intent(context, DeskClock.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(DeskClock.SELECT_TAB_INTENT_EXTRA, DeskClock.STOPWATCH_TAB_INDEX);
        // add category to distinguish between stopwatch intents and timer intents
        intent.addCategory("stopwatch");
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);

        // Set up remoteviews for the notification.
        RemoteViews remoteViewsCollapsed = new RemoteViews(getPackageName(),
                R.layout.stopwatch_notif_collapsed);
        remoteViewsCollapsed.setOnClickPendingIntent(R.id.swn_collapsed_hitspace, pendingIntent);
        remoteViewsCollapsed.setChronometer(
                R.id.swn_collapsed_chronometer, clockBaseTime, null, clockRunning);
        remoteViewsCollapsed.
                setImageViewResource(R.id.notification_icon, R.drawable.stat_notify_stopwatch);
        RemoteViews remoteViewsExpanded = new RemoteViews(getPackageName(),
                R.layout.stopwatch_notif_expanded);
        remoteViewsExpanded.setOnClickPendingIntent(R.id.swn_expanded_hitspace, pendingIntent);
        remoteViewsExpanded.setChronometer(
                R.id.swn_expanded_chronometer, clockBaseTime, null, clockRunning);
        remoteViewsExpanded.
                setImageViewResource(R.id.notification_icon, R.drawable.stat_notify_stopwatch);

        if (clockRunning) {
            // Left button: lap
            remoteViewsExpanded.setTextViewText(
                    R.id.swn_left_button, getResources().getText(R.string.sw_lap_button));
            Intent leftButtonIntent = new Intent(context, StopwatchService.class);
            leftButtonIntent.setAction(HandleDeskClockApiCalls.ACTION_LAP_STOPWATCH);
            remoteViewsExpanded.setOnClickPendingIntent(R.id.swn_left_button,
                    PendingIntent.getService(context, 0, leftButtonIntent, 0));
            remoteViewsExpanded.
                    setTextViewCompoundDrawablesRelative(R.id.swn_left_button,
                            R.drawable.ic_lap_24dp, 0, 0, 0);

            // Right button: stop clock
            remoteViewsExpanded.setTextViewText(
                    R.id.swn_right_button, getResources().getText(R.string.sw_stop_button));
            Intent rightButtonIntent = new Intent(context, StopwatchService.class);
            rightButtonIntent.setAction(HandleDeskClockApiCalls.ACTION_STOP_STOPWATCH);
            remoteViewsExpanded.setOnClickPendingIntent(R.id.swn_right_button,
                    PendingIntent.getService(context, 0, rightButtonIntent, 0));
            remoteViewsExpanded.
                    setTextViewCompoundDrawablesRelative(R.id.swn_right_button,
                            R.drawable.ic_stop_24dp, 0, 0, 0);

            // Show the laps if applicable.
            if (numLaps > 0) {
                String lapText = String.format(
                        context.getString(R.string.sw_notification_lap_number), numLaps);
                remoteViewsCollapsed.setTextViewText(R.id.swn_collapsed_laps, lapText);
                remoteViewsCollapsed.setViewVisibility(R.id.swn_collapsed_laps, View.VISIBLE);
                remoteViewsExpanded.setTextViewText(R.id.swn_expanded_laps, lapText);
                remoteViewsExpanded.setViewVisibility(R.id.swn_expanded_laps, View.VISIBLE);
            } else {
                remoteViewsCollapsed.setViewVisibility(R.id.swn_collapsed_laps, View.GONE);
                remoteViewsExpanded.setViewVisibility(R.id.swn_expanded_laps, View.GONE);
            }
        } else {
            // Left button: reset clock
            remoteViewsExpanded.setTextViewText(
                    R.id.swn_left_button, getResources().getText(R.string.sw_reset_button));
            Intent leftButtonIntent = new Intent(context, StopwatchService.class);
            leftButtonIntent.setAction(Stopwatches.RESET_AND_LAUNCH_STOPWATCH);
            remoteViewsExpanded.setOnClickPendingIntent(R.id.swn_left_button,
                    PendingIntent.getService(context, 0, leftButtonIntent, 0));
            remoteViewsExpanded.
                    setTextViewCompoundDrawablesRelative(R.id.swn_left_button,
                            R.drawable.ic_reset_24dp, 0, 0, 0);

            // Right button: start clock
            remoteViewsExpanded.setTextViewText(
                    R.id.swn_right_button, getResources().getText(R.string.sw_start_button));
            Intent rightButtonIntent = new Intent(context, StopwatchService.class);
            rightButtonIntent.setAction(HandleDeskClockApiCalls.ACTION_START_STOPWATCH);
            remoteViewsExpanded.setOnClickPendingIntent(R.id.swn_right_button,
                    PendingIntent.getService(context, 0, rightButtonIntent, 0));
            remoteViewsExpanded.
                    setTextViewCompoundDrawablesRelative(R.id.swn_right_button,
                            R.drawable.ic_start_24dp, 0, 0, 0);

            // Show stopped string.
            remoteViewsCollapsed.
                    setTextViewText(R.id.swn_collapsed_laps, getString(R.string.swn_stopped));
            remoteViewsCollapsed.setViewVisibility(R.id.swn_collapsed_laps, View.VISIBLE);
            remoteViewsExpanded.
                    setTextViewText(R.id.swn_expanded_laps, getString(R.string.swn_stopped));
            remoteViewsExpanded.setViewVisibility(R.id.swn_expanded_laps, View.VISIBLE);
        }

        Intent dismissIntent = new Intent(context, StopwatchService.class);
        dismissIntent.setAction(HandleDeskClockApiCalls.ACTION_RESET_STOPWATCH);

        Notification notification = new NotificationCompat.Builder(context)
                .setAutoCancel(!clockRunning)
                .setContent(remoteViewsCollapsed)
                .setOngoing(clockRunning)
                .setDeleteIntent(PendingIntent.getService(context, 0, dismissIntent, 0))
                .setSmallIcon(R.drawable.ic_tab_stopwatch_activated)
                .setPriority(Notification.PRIORITY_MAX)
                .setLocalOnly(true)
                .build();
        notification.bigContentView = remoteViewsExpanded;
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    /** Save the notification to be shown when the app is closed. **/
    private void saveNotification(long clockTime, boolean clockRunning, int numLaps) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        if (clockRunning) {
            editor.putLong(Stopwatches.NOTIF_CLOCK_BASE, clockTime);
            editor.putLong(Stopwatches.NOTIF_CLOCK_ELAPSED, -1);
            editor.putBoolean(Stopwatches.NOTIF_CLOCK_RUNNING, true);
        } else {
            editor.putLong(Stopwatches.NOTIF_CLOCK_ELAPSED, clockTime);
            editor.putLong(Stopwatches.NOTIF_CLOCK_BASE, -1);
            editor.putBoolean(Stopwatches.NOTIF_CLOCK_RUNNING, false);
        }
        editor.putBoolean(Stopwatches.PREF_UPDATE_CIRCLE, false);
        editor.apply();
    }

    /** Show the most recently saved notification. **/
    private boolean showSavedNotification() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        long clockBaseTime = prefs.getLong(Stopwatches.NOTIF_CLOCK_BASE, -1);
        long clockElapsedTime = prefs.getLong(Stopwatches.NOTIF_CLOCK_ELAPSED, -1);
        boolean clockRunning = prefs.getBoolean(Stopwatches.NOTIF_CLOCK_RUNNING, false);
        int numLaps = prefs.getInt(Stopwatches.PREF_LAP_NUM, -1);
        if (clockBaseTime == -1) {
            if (clockElapsedTime == -1) {
                return false;
            } else {
                // We don't have a clock base time, so the clock is stopped.
                // Use the elapsed time to figure out what time to show.
                mElapsedTime = clockElapsedTime;
                clockBaseTime = Utils.getTimeNow() - clockElapsedTime;
            }
        }
        setNotification(clockBaseTime, clockRunning, numLaps);
        return true;
    }

    private void clearSavedNotification() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(Stopwatches.NOTIF_CLOCK_BASE);
        editor.remove(Stopwatches.NOTIF_CLOCK_RUNNING);
        editor.remove(Stopwatches.NOTIF_CLOCK_ELAPSED);
        editor.apply();
    }

    private void closeNotificationShade() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(intent);
    }

    private void readFromSharedPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        mStartTime = prefs.getLong(Stopwatches.PREF_START_TIME, 0);
        mElapsedTime = prefs.getLong(Stopwatches.PREF_ACCUM_TIME, 0);
        mNumLaps = prefs.getInt(Stopwatches.PREF_LAP_NUM, Stopwatches.STOPWATCH_RESET);
    }

    private long[] readLapsFromPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        int numLaps = prefs.getInt(Stopwatches.PREF_LAP_NUM, Stopwatches.STOPWATCH_RESET);
        long[] laps = new long[numLaps];
        long prevLapElapsedTime = 0;
        for (int lap_i = 0; lap_i < numLaps; lap_i++) {
            String key = Stopwatches.PREF_LAP_TIME + Integer.toString(lap_i + 1);
            long lap = prefs.getLong(key, 0);
            if (lap == prevLapElapsedTime && lap_i == numLaps - 1) {
                lap = mElapsedTime;
            }
            laps[numLaps - lap_i - 1] = lap - prevLapElapsedTime;
            prevLapElapsedTime = lap;
        }
        return laps;
    }

    private void writeToSharedPrefs(Long startTime, Long lapTimeElapsed, Long elapsedTime,
            Integer state, boolean updateCircle) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        if (startTime != null) {
            editor.putLong(Stopwatches.PREF_START_TIME, startTime);
            mStartTime = startTime;
        }
        if (lapTimeElapsed != null) {
            int numLaps = prefs.getInt(Stopwatches.PREF_LAP_NUM, 0);
            if (numLaps == 0) {
                mNumLaps++;
                numLaps++;
            }
            editor.putLong(Stopwatches.PREF_LAP_TIME + Integer.toString(numLaps), lapTimeElapsed);
            numLaps++;
            editor.putLong(Stopwatches.PREF_LAP_TIME + Integer.toString(numLaps), lapTimeElapsed);
            editor.putInt(Stopwatches.PREF_LAP_NUM, numLaps);
        }
        if (elapsedTime != null) {
            editor.putLong(Stopwatches.PREF_ACCUM_TIME, elapsedTime);
            mElapsedTime = elapsedTime;
        }
        if (state != null) {
            if (state == Stopwatches.STOPWATCH_RESET) {
                editor.putInt(Stopwatches.PREF_STATE, Stopwatches.STOPWATCH_RESET);
            } else if (state == Stopwatches.STOPWATCH_RUNNING) {
                editor.putInt(Stopwatches.PREF_STATE, Stopwatches.STOPWATCH_RUNNING);
            } else if (state == Stopwatches.STOPWATCH_STOPPED) {
                editor.putInt(Stopwatches.PREF_STATE, Stopwatches.STOPWATCH_STOPPED);
            }
        }
        editor.putBoolean(Stopwatches.PREF_UPDATE_CIRCLE, updateCircle);
        editor.apply();
    }

    private void writeSharedPrefsStarted(long startTime, boolean updateCircle) {
        writeToSharedPrefs(startTime, null, null, Stopwatches.STOPWATCH_RUNNING, updateCircle);
        if (updateCircle) {
            long time = Utils.getTimeNow();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext());
            long intervalStartTime = prefs.getLong(
                    Stopwatches.KEY + CircleTimerView.PREF_CTV_INTERVAL_START, -1);
            if (intervalStartTime != -1) {
                intervalStartTime = time;
                SharedPreferences.Editor editor = prefs.edit();
                editor.putLong(Stopwatches.KEY + CircleTimerView.PREF_CTV_INTERVAL_START,
                        intervalStartTime);
                editor.putBoolean(Stopwatches.KEY + CircleTimerView.PREF_CTV_PAUSED, false);
                editor.apply();
            }
        }
    }

    private void writeSharedPrefsLap(long lapTimeElapsed, boolean updateCircle) {
        writeToSharedPrefs(null, lapTimeElapsed, null, null, updateCircle);
        if (updateCircle) {
            long time = Utils.getTimeNow();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext());
            SharedPreferences.Editor editor = prefs.edit();
            long laps[] = readLapsFromPrefs();
            int numLaps = laps.length;
            long lapTime = laps[1];
            if (numLaps == 2) { // Have only hit lap once.
                editor.putLong(Stopwatches.KEY + CircleTimerView.PREF_CTV_INTERVAL, lapTime);
            } else {
                editor.putLong(Stopwatches.KEY + CircleTimerView.PREF_CTV_MARKER_TIME, lapTime);
            }
            editor.putLong(Stopwatches.KEY + CircleTimerView.PREF_CTV_ACCUM_TIME, 0);
            if (numLaps < Stopwatches.MAX_LAPS) {
                editor.putLong(Stopwatches.KEY + CircleTimerView.PREF_CTV_INTERVAL_START, time);
                editor.putBoolean(Stopwatches.KEY + CircleTimerView.PREF_CTV_PAUSED, false);
            } else {
                editor.putLong(Stopwatches.KEY + CircleTimerView.PREF_CTV_INTERVAL_START, -1);
            }
            editor.apply();
        }
    }

    private void writeSharedPrefsStopped(long elapsedTime, boolean updateCircle) {
        writeToSharedPrefs(null, null, elapsedTime, Stopwatches.STOPWATCH_STOPPED, updateCircle);
        if (updateCircle) {
            long time = Utils.getTimeNow();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext());
            long accumulatedTime = prefs.getLong(
                    Stopwatches.KEY + CircleTimerView.PREF_CTV_ACCUM_TIME, 0);
            long intervalStartTime = prefs.getLong(
                    Stopwatches.KEY + CircleTimerView.PREF_CTV_INTERVAL_START, -1);
            accumulatedTime += time - intervalStartTime;
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(Stopwatches.KEY + CircleTimerView.PREF_CTV_ACCUM_TIME, accumulatedTime);
            editor.putBoolean(Stopwatches.KEY + CircleTimerView.PREF_CTV_PAUSED, true);
            editor.putLong(
                    Stopwatches.KEY + CircleTimerView.PREF_CTV_CURRENT_INTERVAL, accumulatedTime);
            editor.apply();
        }
    }

    private void writeSharedPrefsReset(boolean updateCircle) {
        writeToSharedPrefs(null, null, null, Stopwatches.STOPWATCH_RESET, updateCircle);
    }
}
