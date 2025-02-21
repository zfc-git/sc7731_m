package com.cghs.stresstest.test;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Created by flyz on 2017/11/27 0027.
 */

public class FlashTest extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent1 = new Intent();
        intent1.setClassName("com.android.sprd.flashtest", "com.android.sprd.flashtest.MainActivity");
        intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent1);
    }


}
