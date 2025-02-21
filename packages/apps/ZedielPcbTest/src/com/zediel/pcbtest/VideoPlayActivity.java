package com.zediel.pcbtest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.zediel.util.LedLightUtil;
import com.zediel.util.ThreadUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class VideoPlayActivity extends Activity implements View.OnClickListener {
    private static String TAG = "VideoPlayActivity";
    public final String KEY_LINE_FEED = "\n";
    public final String KEY_LINE_TABLE = "\t";
    public static final String KEY_DATA_PLAY = "data_play";
    public static final String KEY_PLAY_TOTAL_TIME = "play_total_time";
    public static final String KEY_PLAY_TIME_SET = "play_time_set";
    public static final String KEY_PLAY_STATUS = "play_status";
    private long mPlayTotalOrig = 0, mPlayTotalTime = 0, mPlayTimeSet = 0;
    private int mPlayStatus = 0;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    private InputMethodManager mInputMethodManager;
    private MediaPlayer mMediaPlayer;
    private SurfaceView mVideoPlaySurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Chronometer timer;
    private SimpleDateFormat sdf;
    private Button mSettingBtn;
    private View mVideoContainer;
    private RelativeLayout mSettingContainer;
    private TextView mPlayerTotalTxt;
    private TextView mPlayerStatusTxt;
    private TextView mPlayerSettingTxt;
    private EditText mEditText;
    private Button mConfirmBtn;
    private Button mPlayClear;
    private Button mSaveExit;
    private Button mGoLeft;
    private Button mGoRight;
    private int mSaveInternal = 1 * 60 * 1000;
    private long mLastSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_video);
        Log.d(TAG, "<onCreate>---");

        mSharedPreferences = getSharedPreferences(KEY_DATA_PLAY, Context.MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
//        mPlayTotalTime = mSharedPreferences.getLong(KEY_PLAY_TOTAL_TIME, 0);
        mPlayTotalTime = 0;
        mPlayTotalOrig = mPlayTotalTime;
        mPlayTimeSet = mSharedPreferences.getLong(KEY_PLAY_TIME_SET, 0);
        mPlayStatus = mSharedPreferences.getInt(KEY_PLAY_STATUS, 0);
        mInputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        AudioManager mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0); //音量调到最大  

        mVideoPlaySurfaceView = (SurfaceView) findViewById(R.id.video_play_surface_view);
        initMediaPlayer();
        initSurfaceViewStateListener();

        sdf = new SimpleDateFormat("HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        timer = (Chronometer) findViewById(R.id.timer);
        timer.setBase(SystemClock.elapsedRealtime()); //计时器清零
        timer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer chronometer) {
                long time = SystemClock.elapsedRealtime() - chronometer.getBase();
                timer.setText(sdf.format(time));
                mPlayTotalTime = mPlayTotalOrig + time;
                boolean done = (mPlayTimeSet == 0 ? false : ((mPlayTotalTime / (1000 * 60)) >= mPlayTimeSet));
                mPlayStatus = done ? 1 : 0;
				
				if(done){
					LedLightUtil.isAgeingOK = true ;
				}

                mPlayerTotalTxt.setText(getString(R.string.player_total, (mPlayTotalTime / (1000 * 60))));
                mPlayerStatusTxt.setText(done ? getString(R.string.player_status_done) : getString(R.string.player_status_doing));
                mPlayerStatusTxt.setTextColor(done ? getColor(R.color.green) : getColor(R.color.red));
                if (System.currentTimeMillis() - mLastSave > mSaveInternal) {
                    mLastSave = System.currentTimeMillis();
                    save();
                }
            }
        });
        timer.start();

        mVideoContainer = (View) findViewById(R.id.click_container);
        mVideoContainer.setOnClickListener(this);

        mPlayerTotalTxt = (TextView) findViewById(R.id.player_total);
        mPlayerStatusTxt = (TextView) findViewById(R.id.player_status);
        mPlayerSettingTxt = (TextView) findViewById(R.id.player_setting);
        mPlayerTotalTxt.setText(getString(R.string.player_total, (mPlayTotalTime / (1000 * 60))));
        mPlayerStatusTxt.setText(mPlayTimeSet == 0 ? getString(R.string.player_status_doing) : (mPlayStatus == 1 ? getString(R.string.player_status_done) : getString(R.string.player_status_doing)));
        mPlayerSettingTxt.setText(getString(R.string.player_setting, mPlayTimeSet));

        mEditText = (EditText) findViewById(R.id.player_edit);
        mEditText.setText(String.valueOf(mPlayTimeSet));
        mSettingBtn = (Button) findViewById(R.id.setting);
        mSaveExit = (Button) findViewById(R.id.save_exit);
        mSaveExit.setOnClickListener(this);
        mConfirmBtn = (Button) findViewById(R.id.confirm);
        mConfirmBtn.setOnClickListener(this);
        mPlayClear = (Button) findViewById(R.id.player_clear);
        mPlayClear.setOnClickListener(this);
        mSettingContainer = (RelativeLayout) findViewById(R.id.setting_rl);
        mSettingBtn.setOnClickListener(this);
        mGoLeft = (Button) findViewById(R.id.goto_left);
        mGoRight = (Button) findViewById(R.id.goto_right);
        mGoLeft.setOnClickListener(this);
        mGoRight.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LedLightUtil.testWorkLed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LedLightUtil.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "<onNewIntent>---");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "<onStart>---");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "<onStop>---");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "<onDestroy>---");
        timer.stop();
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        ThreadUtils.getInstance().shutdown();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.d(TAG, "<onBackPressed>---");
        saveResult();
        finish();
    }

    private  void saveResult() {
        long diff = SystemClock.elapsedRealtime() - timer.getBase();
        mEditor.putLong(KEY_PLAY_TOTAL_TIME, mPlayTotalTime);
        mEditor.putInt(KEY_PLAY_STATUS, mPlayStatus);
        mEditor.commit();

        String time = getSecondToDayHourMinutes(diff);
        Log.d(TAG, "<onBackPressed>---diff=" + diff + "  time=" + time);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(getString(R.string.player_result));
        stringBuilder.append(mPlayStatus == 1 ? getString(R.string.player_save_done) : getString(R.string.player_save_doing));
        stringBuilder.append(KEY_LINE_TABLE);
        stringBuilder.append(getString(R.string.player_save_count));
        stringBuilder.append(time);
        stringBuilder.append(KEY_LINE_TABLE);
//        stringBuilder.append(getString(R.string.player_total, (mPlayTotalTime / (1000 * 60))));
//        stringBuilder.append(KEY_LINE_FEED);
        stringBuilder.append(getString(R.string.player_save_setting, mPlayTimeSet));
        stringBuilder.append(KEY_LINE_FEED);
        String timeNow = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(System.currentTimeMillis());
        stringBuilder.append(getString(R.string.player_finish_time, timeNow));
        stringBuilder.append(KEY_LINE_FEED);

        writeResultState(stringBuilder.toString());
    }

    private void initSurfaceViewStateListener() {
        mSurfaceHolder = mVideoPlaySurfaceView.getHolder();
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "<surfaceCreated>---");
                mMediaPlayer.setDisplay(holder); //给mMediaPlayer添加预览的SurfaceHolder
                setPlayVideo(); //添加播放视频的路径
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "<surfaceChanged>---");
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "<surfaceDestroyed>---");
                if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause(); //添加播放视频的路径
                }
            }
        });
    }

    private void initMediaPlayer() {
        mMediaPlayer = new MediaPlayer();
    }

    private void setPlayVideo() {
        try {
            AssetFileDescriptor afd = getResources().getAssets().openFd("demo.mp4");
            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            mMediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT); //缩放模式
            mMediaPlayer.setLooping(true); //设置循环播放
            mMediaPlayer.prepareAsync(); //异步准备
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { //准备完成回调
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 方法一 将毫秒数换算成x天x时x分x秒x毫秒
     * <p>
     * time 毫秒
     */
    public static String getSecondToDayHourMinutes(long ms) {
        int ss = 1000;
        int mi = ss * 60;
        int hh = mi * 60;
        int dd = hh * 24;
        long day = ms / dd;
        long hour = (ms - day * dd) / hh;
        long minute = (ms - day * dd - hour * hh) / mi;
        long second = (ms - day * dd - hour * hh - minute * mi) / ss;
        long milliSecond = ms - day * dd - hour * hh - minute * mi - second * ss;
        String strDay = day < 10 ? "0" + day : "" + day;
        String strHour = hour < 10 ? "0" + hour : "" + hour;
        String strMinute = minute < 10 ? "0" + minute : "" + minute;
        String strSecond = second < 10 ? "0" + second : "" + second;
        return strDay + "天" + strHour + ":" + strMinute + ":" + strSecond;
    }

    private void deleteResult() {
        File file = new File(getFilesDir(), "/"+ZedielTools.PCBName+"agingTestTime.txt");
        if (file != null && file.exists()) {
            Log.d(TAG, "deleteResult");
            file.delete();
        }
    }

    private void writeResultState(String content) {
        FileOutputStream fos = null;
        File file;
        //     file = new File("/oem/pcb/agingTestTime.txt");
        file = new File(getFilesDir(), "/"+ZedielTools.PCBName+"agingTestTime.txt");
        if (file != null && !file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Log.e("TAG", "Exception-00--e=" + e);
                e.printStackTrace();
            }
        }

        try {
            fos = new FileOutputStream(file); //追加写操作
            fos.write(content.getBytes());
            fos.flush();
            fos.close();
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

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.setting) {
            showOrHideSettingContainer(mSettingContainer.getVisibility() == View.GONE);
        } else if (view.getId() == R.id.click_container) {
            if (mSettingContainer.getVisibility() == View.VISIBLE) {
                showOrHideSettingContainer(false);
            }
        } else if (view.getId() == R.id.confirm) {
            String input = mEditText.getText().toString();
            if (!TextUtils.isEmpty(input)) {
                mPlayTimeSet = Long.parseLong(input);
                mPlayerSettingTxt.setText(getString(R.string.player_setting, mPlayTimeSet));
                mEditor.putLong(KEY_PLAY_TIME_SET, mPlayTimeSet);
                mEditor.commit();
            }
        } else if (view.getId() == R.id.save_exit) {
            onBackPressed();
        } else if (view.getId() == R.id.goto_left) {
            checkTestResult();
        } else if (view.getId() == R.id.goto_right) {
            startTestMode();
        } else if (view.getId() == R.id.player_clear) {
            mPlayTotalTime = 0;
            mPlayTotalOrig = 0;
            mPlayTimeSet = 0;
            mPlayStatus = 0;

            mEditor.putLong(KEY_PLAY_TOTAL_TIME, mPlayTotalTime);
            mEditor.putLong(KEY_PLAY_TIME_SET, mPlayTimeSet);
            mEditor.putInt(KEY_PLAY_STATUS, mPlayStatus);
            mEditor.commit();
            deleteResult();

            timer.setBase(SystemClock.elapsedRealtime());
            mPlayerSettingTxt.setText(getString(R.string.player_setting, mPlayTimeSet));
            mEditText.setText(String.valueOf(mPlayTimeSet));
        }
    }

    public void checkTestResult() {
        onBackPressed();
        Intent it = new Intent(VideoPlayActivity.this, CheckTestResultActivity.class);
        startActivity(it);
    }

    public void startTestMode() {
        onBackPressed();
        Intent it = new Intent(VideoPlayActivity.this, ZedielTools.class);
        it.putExtra("force", "1");
        startActivity(it);
    }

    private void showOrHideSettingContainer(boolean show) {
        mSettingContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            closeSoftKeyboard();
        }
    }

    private void closeSoftKeyboard() {
        mInputMethodManager.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
    }

    private void save() {
        ThreadUtils.getInstance().execInBg(new Runnable() {
            @Override
            public void run() {
                saveResult();
            }
        });
    }
}
