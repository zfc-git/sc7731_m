/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.settings.fuelgauge;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.BatteryStats;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.os.BatterySipper;
import com.android.internal.os.BatterySipper.DrainType;
import com.android.internal.os.PowerProfile;
import com.android.settings.HelpUtils;
import com.android.settings.R;
import com.android.settings.Settings.HighPowerApplicationsActivity;
import com.android.settings.SettingsActivity;
import com.android.settings.applications.ManageApplications;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Displays a list of apps and subsystems that consume power, ordered by how much power was
 * consumed since the last time it was unplugged.
 */
public class PowerUsageSummary extends PowerUsageBase {

    private static final boolean DEBUG = false;

    private static final boolean USE_FAKE_DATA = false;

    static final String TAG = "PowerUsageSummary";

    private static final String KEY_APP_LIST = "app_list";
    private static final String KEY_BATTERY_HISTORY = "battery_history";
    //SPRD:Bug475755  Add battery level percent feature
    private static final String KEY_BATTERY_PERCENTAGE = "battery_percentage_on";

    private static final int MENU_STATS_TYPE = Menu.FIRST;
    private static final int MENU_BATTERY_SAVER = Menu.FIRST + 2;
    private static final int MENU_HIGH_POWER_APPS = Menu.FIRST + 3;
    private static final int MENU_HELP = Menu.FIRST + 4;
    /* SPRD:Bug475755  Add battery level percent feature @{ */
    private static final String BATTERY_PERCENTAGE_ENABLED = "battery_percentage_enabled";
    private static final String LEVEL_SHOW_CHANGED = "com.sprd.battery.percentage";
    private static final int NOT_SHOW_BATTERY_PERCENTAGE = 0;
    private static final int SHOW_BATTERY_PERCENTAGE = 1;
    private static final int BATTERY_PERCENTAG_ORDER = -3;
    /* @} */

    private BatteryHistoryPreference mHistPref;
    private PreferenceGroup mAppListGroup;
    //SPRD:Bug475755  Add battery level percent feature
    private CheckBoxPreference mBatteryPercentagePref;

    private int mStatsType = BatteryStats.STATS_SINCE_CHARGED;

    private static final int MIN_POWER_THRESHOLD_MILLI_AMP = 5;
    private static final int MAX_ITEMS_TO_LIST = USE_FAKE_DATA ? 30 : 10;
    private static final int MIN_AVERAGE_POWER_THRESHOLD_MILLI_AMP = 10;
    private static final int SECONDS_IN_HOUR = 60 * 60;
    private Preference mHeartBeatSyncSettings;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.power_usage_summary);
        /* SPRD: add by Bug 494158, remove battery level percent feature @{ */
        //SPRD:Bug475755  Add battery level percent feature
        //mBatteryPercentagePref = (CheckBoxPreference) findPreference(KEY_BATTERY_PERCENTAGE);
        /* @} */
        mHistPref = (BatteryHistoryPreference) findPreference(KEY_BATTERY_HISTORY);
        mAppListGroup = (PreferenceGroup) findPreference(KEY_APP_LIST);
        mHeartBeatSyncSettings = (Preference) mAppListGroup.findPreference("heartbeat_sync");
        com.sprd.settings.HeartBeatSyncSettingsHelper.prepare(getActivity());
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.FUELGAUGE_POWER_USAGE_SUMMARY;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStats();
    }

    @Override
    public void onPause() {
        BatteryEntry.stopRequestQueue();
        mHandler.removeMessages(BatteryEntry.MSG_UPDATE_NAME_ICON);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (getActivity().isChangingConfigurations()) {
            BatteryEntry.clearUidCache();
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        /* SPRD: add by Bug 494158, remove battery level percent feature @{ */
        /* SPRD:Bug475755 Add battery level percent feature @{ */
        /*if (mBatteryPercentagePref == preference) {
            Settings.System.putIntForUser(getActivity().getContentResolver(), BATTERY_PERCENTAGE_ENABLED,
                    mBatteryPercentagePref.isChecked() ? SHOW_BATTERY_PERCENTAGE : NOT_SHOW_BATTERY_PERCENTAGE,
                    UserHandle.USER_OWNER);
            Intent levelShowChanged = new Intent(LEVEL_SHOW_CHANGED);
            getActivity().sendBroadcastAsUser(levelShowChanged, UserHandle.ALL,
                    android.Manifest.permission.INTERACT_ACROSS_USERS);
            return true;
        }*/
        /* @} */
        /* @} */
        if (!(preference instanceof PowerGaugePreference)) {
            return false;
        }
        PowerGaugePreference pgp = (PowerGaugePreference) preference;
        BatteryEntry entry = pgp.getInfo();
        PowerUsageDetail.startBatteryDetailPage((SettingsActivity) getActivity(), mStatsHelper,
                mStatsType, entry, true);
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (DEBUG) {
            menu.add(0, MENU_STATS_TYPE, 0, R.string.menu_stats_total)
                    .setIcon(com.android.internal.R.drawable.ic_menu_info_details)
                    .setAlphabeticShortcut('t');
        }

        MenuItem batterySaver = menu.add(0, MENU_BATTERY_SAVER, 0, R.string.battery_saver);
        batterySaver.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        menu.add(0, MENU_HIGH_POWER_APPS, 0, R.string.high_power_apps);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_battery;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final SettingsActivity sa = (SettingsActivity) getActivity();
        switch (item.getItemId()) {
            case MENU_STATS_TYPE:
                if (mStatsType == BatteryStats.STATS_SINCE_CHARGED) {
                    mStatsType = BatteryStats.STATS_SINCE_UNPLUGGED;
                } else {
                    mStatsType = BatteryStats.STATS_SINCE_CHARGED;
                }
                refreshStats();
                return true;
            case MENU_BATTERY_SAVER:
                sa.startPreferencePanel(BatterySaverSettings.class.getName(), null,
                        R.string.battery_saver, null, null, 0);
                return true;
            case MENU_HIGH_POWER_APPS:
                Bundle args = new Bundle();
                args.putString(ManageApplications.EXTRA_CLASSNAME,
                        HighPowerApplicationsActivity.class.getName());
                sa.startPreferencePanel(ManageApplications.class.getName(), args,
                        R.string.high_power_apps, null, null, 0);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void addNotAvailableMessage() {
        Preference notAvailable = new Preference(getActivity());
        notAvailable.setTitle(R.string.power_usage_not_available);
        mAppListGroup.addPreference(notAvailable);
    }

    private static boolean isSharedGid(int uid) {
        return UserHandle.getAppIdFromSharedAppGid(uid) > 0;
    }

    private static boolean isSystemUid(int uid) {
        return uid >= Process.SYSTEM_UID && uid < Process.FIRST_APPLICATION_UID;
    }

    /**
     * We want to coalesce some UIDs. For example, dex2oat runs under a shared gid that
     * exists for all users of the same app. We detect this case and merge the power use
     * for dex2oat to the device OWNER's use of the app.
     * @return A sorted list of apps using power.
     */
    private static List<BatterySipper> getCoalescedUsageList(final List<BatterySipper> sippers) {
        final SparseArray<BatterySipper> uidList = new SparseArray<>();

        final ArrayList<BatterySipper> results = new ArrayList<>();
        final int numSippers = sippers.size();
        for (int i = 0; i < numSippers; i++) {
            BatterySipper sipper = sippers.get(i);
            if (sipper.getUid() > 0) {
                int realUid = sipper.getUid();

                // Check if this UID is a shared GID. If so, we combine it with the OWNER's
                // actual app UID.
                if (isSharedGid(sipper.getUid())) {
                    realUid = UserHandle.getUid(UserHandle.USER_OWNER,
                            UserHandle.getAppIdFromSharedAppGid(sipper.getUid()));
                }

                // Check if this UID is a system UID (mediaserver, logd, nfc, drm, etc).
                if (isSystemUid(realUid)
                        && !"mediaserver".equals(sipper.packageWithHighestDrain)) {
                    // Use the system UID for all UIDs running in their own sandbox that
                    // are not apps. We exclude mediaserver because we already are expected to
                    // report that as a separate item.
                    realUid = Process.SYSTEM_UID;
                }

                if (realUid != sipper.getUid()) {
                    // Replace the BatterySipper with a new one with the real UID set.
                    BatterySipper newSipper = new BatterySipper(sipper.drainType,
                            new FakeUid(realUid), 0.0);
                    newSipper.add(sipper);
                    newSipper.packageWithHighestDrain = sipper.packageWithHighestDrain;
                    newSipper.mPackages = sipper.mPackages;
                    sipper = newSipper;
                }

                int index = uidList.indexOfKey(realUid);
                if (index < 0) {
                    // New entry.
                    uidList.put(realUid, sipper);
                } else {
                    // Combine BatterySippers if we already have one with this UID.
                    final BatterySipper existingSipper = uidList.valueAt(index);
                    existingSipper.add(sipper);
                    if (existingSipper.packageWithHighestDrain == null
                            && sipper.packageWithHighestDrain != null) {
                        existingSipper.packageWithHighestDrain = sipper.packageWithHighestDrain;
                    }

                    final int existingPackageLen = existingSipper.mPackages != null ?
                            existingSipper.mPackages.length : 0;
                    final int newPackageLen = sipper.mPackages != null ?
                            sipper.mPackages.length : 0;
                    if (newPackageLen > 0) {
                        String[] newPackages = new String[existingPackageLen + newPackageLen];
                        if (existingPackageLen > 0) {
                            System.arraycopy(existingSipper.mPackages, 0, newPackages, 0,
                                    existingPackageLen);
                        }
                        System.arraycopy(sipper.mPackages, 0, newPackages, existingPackageLen,
                                newPackageLen);
                        existingSipper.mPackages = newPackages;
                    }
                }
            } else {
                results.add(sipper);
            }
        }

        final int numUidSippers = uidList.size();
        for (int i = 0; i < numUidSippers; i++) {
            results.add(uidList.valueAt(i));
        }

        // The sort order must have changed, so re-sort based on total power use.
        Collections.sort(results, new Comparator<BatterySipper>() {
            @Override
            public int compare(BatterySipper a, BatterySipper b) {
                return Double.compare(b.totalPowerMah, a.totalPowerMah);
            }
        });
        return results;
    }

    protected void refreshStats() {
        super.refreshStats();
        updatePreference(mHistPref);
        mAppListGroup.removeAll();
        mAppListGroup.setOrderingAsAdded(false);
        if (com.sprd.settings.HeartBeatSyncSettingsHelper.getInstance().isAvailable()) {
            mAppListGroup.addPreference(mHeartBeatSyncSettings);
            mHeartBeatSyncSettings.setOrder(-4);
        }
        /* SPRD: add by Bug 494158, remove battery level percent feature @{ */
        /* SPRD:Bug475755 Add battery level percent feature @{ */
        //mBatteryPercentagePref.setOrder(BATTERY_PERCENTAG_ORDER);
        //mBatteryPercentagePref.setChecked(Settings.System.getIntForUser(getActivity().getContentResolver(),
        //        BATTERY_PERCENTAGE_ENABLED, NOT_SHOW_BATTERY_PERCENTAGE, UserHandle.USER_OWNER) == SHOW_BATTERY_PERCENTAGE);
        /* @} */
        /* @} */
        boolean addedSome = false;

        final PowerProfile powerProfile = mStatsHelper.getPowerProfile();
        final BatteryStats stats = mStatsHelper.getStats();
        final double averagePower = powerProfile.getAveragePower(PowerProfile.POWER_SCREEN_FULL);

        /* SPRD: add for bug493251, power usage info cannot be displayed @{ */
        long uSecTime = android.os.SystemClock.elapsedRealtime() * 1000;
        long uSecNow = mStatsHelper.getStats().computeBatteryRealtime(uSecTime, BatteryStats.STATS_SINCE_CHARGED);
        long screenTime = mStatsHelper.getStats().getScreenOnTime(uSecNow,BatteryStats.STATS_SINCE_CHARGED);

        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.colorControlNormal, value, true);
        int colorControl = getContext().getColor(value.resourceId);

        //if (averagePower >= MIN_AVERAGE_POWER_THRESHOLD_MILLI_AMP || USE_FAKE_DATA) {
        if ((screenTime > 0 && averagePower * screenTime >= MIN_AVERAGE_POWER_THRESHOLD_MILLI_AMP) || USE_FAKE_DATA){
        /* @} */
            final List<BatterySipper> usageList = getCoalescedUsageList(
                    USE_FAKE_DATA ? getFakeStats() : mStatsHelper.getUsageList());

            final int dischargeAmount = USE_FAKE_DATA ? 5000
                    : stats != null ? stats.getDischargeAmount(mStatsType) : 0;
            final int numSippers = usageList.size();
            for (int i = 0; i < numSippers; i++) {
                final BatterySipper sipper = usageList.get(i);
                if ((sipper.totalPowerMah * SECONDS_IN_HOUR) < MIN_POWER_THRESHOLD_MILLI_AMP) {
                    continue;
                }
                double totalPower = USE_FAKE_DATA ? 4000 : mStatsHelper.getTotalPower();
                final double percentOfTotal =
                        ((sipper.totalPowerMah / totalPower) * dischargeAmount);
                if (((int) (percentOfTotal + .5)) < 1) {
                    continue;
                }
                if (sipper.drainType == BatterySipper.DrainType.OVERCOUNTED) {
                    // Don't show over-counted unless it is at least 2/3 the size of
                    // the largest real entry, and its percent of total is more significant
                    if (sipper.totalPowerMah < ((mStatsHelper.getMaxRealPower()*2)/3)) {
                        continue;
                    }
                    if (percentOfTotal < 10) {
                        continue;
                    }
                    if ("user".equals(Build.TYPE)) {
                        continue;
                    }
                }
                if (sipper.drainType == BatterySipper.DrainType.UNACCOUNTED) {
                    // Don't show over-counted unless it is at least 1/2 the size of
                    // the largest real entry, and its percent of total is more significant
                    if (sipper.totalPowerMah < (mStatsHelper.getMaxRealPower()/2)) {
                        continue;
                    }
                    if (percentOfTotal < 5) {
                        continue;
                    }
                    if ("user".equals(Build.TYPE)) {
                        continue;
                    }
                }
                final UserHandle userHandle = new UserHandle(UserHandle.getUserId(sipper.getUid()));
                final BatteryEntry entry = new BatteryEntry(getActivity(), mHandler, mUm, sipper);
                final Drawable badgedIcon = mUm.getBadgedIconForUser(entry.getIcon(),
                        userHandle);
                final CharSequence contentDescription = mUm.getBadgedLabelForUser(entry.getLabel(),
                        userHandle);
                final PowerGaugePreference pref = new PowerGaugePreference(getActivity(),
                        badgedIcon, contentDescription, entry);

                final double percentOfMax = (sipper.totalPowerMah * 100)
                        / mStatsHelper.getMaxPower();
                sipper.percent = percentOfTotal;
                pref.setTitle(entry.getLabel());
                pref.setOrder(i + 1);
                pref.setPercent(percentOfMax, percentOfTotal);
                if (sipper.uidObj != null) {
                    pref.setKey(Integer.toString(sipper.uidObj.getUid()));
                }
                if ((sipper.drainType != DrainType.APP || sipper.uidObj.getUid() == 0)
                         && sipper.drainType != DrainType.USER) {
                    pref.setTint(colorControl);
                }
                addedSome = true;
                mAppListGroup.addPreference(pref);
                if (mAppListGroup.getPreferenceCount() > (MAX_ITEMS_TO_LIST + 1)) {
                    break;
                }
            }
        }
        if (!addedSome) {
            addNotAvailableMessage();
        }

        BatteryEntry.startRequestQueue();
    }

    private static List<BatterySipper> getFakeStats() {
        ArrayList<BatterySipper> stats = new ArrayList<>();
        float use = 5;
        for (DrainType type : DrainType.values()) {
            if (type == DrainType.APP) {
                continue;
            }
            stats.add(new BatterySipper(type, null, use));
            use += 5;
        }
        stats.add(new BatterySipper(DrainType.APP,
                new FakeUid(Process.FIRST_APPLICATION_UID), use));
        stats.add(new BatterySipper(DrainType.APP,
                new FakeUid(0), use));

        // Simulate dex2oat process.
        BatterySipper sipper = new BatterySipper(DrainType.APP,
                new FakeUid(UserHandle.getSharedAppGid(Process.FIRST_APPLICATION_UID)), 10.0f);
        sipper.packageWithHighestDrain = "dex2oat";
        stats.add(sipper);

        sipper = new BatterySipper(DrainType.APP,
                new FakeUid(UserHandle.getSharedAppGid(Process.FIRST_APPLICATION_UID + 1)), 10.0f);
        sipper.packageWithHighestDrain = "dex2oat";
        stats.add(sipper);

        sipper = new BatterySipper(DrainType.APP,
                new FakeUid(UserHandle.getSharedAppGid(Process.LOG_UID)), 9.0f);
        stats.add(sipper);

        return stats;
    }

    Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BatteryEntry.MSG_UPDATE_NAME_ICON:
                    BatteryEntry entry = (BatteryEntry) msg.obj;
                    PowerGaugePreference pgp =
                            (PowerGaugePreference) findPreference(
                                    Integer.toString(entry.sipper.uidObj.getUid()));
                    if (pgp != null) {
                        final int userId = UserHandle.getUserId(entry.sipper.getUid());
                        final UserHandle userHandle = new UserHandle(userId);
                        pgp.setIcon(mUm.getBadgedIconForUser(entry.getIcon(), userHandle));
                        pgp.setTitle(entry.name);
                    }
                    break;
                case BatteryEntry.MSG_REPORT_FULLY_DRAWN:
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.reportFullyDrawn();
                    }
                    break;
            }
            super.handleMessage(msg);
        }
    };
}
