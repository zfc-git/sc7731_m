/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.bluetooth;

import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothManagerCallback;
import android.bluetooth.IBluetoothProfileServiceConnection;
import android.bluetooth.IBluetoothStateChangeCallback;

/**
 * System private API for talking with the Bluetooth service.
 *
 * {@hide}
 */
interface IBluetoothManager
{
    IBluetooth registerAdapter(in IBluetoothManagerCallback callback);
    void unregisterAdapter(in IBluetoothManagerCallback callback);
    void registerStateChangeCallback(in IBluetoothStateChangeCallback callback);
    void unregisterStateChangeCallback(in IBluetoothStateChangeCallback callback);
    boolean isEnabled();
    boolean enable();
    boolean enableNoAutoConnect();
    boolean disable(boolean persist);
    IBluetoothGatt getBluetoothGatt();

    boolean bindBluetoothProfileService(int profile, IBluetoothProfileServiceConnection proxy);
    void unbindBluetoothProfileService(int profile, IBluetoothProfileServiceConnection proxy);

    String getAddress();
    String getName();

    boolean isBleScanAlwaysAvailable();
    int updateBleAppCount(IBinder b, boolean enable);
    boolean isBleAppPresent();
    
    boolean isRadioEnabled();
    boolean enableRadio();
    boolean disableRadio();
}
