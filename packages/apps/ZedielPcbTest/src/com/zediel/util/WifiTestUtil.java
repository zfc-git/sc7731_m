package com.zediel.util;

import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.util.Log;
import android.net.wifi.WifiConfiguration;
import java.util.ArrayList;

public class WifiTestUtil {
    private static String TAG = "WifiTestUtil";

    private volatile boolean mStopSearch;
    private static final int DELAY_TIME = 15000;
    private static String mWifiSSID = "";
    private WifiManager mWifiManager = null;
    private Context mContext = null;
    private int mLastCount = 0;
    private WifiStateChangeReceiver mWifiStateChangeReceiver = null;
    private WifiScanReceiver mWifiScanReceiver = null;
    private StartScanThread mStartScanThread = null;

    public WifiTestUtil(WifiManager wifiManager) {
        mWifiManager = wifiManager;
		deleteSavedWifi(mWifiManager);
    }

    public static boolean checkWifi(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
    }

    private void registerAllReceiver() {
        mWifiStateChangeReceiver = new WifiStateChangeReceiver();
        String filterFlag1 = WifiManager.WIFI_STATE_CHANGED_ACTION;
        IntentFilter filter1 = new IntentFilter(filterFlag1);
        filter1.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mContext.registerReceiver(mWifiStateChangeReceiver, filter1);

        mWifiScanReceiver = new WifiScanReceiver();
        String filterFlag2 = WifiManager.SCAN_RESULTS_AVAILABLE_ACTION;
        IntentFilter filter2 = new IntentFilter(filterFlag2);
        mContext.registerReceiver(mWifiScanReceiver, filter2);
    }

    private void unregisterAllReceiver() {
        // release wifi enabled receiver
        if (mWifiStateChangeReceiver != null) {
            mContext.unregisterReceiver(mWifiStateChangeReceiver);
            mWifiStateChangeReceiver = null;
        }

        // release wifi scan receiver
        if (mWifiScanReceiver != null) {
            mContext.unregisterReceiver(mWifiScanReceiver);
            mWifiScanReceiver = null;
        }
        if (mStartScanThread != null) {
            mStartScanThread.interrupt();
            mStartScanThread = null;
        }
        ThreadUtils.getInstance().shutdown();

        mContext = null;
    }

    public WifiManager getWifiManager() {
        return mWifiManager;
    }

    public void startTest(Context context) {
        if (mWifiManager == null) {
            return;
        }
        mContext = context;
        registerAllReceiver();
        if (mWifiManager.isWifiEnabled()) {
            wifiStateChange(WifiManager.WIFI_STATE_ENABLED);
            wifiStartDiscovery();
        } else {
            wifiStateChange(WifiManager.WIFI_STATE_DISABLED);
            mWifiManager.setWifiEnabled(true);
        }
    }

    public void stopTest() {
        if (mWifiManager == null) {
            return;
        }
        // mWifiManager.cancelDiscovery();
        mWifiManager.setWifiEnabled(false);
        unregisterAllReceiver();
    }

    private void disableConnetedWifi(String ssid) {
        int wifiState = mWifiManager.getWifiState();
        if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
            WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
            String accout = "\"" + ssid + "\"";
            if (wifiInfo != null && ssid != null && !accout.equals(wifiInfo.getSSID())) {
                Log.w(TAG, " ssid =" + accout + " wifiInfo.getSSID()=" + wifiInfo.getSSID());
                mWifiManager.disableNetwork(wifiInfo.getNetworkId());
                mWifiManager.disconnect();
                mWifiManager.removeNetwork(wifiInfo.getNetworkId());
                mWifiManager.saveConfiguration();
            }
        }
    }
	
	// Delete all saved wifi connect
	public void deleteSavedWifi(WifiManager wifiManager){
		List<Integer> networkIds = getAllSavedNetworkIds(wifiManager);
		
		for (Integer networkId : networkIds) {
			// Network is saved, forget the network
			wifiManager.forget(networkId, new WifiManager.ActionListener() {
				@Override
				public void onSuccess() {
				// Forget network success
				}

				@Override
				public void onFailure(int reason) {
				// Forget network failed
				}
			});
		}
	}
	
	// Settings-WLAN-Saved networks,Gets all saved network ids
	private List<Integer> getAllSavedNetworkIds(WifiManager wifiManager) {
		List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
		List<Integer> networkIds = new ArrayList<>();

		if (configuredNetworks != null) {
			for (WifiConfiguration config : configuredNetworks) {
				networkIds.add(config.networkId);
			}
		}

		return networkIds;
	}

    public boolean isConnected(String ssid) {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            return false;
        }
        switch (wifiInfo.getSupplicantState()) {
            case AUTHENTICATING:
            case ASSOCIATING:
            case ASSOCIATED:
            case FOUR_WAY_HANDSHAKE:
            case GROUP_HANDSHAKE:
            case COMPLETED:
                return wifiInfo.getSSID().replace("\"", "").equals(ssid);
            default:
                return false;
        }
    }

    public boolean isConnetedWifi(String ssid) {
        boolean result = false;
        int wifiState = mWifiManager.getWifiState();
        if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
            WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
            String accout = "\"" + ssid + "\"";
            if (wifiInfo != null && ssid != null && !accout.equals(wifiInfo.getSSID())) {
                result = true;
            }
        }
        return result;
    }

	 public void startConnectWifiTest(final String WIFIaccount, final String WIFIpassword) {
        // mWifiManager.cancelDiscovery();
		if(!("".equals(WIFIaccount)||null==WIFIaccount)){
            ThreadUtils.getInstance().execInBg(new Runnable() {

                @Override
                public void run() {
                    Log.w(TAG, "============startConnectWifiTest===============");
                    if (isConnected(WIFIaccount)) {
                        return;
                    }
                    disableConnetedWifi(WIFIaccount);
                    mWifiSSID = WIFIaccount;
                    mWifiManager.enableNetwork(mWifiManager.addNetwork(setWifiParamsPassword(WIFIaccount, WIFIpassword)), true);
                }
            });
		}
    }

    private void wifiStartDiscovery() {
        if (mWifiManager != null) {
            mStartScanThread = new StartScanThread();
            mStartScanThread.start();
            Log.w(TAG, "============startDiscovery===============");
        }
    }

    public void wifiStateChange(int newState) {
        // for override
    }

    public void wifiDeviceListChange(List<ScanResult> wifiDeviceList) {
        // for override
    }

    public void wifiDiscoveryFinished() {
        // for override
    }

    public void wifiConnected(String ssid) {
        // for override
    }

    private class WifiStateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (NetworkInfo.State.CONNECTED.equals(info.getState())) {
                    WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
                    //获取当前wifi名称
                    Log.d(TAG, "connected net " + wifiInfo.getSSID());
                    if (wifiInfo.getSSID() != null && wifiInfo.getSSID().length() > 1) {
                        String ssid = wifiInfo.getSSID().substring(1, wifiInfo.getSSID().length() - 1);
                        if (mWifiSSID.equals(ssid)) {
                            mStopSearch = true;
                            Log.d(TAG, "connected mStopSearch = true");
                        }
                        wifiConnected(ssid);
                    }
                }
            }
            int newState = mWifiManager.getWifiState();
            Log.d(TAG, "newState =" + newState);
            switch (newState) {
                case WifiManager.WIFI_STATE_ENABLED:
                    wifiStartDiscovery();
                    break;
                case WifiManager.WIFI_STATE_DISABLED:
                case WifiManager.WIFI_STATE_DISABLING:
                case WifiManager.WIFI_STATE_UNKNOWN:
                case WifiManager.WIFI_STATE_ENABLING:
                default:
                    // do nothing
                    break;
            }

            wifiStateChange(newState);
        }
    }

     /**
     * 连接wifi.
     *
     * @param SSID     ssid
     * @param Password Password
     * @return apConfig
     */
    private WifiConfiguration setWifiParamsPassword(String SSID, String Password) {
        WifiConfiguration apConfig = new WifiConfiguration();
        apConfig.SSID = "\"" + SSID + "\"";
        apConfig.preSharedKey = "\"" + Password + "\"";
        //不广播其SSID的网络
        apConfig.hiddenSSID = true;
        apConfig.status = WifiConfiguration.Status.ENABLED;
        //公认的IEEE 802.11验证算法。
        apConfig.allowedAuthAlgorithms.clear();
        apConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        //公认的的公共组密码
        apConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        apConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        //公认的密钥管理方案
        apConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        //密码为WPA。
        apConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        apConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        //公认的安全协议。
        apConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        return apConfig;
    }

    public List<ScanResult> getScanResults() {
        if (mWifiManager != null) {
            return mWifiManager.getScanResults();
        }
        return new ArrayList<>();
    }

    public void stopSearch() {
        if (!mStopSearch) {
            mStopSearch = true;
        }
    }


    private class WifiScanReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                List<ScanResult> wifiScanResultList = mWifiManager.getScanResults();

                if (wifiScanResultList != null
                        && wifiScanResultList.size() > 0 /*!= mLastCount*/) {
                    wifiDeviceListChange(wifiScanResultList);

                    mLastCount = wifiScanResultList.size();
                }
            }
        }
    }

    class StartScanThread extends Thread {
        @Override
        public void run() {
            synchronized (StartScanThread.class) {
                Log.d(TAG, "StartScanThread run");
                while (!mStopSearch) {
                    Log.d(TAG, "StartScanThread mStopSearch =" + mStopSearch);
                    if (!isInterrupted()) {
                        try {
                            // wait until other actions finish.
                            mWifiManager.startScan();
                            Thread.sleep(DELAY_TIME);
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                }
            }
        }
    }
}
