/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.qs.tiles;

import com.android.systemui.R;
import android.util.Log;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import android.view.KeyEvent;
import android.os.SystemClock;
import android.view.KeyCharacterMap;
import android.view.InputDevice;
import android.hardware.input.InputManager;
import android.os.Handler;
import com.android.internal.logging.MetricsLogger;



/** Quick settings tile: ScreenShot **/
public class ScreenShotTile extends QSTile<QSTile.BooleanState> {

    private final Icon mEnable =
            ResourceIcon.get(R.drawable.ic_screenshot);
    private final Icon mDisable =
            ResourceIcon.get(R.drawable.ic_screenshot);
    private QSTile.Host mHost;
	private final KeyguardMonitor mKeyguard;

	private Handler mHandler = new Handler();

	private Runnable postScreenShot = new Runnable() {
        @Override
        public void run() {
            takeScreenShot();
        }

	};

    public ScreenShotTile(Host host) {
        super(host);
		mHost = host;
		mKeyguard = host.getKeyguardMonitor();
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
    }

    @Override
    protected void handleClick() {
        final boolean wasEnabled = (Boolean) mState.value;

		MetricsLogger.action(mContext, getMetricsCategory(), !wasEnabled);
		if(mKeyguard.isShowing())
			//add this fun because collapsePanels not work in keyguard
			mHost.closeExpandedPanels();
		else
			mHost.collapsePanels();
        mHandler.postDelayed(postScreenShot,1000);
		
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {

        state.visible = true;
        /* @} */
        state.value = true;
        if (state.value) {
            state.icon = mEnable;
            state.label = mContext.getString(R.string.quick_settings_screenshot_label);
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_screenshot_on);
        } else {
            state.icon = mDisable;
            state.label = mContext.getString(R.string.quick_settings_screenshot_label);
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_screenshot_off);
        }
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_screenshot_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_screenshot_off);
        }
    }

	@Override
    public int getMetricsCategory() {
        return MetricsLogger.QS_SCREENSHOT;
    }


	private void takeScreenShot(){
        final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
				KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SYSRQ, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                0,InputDevice.SOURCE_KEYBOARD);
        InputManager.getInstance().injectInputEvent(ev,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);

	}
	
}
