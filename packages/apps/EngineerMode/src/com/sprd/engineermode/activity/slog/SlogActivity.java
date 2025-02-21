
package com.sprd.engineermode.activity.slog;

/*
 * Copyright (C) 2013 Spreadtrum Communications Inc.
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

import static com.sprd.engineermode.debuglog.slogui.SlogService.NOTIFICATION_SNAP;
import static com.sprd.engineermode.debuglog.slogui.SlogService.NOTIFICATION_LOW_STORAGE;
import static com.sprd.engineermode.debuglog.slogui.SlogService.SERVICE_SLOG_KEY;
import static com.sprd.engineermode.debuglog.slogui.SlogService.SERVICE_SNAP_KEY;
import static com.sprd.engineermode.debuglog.slogui.SlogService.SERVICES_SETTINGS_KEY;

import java.io.File;
import java.util.Date;
import java.util.HashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.shapes.Shape;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.KeyEvent;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.content.ContextWrapper;

import com.sprd.engineermode.debuglog.slogui.ISlogService;
import com.android.internal.app.IMediaContainerService;
import com.sprd.engineermode.core.SlogCore;
import com.sprd.engineermode.debuglog.slogui.SlogUIAlert.AlertCallBack;
import com.sprd.engineermode.R;
import com.sprd.engineermode.debuglog.slogui.AbsSlogUIActivity;
import com.sprd.engineermode.debuglog.slogui.ModemLogSettings;
//import com.spreadst.android.eng.engconstents;
import com.sprd.engineermode.debuglog.slogui.SlogAction;
import com.sprd.engineermode.debuglog.slogui.SlogConfListener;
import com.sprd.engineermode.debuglog.slogui.SlogService;
import com.sprd.engineermode.debuglog.slogui.SlogUIAlert;
import com.sprd.engineermode.debuglog.slogui.SlogUIAlert.AlertCallBack;
import com.sprd.engineermode.debuglog.slogui.SlogUICommonControl;
import com.sprd.engineermode.debuglog.slogui.StorageUtil;
import android.graphics.Color;
import com.sprd.engineermode.utils.SocketUtils;
import android.os.Message;
import android.os.Handler;
import com.android.internal.app.IMediaContainerService;

import android.os.SystemProperties;

public class SlogActivity extends Activity implements OnClickListener {
    private ToggleButton mGeneral;
    private ProgressBar mStorageUsage;

    private Button mClear;
    private Button mScene;
    private Button mTool;
    private TextView mSceneInfo;
    private TextView mSceneTime;
    private TextView mSceneSavePath;
    private TextView mStorageUsed;
    private TextView mStorageFree;
    private TextView mCPLogPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_slog_main);

        mGeneral = (ToggleButton) findViewById(R.id.general);
        mClear = (Button) findViewById(R.id.clearlog);
        mTool = (Button) findViewById(R.id.btn_tool);
        mScene = (Button) findViewById(R.id.btn_scene);

        mSceneInfo = (TextView) findViewById(R.id.tv_scene);
        mSceneTime = (TextView) findViewById(R.id.tv_logtime);
        mSceneSavePath = (TextView) findViewById(R.id.tv_logpath);
        mStorageUsed = (TextView) findViewById(R.id.storage_usage_used);
        mStorageFree = (TextView) findViewById(R.id.storage_usage_free);
        mStorageUsage = (ProgressBar) findViewById(R.id.storage_usage);
        mCPLogPath = (TextView) findViewById(R.id.tv_modem_logpath);

        mGeneral.setOnClickListener(this);
        mClear.setOnClickListener(this);
        mTool.setOnClickListener(this);
        SlogInfo.x = SlogActivity.this;

        mScene.setOnClickListener(this);
        mMainThreadHandler = new Handler(getMainLooper());

        bindService(new Intent().setComponent(DEFAULT_CONTAINER_COMPONENT),
                mMCSConnection, BIND_AUTO_CREATE);

        bindSlogService();
        initPopuptWindow();

        // mSettings.edit().putBoolean(SERVICE_SLOG_KEY, false);
        // mSettings.edit().putBoolean(SERVICE_SNAP_KEY, false);

    }

    @Override
    public void onResume() {
        super.onResume();
        mGeneral.setText(SlogInfo.self().getTitle(SlogInfo.self().getSceneStatus()));
        mGeneral.setChecked(SlogInfo.self().slog_tmp == SlogInfo.SceneStatus.close);
        mGeneral.setBackgroundColor(SlogInfo.self().slog_tmp == SlogInfo.SceneStatus.close ? Color.GREEN
                : Color.GRAY);
        mSceneTime.setText("0:0:0");

        mSceneInfo.setText(SlogInfo.self().getSceneInfo(SlogInfo.self().getSceneStatus()));
        mHandler.obtainMessage(2).sendToTarget();
        mHandler.obtainMessage(4).sendToTarget();
        // mSceneSavePath.setText("Path: " + SlogCore.SlogCore_GetLogSavePath());

    }

    @Override
    public void onPause() {
        mStop = true;
        super.onPause();

    }

    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        switch (v.getId()) {
            case R.id.general:
                if (mGeneral.isChecked()) {
                    Log.d("Slog", "open");
                    SlogInfo.self().open(SlogInfo.self().getSceneStatus());
                    mStop = false;
                    mHandler.obtainMessage(2).sendToTarget();
                    mGeneral.setBackgroundColor(Color.GREEN);
                }
                else {
                    Log.d("Slog", "close");
                    mStop = true;
                    SlogInfo.self().closeScene();
                    mGeneral.setBackgroundColor(Color.GRAY);
                }
                break;
            case R.id.clearlog:
                new AlertDialog.Builder(this)
                        .setTitle("clear")
                        .setMessage(this.getString(R.string.slog_want_clear))
                        .setPositiveButton(this.getString(R.string.alertdialog_ok),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        SlogAction.clear(SlogActivity.this);
                                        SlogInfo.self().resetStartTime();
                                    }
                                })
                        .setNegativeButton(this.getString(R.string.alertdialog_cancel), null)
                        .show();
                break;
            case R.id.btn_tool:
                getPopupWindow();
                int[] location = new int[2];
                v.getLocationOnScreen(location);
                popupWindow.showAtLocation(v, Gravity.TOP | Gravity.CENTER_VERTICAL, 0, location[1]
                        - popupWindow.getHeight() + 20);
                break;
            case R.id.btn_scene:
                Intent iScene = new Intent(this, SceneActivity.class);
                startActivity(iScene);
                break;
        }
    }

    public int time = 0;
    Runnable runnable_Time = new Runnable() {
        @Override
        public void run() {
            while (mStop != true) {
                mHandler.obtainMessage(0).sendToTarget();
                // if((time=(time+1)%100)==0)
                mHandler.obtainMessage(4).sendToTarget();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    };
    protected static final ComponentName DEFAULT_CONTAINER_COMPONENT = new ComponentName(
            "com.android.defcontainer",
            "com.android.defcontainer.DefaultContainerService");
    IMediaContainerService mMediaContainerService;
    protected boolean mMCSEnable;
    protected Handler mMainThreadHandler;
    private ServiceConnection mMCSConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMCSEnable = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMediaContainerService = IMediaContainerService.Stub
                    .asInterface(service);
            mMCSEnable = true;
            setUsage();
        }
    };

    @Override
    public void onDestroy() {
        unbindService(mMCSConnection);
        super.onDestroy();
    }

    private void setUsage() {
        if (mStorageUsed == null || mStorageUsage == null
                || mStorageFree == null || mMediaContainerService == null) {
            return;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean isExternal = StorageUtil.getExternalStorageState();
                final long freespace = SlogAction.getFreeSpace(
                        mMediaContainerService, isExternal);
                // Log.d("slog123",String.format("%d",freespace));
                final long total = SlogAction.getTotalSpace(
                        mMediaContainerService, isExternal);
                // Log.d("slog123",String.format("%d",total));
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        int progress = 0;
                        if (total != 0) {
                            progress = (int) ((total - freespace) * 100 / total);
                        }
                        mStorageFree.setText(Formatter.formatFileSize(
                                SlogActivity.this, freespace)
                                + " "
                                + getText(R.string.storage_free));
                        mStorageUsed.setText(Formatter.formatFileSize(
                                SlogActivity.this, total - freespace)
                                + " " + getText(R.string.storage_usage));
                        mStorageUsage.setProgress(progress);

                    }
                });
            }
        });
    }

    private boolean mStop;
    public Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    long span = (new Date()).getTime()
                            - SlogInfo.self().getSlogStartTime().getTime();
                    long day = span / (24 * 60 * 60 * 1000);
                    long hour = (span / (60 * 60 * 1000));
                    long min = ((span / (60 * 1000)) - hour * 60);
                    long s = (span / 1000 - hour * 60 * 60 - min * 60);
                    mSceneTime.setText(
                            String.format("%d:%d:%d", hour, min, s));
                    break;
                case 1:
                    break;
                case 2:
                    mStop = !mGeneral.isChecked();
                    new Thread(runnable_Time).start();
                    break;
                case 3:
                    btnDumpWcn.setClickable(true);
                    break;
                case 4:
                    mSceneSavePath.setText("Path: " + SlogCore.SlogCore_GetLogSavePath());
                    boolean isExternal = StorageUtil.getExternalStorageState();
                    if (SystemProperties.get("persist.sys.engpc.disable")
                            .equals("0")) {
                        mCPLogPath.setText("Path: "
                                + getResources().getString(
                                        R.string.modem_saved_pc));
                    } else {
                        mCPLogPath.setText("Path: "
                                + (isExternal ? StorageUtil.getExternalStorage().getAbsolutePath()
                                        + File.separator + "modem_log"
                                        : Environment.getDataDirectory()
                                                .getAbsolutePath()
                                                + File.separator
                                                + "modem_log"));
                    }
                    setUsage();
                    break;
                case 5:
                    new Thread(sleepLogThread).start();
                    break;
                case 6:
                    new Thread(logBufThread).start();
                    break;
                case 7:
                    Toast.makeText(getApplicationContext(), "is saving sleep log",
                            Toast.LENGTH_LONG).show();
                    break;
                case 8:
                    Toast.makeText(getApplicationContext(), "is saving log buf", Toast.LENGTH_LONG)
                            .show();
                    break;
                case 9:
                    Toast.makeText(getApplicationContext(), getResources().getString(
                            R.string.slogmodem_error_1), Toast.LENGTH_LONG)
                            .show();
                    break;
                case 10:
                    Toast.makeText(getApplicationContext(), getResources().getString(
                            R.string.slogmodem_error_2), Toast.LENGTH_LONG)
                            .show();
                    break;
                case 11:
                    Toast.makeText(getApplicationContext(), getResources().getString(
                            R.string.slogmodem_error_3), Toast.LENGTH_LONG)
                            .show();
                    break;
                case 12:
                    Toast.makeText(getApplicationContext(), getResources().getString(
                            R.string.slogmodem_error_4), Toast.LENGTH_LONG)
                            .show();
                    break;
                case 13:
                    Toast.makeText(getApplicationContext(), getResources().getString(
                            R.string.slogmodem_error_5), Toast.LENGTH_LONG)
                            .show();
                    break;
                case 14:
                    Toast.makeText(getApplicationContext(), getResources().getString(
                            R.string.slogmodem_error_6), Toast.LENGTH_LONG)
                            .show();
                    break;
                case 15:
                    Toast.makeText(getApplicationContext(), getResources().getString(
                            R.string.slogmodem_error_7), Toast.LENGTH_LONG)
                            .show();
                    break;
                case 16:
                    Toast.makeText(getApplicationContext(), getResources().getString(
                            R.string.slogmodem_error_7), Toast.LENGTH_LONG)
                            .show();
                    break;
                case 17:
                    Toast.makeText(getApplicationContext(),"start dump wcn mem", Toast.LENGTH_LONG)
                            .show();
                    new Thread(runnable_dump).start();
                case 18:
                    Toast.makeText(getApplicationContext(),"dump wcn mem finished", Toast.LENGTH_LONG)
                            .show();
                    btnDumpWcn.setClickable(true);

            }

        }

    };

    private Runnable runnable_dump = new Runnable() {
        @Override
        public void run() {
            SlogCore.dumpwcnMem();
            mHandler.obtainMessage(18).sendToTarget();
        }
    };

    private Runnable sleepLogThread = new Runnable() {
        @Override
        public void run() {
            Log.d("SlogActivity", "save sleeplog start");
            int ret = SlogCore.saveSleepLog();
            Log.d("SlogActivity", "save sleeplog finish");
            if (ret == 0) {
                mHandler.obtainMessage(7).sendToTarget();
            } else {
                mHandler.obtainMessage(8 + ret).sendToTarget();
            }
        }
    };

    private Runnable logBufThread = new Runnable() {
        @Override
        public void run() {
            Log.d("SlogActivity", "save logbuf start");
            int ret = SlogCore.saveLogBuf();
            Log.d("SlogActivity", "save logbuf finish");
            if (ret == 0) {
                mHandler.obtainMessage(8).sendToTarget();
            } else {
                mHandler.obtainMessage(8 + ret).sendToTarget();
            }
        }
    };

    private PopupWindow popupWindow;
    private Button btnDumpWcn;

    public static final int NOTIFICATION_SLOG = 1;

    protected ISlogService mService;
    private ServiceConnection mSlogConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // onSlogServiceDisconnected();
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ISlogService.Stub.asInterface(service);
            onSlogServiceConnected();
        }
    };

    public void bindSlogService() {
        Intent intent = new Intent();
        intent.setClass(getApplicationContext(), SlogService.class);
        bindService(intent, mSlogConnection, BIND_AUTO_CREATE);
    }

    public void unbindSlogUIService() {
        unbindService(mSlogConnection);
    }

    public ISlogService getSlogService() {
        return mService;
    }

    void onSlogServiceConnected() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                try {
                    mService.setNotification(NOTIFICATION_SLOG,
                            SlogInfo.self().mySharedPreferences.getBoolean(SERVICE_SLOG_KEY, false));
                    Log.d("Slog",
                            "slog preference:"
                                    + (SlogInfo.self().mySharedPreferences.getBoolean(
                                            SERVICE_SLOG_KEY, false) ? "1" : "0"));
                    mService.setNotification(NOTIFICATION_SNAP,
                            SlogInfo.self().mySharedPreferences.getBoolean(SERVICE_SNAP_KEY, false));
                    Log.d("Slog",
                            "slog preference:"
                                    + (SlogInfo.self().mySharedPreferences.getBoolean(
                                            SERVICE_SNAP_KEY, false) ? "1" : "0"));
                } catch (Exception e) {
                    Log.d("Slog", e.toString());
                }
            }
        });
    }

    protected void initPopuptWindow() {

        View popupWindow_view = getLayoutInflater().inflate(R.layout.popwindow_slog, null,
                false);
        popupWindow = new PopupWindow(popupWindow_view, LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, true);
        Button btnModemAssert = (Button) popupWindow_view.findViewById(R.id.btn_modem_assert);
        btnDumpWcn = (Button) popupWindow_view.findViewById(R.id.btn_dump_wcn);
        // if wcn hardware product is not marlin, the function is not support
        if (!"marlin".equals(SystemProperties.get("ro.wcn.hardware.product"))) {
            btnDumpWcn.setVisibility(View.GONE);
        }
        Button btnSaveSleepLog = (Button) popupWindow_view.findViewById(R.id.btn_save_sleeplog);
        Button btnSaveLogbuf = (Button) popupWindow_view.findViewById(R.id.btn_save_logbuf);
        Button btnShowNotif = (Button) popupWindow_view.findViewById(R.id.btn_general_1);
        Button btnModemToPC = (Button) popupWindow_view.findViewById(R.id.btn_modem_to_pc);
        Button btnGnssLog = (Button) popupWindow_view.findViewById(R.id.btn_dump_gps);
        btnGnssLog.setBackgroundColor(
                SlogCore.getGnssStatus() ? Color.GREEN : Color.GRAY);
        btnModemToPC.setBackgroundColor(
                SlogCore.getModemLogType() ? Color.GREEN : Color.GRAY);

        btnShowNotif
                .setBackgroundColor(
                SlogInfo.self().mySharedPreferences.getBoolean(SERVICE_SLOG_KEY, false) ? Color.GREEN
                        : Color.GRAY);

        Button btnSnapDevice = (Button) popupWindow_view.findViewById(R.id.btn_general_2);
        btnSnapDevice
                .setBackgroundColor(
                SlogInfo.self().mySharedPreferences.getBoolean(SERVICE_SNAP_KEY, false) ? Color.GREEN
                        : Color.GRAY);
        Button btnDumpArt = (Button) popupWindow_view.findViewById(R.id.btn_dump_art);
        btnDumpArt.setBackgroundColor(
                SlogInfo.self().mySharedPreferences.getBoolean("art_debug", false) ? Color.GREEN
                        : Color.GRAY);
        View.OnClickListener l = new OnClickListener() {
            @Override
            public void onClick(View v) {
                SlogCore s = new SlogCore();
                switch (v.getId()) {
                    case R.id.btn_modem_assert:
                        s.sendModemAssert();
                        break;
                    case R.id.btn_dump_wcn:
                        v.setClickable(false);
                        mHandler.obtainMessage(17).sendToTarget();
                        //SlogCore.dumpwcnMem();
                        //v.setClickable(true);
                        break;
                    case R.id.btn_dump_art:
                        SystemProperties.set(
                                "persist.sys.art.log.sprd_debug",
                                (!SlogInfo.self().mySharedPreferences
                                        .getBoolean("art_debug", false)) ? "1" : "0");
                        SlogInfo.self().mySharedPreferences
                                .edit()
                                .putBoolean(
                                        "art_debug",
                                        !SlogInfo.self().mySharedPreferences.getBoolean(
                                                "art_debug", false))
                                .commit();
                        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                        pm.reboot(null);
                        v.setBackgroundColor(
                                SlogInfo.self().mySharedPreferences.getBoolean("art_debug", false) ? Color.GREEN
                                        : Color.GRAY);
                        break;
                    case R.id.btn_dump_gps:
                        if (SlogCore.getGnssStatus()) {
                            SlogCore.setGnssClose();
                        } else {
                            SlogCore.setGnssOpen();
                        }
                        v.setBackgroundColor(
                                SlogCore.getGnssStatus() ? Color.GREEN : Color.GRAY);
                        break;
                    case R.id.btn_save_sleeplog:
                        mHandler.obtainMessage(5).sendToTarget();
                        break;
                    case R.id.btn_save_logbuf:
                        mHandler.obtainMessage(6).sendToTarget();
                        break;
                    case R.id.btn_general_1:
                        // SlogCore.SlogCore_AlwaysShowSlogInNotification("1");
                        try {

                            mService.setNotification(NOTIFICATION_SLOG,
                                    !SlogInfo.self().mySharedPreferences.getBoolean(
                                            SERVICE_SLOG_KEY, false));
                            SlogInfo.self().mySharedPreferences
                                    .edit()
                                    .putBoolean(
                                            SERVICE_SLOG_KEY,
                                            !SlogInfo.self().mySharedPreferences.getBoolean(
                                                    SERVICE_SLOG_KEY, false))
                                    .commit();
                        } catch (RemoteException e) {
                            // e.printStackTrace();
                        }
                        v.setBackgroundColor(
                                SlogInfo.self().mySharedPreferences.getBoolean(SERVICE_SLOG_KEY,
                                        false) ? Color.GREEN : Color.GRAY);
                        break;
                    case R.id.btn_general_2:
                        try {
                            mService.setNotification(NOTIFICATION_SNAP,
                                    !SlogInfo.self().mySharedPreferences.getBoolean(
                                            SERVICE_SNAP_KEY, false));

                            SlogInfo.self().mySharedPreferences
                                    .edit()
                                    .putBoolean(
                                            SERVICE_SNAP_KEY,
                                            !SlogInfo.self().mySharedPreferences.getBoolean(
                                                    SERVICE_SNAP_KEY, false))
                                    .commit();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        v.setBackgroundColor(
                                SlogInfo.self().mySharedPreferences.getBoolean(SERVICE_SNAP_KEY,
                                        false) ? Color.GREEN : Color.GRAY);
                        break;
                    case R.id.btn_modem_to_pc:
                        if (SlogCore.getModemLogType()) {
                            SlogCore.setModemLogToPhone();
                        } else {
                            SlogCore.setModemLogToPC();
                        }
                        v.setBackgroundColor(
                                SlogCore.getModemLogType() ? Color.GREEN : Color.GRAY);
                        break;
                }
            }
        };
        btnModemAssert.setOnClickListener(l);
        btnDumpWcn.setOnClickListener(l);
        btnSaveSleepLog.setOnClickListener(l);
        btnSaveLogbuf.setOnClickListener(l);
        btnShowNotif.setOnClickListener(l);
        btnSnapDevice.setOnClickListener(l);
        btnDumpArt.setOnClickListener(l);
        btnModemToPC.setOnClickListener(l);
        btnGnssLog.setOnClickListener(l);

        popupWindow_view.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // TODO Auto-generated method stub
                if (popupWindow != null && popupWindow.isShowing()) {
                    popupWindow.dismiss();
                    popupWindow = null;
                }
                return false;
            }

        });

        popupWindow_view.setFocusableInTouchMode(true);
        popupWindow_view.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event)
            {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (popupWindow != null && popupWindow.isShowing()) {
                        popupWindow.dismiss();
                        popupWindow = null;
                    }
                }
                return true;
            }

        });

    }

    private void getPopupWindow() {
        if (null != popupWindow) {
            popupWindow.dismiss();
            return;
        } else {
            initPopuptWindow();
        }
    }

}
