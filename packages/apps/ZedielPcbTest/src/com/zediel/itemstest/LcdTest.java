package com.zediel.itemstest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.os.Message;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.zediel.pcbtest.R;
import java.util.Timer;
import java.util.TimerTask;


public class LcdTest extends Activity implements View.OnClickListener, View.OnLongClickListener{

    private static final int LCD_COLOR[] = {R.color.red, R.color.green, R.color.blue, R.color.white, R.color.black,
        R.drawable.colorone, R.drawable.colortwo, R.drawable.colorthree};

    private ImageView mLcdIv;
    private Timer mTimer = null;

    private static int index = 0;
    private static final int MSG_LCD_TEST = 0x81;
    private TimerTask mTask = null;
    private static boolean mTimerFlag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.lcd_test);
        Intent intent2 = new Intent();
        intent2.setAction("action.zed.hidenav");
		//If the status bar is displayed, you must add a broadcast to hide the status bar
        //this.sendBroadcast(intent2);

        mLcdIv = (ImageView)findViewById(R.id.iv_lcd);
        mLcdIv.setOnClickListener(this);
        mLcdIv.setOnLongClickListener(this);

    }

    private void testLcd(){
        if(++index >= LCD_COLOR.length){
            index = 0;

			showDialog();
        }
        mLcdIv.setBackgroundResource(LCD_COLOR[index]);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //startTimer();
        startTestLcd();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTestLcd();
        if(mTimer != null){
            mTimer.cancel();
            mTimer = null;
        }

    }



    private void stopTimer(){
        if(mTimer != null){
            mTimer.cancel();
            mTimer = null;
        }
        mTimerFlag = false;
    }

    private void startTimer(){

        if(mTimer == null){
            mTimer = new Timer();
            mTask = new TimerTask() {
                @Override
                public void run() {
                    sendTestCmd(MSG_LCD_TEST);
                    //testLcd();
                }
            };

        }
        mTimer.schedule(mTask, 1000, 5000);
        mTimerFlag = true;
    }


    private void startTestLcd(){
        mTimerFlag = true;
        index = 0;
        sendTestCmd(MSG_LCD_TEST, 100);
    }

    private void stopTestLcd(){
        mTimerFlag = false;
        removeTestCmd(MSG_LCD_TEST);
    }

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case MSG_LCD_TEST:
                    testLcd();
                    //sendTestCmd(MSG_LCD_TEST, 3000);
                    break;
                default:
                    break;
            }
        }
    };

    private void sendTestCmd(int what, int delay){
        Message msg = Message.obtain();
        msg.what = what;
        mHandler.sendMessageDelayed(msg, delay);
    }

    private void sendTestCmd(int what){
        Message msg = Message.obtain();
        msg.what = what;
        mHandler.sendMessageDelayed(msg, 50);
    }

    private void removeTestCmd(int what){
        //Message msg = Message.obtain();
        //msg.what = what;
        mHandler.removeMessages(what);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.iv_lcd:
                sendTestCmd(MSG_LCD_TEST, 100);
//                if(mTimerFlag){
//                    stopTestLcd();
//                    //stopTimer();
//                }else{
//                    //startTimer();
//                    startTestLcd();
//                }
            default:
                break;

        }
    }


    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent();
        intent.setAction("action.zed.shownav");
        this.sendBroadcast(intent);
        stopTestLcd();
        finish();
        return false;
    }

    @Override
    public void onBackPressed() {
        showDialog();
    //    super.onBackPressed();
    }


    public void showDialog(){
        AlertDialog alertDialog2 = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_LIGHT)
                .setTitle("提示")
                .setMessage("LCD是否测试通过")
                .setIcon(R.drawable.ic_launcher)
                .setPositiveButton("是", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(LcdTest.this, "LCD测试通过", Toast.LENGTH_SHORT).show();
                        Intent y = new Intent();
                        setResult(1, y);

                        finish();
                    }
                })

                .setNegativeButton("否", new DialogInterface.OnClickListener() {//添加取消
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(LcdTest.this, "LCD测试失败", Toast.LENGTH_SHORT).show();
                        Intent y = new Intent();
                        setResult(0, y);
                        finish();
                    }
                })
                .create();
        alertDialog2.show();

    }
    @Override
    protected void onDestroy() {
        Intent intent = new Intent();
        intent.setAction("action.zed.shownav");
        this.sendBroadcast(intent);
        super.onDestroy();
    }
}