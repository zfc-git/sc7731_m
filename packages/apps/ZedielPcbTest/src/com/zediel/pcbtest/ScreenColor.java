package com.zediel.pcbtest;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class ScreenColor extends Activity implements View.OnClickListener {

    int mIndex = 0, mCount = 0;
    private Handler mUiHandler = new Handler();;
    private Runnable mRunnable;
    private static final int[] COLOR_ARRAY = new int[] {
            Color.WHITE, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW
    };
    private static final int TIMES = 4;
    private TextView lcdcolorTv;
	private Button colorpassBtn,colorfailBtn;
    @Override
    protected void onCreate( Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
		 final View decorView = getWindow().getDecorView();
        final int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int i) {
                if ((i & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.setSystemUiVisibility(uiOptions);
                } 
            }
        });
				
        setContentView(R.layout.activity_screencolor);
        lcdcolorTv=(TextView)findViewById(R.id.lcdtest_tv);
		colorpassBtn =(Button)findViewById(R.id.btn_colortest_pass);
        colorfailBtn= (Button)findViewById(R.id.btn_colortest_fail);
        colorpassBtn.setOnClickListener(this);
        colorfailBtn.setOnClickListener(this);
        lcdcolorTv.setOnClickListener(this);
		 
    }

    

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

       @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.lcdtest_tv:
                lcdcolorTv.setBackgroundColor(COLOR_ARRAY[mIndex]);
                mIndex++;
                mCount++;
                if(mIndex>4){
                    mIndex=0;
                    mCount=0;
                }
                break;
            case R.id.btn_colortest_pass:
                Intent intentpass = new Intent();
                intentpass.putExtra("result", "pass");
                setResult(RESULT_OK, intentpass);
                finish();
                break;
            case R.id.btn_colortest_fail:
                Intent intentfail = new Intent();
                intentfail.putExtra("result", "fail");
                setResult(RESULT_OK, intentfail);
                finish();
                break;
        }
    }

}
