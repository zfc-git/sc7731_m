package com.sprd.dialer.settings;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.android.dialer.R;

public class CallRecordingSettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.call_recording_settings_ex);
    }
}
