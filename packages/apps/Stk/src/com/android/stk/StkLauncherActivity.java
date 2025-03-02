/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.stk;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.android.internal.telephony.cat.Item;
import com.android.internal.telephony.cat.Menu;
import com.android.internal.telephony.cat.CatLog;
import com.android.internal.telephony.PhoneConstants;

import android.telephony.TelephonyManager;

import java.util.ArrayList;
/* SPRD: airplane mode change install/uninstall  support @{*/
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.PhoneConstants;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
/* @}*/
/* SPRD: SIM standby changed install/uninstall  support @{*/
import android.provider.Settings;
import android.provider.Settings.System;
/* @} */
import com.sprd.stk.StkSetupMenuPluginsHelper;
import com.sprd.stk.StkTelcelOperatorPluginsHelper;

/**
 * Launcher class. Serve as the app's MAIN activity, send an intent to the
 * StkAppService and finish.
 *
 */
public class StkLauncherActivity extends ListActivity {
    private TextView mTitleTextView = null;
    private ImageView mTitleIconView = null;
    private static final String className = new Object(){}.getClass().getEnclosingClass().getName();
    private static final String LOG_TAG = className.substring(className.lastIndexOf('.') + 1);
    private ArrayList<Item> mStkMenuList = null;
    private int mSingleSimId = -1;
    private Context mContext = null;
    private TelephonyManager mTm = null;
    private Bitmap mBitMap = null;
    private boolean mAcceptUsersInput = true;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        CatLog.d(LOG_TAG, "onCreate+");
        mContext = getBaseContext();
        mTm = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.stk_menu_list);
        mTitleTextView = (TextView) findViewById(R.id.title_text);
        mTitleIconView = (ImageView) findViewById(R.id.title_icon);
        mTitleTextView.setText(R.string.app_name);
        mBitMap = BitmapFactory.decodeResource(getResources(),
                R.drawable.ic_launcher_sim_toolkit);
        /* SPRD: airplane mode change install/uninstall  support @{*/
        IntentFilter intentFilterForInstall = new IntentFilter();
        intentFilterForInstall.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilterForInstall.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        /* SPRD: SIM standby changed install/uninstall  support @{*/
        intentFilterForInstall.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        /* @} */
        mContext.registerReceiver(mBroadcastReceiverForInstall, intentFilterForInstall);
        /* @}*/
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        if (!mAcceptUsersInput) {
            CatLog.d(LOG_TAG, "mAcceptUsersInput:false");
            return;
        }
        int simCount = TelephonyManager.from(mContext).getSimCount();
        Item item = getSelectedItem(position);
        if (item == null) {
            CatLog.d(LOG_TAG, "Item is null");
            return;
        }
        CatLog.d(LOG_TAG, "launch stk menu id: " + item.id);
        if (item.id >= PhoneConstants.SIM_ID_1 && item.id < simCount) {
            mAcceptUsersInput = false;
            launchSTKMainMenu(item.id);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        CatLog.d(LOG_TAG, "mAcceptUsersInput: " + mAcceptUsersInput);
        if (!mAcceptUsersInput) {
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                CatLog.d(LOG_TAG, "KEYCODE_BACK.");
                mAcceptUsersInput = false;
                finish();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onResume() {
        super.onResume();
        CatLog.d(LOG_TAG, "onResume");
        mAcceptUsersInput = true;
        int itemSize = addStkMenuListItems();
        if (itemSize == 0) {
            CatLog.d(LOG_TAG, "item size = 0 so finish.");
            finish();
        } else if (itemSize == 1) {
            launchSTKMainMenu(mSingleSimId);
            finish();
        } else {
            CatLog.d(LOG_TAG, "resume to show multiple stk list.");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        CatLog.d(LOG_TAG, "onPause");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        CatLog.d(LOG_TAG, "onDestroy");
        /* SPRD: airplane mode change install/uninstall  support @{*/
        if(mBroadcastReceiverForInstall != null){
            unregisterReceiver(mBroadcastReceiverForInstall);
        }
        /* @}*/

    }

    private Item getSelectedItem(int position) {
        Item item = null;
        if (mStkMenuList != null) {
            try {
                item = mStkMenuList.get(position);
            } catch (IndexOutOfBoundsException e) {
                if (StkApp.DBG) {
                    CatLog.d(LOG_TAG, "IOOBE Invalid menu");
                }
            } catch (NullPointerException e) {
                if (StkApp.DBG) {
                    CatLog.d(LOG_TAG, "NPE Invalid menu");
                }
            }
        }
        return item;
    }

    private int addStkMenuListItems() {
        String appName = mContext.getResources().getString(R.string.app_name);
        String stkItemName = null;
        int simCount = TelephonyManager.from(mContext).getSimCount();
        mStkMenuList = new ArrayList<Item>();

        CatLog.d(LOG_TAG, "simCount: " + simCount);
        for (int i = 0; i < simCount; i++) {
            //Check if the card is inserted.
            if (mTm.hasIccCard(i)) {
                CatLog.d(LOG_TAG, "SIM " + i + " add to menu.");
                mSingleSimId = i;
                /* SPRD: Set STK list item name from setup menu. @{ */
                //CarrierConfigManager mCCM = (CarrierConfigManager) getSystemService(Context.CARRIER_CONFIG_SERVICE);
                //PersistableBundle pBundle = mCCM.getConfigForPhoneId(i);
                //if (pBundle != null && pBundle.getBoolean(CarrierConfigManager.KEY_FEATURE_STK_NAME_FROMSETUPMENU_BOOL)) {
                if (StkSetupMenuPluginsHelper.getInstance(mContext).isShowSetupMenuTitle()) {
                    if (StkAppService.getInstance() != null && StkAppService.getInstance().getMenu(i) != null) {
                        stkItemName = StkAppService.getInstance().getMenu(i).title;
                        CatLog.d(LOG_TAG, "stkItemName:" + stkItemName + " add to menu.");
                    }
                } else {
                    stkItemName = new StringBuilder(appName).append(" ")
                            .append(Integer.toString(i + 1)).toString();
                }
                /* @} */
                Item item = new Item(i + 1, stkItemName, mBitMap);
                item.id = i;
                mStkMenuList.add(item);
            } else {
                CatLog.d(LOG_TAG, "SIM " + i + " is not inserted.");
            }
        }
        if (mStkMenuList != null && mStkMenuList.size() > 0) {
            if (mStkMenuList.size() > 1) {
                StkMenuAdapter adapter = new StkMenuAdapter(this,
                        mStkMenuList, false);
                // Bind menu list to the new adapter.
                this.setListAdapter(adapter);
            }
            return mStkMenuList.size();
        } else {
            CatLog.d(LOG_TAG, "No stk menu item add.");
            return 0;
        }
    }
    private void launchSTKMainMenu(int slodId) {
        Bundle args = new Bundle();
        CatLog.d(LOG_TAG, "launchSTKMainMenu.");
        args.putInt(StkAppService.OPCODE, StkAppService.OP_LAUNCH_APP);
        args.putInt(StkAppService.SLOT_ID
                , PhoneConstants.SIM_ID_1 + slodId);
        startService(new Intent(this, StkAppService.class)
                .putExtras(args));
    }
    /* SPRD: airplane mode change install/uninstall  support @{*/
    private final BroadcastReceiver mBroadcastReceiverForInstall = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null ) return;

            CatLog.d(LOG_TAG, "onReceive, action=" + action );
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                CatLog.d(LOG_TAG, "ACTION_AIRPLANE_MODE_CHANGED rcvd finish");
                finish();
            }else if(action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)){
                TelephonyManager tm = TelephonyManager.from(mContext);
                int slotID = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);
                if(tm.getSimState(slotID) == TelephonyManager.SIM_STATE_ABSENT){
                    CatLog.d(LOG_TAG, "ACTION_SIM_STATE_CHANGED rcvd, finish");
                    finish();
                }

            /* SPRD: SIM standby changed install/uninstall  support @{*/
            }else if(action.equals(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED)){
                int simCount = TelephonyManager.from(mContext).getSimCount();
                for (int i = 0; i < simCount; i++) {
                    boolean isStandby = Settings.Global.getInt(
                            context.getContentResolver(), Settings.Global.SIM_STANDBY + i, 1) == 1;
                    CatLog.d(LOG_TAG, "SIM " + i + " isStandby=" + isStandby);
                    if (!isStandby) {
                        CatLog.d(LOG_TAG, "ACTION_SERVICE_STATE_CHANGED rcvd finish");
                        finish();
                    }
                }
            /* @} */
            }
        }
    };
    /* @}*/
}
