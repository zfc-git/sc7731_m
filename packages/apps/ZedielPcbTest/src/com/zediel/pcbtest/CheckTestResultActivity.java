package com.zediel.pcbtest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CheckTestResultActivity extends Activity implements View.OnClickListener {

    private TextView tv_result;
    private TextView profileResult;
    private Button mExit;
    private Button mGoLeft;
    private Button mGoRight;
    private SharedPreferences mSharedPreferences;
    private final String KEY_DATA_PLAY = "data_play";
    private final String KEY_PLAY_TOTAL_TIME = "play_total_time";
    private boolean mPass = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_checktest);

        mSharedPreferences = getSharedPreferences(KEY_DATA_PLAY, Context.MODE_PRIVATE);
        tv_result = (TextView) findViewById(R.id.tv_result);
        profileResult = (TextView) findViewById(R.id.test_final_result);
        mExit = (Button) findViewById(R.id.exit);
        mGoLeft = (Button) findViewById(R.id.goto_left);
        mGoRight = (Button) findViewById(R.id.goto_right);
        mGoLeft.setOnClickListener(this);
        mGoRight.setOnClickListener(this);
        mExit.setOnClickListener(this);
        profileResult.setTextSize(23);
        tv_result.setTextSize(20);
//        tv_result.setText(ReadTxtFile("/oem/pcb/testresult.txt")+"\n"+ReadTxtFile("/oem/pcb/agingTestTime.txt"));
        long playTotalTime = mSharedPreferences.getLong(KEY_PLAY_TOTAL_TIME, 0);
        if (playTotalTime == 0) {
            tv_result.setText(Html.fromHtml(ReadTxtFile("/data/data/" + getPackageName() + "/files/"+ZedielTools.PCBName+"testresult.txt")
                    + getString(R.string.player_status_todo)));
        } else {
            tv_result.setText(Html.fromHtml(ReadTxtFile("/data/data/" + getPackageName() + "/files/"+ZedielTools.PCBName+"testresult.txt")
                    + ReadTxtFile("/data/data/" + getPackageName() + "/files/"+ZedielTools.PCBName+"agingTestTime.txt")));
        }
        profileResult.setText(mPass ? getText(R.string.test_pass) : getText(R.string.test_fail));
        profileResult.setTextColor(mPass ? getColor(R.color.green) : getColor(R.color.red));

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private String ReadTxtFile(String strFilePath) {
        String path = strFilePath;
        String content = ""; //文件内容字符串
        //打开文件
        File file = new File(path);
        //如果path是传递过来的参数，可以做一个非目录的判断
        if (file.isDirectory() || !file.exists()) {
            Log.d("TestFile", "The File doesn't not exist.");
        } else {
            try {
                InputStream instream = new FileInputStream(file);
                if (instream != null) {
                    InputStreamReader inputreader = new InputStreamReader(instream);
                    BufferedReader buffreader = new BufferedReader(inputreader);
                    String line;
                    //分行读取
                    while ((line = buffreader.readLine()) != null) {
                        if (line.contains("Fail") || line.contains("fail") || line.contains("失败") || line.contains("未完成")) {
                            line = "<font color=\"#FF0000\">" + line + "</font>";
                            if (mPass) {
                                mPass = false;
                            }
                        } else if (line.contains("Pass") || line.contains("pass") || line.contains("通过") || line.contains("完成")) {
                            line = "<font color=\"#00FF00\">" + line + "</font>";
                        } else if (line.contains("null") || line.contains("Null")) {
                            line = "<font color=\"#0000FF\">" + line + "</font>";
                            if (mPass) {
                                mPass = false;
                            }
                        } else {
                            line = "<font color=\"#00FF00\">" + line + "</font>";
                        }
                        content += "   " + line + "<br />" + "<br />";
                    }
                    instream.close();
                }
            } catch (FileNotFoundException e) {
                Log.d("TestFile", "The File doesn't not exist.");
            } catch (IOException e) {
                Log.d("TestFile", e.getMessage());
            }
        }
        if (strFilePath.contains("testresult") && TextUtils.isEmpty(content)) {
            if (mPass) {
                mPass = false;
            }
        }
        return content;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.exit) {
            onBackPressed();
        } else if (view.getId() == R.id.goto_left) {
            startTestMode();
        } else if (view.getId() == R.id.goto_right) {
            startAgingTest();
        }
    }

    public void startAgingTest() {
        onBackPressed();
        Intent it = new Intent(CheckTestResultActivity.this, VideoPlayActivity.class);
        startActivity(it);

    }

    public void startTestMode() {
        onBackPressed();
        Intent it = new Intent(CheckTestResultActivity.this, ZedielTools.class);
        it.putExtra("force", "1");
        startActivity(it);
    }
}