
package com.sprd.engineermode.debuglog;

import android.app.Activity;

import android.content.Context;

import android.content.Intent;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import com.sprd.engineermode.R;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import android.content.BroadcastReceiver;
import android.os.SystemProperties;

public class ModemAssertInfoActivity extends SwitchBaseActivity {
    private static final String TAG = "ModemAssertInfoActivity";

    private static List<String> mTimeArray = new ArrayList<String>();
    private static List<String> mInfoArray = new ArrayList<String>();

    private SwitchMachineAdapter mAdapter;
    private static final String MODEM_ASSERT = "modem_assert";
    private static final String MODEM_STAT = "modem_stat";
    private static final String MODEM_INFO = "modem_info";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModemAssertPref = getSharedPreferences(MODEM_ASSERT_PREF_NAME, Context.MODE_PRIVATE);
        mModemAssertCount = mModemAssertPref.getLong(INFO_COUNT, 0);
        mEditor = mModemAssertPref.edit();
        if (mTimeArray.size() > 0) {
            mTimeArray.clear();
            mInfoArray.clear();
        }
        for (int i = 1; i <= mModemAssertCount; i++) {
            String key = PREF_INFO_NUM + Integer.toString(i);
            String keyTime = key + PREF_INFO_TIME;
            String keyInfo = key + PREF_MODEM_INFO;
            Log.d(TAG, "i = " + i + " keyTime = " + keyTime + " keyInfo = " + keyInfo);
            Log.d(TAG, "mModemAssertPref.getString(keyTime, null) = " + mModemAssertPref.getString(keyTime, null));
            Log.d(TAG, "mModemAssertPref.getString(keyMode, null) = " + mModemAssertPref.getString(keyInfo, null));
            mTimeArray.add(mModemAssertPref.getString(keyTime, null));
            mInfoArray.add(mModemAssertPref.getString(keyInfo, null));
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "mModemAssertCount = " + mModemAssertCount);

        if (mAdapter == null) {
            Log.d(TAG, "mAdapter == null");
            mAdapter = new SwitchMachineAdapter(this, mTimeArray, mInfoArray, MODE_MODEM_ASSERT);
            mListView.setAdapter(mAdapter);
            mListView.setOnScrollListener(new OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                        // mAdapter.setScrollingBlean(true);
                        // mAdapter.notifyDataSetChanged();
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem,
                        int visibleItemCount, int totalItemCount) {
                }
            });
        }
        mAdapter.notifyDataSetChanged();
        mListView.setSelection(0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.dump_info:
                Log.i(TAG, "dump_info");
                checkSDCard();
                if (mModemAssertCount == 0) {
                    Toast.makeText(ModemAssertInfoActivity.this, R.string.no_data_toast, Toast.LENGTH_SHORT)
                            .show();
                } else if (!mIsMounted) {
                    Toast.makeText(ModemAssertInfoActivity.this, R.string.no_sd_toast, Toast.LENGTH_SHORT)
                            .show();
                } else {
                    saveToSd(MODEM_ASSERT_PATH, MODEM_ASSERT_NAME);
                }
                return true;
            case R.id.clear_info:
                Log.i(TAG, "click clear_info");
                if (mModemAssertCount == 0) {
                    Toast.makeText(ModemAssertInfoActivity.this, R.string.no_data_toast, Toast.LENGTH_SHORT)
                            .show();
                    return false;
                }
                doReset();
                Toast.makeText(ModemAssertInfoActivity.this, R.string.clear_success_toast, Toast.LENGTH_SHORT).show();
                return true;
            default:
                Log.i(TAG, "default");
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doReset() {
        mModemAssertCount = 0;
        mTimeArray.clear();
        mInfoArray.clear();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        mEditor.clear();
        mEditor.apply();
        invalidateOptionsMenu();
    }

    public static class ModemAssertReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG,"modem assert happen");
            String modemAsserStat = intent.getStringExtra(MODEM_STAT);
            if (MODEM_ASSERT.equals(modemAsserStat)) {
                mModemAssertPref = context.getSharedPreferences(MODEM_ASSERT_PREF_NAME,
                        Context.MODE_PRIVATE);

                mModemAssertCount = mModemAssertPref.getLong(INFO_COUNT, 0);

                mModemAssertCount++;
                mEditor = mModemAssertPref.edit();

                SimpleDateFormat sDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss");
                String date = sDateFormat.format(new java.util.Date());
                String modemAsserInfo = intent.getStringExtra(MODEM_INFO);
                Log.d(TAG, "date = " + date + " modemAsserInfo = " + modemAsserInfo
                        + " mModemAssertCount = "+ mModemAssertCount);
                mEditor.putLong(INFO_COUNT, mModemAssertCount);

                String key = PREF_INFO_NUM + Long.toString(mModemAssertCount);
                String keyTime = key + PREF_INFO_TIME;
                String keyInfo = key + PREF_MODEM_INFO;

                mEditor.putString(keyTime, date);
                mEditor.putString(keyInfo, modemAsserInfo);
                mEditor.apply();
                if (SystemProperties.getBoolean("persist.sys.modemassertdump",false)) {
                    context.sendBroadcast(new Intent("com.sprd.engineermode.MODEM_ASSERT_DUMP"));
                }
            }

        }
    }

    @Override
    public void finish() {
        super.finish();
    }
}
