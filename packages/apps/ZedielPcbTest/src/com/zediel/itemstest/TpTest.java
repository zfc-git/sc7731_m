package com.zediel.itemstest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.zediel.pcbtest.R;
import com.zediel.tpui.SimpleView6;
import android.provider.Settings;
import android.content.pm.ActivityInfo;
import android.util.DisplayMetrics;
import android.util.Log;
import android.graphics.Point;
import android.widget.FrameLayout;

public class TpTest extends Activity {

    public static  int LCM_W = 1280;  //800*1280    1920*1080
    public static  int LCM_H = 800;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
 //       Settings.System.putInt(this.getContentResolver(),
 //              Settings.System.POINTER_LOCATION, 1);
        //TouchDraw draw = new TouchDraw(this);
        //setContentView(draw);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Point outSize = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(outSize);
        LCM_W = outSize.x;
        LCM_H = outSize.y;
        Log.w("zgl", "RealSize--x = " + LCM_W + ",y = " + LCM_H);

        Intent intent2 = new Intent();
        intent2.setAction("action.zed.hidenav");
		//If the status bar is displayed, you must add a broadcast to hide the status bar
        //this.sendBroadcast(intent2);

        SimpleView6 draw = new SimpleView6(this);
        setContentView(draw);

        Button  btn = new Button(this);
       //       LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(350, 100);
        int width = (int) ((LCM_W / 2) - draw.getTileWidth() *2);
        int height = (int) ((LCM_H / 2) - draw.getTileHeight() *2);
		FrameLayout.LayoutParams params2 = new FrameLayout.LayoutParams(width , height / 2);
//        btn.setLayoutParams(layoutParams);
        btn.setText("若测试失败，点击退出TP测试");
		btn.setTextColor(0xff0000ff);
		btn.setTextSize(15);
		btn.getBackground().setAlpha(200);
		params2.leftMargin = (int) (draw.getTileWidth() * 5 / 4);
        params2.topMargin = (int) (draw.getTileHeight() + height / 2);

        addContentView(btn,params2);
 		btn.setOnClickListener(new StartButtonListener());
//            @Override
//            public void onClick(View v) {
//				 Intent y = new Intent();
 //                       setResult(0, y);
 //               finish();
 //           }
//        });

        //getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
    }


class StartButtonListener implements android.view.View.OnClickListener {  
  @Override 
  public void onClick(View v) {   
     Intent y = new Intent();
     setResult(0, y);
     finish();
  }
 }

    @Override
    public void onBackPressed() {
        showDialog();
    //    super.onBackPressed();
    }

    public void showDialog(){
        AlertDialog alertDialog2 = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_LIGHT)
                .setTitle("提示")
                .setMessage("TP是否测试通过")
                .setIcon(R.drawable.ic_launcher)
                .setPositiveButton("是", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(TpTest.this, "TP测试通过", Toast.LENGTH_SHORT).show();
                        Intent y = new Intent();
                        setResult(1, y);
                        finish();
                    }
                })

                .setNegativeButton("否", new DialogInterface.OnClickListener() {//添加取消
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(TpTest.this, "TP测试失败", Toast.LENGTH_SHORT).show();
                        Intent y = new Intent();
                        setResult(0, y);
                        finish();
                    }
                })
                .create();
        alertDialog2.show();
    }

  @Override
    protected void onPause() {
        super.onPause();
        Intent intent = new Intent();
        intent.setAction("action.zed.shownav");
        this.sendBroadcast(intent);
    }

    @Override
    protected void onDestroy() {
        Intent intent = new Intent();
        intent.setAction("action.zed.shownav");
        this.sendBroadcast(intent);
//		Settings.System.putInt(this.getContentResolver(),
 //               Settings.System.POINTER_LOCATION, 0);
        super.onDestroy();
    }

}