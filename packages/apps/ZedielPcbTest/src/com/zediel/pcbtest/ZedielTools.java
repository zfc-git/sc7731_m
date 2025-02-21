package com.zediel.pcbtest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.text.format.Formatter;
import com.zediel.pcbtest.PlayThread;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.os.AsyncTask;
import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.zediel.itemstest.MemeryItme;
import com.zediel.itemstest.NandItem;
import com.zediel.itemstest.RTCTest;
import com.zediel.itemstest.SIMItem;
import com.zediel.pcbtest.CameraActivity;
import com.zediel.pcbtest.VideoPlayActivity;
import com.zediel.pcbtest.CheckTestResultActivity;
import com.zediel.itemstest.VersionTest;
import com.zediel.receiver.ZedielUsbReceiver;
import com.zediel.util.BtTestUtil;
import com.zediel.util.LedLightUtil;
import com.zediel.util.WifiTestUtil;
import com.zediel.util.Rj45TestUtil;
import com.zediel.util.SerialNoUtil;
import com.zediel.util.ThreadUtils;
import com.zediel.pcbtest.ScreenColor;

import java.util.Iterator;
import java.util.List;
import java.text.SimpleDateFormat;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import android.os.AsyncTask;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraAccessException;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.content.res.AssetFileDescriptor;

import android.widget.SeekBar;
import android.content.ContentResolver;
import android.net.Uri;
import android.serialport.api.SerialPort;
import android.serialport.api.SerialPortFinder;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.InvalidParameterException;
import java.util.Arrays;

import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.os.Environment;

import com.zediel.itemstest.DistanceTest;
import com.zediel.itemstest.LcdTest;
import com.zediel.itemstest.TpTest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.os.storage.StorageManager;
import android.content.ComponentName;

public class ZedielTools extends Activity implements OnClickListener, OnTouchListener,  DistanceTest.CallBack {
    private static String TAG = "ZedielToolsMA";
    //camera
    private Camera mCamera;
    private Preview mPreview;
    private int numCamera;
    private int cameraId;
    private int cameraCurrentId;

    private int mcount = 0;
    private boolean cameraIsOk = true;
    private boolean isFirstResume = true;
    private boolean isCanBofang = true;
    //checkflags
    private boolean mSearchFlag = false;
    private boolean mFindFlag = false;
    private boolean isLedOpen = false;
    private Runnable mRunnable;
    private Runnable mRunnablemin;
    public MyHandler mhandler;

    private boolean isRunmax = true;
    //zhendong
    private Vibrator vibrator;
    //show touch
    private List<Point> allPoints = new ArrayList<Point>();
    //recorder
    private MediaRecorder mediaRecorder;
    private File soundFile;
    private int timeRunCounts = 1;

    private boolean timeIsRunning = false;
    //usb
    private ZedielUsbReceiver usbstates;

    private RTCTest rtctest;
    private MemeryItme memeryitem;

    //gsensor
    private float x = 0, y = 0, z = 0;
    private SensorManager sensorMgr;

    //wifi
    private WifiTestUtil wifiTestUtil;
    //bt
    private BtTestUtil btTestUtil = null;
    //rj45
    private Rj45TestUtil rj45TestUtil = null;

    //sim
    private SIMItem simItem;

    //gps
    private LocationListener locationListener = null;
    /**
     * gps status listener object
     */
    private GpsStatus.Listener gpsStatusListener = null;
    /**
     * location manager object
     */
    private LocationManager manager = null;
    /**
     * GPS provider name
     */
    private static final String PROVIDER = LocationManager.GPS_PROVIDER;
    /**
     * satellite min count for OK
     */
    private static final int SATELLITE_COUNT_MIN = 4;
    /**
     * max satellite count that have been searched
     */
    private int mSatelliteCount;
    /**
     * location update min time
     */
    private static final long UPDATE_MIN_TIME = 1000;

    private boolean isFirstTime = true;
    private View main_layout;
    //text
    private TextView tv_recorder,tv_brightness;
    private ProgressBar progressBar;
    private Switch sw_recorder, sw_key, sw_erji, sw_brightness, sw_leftmusic, sw_rightmusic, sw_led, sw_call, sw_led_light,
            sw_motor,sw_cam;

    private TextView tv_key, tv_appversion;
    private TextView tv_order, tv_pcba;
    /*
    private TextView tv_udisk;
    private TextView tv_sdcard;
    */
    private TextView tv_charge;
    private TextView tv_erji, tv_led_light;
    //util.getKernelVersion()
    private TextView tv_sys_version;
    private TextView tv_rtc;

    private TextView tv_Firmware, tv_mem, tv_nand, tv_nand_tip, tv_sdcard, tv_usbdisk, tv_gsensor, tv_wifi, tv_bt, tv_sim,
            tv_gps, tv_rjethernet, tv_cpu, tv_sn, tv_mac, tv_bt_mac, tv_distance_result;
    private SeekBar brightness_sb, motor_sb;
    /*private TextView*/

    private WifiManager mWifiManager;

    private MediaPlayer codecPlayer;

    //phone

    private Button bt_phone;
    private Button btn_leftmusic, btn_rightmusic;
    private Button btn_lcd;
    private Button btn_led;
    private Button btn_tp, tv_cam, btn_recorder;
  

    private boolean mIsRecording = false;
    private final int SERIAL_MSG = 0x00040;
    private final int ETHNET_MSG = 0x00041;
    private final int RTC_MSG = 0x00042;
    private final int BL_MSG = 0x00043;
    private final int WIFI_MSG = 0x00044;
    private final int WIFI_RESULT_MSG = 0x00045;
    private final int RECORDER_START_MSG = 0x00045;
    private final int RECORDER_PLAYER_MSG = 0x00046;
    private final int RECORDER_PROBAR_MSG = 0x00047;
    private final int START_RECORDER_MSG = 0x00048;

    // 控制节点地址
    //   private final String motor_patch = "proc/zed_motor/zed_motor_ctl";
    private final String motor_patch = "proc/zed_pwm/zed_pwm_ctl";
    private final String led_Path = "/proc/zed_led/zed_led_ctl";
    private final String pintpower_Path="/proc/zed_printer/zed_printer_ctl";

	public static String PCBName = "";

    //SerialPortUtil
    private Spinner mDeviceSpinner;
    private String[] mDevices;
    private List<BluetoothDevice> mBluetoothDeviceList = new ArrayList<BluetoothDevice>();
    protected SerialPort mSerialPort;
    protected OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;
    private SendingThread mSendingThread;
    private byte[] mBuffer;
    private TextView mSerialstateTv;
    private String devicePath;
    private Button serialportstateBtn;

    private LinearLayout ll_bl, ll_serial, ll_screen, ll_led, ll_distance;
    private RelativeLayout rl_erji, rl_key, rl_recoder, rl_call, rl_motor ,rl_cam, rl_led_light;
    //activity result
    private final int SCREENCOLOR = 0x00001;

    private MediaPlayer mPlayer, mp2,mp3 ,mp4;
    private Button mSaveExit;
    private Button mGoLeft;
    private Button mGoRight;
    private AudioManager mAudioManager;
    private BroadcastReceiver mChargeBroadcastReceiver;
    private BroadcastReceiver mErjiBroadcastReceiver;
    private WorkHandler mWorkHandler;
    private int mBrightnrssTimes = 0;
    private int mBrightnrssLevel = 30;
    private int mSaveInternal = 1200;
    private long mLastSaved;
    private int mWifiCount = 10;
    private int mWifiLevelCount;
    private int[] mWifiLevel = new int[mWifiCount];

    private class ReadThread extends Thread {

        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {
                int size;
                try {
                    byte[] buffer = new byte[64];
                    if (mInputStream == null) return;
                    size = mInputStream.read(buffer);
                    Log.i(TAG, "ReadThread=" + size + new String(buffer));
                    if (size > 0) {
                        onDataReceived(buffer, size);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (mInputStream != null) {
                        try {
                            mInputStream.close();
                        } catch (IOException ei) {
                            ei.printStackTrace();
                        }
                    }
                    return;
                }
            }
            if (mInputStream != null) {
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class SendingThread extends Thread {
        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    mBuffer = new byte[1024];
                    Arrays.fill(mBuffer, (byte) 0x55);
                    if (mOutputStream != null) {
                        mOutputStream.write(mBuffer);
                    } else {
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (mOutputStream != null) {
                        try {
                            mOutputStream.close();
                        } catch (IOException ei) {
                            ei.printStackTrace();
                        }
                    }
                    return;
                }
            }
            if (mOutputStream != null) {
                try {
                    mOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void onDataReceived(final byte[] buffer, final int size) {
        Message message = getWorkHandler().obtainMessage();
        message.what = SERIAL_MSG;
        message.obj = new String(buffer);
        getWorkHandler().sendMessage(message);
        Log.i(TAG, "onDataReceived=" + size + new String(buffer));
    }

    @Override
    public void onSuccess() {
        mDistanceResult = "Pass";
        tv_distance_result.setTextColor(getResources().getColor(R.color.green));
        tv_distance_result.setText(getResources().getString(R.string.str_distance, mDistanceResult));
        saveResult();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main_tools);
    //    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        boolean isRead = readState(getsdcardpatch(this) + "/zedpcbtest.txt");
        //    boolean isRead = readState("sdcard/zedpcbtest.txt");
        Log.d(TAG, "onCreate---readState----isRead=" + isRead + "  sdcardpatch=" + getsdcardpatch(this));

        if (getIntent() != null && getIntent().hasExtra("force")) {
            TestMode = 1;
        }
        if (TestMode == 2) {  //检查上次测试结果
            checkTestResult();
        } else if (TestMode == 3) { //老化测试模式  TestMode==3
            startAgingTest();
        }

        if (TestMode == 1) {
            mSaveExit = (Button) findViewById(R.id.save_exit);
            mGoLeft = (Button) findViewById(R.id.goto_left);
            mGoRight = (Button) findViewById(R.id.goto_right);
            mSaveExit.setOnClickListener(this);
            mGoLeft.setOnClickListener(this);
            mGoRight.setOnClickListener(this);
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            }
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);

            //mhandler = new MyHandler();

            //usb
            //usbstates = new ZedielUsbReceiver(this);
            //usbstates.registerReceiver();

            if (isFirstTime) {
                //zhendong
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(new long[]{100, 100, 100, 1000}, 0); //chixu
                    vibrator.vibrate(5000);
                }
                isFirstTime = false;
            }
            initview();
            if (MICShow) {
                getWorkHandler().sendEmptyMessageDelayed(START_RECORDER_MSG, 2500);
            }
            saveResult();
        }
    }

    public void startCameraTest() {
        Intent it = new Intent(ZedielTools.this, CameraActivity.class);
        startActivityForResult(it, 4);
    }

    public void startAgingTest() {
        onBackPressed();
        if (TestMode == 1) {
            writeresultState(formatStateContent());
        }
        Intent it = new Intent(ZedielTools.this, VideoPlayActivity.class);
        startActivity(it);

    }

    public void checkTestResult() {
        onBackPressed();
        if (TestMode == 1) {
            writeresultState(formatStateContent());
        }
        Intent it = new Intent(ZedielTools.this, CheckTestResultActivity.class);
        startActivity(it);
    }

    private void playRecorder() {
        if (soundFile.exists() && soundFile.length() != 0) {
            if (null != mediaRecorder && mIsRecording == true) {
                mediaRecorder.stop();
                mediaRecorder.reset();
                // 释放资源
                mediaRecorder.release();
                mediaRecorder = null;
            }
            if (null != mPlayer) {
                if (mPlayer.isPlaying()) {
                    mPlayer.stop();
                }
                mPlayer.release();
                mPlayer = null;
            }
            mPlayer = new MediaPlayer();
            try {
                mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        btn_recorder.setEnabled(true);
                    }
                });
                //设置要播放的文件
                mPlayer.setDataSource(soundFile.getAbsolutePath());
                mPlayer.prepare();
                //播放之
                mPlayer.start();
            } catch (IOException e) {
                btn_recorder.setEnabled(true);
                Log.e(TAG, "prepare() failed");
            }
        } else {
            btn_recorder.setEnabled(true);
            Log.i(TAG, "<<<<< wenjian play faild >>>>>>");
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // TODO Auto-generated method stub
        super.onConfigurationChanged(newConfig);
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.v(TAG, "ORIENTATION_LANDSCAPE");
        }
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.v(TAG, "ORIENTATION_PORTRAIT");
        }
        if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
        } else if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
        }

    }

    private void initview() {
        tv_appversion = (TextView) this.findViewById(R.id.tv_appversion);
        tv_appversion.setTextSize(30);
        tv_appversion.setText("App版本号：V" + getVersionCode(this));

        tv_pcba = (TextView) this.findViewById(R.id.tv_pcba);
        tv_pcba.setText("PCBA:" + " " + PCBA);

        tv_order = (TextView) this.findViewById(R.id.tv_order);
        tv_order.setText("ORDER:" + " " + ORDER);

        tv_cpu = (TextView) this.findViewById(R.id.tv_cpu);
        tv_cpu.setText("CPU:" + " " + CPU +  ", 实际为" + SystemProperties.get("ro.board.platform"));

        tv_Firmware = (TextView) this.findViewById(R.id.tv_Firmware);
        tv_Firmware.setText("固件版本:" + " " + Firmware);
        Log.d(TAG, "--android.os.Build=" + android.os.Build.DISPLAY);
        if (Firmware != null && Firmware.equals(android.os.Build.DISPLAY)) {
            Firmware = "[通过] " + Firmware;
            tv_Firmware.setTextColor(Color.GREEN);
        } else {
            Firmware = Firmware + "   测试失败";
            tv_Firmware.setTextColor(Color.RED);
        }

        tv_sn = (TextView) this.findViewById(R.id.tv_sn);
        if (mSNPrefixShow) {
            tv_sn.setVisibility(View.VISIBLE);
            mSNPrefixResult = SerialNoUtil.getSerial();
            if (!TextUtils.isEmpty(mSNPrefixResult) && mSNPrefixResult.startsWith(mSNPrefix)) {
                tv_sn.setTextColor(Color.GREEN);
                mSNPrefixResult += "\tSN匹配前缀:" + mSNPrefix + "\tPass";
            } else {
                tv_sn.setTextColor(Color.RED);
                mSNPrefixResult += "\tSN匹配前缀:" + mSNPrefix + "\tFail";
            }
            tv_sn.setText("SN序列号: " + mSNPrefixResult);
        } else {
            tv_sn.setVisibility(View.GONE);
        }

        mWifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        tv_mac = (TextView) this.findViewById(R.id.tv_mac);
        //tv_mac.setText("WIFI-MAC: " + getWifiMac());
        tv_bt_mac = (TextView) this.findViewById(R.id.tv_bt_mac);

        tv_sys_version = (TextView) this.findViewById(R.id.tv_sys_version);
        KernelVersion = "版本：" +
                "[android" + VersionTest.getAndroidRelease() + "]  " + "[kernel" + VersionTest.getKernelVersion() + "]";
        tv_sys_version.setText(KernelVersion);

        mDistanceResult = "Fail";
        ll_distance = (LinearLayout) this.findViewById(R.id.distance_sensor);
        tv_distance_result = (TextView) this.findViewById(R.id.distance_sensor_result);
        tv_distance_result.setTextColor(getResources().getColor(R.color.red));
        tv_distance_result.setText(getResources().getString(R.string.str_distance, mDistanceResult));
        if (mDistanceShow) {
            DistanceTest.getInstance().registerListener(this);
            DistanceTest.getInstance().setCallBack(this);
            ll_distance.setVisibility(View.VISIBLE);
        } else {
            ll_distance.setVisibility(View.GONE);
        }

        rl_recoder = (RelativeLayout) this.findViewById(R.id.rl_recoder);
        tv_recorder = (TextView) this.findViewById(R.id.tv_recorder);
        progressBar = (ProgressBar) this.findViewById(R.id.progressBar);
        sw_recorder = (Switch) this.findViewById(R.id.sw_recorder);
        btn_recorder = (Button) this.findViewById(R.id.btn_recorder);
        if (MICShow) {
            btn_recorder.setVisibility(View.VISIBLE);
            tv_recorder.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            sw_recorder.setVisibility(View.VISIBLE);
            rl_recoder.setVisibility(View.VISIBLE);
            btn_recorder.setOnClickListener(this);
            sw_recorder.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setSwitchTextAppearance(sw_recorder, isChecked);
                    if (isChecked) {
                        SoundRecoder = "Pass";
                    } else {
                        SoundRecoder = "Fail";
                    }
                }
            });
        } else {
            btn_recorder.setVisibility(View.GONE);
            tv_recorder.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            sw_recorder.setVisibility(View.GONE);
            rl_recoder.setVisibility(View.GONE);
        }

        tv_cam = (Button) this.findViewById(R.id.tv_cam);
        sw_cam = (Switch) this.findViewById(R.id.sw_cam);
        rl_cam = (RelativeLayout) this.findViewById(R.id.rl_cam);
        if (CAMShow) {
            rl_cam.setVisibility(View.VISIBLE);
            /*try {
                if (checkCameraHardware(this)) {
                    mCamera = Camera.open();
                    cameraIsOk = true;
                    Log.i(TAG, "&&&&&&&& camerIsOk true &&&&&&&&");
                } else {
                    cameraIsOk = false;
                    Log.i(TAG, "&&&&&&&& camerIsOk false &&&&&&&&");
                }
                if (cameraIsOk) {
                    mPreview = new Preview(this);
                    FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
                    preview.addView(mPreview);
                    preview.setOnClickListener(this);
                    numCamera = Camera.getNumberOfCameras();
                    CameraInfo info = new CameraInfo();
                    for (int i = 0; i < numCamera; i++) {
                        Camera.getCameraInfo(i, info);
                        if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                            cameraId = i;
                        }
                    }
                }
            } catch (Exception e) {
                // TODO: handle exception
                Toast.makeText(this, "camera 打开异常！", Toast.LENGTH_LONG).show();
            }*/

            tv_cam.setTextColor(Color.BLUE);
            tv_cam.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (ZedielTools.this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                        startCameraTest();
                    }
                    /*if (isInstalled(ZedielTools.this, "com.android.camera2")) {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.setClassName("com.android.camera2", "com.android.camera.CameraLauncher");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }*/
                }
            });
            sw_cam.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setSwitchTextAppearance(sw_cam, isChecked);
                    if (isChecked) {
                        camResult = "pass";
                        tv_cam.setTextColor(Color.GREEN);
                    } else {
                        camResult = "fail";
                        tv_cam.setTextColor(Color.RED);
                    }
                }
            });
        } else {
            tv_cam.setVisibility(View.GONE);
            sw_cam.setVisibility(View.GONE);
            rl_cam.setVisibility(View.GONE);
        }
        //gsensor
        tv_gsensor = (TextView) this.findViewById(R.id.tv_gsensor);
        if (!GsensorShow) {
            tv_gsensor.setVisibility(View.GONE);
        } else {
            mGsensorResult.clear();
            if (mGsensorTestKey.size() == 0) {
                mGsensorTestKey.add(ZP_TEST);
                mGsensorTestKey.add(ZN_TEST);
                mGsensorTestKey.add(XP_TEST);
                mGsensorTestKey.add(XN_TEST);
                mGsensorTestKey.add(YP_TEST);
                mGsensorTestKey.add(YN_TEST);
            }
            sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            SensorEventListener lsn = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent arg0) {
                    // TODO Auto-generated method stub
                    x = arg0.values[SensorManager.DATA_X];
                    y = arg0.values[SensorManager.DATA_Y];
                    z = arg0.values[SensorManager.DATA_Z];
        //            Log.d(TAG, "-SensorEventListener---x=" + x + "  y=" + y);
                    long oldtime = System.currentTimeMillis();
                    //gesensor
                    mGsensorCount = mGsensorResult.size();
                    if (x > MAX_VALUE && x < MAX_VALUE1 && Math.abs(y) < MIN_VALUE && Math.abs(z) < MIN_VALUE) {
                        mGsensorResult.add(XP_TEST);
                    } else if (x < -MAX_VALUE && x > -MAX_VALUE1 && Math.abs(y) < MIN_VALUE && Math.abs(z) < MIN_VALUE) {
                        mGsensorResult.add(XN_TEST);
                    } else if (y > MAX_VALUE && y < MAX_VALUE1 && Math.abs(x) < MIN_VALUE && Math.abs(z) < MIN_VALUE) {
                        mGsensorResult.add(YP_TEST);
                    } else if (y < -MAX_VALUE && y > -MAX_VALUE1 && Math.abs(x) < MIN_VALUE && Math.abs(z) < MIN_VALUE) {
                        mGsensorResult.add(YN_TEST);
                    } else if (z > MAX_VALUE && z < MAX_VALUE1 && Math.abs(y) < MIN_VALUE && Math.abs(x) < MIN_VALUE) {
                        mGsensorResult.add(ZP_TEST);
                    } else if (z < -MAX_VALUE && z > -MAX_VALUE1 && Math.abs(y) < MIN_VALUE && Math.abs(x) < MIN_VALUE) {
                        mGsensorResult.add(ZN_TEST);
                    }
                    /*if (Math.abs(z) > 9.5f && Math.abs(z) < 10.5f && Math.abs(y) < 0.5f && Math.abs(x) < 0.5f) {
                        mGsensorResult.add(ZP_TEST);
                    }*/
                    if (mGsensorResult.containsAll(mGsensorTestKey)) {
                        GsensorResult = "pass";
                        tv_gsensor.setTextColor(Color.GREEN);
                    } else {
                        GsensorResult = "fail";
                        tv_gsensor.setTextColor(Color.RED);
                    }
                    tv_gsensor.setText(getResources().getString(R.string.str_gsensor) + "\t" + mGsensorResult.toString() + "\t" + "[" + x + ",\t" + y + ",\t" + z + "]");
                    if (mGsensorResult.size() != mGsensorCount) {
                        saveResult();
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor arg0, int arg1) {
                    // TODO Auto-generated method stub
                }
            };

            sensorMgr.registerListener(lsn, sensor, SensorManager.SENSOR_DELAY_GAME);
            if (GsensorResult == null) {
                GsensorResult = "fail";
                tv_gsensor.setText(getResources().getString(R.string.str_gsensor) + "[未通过] ");
                tv_gsensor.setTextColor(Color.RED);
            }
        }

        rl_key = (RelativeLayout) this.findViewById(R.id.rl_key);
        tv_key = (TextView) this.findViewById(R.id.tv_key);
        sw_key = (Switch) this.findViewById(R.id.sw_key);
        if (KEYShow) {
            tv_key.setVisibility(View.VISIBLE);
            rl_key.setVisibility(View.VISIBLE);
            /*sw_key.setVisibility(View.VISIBLE);
            sw_key.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setSwitchTextAppearance(sw_key, isChecked);
                    if (isChecked) {
                        KeyResponse = "Pass";
                        tv_key.setTextColor(Color.GREEN);
                    } else {
                        KeyResponse = "Fail";
                        tv_key.setTextColor(Color.RED);
                    }
                }
            });*/
        } else {
            tv_key.setVisibility(View.GONE);
            sw_key.setVisibility(View.GONE);
            rl_key.setVisibility(View.GONE);
        }

        tv_charge = (TextView) this.findViewById(R.id.tv_charge);
        if (CHARGEShow) {
            tv_charge.setVisibility(View.VISIBLE);
            ChargeRespone = "Fail";   //默认失败
            mChargeBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                        int status = intent.getIntExtra("status", 0);
                        int plugged = intent.getIntExtra("plugged", 0);
                        int voltage = intent.getIntExtra("voltage", 0);

                        String acString = "";
                        switch (plugged) {
                            case BatteryManager.BATTERY_PLUGGED_AC:
                                acString = "ac充电器插入";
                                ChargeRespone = "Pass";
                                tv_charge.setTextColor(Color.GREEN);
                                break;
                            case BatteryManager.BATTERY_PLUGGED_USB:
                                acString = "usb充电器插入";
                                ChargeRespone = "Pass";
                                tv_charge.setTextColor(Color.GREEN);
                                break;
                        }
                        tv_charge.setText(getResources().getString(R.string.str_charger) + acString + " [" + voltage + "mv]");
                        saveResult();
                    }
                }
            };
            IntentFilter filterCharge = new IntentFilter();
            filterCharge.addAction(Intent.ACTION_BATTERY_CHANGED);
            registerReceiver(mChargeBroadcastReceiver, filterCharge);
        } else {
            tv_charge.setVisibility(View.GONE);
        }

        rl_led_light = (RelativeLayout) this.findViewById(R.id.rl_led_light);
        tv_led_light = (TextView) this.findViewById(R.id.tv_led_light);
        sw_led_light = (Switch) this.findViewById(R.id.sw_led_light);
        if (mLedLightShow) {
            rl_led_light.setVisibility(View.VISIBLE);
            tv_led_light.setVisibility(View.VISIBLE);
            sw_led_light.setVisibility(View.VISIBLE);
            sw_led_light.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setSwitchTextAppearance(sw_led_light, isChecked);
                    if (isChecked) {
                        mLedLightResponse = "Pass";
                        tv_led_light.setTextColor(Color.GREEN);
                    } else {
                        mLedLightResponse = "Fail";
                        tv_led_light.setTextColor(Color.RED);
                    }
                }
            });
        } else {
            rl_led_light.setVisibility(View.GONE);
            tv_led_light.setVisibility(View.GONE);
            sw_led_light.setVisibility(View.GONE);
        }

        rl_erji = (RelativeLayout) this.findViewById(R.id.rl_erji);
        tv_erji = (TextView) this.findViewById(R.id.tv_erji);
        sw_erji = (Switch) this.findViewById(R.id.sw_erji);
        if (ERJIShow) {
            tv_erji.setVisibility(View.VISIBLE);
            sw_erji.setVisibility(View.VISIBLE);
            rl_erji.setVisibility(View.VISIBLE);
            sw_erji.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setSwitchTextAppearance(sw_erji, isChecked);
                    if (isChecked) {
                        ErjiResponse = "Pass";
                        tv_erji.setTextColor(Color.GREEN);
                    } else {
                        ErjiResponse = "Fail";
                        tv_erji.setTextColor(Color.RED);
                    }
                }
            });
            mErjiBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals("android.intent.action.HEADSET_PLUG")) {
                        if (intent.hasExtra("state")) {
                            if (intent.getIntExtra("state", 0) == 0) {
                                tv_erji.setText("耳机：未插入耳机");
                            } else if (intent.getIntExtra("state", 0) == 1) {
                                tv_erji.setText("耳机：已插入耳机");
                                Log.i(TAG, "<<<<<< start fm >>>>>>");

                                if (null != mediaRecorder && mIsRecording == true) {
                                    mediaRecorder.stop();
                                    mediaRecorder.reset();
                                    // 释放资源
                                    mediaRecorder.release();
                                    mediaRecorder = null;
                                }
                                if (isInstalled(context, "com.android.fmradio")) {
                                    Intent intentFm = new Intent(Intent.ACTION_MAIN);
                                    intentFm.setClassName("com.android.fmradio", "com.android.fmradio.FmMainActivity");
                                    intentFm.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intentFm);
                                }
                            }
                        }
                    }
                }
            };
            IntentFilter filterHeadset = new IntentFilter();
            filterHeadset.addAction("android.intent.action.HEADSET_PLUG");
            registerReceiver(mErjiBroadcastReceiver, filterHeadset);
        } else {
            tv_erji.setVisibility(View.GONE);
            sw_erji.setVisibility(View.GONE);
            rl_erji.setVisibility(View.GONE);
        }

        //rj45
        tv_rjethernet = (TextView) this.findViewById(R.id.tv_rjethernet);
        if (EthernetShow) {
            tv_rjethernet.setVisibility(View.VISIBLE);
            rj45TestUtil = new Rj45TestUtil();
            rj45TestUtil.startTest(this);
            getWorkHandler().sendEmptyMessageDelayed(ETHNET_MSG, 500);
        } else {
            tv_rjethernet.setVisibility(View.GONE);
        }

        tv_rtc = (TextView) this.findViewById(R.id.tv_rtc);
        if (RTCShow) {
            tv_rtc.setVisibility(View.VISIBLE);
            rtctest = new RTCTest();
            getWorkHandler().sendEmptyMessageDelayed(RTC_MSG, 500);
        } else {
            tv_rtc.setVisibility(View.GONE);
        }

        //mem
        tv_mem = (TextView) this.findViewById(R.id.tv_mem);
        String ddrstr = "DDR内存:";
        if (DDRShow) {
            tv_mem.setVisibility(View.VISIBLE);
            memeryitem = new MemeryItme(this);
            int ram = memeryitem.getmem_TOLAL();

            int chazhi = DDRint - ram ;
            if (0 < chazhi && chazhi< 200) {
                ddrstr = "DDR内存:[通过] " + memeryitem.getHasUsedMem() + "/" + ram;
                DDRResult = "Pass";
                tv_mem.setTextColor(Color.GREEN);
            } else {
                ddrstr = "DDR内存:[失败] " + memeryitem.getHasUsedMem() + "/" + ram;
                DDRResult = "Fail";
                tv_mem.setTextColor(Color.RED);
            }
            tv_mem.setText(ddrstr);
        } else {
            tv_mem.setVisibility(View.GONE);
        }

        tv_sdcard = (TextView) this.findViewById(R.id.tv_sdcard);
        tv_sdcard.setVisibility(View.GONE);

        //nand
        tv_nand = (TextView) this.findViewById(R.id.tv_nand);
        tv_nand_tip = (TextView) this.findViewById(R.id.tv_nand_tip);
        if (FLASHShow) {
            tv_nand.setVisibility(View.VISIBLE);
            tv_nand_tip.setVisibility(View.VISIBLE);
            long[] size = SerialNoUtil.getPrivateStorageSize(this);
            if (FLASH != null) {
                FLASH = FLASH.replaceAll(" " , "");
            }
            String flashValue = MemeryItme.getDecimalNumber(FLASH);
            if (flashValue != null) {
                DecimalFormat df = new DecimalFormat("##0.00");
                BigDecimal avil = new BigDecimal(df.format((double) size[1] / SerialNoUtil.ONE_GB).replace(",", "."));
                BigDecimal avilSet = new BigDecimal(flashValue);
                BigDecimal total = MemeryItme.getReasonableSize(avilSet);
                BigDecimal avilMin = new BigDecimal(df.format(avilSet.floatValue() - total.floatValue() * 0.02).replace(",", "."));
                Log.d(TAG, "avil=" + avil.toString() + " avilMin=" + avilMin.toString() + "  avilSet=" + avilSet.toString() + "  total=" + total.toString());
                if (total.compareTo(avil) > 0 && avil.compareTo(avilMin) > 0) {
                    FlashResult = "Pass";
                    tv_nand.setTextColor(Color.GREEN);
                } else {
                    FlashResult = "Fail";
                    tv_nand.setTextColor(Color.RED);
                }
            } else {
                FlashResult = "Fail";
                tv_nand.setTextColor(Color.RED);
            }
            tv_nand.setText("机身存储: 总共(除系统占用) " + Formatter.formatFileSize(this, size[0]) + ", 可用 " + Formatter.formatFileSize(this, size[1]) + ", 配置 " + FLASH + ", " + FlashResult);
        } else {
            tv_nand_tip.setVisibility(View.GONE);
            tv_nand.setVisibility(View.GONE);
        }

        tv_usbdisk = (TextView) this.findViewById(R.id.tv_usbdisk);
        if (UDISKShow) {
            Exmemory = NandItem.getmemorysize(this);
            tv_usbdisk.setText("外部存储设备: " + Exmemory);
            tv_usbdisk.setTextColor(Color.GREEN);
        } else {
            tv_usbdisk.setVisibility(View.GONE);
        }

        //motor
        rl_motor = (RelativeLayout) this.findViewById(R.id.rl_motor);
        sw_motor = (Switch) findViewById(R.id.sw_motor);
        motor_sb = (SeekBar) findViewById(R.id.motorleft_sb);
        if (motorShow) {
            rl_motor.setVisibility(View.VISIBLE);
            motor_sb.setVisibility(View.VISIBLE);
            motor_sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (progress < 10) {
                        setNodeString(motor_patch, "0" + progress);
                    } else {
                        setNodeString(motor_patch, progress + "");
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
            sw_motor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setSwitchTextAppearance(sw_motor, isChecked);
                    if (isChecked) {
                        motorResult = "Pass";
                    } else {
                        motorResult = "Fail";
                    }
                }
            });
        } else {
            rl_motor.setVisibility(View.GONE);
            motor_sb.setVisibility(View.GONE);
        }

        //brightness start
        ll_bl = (LinearLayout) this.findViewById(R.id.ll_bl);
        tv_brightness = (TextView) this.findViewById(R.id.tv_brightness);
        sw_brightness = (Switch) findViewById(R.id.sw_brightness);
        brightness_sb = (SeekBar) findViewById(R.id.brightness_sb);
        if (BLShow) {
            brightness_sb.setVisibility(View.VISIBLE);
            ll_bl.setVisibility(View.VISIBLE);
            brightness_sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    // Log.i(TAG,"progress:"+progress);
                    ContentResolver resolver = ZedielTools.this.getContentResolver();
                    Uri uri = android.provider.Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS);
                    android.provider.Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, progress);
                    resolver.notifyChange(uri, null);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
            sw_brightness.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setSwitchTextAppearance(sw_brightness, isChecked);
                    if (isChecked) {
                        BLResult = "Pass";
                        tv_brightness.setTextColor(Color.GREEN);
                    } else {
                        BLResult = "Fail";
                        tv_brightness.setTextColor(Color.RED);
                    }
                }
            });
            //实现循环亮度调节
            getWorkHandler().sendEmptyMessageDelayed(BL_MSG, 1000);
        } else {
            brightness_sb.setVisibility(View.GONE);
            ll_bl.setVisibility(View.GONE);
        }

        //serailPort start
        ll_serial = (LinearLayout) this.findViewById(R.id.ll_serial);
        mDeviceSpinner = (Spinner) findViewById(R.id.device_spinner);
        mSerialstateTv = (TextView) findViewById(R.id.serialportstate_tv);
        serialportstateBtn = (Button) findViewById(R.id.serialportstate_btn);
        if (!UARTShow) {
            ll_serial.setVisibility(View.GONE);
            mSerialstateTv.setVisibility(View.GONE);
            mDeviceSpinner.setVisibility(View.GONE);
            serialportstateBtn.setVisibility(View.GONE);
        } else {
            if (null == UART || "".equals(UART) || '#' == UART.charAt(0)) {
                ll_serial.setVisibility(View.GONE);
                mSerialstateTv.setVisibility(View.GONE);
                mDeviceSpinner.setVisibility(View.GONE);
                serialportstateBtn.setVisibility(View.GONE);
            } else {
                ll_serial.setVisibility(View.VISIBLE);
                mSerialstateTv.setVisibility(View.VISIBLE);
                mDeviceSpinner.setVisibility(View.VISIBLE);
                serialportstateBtn.setVisibility(View.VISIBLE);
                //获取设备
                SerialPortFinder mSerialPortFinder = new SerialPortFinder();
                mDevices = mSerialPortFinder.getAllDevicesPath();
                Log.i(TAG, "mDevices length:" + mDevices.length);
                if (mDevices.length <= 0) {
                    mDevices = new String[]{"no device"};
                } else {
                    ArrayAdapter<String> deciveAdapter = new ArrayAdapter<String>(this,
                            android.R.layout.simple_spinner_item, mDevices);
                    mDeviceSpinner.setAdapter(deciveAdapter);
                }
                mDeviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        devicePath = mDevices[i];
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {

                    }
                });
                serialportstateBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            mSerialstateTv.setText("");
                            //默认波特率 9600
                            mSerialPort = new SerialPort(new File(devicePath), 9600, 0);
                            mOutputStream = mSerialPort.getOutputStream();
                            mInputStream = mSerialPort.getInputStream();
                            if (mSerialPort != null) {
                                Log.i(TAG, "send----mSerialPort=" + mSerialPort);
                                mSendingThread = new SendingThread();
                                mSendingThread.start();
                                mReadThread = new ReadThread();
                                mReadThread.start();
                            }
                        } catch (SecurityException e) {
                            mSerialstateTv.setText("error");
                            mSerialstateTv.setTextColor(Color.RED);
                            Log.e(TAG, e.toString() + "");
                        } catch (IOException e) {
                            mSerialstateTv.setText("error");
                            mSerialstateTv.setTextColor(Color.RED);
                            Log.e(TAG, e.toString() + "");
                        } catch (InvalidParameterException e) {
                            mSerialstateTv.setText("error");
                            mSerialstateTv.setTextColor(Color.RED);
                            Log.e(TAG, e.toString() + "");
                        }
                    }
                });
            }
        }
        //serialport end

        //wifi
        tv_wifi = (TextView) this.findViewById(R.id.tv_wifi);
        if (WIFIShow) {
            Log.d(TAG, "======checkWifi : " + WifiTestUtil.checkWifi(this));
            if (!WifiTestUtil.checkWifi(this)) {
                return;
            }
            tv_wifi.setVisibility(View.VISIBLE);
            wifiTestUtil = new WifiTestUtil((WifiManager) getSystemService(Context.WIFI_SERVICE)) {
                public void wifiStateChange(int newState) {
                    saveResult();
                    if (wifiTestUtil.isConnected(WIFIaccount)) {
                        return;
                    }
                    switch (newState) {
                        case WifiManager.WIFI_STATE_ENABLED:
                            tv_wifi.setText("WIFI：Wifi ON,Discovering...");
                            WIFIResult = "Fail";
                            tv_wifi.setTextColor(Color.YELLOW);
                            if (mSearchFlag) {
                                for (ScanResult result : wifiTestUtil.getScanResults()) {
                                    if (result.SSID.equals(WIFIaccount)) {
                                        tv_wifi.append(" [device:");
                                        tv_wifi.append(result.SSID);
                                        tv_wifi.append(" level: ");
                                        tv_wifi.append(String.valueOf(result.level));
                                        tv_wifi.append("]");
                                        Log.d(TAG, "wifiStateChange wifi-真实强度为" + result.level + "   达标强度为" + WIFIlevel);
                                        break;
                                    }
                                }
                            }
                            break;
                        case WifiManager.WIFI_STATE_DISABLED:
                            tv_wifi.setText("WIFI：Wifi OFF [失败]");
                            WIFIResult = "Fail";
                            tv_wifi.setTextColor(Color.RED);
                            break;
                        case WifiManager.WIFI_STATE_DISABLING:
                            tv_wifi.setText("WIFI：Wifi Closing");
                            break;
                        case WifiManager.WIFI_STATE_ENABLING:
                            tv_wifi.setText("WIFI：Wifi Opening");
                            WIFIResult = "Fail";
                            tv_wifi.setTextColor(Color.YELLOW);
                            break;
                        case WifiManager.WIFI_STATE_UNKNOWN:
                        default:
                            tv_wifi.setText("WIFI：Wifi state Unknown [失败]");
                            WIFIResult = "Fail";
                            tv_wifi.setTextColor(Color.RED);
                            // do nothing
                            break;
                    }
                }

                public void wifiConnected(String ssid) {
                    Log.d(TAG, "wifiConnected ssid =" + ssid);
                    if (mFindFlag) {
                        return;
                    }
                    if (!TextUtils.isEmpty(ssid) && ssid.equals(WIFIaccount)) {
                        if (wifiTestUtil.isConnected(WIFIaccount)) {
                            for (ScanResult result : wifiTestUtil.getScanResults()) {
                                Log.d(TAG, "wifi-result.SSID=" + result.SSID);
                                if (result.SSID.equals(WIFIaccount)) {
                                    getWorkHandler().sendEmptyMessageDelayed(WIFI_RESULT_MSG, 1000);
                                    //mFindFlag = true;
                                    break;
                                }
                            }
                        } else {
                            getWorkHandler().sendEmptyMessageDelayed(WIFI_MSG, 1000);
                        }
                    } else {
                        getWorkHandler().sendEmptyMessageDelayed(WIFI_MSG, 1000);
                    }
                }

                public void wifiDeviceListChange(List<ScanResult> wifiDeviceList) {
                    if (mFindFlag) {
                        return;
                    }
                    if (wifiDeviceList == null) {
                        WIFIResult = "Fail";
                        saveResult();
                        return;
                    }

                    tv_wifi.setText(getResources().getString(R.string.str_wifi) +
                            "[add:" + wifiTestUtil.getWifiManager().getConnectionInfo().getMacAddress()
                            + "]");
                   tv_wifi.setTextColor(Color.BLUE);

                    if (mWifiPrefixShow) {
                        tv_mac.setVisibility(View.VISIBLE);
                        mWifiPrefixResult = getWifiMac();
                        if (!TextUtils.isEmpty(mWifiPrefixResult) && (mWifiPrefixResult.startsWith(mWifiPrefix) || mWifiPrefixResult.toUpperCase().startsWith(mWifiPrefix))) {
                            tv_mac.setTextColor(Color.GREEN);
                            mWifiPrefixResult += "\tWifi地址匹配前缀:" + mWifiPrefix + "\tPass";
                        } else {
                            tv_mac.setTextColor(Color.RED);
                            mWifiPrefixResult += "\tWifi地址匹配前缀:" + mWifiPrefix + "\tFail";
                        }
                        tv_mac.setText("WIFI-MAC: " + mWifiPrefixResult);
                    } else {
                        tv_mac.setVisibility(View.GONE);
                    }

                    for (ScanResult result : wifiDeviceList) {
                        Log.d(TAG, "wifi-result.SSID=" + result.SSID);
                        //if (result.SSID.equals(WIFIaccount)) {
                        if (result.SSID.equals(WIFIaccount)) {
                            mSearchFlag = true;
                            /*if (wifiTestUtil.isConnected(WIFIaccount)) {
                                tv_wifi.append(" [device:");
                                tv_wifi.append(result.SSID);
                                tv_wifi.append(" level: ");
                                tv_wifi.append(String.valueOf(result.level));
                                tv_wifi.append("]");
                                Log.d(TAG, "wifi-真实强度为" + result.level + "   达标强度为" + WIFIlevel);
                                if (result.level > WIFIlevel) {
                                    tv_wifi.append("[通过]");
                                    WIFIResult = "Pass";
                                    tv_wifi.setTextColor(Color.GREEN);
                                    mFindFlag = true;
                                } else {
                                    tv_wifi.append("[失败]");
                                    WIFIResult = "Fail";
                                    tv_wifi.setTextColor(Color.RED);
                                }
                                //getWorkHandler().sendEmptyMessageDelayed(WIFI_RESULT_MSG, 1000);
                            } else {
                                getWorkHandler().sendEmptyMessageDelayed(WIFI_MSG, 1000);
                            }*/
                            getWorkHandler().sendEmptyMessageDelayed(WIFI_MSG, 1000);
                            break;
                        }
                    }
                    saveResult();
                }
            };
            if (wifiTestUtil.getWifiManager() != null) {
                wifiTestUtil.startTest(this);
                //getWorkHandler().sendEmptyMessageDelayed(WIFI_MSG, 1000);
            }
        } else {
            tv_wifi.setVisibility(View.GONE);
        }

        //bt
        tv_bt = (TextView) this.findViewById(R.id.tv_bt);
        if (BTShow) {
            Log.d(TAG, "======checkBluetooth : " + BtTestUtil.checkBluetooth(this));
            if (!BtTestUtil.checkBluetooth(this)) {
                return;
            }
            tv_bt.setVisibility(View.VISIBLE);
            btTestUtil = new BtTestUtil() {
                public void btStateChange(int newState) {
                    switch (newState) {
                        case BluetoothAdapter.STATE_ON:
                            tv_bt.setText("BT：Bluetooth ON,Discovering...");
                            BTResult = "Fail";
                            tv_bt.setTextColor(Color.YELLOW);
                            break;
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            tv_bt.setText("BT：Bluetooth Closing");
                            break;
                        case BluetoothAdapter.STATE_OFF:
                            tv_bt.setText("BT：Bluetooth OFF");
                            BTResult = "Fail";
                            tv_bt.setText("蓝牙：[失败] ");
                            tv_bt.setTextColor(Color.RED);
                            break;
                        case BluetoothAdapter.STATE_TURNING_ON:
                            tv_bt.setText("BT：Bluetooth Opening...");
                            BTResult = "Fail";
                            tv_bt.setTextColor(Color.YELLOW);
                            break;
                        default:
                            tv_bt.setText("BT：Bluetooth state Unknown");
                            BTResult = "Fail";
                            tv_bt.setText("蓝牙：[失败] ");
                            tv_bt.setTextColor(Color.RED);
                            break;
                    }
                    saveResult();
                }

                //btTestUtil.getBluetoothAdapter().getAddress();
                public void btDeviceListAdd(BluetoothDevice device) {
                    if ("Pass".equals(BTResult)) {
                        return;
                    }
                    if (mBluetoothDeviceList.contains(device)) {
                        return;
                    }
                    tv_bt.setText("蓝牙： " + btTestUtil.getBluetoothAdapter().getAddress());

                    if (mBTPrefixShow) {
                        tv_bt_mac.setVisibility(View.VISIBLE);
                        mBTPrefixResult = btTestUtil.getBluetoothAdapter().getAddress();
                        if (!TextUtils.isEmpty(mBTPrefixResult) && (mBTPrefixResult.startsWith(mBTPrefix) || mBTPrefixResult.toUpperCase().startsWith(mBTPrefix))) {
                            tv_bt_mac.setTextColor(Color.GREEN);
                            mBTPrefixResult += "\t蓝牙地址匹配前缀:" + mBTPrefix + "\tPass";
                        } else {
                            tv_bt_mac.setTextColor(Color.RED);
                            mBTPrefixResult += "\t蓝牙地址匹配前缀:" + mBTPrefix + "\tFail";
                        }
                        tv_bt_mac.setText("BT-MAC: " + mBTPrefixResult);
                    } else {
                        tv_bt_mac.setVisibility(View.GONE);
                    }

                    if (device != null) {
                        mBluetoothDeviceList.add(device);
                        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                            String name = device.getName();
                            if (name == null || name.isEmpty()) {
                                BTResult = "Fail";
                                return;
                            }
                            StringBuffer deviceInfo = new StringBuffer();
                            deviceInfo.append("[device name: ");
                            deviceInfo.append(name);
                            deviceInfo.append("]");
                            Log.d(TAG, "======find bluetooth device => name : " + name
                                    + "\n address :" + device.getAddress());
                            BTResult = "Pass";
                            tv_bt.setText("蓝牙：[通过] " + deviceInfo.toString());
                            tv_bt.setTextColor(Color.GREEN);
                        }
                    }
                    saveResult();
                }
            };
            btTestUtil.startTest(this);
        } else {
            tv_bt.setVisibility(View.GONE);
        }

        //sim
        tv_sim = (TextView) this.findViewById(R.id.tv_sim);
        if (hava4GShow) {
            tv_sim.setVisibility(View.VISIBLE);
            simItem = new SIMItem(this);
            String cell = simItem.showDevice();
            if (cell.contains("失败")) {
                hava4G = "Fail";
            } else {
                hava4G = "Pass";
            }
            tv_sim.setText(cell);
        } else {
            tv_sim.setVisibility(View.GONE);
        }

        //show touch
        main_layout = this.findViewById(R.id.main_layout);

        //phone 打电话
        rl_call = (RelativeLayout) this.findViewById(R.id.rl_call);
        bt_phone = (Button) this.findViewById(R.id.bt_phone);
        sw_call = (Switch) this.findViewById(R.id.sw_call);
        if (CALLShow) {
            rl_call.setVisibility(View.VISIBLE);
            bt_phone.setVisibility(View.VISIBLE);
            bt_phone.setOnClickListener(this);
            sw_call.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setSwitchTextAppearance(sw_call, isChecked);
                    if (isChecked) {
                        CALLResult = "Pass";
                    } else {
                        CALLResult = "Fail";
                    }
                }
            });
        } else {
            bt_phone.setVisibility(View.GONE);
            rl_call.setVisibility(View.GONE);
        }

        btn_lcd = (Button) this.findViewById(R.id.lcd_test);
        if (mLCDShow) {
            btn_lcd.setVisibility(View.VISIBLE);
            btn_lcd.setOnClickListener(this);
            btn_lcd.setTextColor(Color.BLUE);
        } else {
            btn_lcd.setVisibility(View.GONE);
        }

        btn_tp = (Button) this.findViewById(R.id.tp_test);
        if (TPShow) {
            btn_tp.setVisibility(View.VISIBLE);
            btn_tp.setOnClickListener(this);
            btn_tp.setTextColor(Color.BLUE);
        } else {
            btn_tp.setVisibility(View.GONE);
        }

        ll_led = (LinearLayout) this.findViewById(R.id.ll_led);
        sw_led = (Switch) this.findViewById(R.id.sw_led);
        btn_led = (Button) this.findViewById(R.id.led_test);
        if (ledShow) {
            ll_led.setVisibility(View.VISIBLE);
            sw_led.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setSwitchTextAppearance(sw_led, isChecked);
                    if (isChecked) {
                        ledResult = "Pass";
                        btn_led.setTextColor(Color.GREEN);
                    } else {
                        ledResult = "Fail";
                        btn_led.setTextColor(Color.RED);
                    }
                }
            });
            btn_led.setOnClickListener(this);
            btn_led.setTextColor(Color.BLUE);
        } else {
            ll_led.setVisibility(View.GONE);
        }

        btn_leftmusic = (Button) this.findViewById(R.id.leftmusic_test);
        btn_rightmusic = (Button) this.findViewById(R.id.rightmusic_test);
        btn_leftmusic.setOnClickListener(this);
        btn_rightmusic.setOnClickListener(this);
        btn_leftmusic.setTextColor(Color.BLUE);
        btn_rightmusic.setTextColor(Color.BLUE);
        sw_leftmusic = (Switch) this.findViewById(R.id.sw_leftmusic);
        sw_leftmusic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setSwitchTextAppearance(sw_leftmusic, isChecked);
                if (isChecked) {
                    LeftMusicResponse = "Pass";
                    btn_leftmusic.setTextColor(Color.GREEN);
                } else {
                    LeftMusicResponse = "Fail";
                    btn_leftmusic.setTextColor(Color.RED);
                }
            }
        });
        sw_rightmusic = (Switch) this.findViewById(R.id.sw_rightmusic);
        sw_rightmusic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setSwitchTextAppearance(sw_rightmusic, isChecked);
                if (isChecked) {
                    RightMusicResponse = "Pass";
                    btn_rightmusic.setTextColor(Color.GREEN);
                } else {
                    RightMusicResponse = "Fail";
                    btn_rightmusic.setTextColor(Color.RED);
                }
            }
        });

        if (SPKShow && SPK == 1) {
            btn_rightmusic.setVisibility(View.GONE);
            sw_rightmusic.setVisibility(View.GONE);
        } else if (!SPKShow) {
            btn_leftmusic.setVisibility(View.GONE);
            sw_leftmusic.setVisibility(View.GONE);
            sw_rightmusic.setVisibility(View.GONE);
            btn_rightmusic.setVisibility(View.GONE);
        }

        //gps
        tv_gps = (TextView) this.findViewById(R.id.tv_gps);
        if (GPSShow) {
            tv_gps.setVisibility(View.VISIBLE);
            manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (hasGPSDevice(this)) {
                Settings.Secure.setLocationProviderEnabled(getContentResolver(),
                        LocationManager.GPS_PROVIDER, true);
                //   */
                Settings.Secure.putInt(getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
                try {
                    locationListener = new LocationListener() {
                        public void onLocationChanged(Location location) {
                        }

                        public void onProviderDisabled(String provider) {
                            showGpsMsg();
                        }

                        public void onProviderEnabled(String provider) {
                            showGpsMsg();
                        }

                        public void onStatusChanged(String provider, int status,
                                                    Bundle extras) {
                        }
                    };
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                            UPDATE_MIN_TIME, 0, locationListener);
                    gpsStatusListener = new GpsStatus.Listener() {
                        public void onGpsStatusChanged(int event) {
                            Log.d(TAG, " " + event);
                            if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
                                showSatelliteCount();
                            }
                        }
                    };
                    manager.addGpsStatusListener(gpsStatusListener);
                } catch (Exception e) {
                    Log.e(TAG, "requestLocationUpdates-----e=" + e);
                    e.printStackTrace();
                }

                Settings.System.putInt(this.getContentResolver(),
                        Settings.System.POINTER_LOCATION, 1);
            }
        } else {
            tv_gps.setVisibility(View.GONE);
        }

        mp2 = MediaPlayer.create(ZedielTools.this, R.raw.left);
        mp2.setLooping(true);
        mp3 = MediaPlayer.create(ZedielTools.this, R.raw.right);
        mp3.setLooping(true);
    }

    private void startRecorderTest() {
        btn_recorder.setEnabled(false);
        codecPlayer = MediaPlayer.create(ZedielTools.this, R.raw.codec);
        codecPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
                getWorkHandler().sendEmptyMessageDelayed(RECORDER_START_MSG, 500);
            }
        });
        codecPlayer.start();
    }

    private void setSwitchTextAppearance(Switch switcher, boolean isChecked) {
        if (switcher == null) {
            return;
        }
        if (isChecked) {
            switcher.setSwitchTextAppearance(ZedielTools.this, R.style.item_switch_style_on);
        } else {
            switcher.setSwitchTextAppearance(ZedielTools.this, R.style.item_switch_style_off);
        }
        saveResult();
    }

    //recorder
    private void startRecorder() {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            soundFile = new File(Environment.getExternalStorageDirectory()
                    .getCanonicalPath() + File.separator + "sound.mp3");
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        mediaRecorder.setOutputFile(soundFile.getAbsolutePath());
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            mIsRecording = true;

        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        change();
    }

    private void change() {
        getWorkHandler().sendEmptyMessageDelayed(RECORDER_PROBAR_MSG, 100);
    }

    private void recorderProgressBarChange() {
        if (mediaRecorder != null) {
            int m = mediaRecorder.getMaxAmplitude();

            // int x = (6 * m / 32768);//通过这种算法计算出的音量大小在1-7之间
            //7*32768/6=max; //38229 y = m/38227  * 100;
            int x = (int) (1.6 * m * 100) / 38227;
            //System.out.println("MMM---->>>" + x);

            if (x >= 100) {
                x = 100;
            }
            tv_recorder.setText("录音：" + "[" + x + "]");
            /*if(0 == x){
                tv_recorder.setTextColor(Color.RED);
            }else{
                tv_recorder.setTextColor(Color.GREEN);
            }*/
            progressBar.setProgress(x);
            change();
        }
    }


    private final String Z_TEST = "Z";
    private final String ZP_TEST = "Z+";
    private final String ZN_TEST = "Z-";
    private final String X_TEST = "X";
    private final String XP_TEST = "X+";
    private final String XN_TEST = "X-";
    private final String Y_TEST = "Y";
    private final String YP_TEST = "Y+";
    private final String YN_TEST = "Y-";
    private final float MIN_VALUE = 0.5f;
    private final float MAX_VALUE = 9.5f;
    private final float MAX_VALUE1 = 10.5f;
    private int mGsensorCount;
    private HashSet<String> mGsensorResult = new HashSet<>();
    private HashSet<String> mAllGsensorTestKey = new HashSet<>(Arrays.asList(Z_TEST, X_TEST, Y_TEST, ZP_TEST, ZN_TEST, XP_TEST, XN_TEST, YP_TEST, YN_TEST));
    private HashSet<String> mGsensorTestKey = new HashSet<>();

    /**
     * key test
     */
    private final String HOME_SYS = "[Home]";
    private final String BACK_SYS = "[Back]";
    private final String MENU_SYS = "[Menu]";
    private final String CAMERA_SYS = "[Camera]";
    private final String VOLP_SYS = "[vol+]";
    private final String VOLM_SYS = "[vol-]";
    private final String UP_SYS = "[↑]";
    private final String DOWN_SYS = "[↓]";
    private final String LEFT_SYS = "[→]";
    private final String RIGHT_SYS = "[←]";
    private final String ENTER_SYS = "[Enter]";
    private final String MENU_TEST = "Menu";
    private final String CAMERA_TEST = "Camera";
    private final String VOLP_TEST = "VolU";
    private final String VOLM_TEST = "VolD";
    private final String UP_TEST = "Up";
    private final String DOWN_TEST = "Down";
    private final String LEFT_TEST = "Left";
    private final String RIGHT_TEST = "Right";
    private final String ENTER_TEST = "Enter";
    private final String KEY_SPLIT = ";";
    private StringBuilder mKeyResult = new StringBuilder();
    private List<String> mAllTestKey = Arrays.asList(MENU_TEST, CAMERA_TEST, VOLP_TEST, VOLM_TEST, UP_TEST, DOWN_TEST, LEFT_TEST, RIGHT_TEST, ENTER_TEST);
    private List<String> mKeyTest = new ArrayList<>();

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.d(TAG, "dispatchKeyEvent--keyCode=" + event.getKeyCode());
        int keyCode = event.getKeyCode();
        if (TextUtils.isEmpty(mKeyResult.toString())) {
            mKeyResult.append(getResources().getString(R.string.str_key));
        }
        if (KeyEvent.KEYCODE_MENU == keyCode) {
            if (!mKeyResult.toString().contains(MENU_SYS)) {
                mKeyResult.append(MENU_SYS);
                mKeyTest.remove(MENU_TEST);
            }
            tv_key.setText(mKeyResult.toString());
            allKeyTest();
            return true;
        } else if (KeyEvent.KEYCODE_CAMERA == keyCode) {
            if (!mKeyResult.toString().contains(CAMERA_SYS)) {
                mKeyResult.append(CAMERA_SYS);
                mKeyTest.remove(CAMERA_TEST);
            }
            tv_key.setText(mKeyResult.toString());
            allKeyTest();
            return true;
        } else if (KeyEvent.KEYCODE_VOLUME_UP == keyCode) {
            if (!mKeyResult.toString().contains(VOLP_SYS)) {
                mKeyResult.append(VOLP_SYS);
                mKeyTest.remove(VOLP_TEST);
            }
            tv_key.setText(mKeyResult.toString());
            allKeyTest();
            return true;
        } else if (KeyEvent.KEYCODE_VOLUME_DOWN == keyCode) {
            if (!mKeyResult.toString().contains(VOLM_SYS)) {
                mKeyResult.append(VOLM_SYS);
                mKeyTest.remove(VOLM_TEST);
            }
            tv_key.setText(mKeyResult.toString());
            allKeyTest();
            return true;
        } else if (KeyEvent.KEYCODE_DPAD_UP == keyCode) {
            if (!mKeyResult.toString().contains(UP_SYS)) {
                mKeyResult.append(UP_SYS);
                mKeyTest.remove(UP_TEST);
            }
            tv_key.setText(mKeyResult.toString());
            allKeyTest();
            return true;
        } else if (KeyEvent.KEYCODE_DPAD_DOWN == keyCode) {
            if (!mKeyResult.toString().contains(DOWN_SYS)) {
                mKeyResult.append(DOWN_SYS);
                mKeyTest.remove(DOWN_TEST);
            }
            tv_key.setText(mKeyResult.toString());
            allKeyTest();
            return true;
        } else if (KeyEvent.KEYCODE_DPAD_RIGHT == keyCode) {
            if (!mKeyResult.toString().contains(LEFT_SYS)) {
                mKeyResult.append(LEFT_SYS);
                mKeyTest.remove(LEFT_TEST);
            }
            tv_key.setText(mKeyResult.toString());
            allKeyTest();
            return true;
        } else if (KeyEvent.KEYCODE_DPAD_LEFT == keyCode) {
            if (!mKeyResult.toString().contains(RIGHT_SYS)) {
                mKeyResult.append(RIGHT_SYS);
                mKeyTest.remove(RIGHT_TEST);
            }
            tv_key.setText(mKeyResult.toString());
            allKeyTest();
            return true;
        } else if (KeyEvent.KEYCODE_ENTER == keyCode) {
            if (!mKeyResult.toString().contains(ENTER_SYS)) {
                mKeyResult.append(ENTER_SYS);
                mKeyTest.remove(ENTER_TEST);
            }
            tv_key.setText(mKeyResult.toString());
            allKeyTest();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        if (KeyEvent.KEYCODE_HOME == keyCode) {
            if (!mKeyResult.toString().contains(HOME_SYS)) {
                mKeyResult.append(HOME_SYS);
            }
            tv_key.setText(mKeyResult.toString());
            //allExit();
        } else if (KeyEvent.KEYCODE_BACK == keyCode) {
            if (!mKeyResult.toString().contains(BACK_SYS)) {
                mKeyResult.append(BACK_SYS);
            }
            tv_key.setText(mKeyResult.toString());
            allExit();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void allKeyTest() {
        if (mKeyTest.size() == 0) {
            KeyResponse = "Pass";
            tv_key.setTextColor(Color.GREEN);
            sw_key.setChecked(true);
        } else {
            KeyResponse = "Fail";
            tv_key.setTextColor(Color.RED);
            sw_key.setChecked(false);
        }
        saveResult();
        Log.d(TAG, "allKeyTest--mKeyTest=" + mKeyTest.toString());
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        return super.onKeyUp(keyCode, event);
    }

    private void setBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1.0f;
        getWindow().setAttributes(lp);
        Log.i(TAG, "<<<<<<<<<<<<<<<< 11111");
        //mUiHandler.postDelayed(mRunnable, 2000);

        isRunmax = false;
        if (mcount > 3) {
            //mRunnable.
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*try {
            if (isFirstResume) {
                //mCamera = Camera.open();
                isFirstResume = false;
                mCamera = Camera.open((cameraCurrentId + 1) % numCamera);
                cameraCurrentId = (cameraCurrentId + 1) % numCamera;
                if (null != mCamera) {
                    mPreview.switchCamera(mCamera);
                    mCamera.setDisplayOrientation(90);

                    Camera.Parameters parameters = mCamera.getParameters();
                    parameters.setPreviewSize(800, 600);
                    mCamera.setParameters(parameters);


                    mCamera.startPreview();
                }
                List<Camera.Size> previewSizes = mCamera.getParameters().getSupportedPreviewSizes();

                for (int i=0; i<previewSizes.size(); i++) {
                    Camera.Size pSize = previewSizes.get(i);
                    Log.i(TAG, "------previewSizes.width = "+pSize.width+"---previewSizes.height = "+pSize.height);
                }

                Log.i(TAG, "<<<<<< first resume >>>>>");
            } else {
                if (mCamera != null) {
                    mPreview.setCamera(null);
                    mCamera.release();
                    mCamera = null;
                }
                mCamera = Camera.open();
                //isFirstResume = false;
                Log.i(TAG, "<<<<<< second resume >>>>>");
            }

            mPreview.setCamera(mCamera);
            cameraCurrentId = cameraId;
        } catch (Exception e) {
            // TODO: handle exception
        }*/

        Log.i(TAG, "<<<< in the resume >>>>>");
        if (mLedLightShow) {
            LedLightUtil.testPowerLed();
            LedLightUtil.testWorkLed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.stopPreview();
            mPreview.setCamera(null);
            mCamera.release();
            mCamera = null;
        }
        if (mLedLightShow) {
            LedLightUtil.onDestroy();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.camera_preview:
                if (mCamera != null) {
                    mCamera.stopPreview();
                    mCamera.release();
                    mCamera = null;
                }
                try {
                    mCamera = Camera.open((cameraCurrentId + 1) % numCamera);
                    cameraCurrentId = (cameraCurrentId + 1) % numCamera;
                } catch (Exception e) {
                    // TODO: handle exception
                    Log.i(TAG, "<<<<<< the camera is null >>>>");
                    mCamera = null;
                }
                //  mCamera = Camera.open((cameraCurrentId + 1) % numCamera);

                if (null != mCamera) {
                    mPreview.switchCamera(mCamera);
                    mCamera.setDisplayOrientation(90);
                    mCamera.startPreview();
                }

                break;
            case R.id.bt_phone:
                Intent intent = new Intent("android.intent.action.CALL_PRIVILEGED", Uri.parse("tel:112"));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("factory_mode", true);
                startActivity(intent);
                break;

            case R.id.lcd_test:
                Intent intent2 = new Intent(ZedielTools.this, LcdTest.class);
                this.startActivityForResult(intent2, 2);
                break;

            case R.id.tp_test:
                Intent intent3 = new Intent(ZedielTools.this, TpTest.class);
                this.startActivityForResult(intent3, 0);
                break;
            case R.id.led_test:
                //led灯光测试开关
                if (isLedOpen) {
                    setNodeString(led_Path, "10");
                    isLedOpen = false;
                } else {
                    setNodeString(led_Path, "11");
                    isLedOpen = true;
                }
                break;
            case R.id.leftmusic_test:
                if (mp2 != null ) {
                    mp2.start();
                }
                break;
            case R.id.rightmusic_test:
                if (mp3 != null ) {
                    mp3.start();
                }
                break;
            case R.id.btn_recorder:
                startRecorderTest();
                break;
            case R.id.save_exit:
                allExit();
                break;
            case R.id.goto_left:
                checkTestResult();
                break;
            case R.id.goto_right:
                startAgingTest();
                break;
            default:
                break;
        }
    }

    private void saveResult() {
        Log.d(TAG, "saveResult");
        ThreadUtils.getInstance().execInBg(new Runnable() {
            @Override
            public void run() {
                writeresultState(formatStateContent());
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.i(TAG, "<<<<<<<< back >>>>>>");
        //finish();
        if (TestMode == 1) {
            writeresultState(formatStateContent());
        }
    }

    private boolean isHome() {
        ActivityManager mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> rti = mActivityManager.getRunningTasks(1);
        return getHomes().contains(rti.get(0).topActivity.getPackageName());
    }

    private List<String> getHomes() {
        List<String> names = new ArrayList<String>();
        PackageManager packageManager = this.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo ri : resolveInfo) {
            names.add(ri.activityInfo.packageName);
        }
        return names;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (TestMode == 2 || TestMode == 3) {
            return;
        }
        // TODO Auto-generated method stub
        Log.d(TAG, "+++++++++++onDestroy+++++++++++TestMode=" + TestMode);

        Settings.System.putInt(getContentResolver(), Settings.System.POINTER_LOCATION, 0);

        if (null != gpsStatusListener) {
            manager.removeGpsStatusListener(gpsStatusListener);
        }
        if (null != locationListener) {
            manager.removeUpdates(locationListener);
        }

        if (null != vibrator) {
            vibrator.cancel();
        }

        if (null != usbstates) {
            //usbstates.unregisterReceiver();
            usbstates = null;
        }
        if (null != mChargeBroadcastReceiver) {
            unregisterReceiver(mChargeBroadcastReceiver);
        }
        if (null != mErjiBroadcastReceiver) {
            unregisterReceiver(mErjiBroadcastReceiver);
        }
        if (null != mp2) {
            if (mp2.isPlaying()) {
                mp2.pause();
            }
            mp2.release();
            mp2 = null;
        }
        if (null != mp3) {
            if (mp3.isPlaying()) {
                mp3.pause();
            }
            mp3.release();
            mp3 = null;
        }

        //wifi
        if (null != wifiTestUtil) {
            //wifiTestUtil.stopTest();
			wifiTestUtil.deleteSavedWifi(mWifiManager);
        }
		
        //bt
        if (null != btTestUtil) {
            btTestUtil.stopTest();
        }

        if (null != soundFile && soundFile.exists() && null != mediaRecorder) {
            //mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            soundFile.delete();
            mediaRecorder = null;
        }

        if (null != mPlayer) {
            if (mPlayer.isPlaying()) {
                mPlayer.stop();
            }
            mPlayer.release();
            mPlayer = null;
        }

        if (mIsRecording) {
            mIsRecording = false;
        }

        if (codecPlayer != null) {
            codecPlayer.release();
            codecPlayer = null;
        }

        closeSerialPort();
        if (mWorkHandler != null) {
            mWorkHandler.removeCallbacksAndMessages(null);
        }
        if (mDistanceShow) {
            DistanceTest.getInstance().unregisterListener();
        }
        if (TestMode == 1) {
            writeresultState(formatStateContent());
        }
        ThreadUtils.getInstance().shutdown();
    }

    private boolean isGpsEnabled() {
        if (manager == null) {
            return false;
        }
        return manager.isProviderEnabled(PROVIDER);
    }

    private void showGpsMsg() {
        if (!isGpsEnabled()) {
            // gps not enabled
            //txtGpsMsg.setText(getString(R.string.gps_not_enabled_msg));
            tv_gps.setText("GPS设备未打开");
            tv_gps.setTextColor(Color.YELLOW);
            // btnShow.setEnabled(true);
        } else {
            //txtGpsMsg.setText("");
            tv_gps.setText("GPS设备打开");
            tv_gps.setTextColor(Color.GREEN);
            // btnShow.setEnabled(false);
        }
    }

    private void showSatelliteCount() {
        int count = 0;
        boolean flag = false;

        if (manager != null) {
            GpsStatus status = manager.getGpsStatus(null);
            Iterator<GpsSatellite> iterator = status.getSatellites().iterator();

            //get satellite count
            while (iterator.hasNext()) {
                Log.d(TAG, "has next.......");
                count++;
                GpsSatellite gpsSatellite = iterator.next();
                float snr = gpsSatellite.getSnr();
                if (snr > 40.0)
                    flag = true;

                tv_gps.append("id: ");
                tv_gps.append(String.valueOf(gpsSatellite.getPrn()));
                //tv_gps.append("\nsnr: ");
                tv_gps.append(":");
                tv_gps.append(String.valueOf(snr));
                tv_gps.append(":");
                //tv_gps.append("\n\n");
            }

            //satellite count is ok
            if (count >= SATELLITE_COUNT_MIN) {
                //search ok
            }

            //save max satellite count that have been searched
            if (count > mSatelliteCount) {
                mSatelliteCount = count;
            }

            //show count
            tv_gps.setText("GPS:卫星数目[" + mSatelliteCount + "]");
            GPSResult = "GPS:卫星数目=" + mSatelliteCount;
            if (0 == mSatelliteCount) {
                tv_gps.setTextColor(Color.YELLOW);
            } else {
                tv_gps.setTextColor(Color.GREEN);
            }
        }
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (gpsStatusListener != null) {
            manager.removeGpsStatusListener(gpsStatusListener);
        }
        if (locationListener != null) {
            manager.removeUpdates(locationListener);
        }

        Settings.System.putInt(this.getContentResolver(),
                Settings.System.POINTER_LOCATION, 0);

        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private void allExit() {
        onBackPressed();
    }

    /**
     * check if has camera
     */
    public boolean checkCameraHardware(Context context) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = manager.getCameraIdList();
            if (cameraIds.length > 0) {
                return true;
            } else {
                return false;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return false;
        }
    }

    public class MyHandler extends Handler {
        public MyHandler() {
        }

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            super.handleMessage(msg);
            //System.out.println("=============mainactivity handler");
//          Usb.setText("usb states");

            if (msg.arg1 == 0x00021) {
                tv_usbdisk.setText("外部存储设备: " + NandItem.getmemorysize(ZedielTools.this));
            } else if (msg.arg1 == 0x00022) {
                tv_usbdisk.setText("外部存储设备: " + NandItem.getmemorysize(ZedielTools.this));
            }else if(msg.arg1 == 0x00222){
                isCanBofang = true ;
                Log.d(TAG, "MyHandler----mp-----isCanBofang=true");
            }
            //update the memory status. maji

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.guanhong_tools, menu);
        return true;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // TODO Auto-generated method stub
        if (v.getId() == R.id.main_layout) {
            Bitmap b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            Point p = new Point((int) event.getX(), (int) event.getY());

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // 用户按下，表示重新开始保存点
                this.allPoints = new ArrayList<Point>();
                Log.i(TAG, "ddddddddddd");
                this.allPoints.add(p);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // 用户松开
                //MyPaintView.this.allPoints.clear();
                this.allPoints.add(p);
                //GuanhongTools.this.postInvalidate();// 重绘图像
                draw(canvas);
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                this.allPoints.add(p);
                Log.i(TAG, "mmmmmmmmmmmmmmm");
                //this.postInvalidate();// 重绘图像
                draw(canvas);
            }
        }
        return true;
        //return false;
    }

    public void draw(Canvas canvas) {
        Paint p = new Paint();// 依靠此类开始画线
        p.setColor(Color.YELLOW);
        p.setStrokeWidth(5);
        if (this.allPoints.size() > 1) {
            // 如果有坐标点，开始绘图
            Iterator<Point> iter = this.allPoints.iterator();
            Point first = null;
            Point last = null;
            while (iter.hasNext()) {
                if (first == null) {
                    first = (Point) iter.next();
                } else {
                    if (last != null) {
                        first = last;
                    }
                    last = (Point) iter.next();// 结束
                    canvas.drawLine(first.x, first.y, last.x, last.y, p);
                }
            }
        }
    }

    // @return true表示已安装，否则返回false
    public static boolean isInstalled(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager(); //获取packagemanager
        List<PackageInfo> installedList = packageManager.getInstalledPackages(0); //获取所有已安装程序的包信息
        Iterator<PackageInfo> iterator = installedList.iterator();

        PackageInfo info;
        String name;
        while (iterator.hasNext()) {
            info = iterator.next();
            name = info.packageName;
            if (name.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    class WorkHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == ETHNET_MSG) {
                EthernetResult = rj45TestUtil.getrjStatus();
                if (getResources().getString(R.string.ethnet_init).equals(EthernetResult)) {
                    tv_rjethernet.setTextColor(Color.BLUE);
                } else if (getResources().getString(R.string.fail).equals(EthernetResult)) {
                    tv_rjethernet.setTextColor(Color.RED);
                } else {
                    tv_rjethernet.setTextColor(Color.GREEN);
                }
                tv_rjethernet.setText("RJ45：" + EthernetResult);
                getWorkHandler().sendEmptyMessageDelayed(ETHNET_MSG, 500);
            } else if (msg.what == RTC_MSG) {
                RTCResult = "[通过] " + rtctest.getTime();
                tv_rtc.setText(getResources().getText(R.string.str_rtc) + RTCResult);
                tv_rtc.setTextColor(Color.GREEN);
                getWorkHandler().sendEmptyMessageDelayed(RTC_MSG, 500);
            } else if (msg.what == BL_MSG) {
                if (mBrightnrssTimes < 2) {
                    mBrightnrssLevel += 30;
                    brightness_sb.setProgress(mBrightnrssLevel);
                    if (mBrightnrssLevel > 255) {
                        mBrightnrssTimes += 1;
                        mBrightnrssLevel = 30;
                    }
                    getWorkHandler().sendEmptyMessageDelayed(BL_MSG, 1000);
                }
            } else if (msg.what == SERIAL_MSG) {
                mSerialstateTv.setText((String) msg.obj);
                mSerialstateTv.setTextColor(getResources().getColor(R.color.item_color));
            } else if (msg.what == WIFI_MSG) {
                wifiTestUtil.startConnectWifiTest(WIFIaccount, WIFIpassword);
            } else if (msg.what == WIFI_RESULT_MSG) {
                calulateWifiLevel();
            } else if (msg.what == RECORDER_START_MSG) {
                startRecorder();
                getWorkHandler().sendEmptyMessageDelayed(RECORDER_PLAYER_MSG, 1800);
            } else if (msg.what == RECORDER_PLAYER_MSG) {
                playRecorder();
            } else if (msg.what == RECORDER_PROBAR_MSG) {
                recorderProgressBarChange();
            } else if (msg.what == START_RECORDER_MSG) {
                btn_recorder.performClick();
            }
        }
    }

    private void calulateWifiLevel() {
        if (mWifiLevelCount < mWifiLevel.length) {
            for (ScanResult result : wifiTestUtil.getScanResults()) {
                if (result.SSID.equals(WIFIaccount)) {
                    mWifiLevel[mWifiLevelCount] = result.level;
                    Log.d(TAG, "calulateWifiLevel mWifiLevelCount=" + mWifiLevelCount + ", result.SSID=" + result.SSID + ", result.level=" + result.level);
                    break;
                }
            }
            mWifiLevelCount++;
            getWorkHandler().sendEmptyMessageDelayed(WIFI_RESULT_MSG, 1000);
        } else {
            Arrays.sort(mWifiLevel);
            int max = mWifiLevel[mWifiLevel.length - 1];
            int min = mWifiLevel[0];
            int sum = 0;
            for (int i = 0; i < mWifiLevel.length; i++) {
                sum += mWifiLevel[i];
            }
            int avg = (sum - max - min) / (mWifiLevel.length - 2);
            showWifiResult(WIFIaccount, avg);
            Log.d(TAG, "calulateWifiLevel avg=" + avg);
        }
    }

    private void showWifiResult(String ssid, int level) {
        if (mFindFlag) {
            return;
        }
        StringBuilder wifiInfo = new StringBuilder();
        wifiInfo.append(getResources().getString(R.string.str_wifi) + "[add:" + wifiTestUtil.getWifiManager().getConnectionInfo().getMacAddress() + "]");
        wifiInfo.append(" [device:");
        wifiInfo.append(ssid);
        wifiInfo.append(" level: ");
        wifiInfo.append(String.valueOf(level));
        wifiInfo.append("]");
        Log.d(TAG, "wifi-真实强度为" + level + "   达标强度为" + WIFIlevel);
        if (level > WIFIlevel) {
            wifiInfo.append("[通过]");
            WIFIResult = "Pass";
            tv_wifi.setTextColor(Color.GREEN);
            mFindFlag = true;
            wifiTestUtil.stopSearch();
        } else {
            wifiInfo.append("[失败]");
            WIFIResult = "Fail";
            tv_wifi.setTextColor(Color.RED);
        }
        tv_wifi.setText(wifiInfo);
    }

    private WorkHandler getWorkHandler() {
        if (mWorkHandler == null) {
            mWorkHandler = new WorkHandler();
        }
        return mWorkHandler;
    }

    //serial port cloae
    public void closeSerialPort() {
        if (UARTShow) {
            if (mReadThread != null) {
                mReadThread.interrupt();
            }
            if (mSendingThread != null) {
                mSendingThread.interrupt();
            }
            if (mSerialPort != null) {
                mSerialPort.close();
                mSerialPort = null;
            }
        }
    }

    public static final String RECOVERY_STATE_FILE = /*Environment.getExternalStorageDirectory().getPath() +*/ "/cache/recovery/Recovery_state";
    //public static final String RECOVERY_STATE_FILE_TF = "/mnt/external_sd/Recovery_state";
    public static String RECOVERY_STATE_FILE_TF = "/mnt/external_sd/Recovery_state";

    private void writeRecoveryState(String content) {
        FileOutputStream fos = null;
        File file;
        file = new File(RECOVERY_STATE_FILE);
        if (file != null && !file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.flush();
            fos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException ie) {
            ie.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String PCBA, ORDER, CPU, DDR, LCD, TP, Gsensor, WIFIaccount, WIFIpassword, BT, Ethernet, hava4G, GPS, RTC, UART, GPIO,
            Firmware, CHARGE, ERJI, FLASH, UDISK, CALL, KEY, ledTest, motorTest,printTest,mKeyContent, mWifiPrefixResult, mBTPrefixResult, mSNPrefixResult;
    public String SoundRecoder, KeyResponse, ChargeRespone, ErjiResponse, LCDResult, Exmemory, GsensorResult, MICResult,
            BTResult, EthernetResult, GPSResult, RTCResult, UARTResult, GPIOResult, KernelVersion, DDRResult,
            TPResult, RightMusicResponse, LeftMusicResponse, RamResult, FlashResult, WIFIResult, BLResult, CALLResult,
            motorResult, ledResult,camResult,printResult, mDistanceResult, mWifiPrefix, mBTPrefix, mSNPrefix, mLedLightResponse;
    public int BL, MIC, SPK, DDRint, WIFIlevel,CAMNUM;
    public int TestMode = 1 ; //默认测试模式
    public boolean GsensorShow, WIFIShow, BTShow, EthernetShow, hava4GShow, GPSShow, RTCShow, UARTShow, GPIOShow, MICShow,
            KEYShow, CHARGEShow, ERJIShow, UDISKShow, FLASHShow, CALLShow, BLShow, TPShow, mLCDShow, SPKShow, DDRShow, motorShow,
            ledShow,CAMShow,PRINTShow, mDistanceShow, mWifiPrefixShow, mBTPrefixShow, mSNPrefixShow, mLedLightShow;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;

    private boolean isNumeric(String str) {
        Pattern pattern = Pattern.compile("[0-9]*");
        Matcher isNum = pattern.matcher(str);
        if (!isNum.matches()) {
            return false;
        }
        return true;
    }

    private boolean isNegativeNumeric(String str) {
        Pattern pattern = Pattern.compile("[-]?[0-9]*");
        Matcher isNum = pattern.matcher(str);
        if (!isNum.matches()) {
            return false;
        }
        return true;
    }

    public boolean readState(String fileName) {
        File file = new File(fileName);
        if (file == null || !file.exists()) {
            return false;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String tempString = null;
            while ((tempString = reader.readLine()) != null) {
                if (tempString == "" || tempString.startsWith("#")) {
                    Log.i(TAG, "忽略---continue-----tempString=" + tempString);
                    continue;
                }
                Log.d(TAG, "readState--测试项目=" + tempString);
                String[] temp = tempString.split("=");
                if (temp[0].equals("KEY_NUM")) {
                    mKeyContent = temp[1];
                    if (!TextUtils.isEmpty(mKeyContent)) {
                        String[] keys = mKeyContent.split(KEY_SPLIT);
                        mKeyTest.clear();
                        if (keys != null) {
                            for (int i = 0; i < keys.length; i++) {
                                Log.d(TAG, "readState--keys[i]=" + keys[i]);
                                if (!TextUtils.isEmpty(keys[i]) && mAllTestKey.contains(keys[i])) {
                                    mKeyTest.add(keys[i]);
                                }
                            }
                        }
                    }
                } else if (temp[0].equals("AgingTime")) {
                    if (mSharedPreferences == null) {
                        mSharedPreferences = getSharedPreferences(VideoPlayActivity.KEY_DATA_PLAY, Context.MODE_PRIVATE);
                        mEditor = mSharedPreferences.edit();
                    }
                    if (!TextUtils.isEmpty(temp[1]) && isNumeric(temp[1])) {
                        mEditor.putLong(VideoPlayActivity.KEY_PLAY_TIME_SET, Long.parseLong(temp[1]));
                    } else {
                        mEditor.putLong(VideoPlayActivity.KEY_PLAY_TIME_SET, 0L);
                    }
                    mEditor.commit();
                } else if (temp[0].equals("PCBA")) {
                    PCBA = temp[1];
                }  else if (temp[0].equals("PCBName")) {
                    PCBName = temp[1];
                } else if (temp[0].equals("ORDER")) {
                    ORDER = temp[1];
                } else if (temp[0].equals("TestMode")) {
                    if (!TextUtils.isEmpty(temp[1]) && isNumeric(temp[1])) {
                        TestMode = Integer.valueOf(temp[1]);
                    } else {
                        TestMode = 1;
                    }
                } else if (temp[0].equals("CPU")) {
                    CPU = temp[1];
                } else if (temp[0].equals("FLASH")) {
                    FLASH = temp[1];
                    FLASHShow = true;
                } else if (temp[0].equals("DDR")) {
                    DDR = temp[1];
                    if (DDR.equals("1GB")) {
                        DDRint = 1024;
                    } else if (DDR.equals("2GB")) {
                        DDRint = 2048;
                    } else if (DDR.equals("4GB")) {
                        DDRint = 4096;
                    }
                    DDRShow = true;
                } else if (temp[0].equals("LCD")) {
                    LCD = temp[1];
                    mLCDShow = true;
                } else if (temp[0].equals("TP")) {
                    TP = temp[1];
                    TPShow = true;
                } else if (temp[0].equals("BL")) {
                    if (!TextUtils.isEmpty(temp[1]) && isNumeric(temp[1])) {
                        BL = Integer.valueOf(temp[1]);
                    } else {
                        BL = 30;
                    }
                    BLShow = true;
                } else if (temp[0].equals("Gsensor")) {
                    Gsensor = temp[1];
                    GsensorShow = true;
                } else if (temp[0].equals("GSENSOR_XYZ")) {
                    String content = temp[1];
                    if (!TextUtils.isEmpty(content)) {
                        String[] keys = content.split(KEY_SPLIT);
                        mGsensorTestKey.clear();
                        if (keys != null) {
                            for (int i = 0; i < keys.length; i++) {
                                Log.d(TAG, "readState--Gsensor[i]=" + keys[i]);
                                if (!TextUtils.isEmpty(keys[i]) && mAllGsensorTestKey.contains(keys[i])) {
                                    if (Z_TEST.equals(keys[i])) {
                                        mGsensorTestKey.add(ZP_TEST);
                                        mGsensorTestKey.add(ZN_TEST);
                                    } else if (X_TEST.equals(keys[i])) {
                                        mGsensorTestKey.add(XP_TEST);
                                        mGsensorTestKey.add(XN_TEST);
                                    } else if (Y_TEST.equals(keys[i])) {
                                        mGsensorTestKey.add(YP_TEST);
                                        mGsensorTestKey.add(YN_TEST);
                                    } else {
                                        mGsensorTestKey.add(keys[i]);
                                    }
                                }
                            }
                        }
                    }
                } else if (temp[0].equals("MIC")) {
                    if (!TextUtils.isEmpty(temp[1]) && isNumeric(temp[1])) {
                        MIC = Integer.valueOf(temp[1]);
                    } else {
                        MIC = 1;
                    }
                    MICShow = true;
                } else if (temp[0].equals("SPK")) {
                    if (!TextUtils.isEmpty(temp[1]) && isNumeric(temp[1])) {
                        SPK = Integer.valueOf(temp[1]);
                    } else {
                        SPK = 1;
                    }
                    SPKShow = true;

                } else if (temp[0].equals("WIFIaccount")) {
                    WIFIaccount = temp[1];
                    WIFIShow = true;
                } else if (temp[0].equals("WIFIpassword")) {
                    WIFIpassword = temp[1];
                } else if (temp[0].equals("WIFIlevel")) {  //WIFIlevel
                    if (!TextUtils.isEmpty(temp[1]) && isNegativeNumeric(temp[1])) {
                        WIFIlevel = Integer.parseInt(temp[1]);
                    } else {
                        WIFIlevel = -80;
                    }
                } else if (temp[0].equals("WifiPrefix")) {
                    mWifiPrefix = temp[1];
                    mWifiPrefixShow = true;
                } else if (temp[0].equals("BTPrefix")) {
                    mBTPrefix = temp[1];
                    mBTPrefixShow = true;
                } else if (temp[0].equals("SNPrefix")) {
                    mSNPrefix = temp[1];
                    mSNPrefixShow = true;
                } else if (temp[0].equals("BT")) {
                    BT = temp[1];
                    BTShow = true;
                } else if (temp[0].equals("Ethernet")) {
                    Ethernet = temp[1];
                    EthernetShow = true;
                } else if (temp[0].equals("4G")) {
                    hava4G = temp[1];
                    hava4GShow = true;
                } else if (temp[0].equals("GPS")) {
                    GPS = temp[1];
                    GPSShow = true;
                } else if (temp[0].equals("RTC")) {
                    RTC = temp[1];
                    RTCShow = true;
                } else if (temp[0].equals("UART")) {
                    UART = temp[1];
                    UARTShow = true;
                } else if (temp[0].equals("GPIO1")) {
                    ledTest = temp[1];
                    ledShow = true;
                } else if (temp[0].equals("GPIO2")) {
                    motorTest = temp[1];
                    motorShow = true;
                }else if (temp[0].equals("GPIO3")) {   //打印机 printpower
                    printTest = temp[1];
                    PRINTShow = true;
                } else if (temp[0].equals("Firmware")) {
                    Firmware = temp[1];
                } else if (temp[0].equals("KEY")) {
                    KEY = temp[1];
                    KEYShow = true;
                } else if (temp[0].equals("CHARGE")) {
                    CHARGE = temp[1];
                    CHARGEShow = true;
                } else if (temp[0].equals("ERJI")) {
                    ERJI = temp[1];
                    ERJIShow = true;
                } else if (temp[0].equals("UDISK")) {
                    UDISK = temp[1];
                    UDISKShow = true;
                } else if (temp[0].equals("CALL")) {
                    CALL = temp[1];
                    CALLShow = true;
                }else if (temp[0].equals("CAM")) {
                    if (!TextUtils.isEmpty(temp[1]) && isNumeric(temp[1])) {
                        CAMNUM = Integer.parseInt(temp[1]);
                    } else {
                        CAMNUM = 1;
                    }
                    CAMShow = true;
                }else if (temp[0].equals("DISTS")) {
                    mDistanceShow = true;
                }else if (temp[0].equals("LED")) {
                    mLedLightShow = true;
                }

            }
            reader.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
        return false;
    }

    private String formatStateContent() {
        long currentTime = System.currentTimeMillis();
        String timeNow = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentTime);

        StringBuilder sb = new StringBuilder();
        if (PCBA != null) {
            sb.append("PCBA:").append(PCBA).append("\n");
        }
        if (ORDER != null) {
            sb.append("ORDER:").append(ORDER).append("\n");
        }
        if (CPU != null) {
            sb.append("CPU:").append(CPU).append("\n");
        }
        if (Firmware != null) {
            sb.append("Firmware:").append(Firmware).append("\n");
        }
        if (MICShow) {
            sb.append("SoundRecoder:").append(SoundRecoder).append("\n");
        }
        if (KEYShow) {
            sb.append("Key:").append(KeyResponse).append("\n");
        }
        if (CHARGEShow) {
            sb.append("Charge:").append(ChargeRespone).append("\n");
        }
        if (mLedLightShow) {
            sb.append("LED Light:").append(mLedLightResponse).append("\n");
        }
        if (ERJIShow) {
            sb.append("Erji:").append(ErjiResponse).append("\n");
        }
        if (EthernetShow) {
            sb.append("Ethernet:").append(EthernetResult).append("\n");
        }
        if (RTCShow) {
            sb.append("RTC:").append(RTCResult).append("\n");
        }
        if (DDRShow) {
            sb.append("DDR:").append(DDRResult).append("\n");
        }
        if (FLASHShow) {
            sb.append("FLASH:").append(FlashResult).append("\n");
        }
        if (Exmemory != null) {
            sb.append("External memory:").append(Exmemory).append("\n");
        }
        if (GsensorShow) {
            sb.append("Gsensor:").append(GsensorResult).append("\n");
        }
        if (mDistanceShow) {
            sb.append("DistanceSensor:").append(mDistanceResult).append("\n");
        }
        if (WIFIShow) {
            sb.append("WIFI:").append(WIFIResult).append("\n");
        }
        if (BTShow) {
            sb.append("BT:").append(BTResult).append("\n");
        }
        if (CAMShow) {
            sb.append("相机:").append(camResult).append("\n");
        }
        if (GPSShow) {
            sb.append("GPS:").append(GPSResult).append("\n");
        }
        if (hava4GShow) {
            sb.append("4G:").append(hava4G).append("\n");
        }
        if (CALLShow) {
            sb.append("拨号:").append(CALLResult).append("\n");
        }
        if (BLShow) {
            sb.append("亮度:").append(BLResult).append("\n");
        }
        if (mLCDShow) {
            sb.append("LCD:").append(LCDResult).append("\n");
        }
        if (TPShow) {
            sb.append("TP:").append(TPResult).append("\n");
        }
        if (SPKShow) {
            if (SPK == 1) {
                sb.append("左声道:").append(LeftMusicResponse).append("\n");
            } else if (SPK == 2) {
                sb.append("右声道:").append(RightMusicResponse).append("\n");
                sb.append("左声道:").append(LeftMusicResponse).append("\n");
            }
        }
        if (UARTShow) {
            sb.append("UART:").append(UARTResult).append("\n");
        }
        if (ledShow) {
            sb.append("GPIO1:").append(ledResult).append("\n");
        }
        if (motorShow) {
            sb.append("GPIO2:").append(motorResult).append("\n");
        }
        if (PRINTShow) {
            sb.append("GPIO3:").append(printResult).append("\n");
        }
        if (mSNPrefixShow) {
            sb.append("SN序列号:").append(mSNPrefixResult).append("\n");
        }
        if (mBTPrefixShow) {
            sb.append("BT-MAC:").append(mBTPrefixResult).append("\n");
        }
        if (mWifiPrefixShow) {
            sb.append("WIFI-MAC:").append(mWifiPrefixResult).append("\n");
        }
        sb.append("KernelVersion:").append(KernelVersion).append("\n");
        sb.append("本次测试结束时间:").append(timeNow).append("\n");
        Log.d("PCBresult", "result" + sb.toString());
        return sb.toString();
    }

    private void writeresultState(String content) {
        Log.d(TAG, "writeresultState saveResult");
        if (System.currentTimeMillis() - mLastSaved < mSaveInternal) {
            return;
        }
        mLastSaved = System.currentTimeMillis();
        FileOutputStream fos = null;
        File file;
        //  file = new File("/sdcard/testresult.txt");  //system/vendor
//        file = new File("/oem/pcb/testresult.txt");
        file = new File(ZedielTools.this.getFilesDir(),"/"+ZedielTools.PCBName+"testresult.txt");
//      file = new File("/system/vendor/pcb/testresult.txt");
        if (file != null && !file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Log.e("TAG", "Exception-00--e=" + e);
                e.printStackTrace();
            }
        }

        try {
            fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.flush();
            fos.close();

            Log.d(TAG, "writeresultState saveResult success");
        } catch (Exception e) {
            Log.e(TAG, "Exception-11--e=" + e);
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Exception--222-e=" + e);
                    e.printStackTrace();
                }
            }
        }
    }

    public static String getsdcardpatch(Context context) {
        StorageManager storageManager = (StorageManager) context.getSystemService("storage");
        try {
            Class storeManagerClazz = Class.forName("android.os.storage.StorageManager");
            Method getVolumesMethod = storeManagerClazz.getMethod("getVolumes");
            List<?> volumeInfos = (List<?>) getVolumesMethod.invoke(storageManager);
            Class volumeInfoClazz = Class.forName("android.os.storage.VolumeInfo");
            Method getFsUuidMethod = volumeInfoClazz.getMethod("getFsUuid");
            Field pathField = volumeInfoClazz.getDeclaredField("path");
            String pathString = "";
            if (volumeInfos != null) {
                for (Object volumeInfo : volumeInfos) {
                    String uuid = (String) getFsUuidMethod.invoke(volumeInfo);
                    if (uuid != null) {
                        pathString = (String) pathField.get(volumeInfo);
                    }
                }
                return pathString;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "unknow";
    }

    public int getVersionCode(Context mContext) {
        int versionCode = 0;
        try {
            //获取软件版本号，对应AndroidManifest.xml下android:versionCode
            versionCode = mContext.getPackageManager().
                    getPackageInfo(mContext.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionCode;
    }

    /**
     * 改写节点
     */
    public static boolean setNodeString(String path, String value) {
        Log.d(TAG, "改写节点path=" + path + "  value= " + value);
        try {
            BufferedWriter bufWriter = null;
            bufWriter = new BufferedWriter(new FileWriter(path));
            bufWriter.write(value);  // 写入数据
            bufWriter.close();
            Log.d(TAG, "改写节点成功!");
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "改写节点失败!");
            return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult---requestCode=" + requestCode + "    resultCode=" + resultCode);
        if (requestCode == 0 && resultCode == 1) {
            btn_tp.setText("TP测试通过");
            TPResult = "Pass";
            btn_tp.setTextColor(Color.GREEN);
        } else if (requestCode == 0 && resultCode == 0) {
            btn_tp.setText("TP测试失败");
            TPResult = "Fail";
            btn_tp.setTextColor(Color.RED);
        } else if (requestCode == 2 && resultCode == 1) {
            btn_lcd.setText("LCD测试通过");
            LCDResult = "Pass";
            btn_lcd.setTextColor(Color.GREEN);
        } else if (requestCode == 2 && resultCode == 0) {
            btn_lcd.setText("LCD测试失败");
            LCDResult = "Fail";
            btn_lcd.setTextColor(Color.RED);
        } else if (requestCode == 4 && resultCode == 1) {
            tv_cam.setText("相机测试通过");
            camResult = "pass";
            tv_cam.setTextColor(Color.GREEN);
        } else if (requestCode == 4 && resultCode == 0) {
            tv_cam.setText("相机测试失败");
            camResult = "fail";
            tv_cam.setTextColor(Color.RED);
        }
        saveResult();
    }

    public boolean hasGPSDevice(Context context) {
        final LocationManager mgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (mgr == null) {
            return false;
        }
        final List<String> providers = mgr.getAllProviders();
        if (providers == null) {
            return false;
        }
        Log.d(TAG, "providers=" + providers.toString());
        return providers.contains(LocationManager.GPS_PROVIDER);
    }

    private String getWifiMac() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        return macAddress;
    }

}
