/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.alarmclock.cts;

import android.alarmclock.common.Utils;
import android.alarmclock.common.Utils.TestcaseType;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.provider.AlarmClock;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AlarmClockTestBase extends ActivityInstrumentationTestCase2<TestStartActivity> {
    static final String TAG = "AlarmClockTestBase";
    protected static final int TIMEOUT_MS = 20 * 1000;

    private Context mContext;
    private String mTestResult;
    private CountDownLatch mLatch;
    private ActivityDoneReceiver mActivityDoneReceiver = null;
    private TestStartActivity mActivity;
    private TestcaseType mTestCaseType;

    public AlarmClockTestBase() {
        super(TestStartActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
    }

    @Override
    protected void tearDown() throws Exception {
        mContext.unregisterReceiver(mActivityDoneReceiver);
        super.tearDown();
    }

    private void registerBroadcastReceiver(TestcaseType testCaseType) throws Exception {
        mTestCaseType = testCaseType;
        mLatch = new CountDownLatch(1);
        if (mActivityDoneReceiver != null) {
            mContext.unregisterReceiver(mActivityDoneReceiver);
        }
        mActivityDoneReceiver = new ActivityDoneReceiver();
        mContext.registerReceiver(mActivityDoneReceiver,
                new IntentFilter(Utils.BROADCAST_INTENT + testCaseType.toString()));
    }

    private boolean isIntentAupported(TestcaseType testCaseType) {
        Intent intent;
        switch (testCaseType) {
          case DISMISS_ALARM:
              intent = new Intent(AlarmClock.ACTION_DISMISS_ALARM);
              break;

          case SET_ALARM:
          case SET_ALARM_FOR_DISMISSAL:
              intent = new Intent(AlarmClock.ACTION_SET_ALARM);
              break;

          case SNOOZE_ALARM:
              intent = new Intent(AlarmClock.ACTION_SNOOZE_ALARM);
              break;

          default:
              // shouldn't happen
              return false;
        }
        final PackageManager manager = mContext.getPackageManager();
        assertNotNull(manager);
        if (manager.resolveActivity(intent, 0) == null) {
            Log.i(TAG, "No Voice Activity found for the intent: " + intent.getAction());
            return false;
        }
        return true;
    }

    protected String runTest(TestcaseType testCaseType) throws Exception {
        Log.i(TAG, "Begin Testing: " + testCaseType);
        // Make sure the corresponding intent is supported by the platform, before testing.
        if (!isIntentAupported(testCaseType)) return Utils.COMPLETION_RESULT;

        if (!startTestActivity(testCaseType)) {
            fail("test activity start failed for testcase = " + testCaseType);
            return "";
        }

        registerBroadcastReceiver(testCaseType);
        mActivity.startTest(testCaseType.toString());
        if (!mLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Failed to receive broadcast in " + TIMEOUT_MS + "msec");
            return "";
        }
        return mTestResult;
    }

    private boolean startTestActivity(TestcaseType testCaseType) {
        Log.i(TAG, "Starting test activity for test: " + testCaseType);
        Intent intent = new Intent();
        intent.setAction("android.intent.action.TEST_START_ACTIVITY_" + testCaseType.toString());
        intent.setComponent(new ComponentName(getInstrumentation().getContext(),
                TestStartActivity.class));
        setActivityIntent(intent);
        mActivity = getActivity();
        return mActivity != null;
    }

    class ActivityDoneReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(
                    Utils.BROADCAST_INTENT + AlarmClockTestBase.this.mTestCaseType.toString())) {
                AlarmClockTestBase.this.mTestResult = intent.getStringExtra(Utils.TEST_RESULT);
                Log.i(TAG, "received_broadcast for testcase_type = " +
                        Utils.BROADCAST_INTENT + AlarmClockTestBase.this.mTestCaseType +
                        ", test_result = " + AlarmClockTestBase.this.mTestResult);
                mLatch.countDown();
            }
        }
    }
}
