/*
 *  This file is part of SmartLamp application.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.smd.smartlamp_ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing connection and serial data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BLESerialPortService extends Service {
    // UUID for the ble serial port client characteristic which is necessary for notifications.
    public static final UUID SERIAL_SERVICE_UUID = UUID.fromString(HM10BleAttributes.HM_10_CONF);
    public final static UUID UUID_HM_RX_TX = UUID.fromString(HM10BleAttributes.HM_RX_TX);

    public final static String ACTION_GATT_CONNECTED =
            "com.smd.smartlamp_ble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.smd.smartlamp_ble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.smd.smartlamp_ble.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.smd.smartlamp_ble.EXTRA_DATA";

    private final static String TAG = BLESerialPortService.class.getSimpleName();
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // Binder for service
    private final IBinder mBinder = new LocalBinder();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic tx;
    private boolean writeInProgress; // Flag to indicate a write is currently in progress
    private int mConnectionState = STATE_DISCONNECTED;
    private String mBluetoothDeviceAddress;
    private String mRecData;

    // Device Information state.
    private BluetoothGattCharacteristic disManuf;
    private BluetoothGattCharacteristic disModel;
    private BluetoothGattCharacteristic disSWRev;

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    public BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String intentAction;
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    intentAction = ACTION_GATT_CONNECTED;
                    broadcastUpdate(intentAction);

                    // Connected to device, start discovering services.
                    mConnectionState = STATE_CONNECTED;
                    Log.i(TAG, "Connected to GATT server.");
                    // Attempts to discover services after successful connection.
                    Log.i(TAG, "Attempting to start service discovery");

                    if (!mBluetoothGatt.discoverServices()) {
                        // Error starting service discovery.
                        Log.i(TAG, "Service discovery failure");
                    }
                } else {
                    // Error connecting to device.
                    mConnectionState = STATE_DISCONNECTED;
                    Log.i(TAG, "Error connecting to device.");
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);

                // Disconnected, notify callbacks of disconnection.
                tx = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableRXNotification();
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            //Log.w(TAG, "onCharacteristicChanged");

            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.w(TAG, "onCharacteristicRead");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, characteristic.getStringValue(0));
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            writeInProgress = false;
        }
    };

    /**
     * Default public constructor
     */
    public BLESerialPortService() {
        super();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothGatt = null;
        tx = null;
        disManuf = null;
        disModel = null;
        disSWRev = null;
        writeInProgress = false;
        mRecData = "";
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // For all profiles, writes the data in ASCII.
        final byte[] data = characteristic.getValue();

        if (data != null && data.length > 0) {
            mRecData += new String(data);

            if (data.length < 20) {
                Log.i(TAG, mRecData);
                intent.putExtra(EXTRA_DATA, mRecData);
                sendBroadcast(intent);
                mRecData = "";
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    /**
     * Initializes a reference to the local Bluetooth Adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
                mBluetoothDeviceAddress = address;
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    // Return true if connected to UART device, false otherwise.
    public boolean isConnected() {
        return (mConnectionState == STATE_CONNECTED);
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        tx = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.readCharacteristic(characteristic);
        }
    }

    /**
     * Write to a given characteristic
     *
     * @param characteristic The characteristic to write to
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    private void enableRXNotification() {
        if (mBluetoothGatt == null) return;

        BluetoothGattService SerialService = mBluetoothGatt.getService(SERIAL_SERVICE_UUID);
        if (SerialService == null) return;

        tx = SerialService.getCharacteristic(UUID_HM_RX_TX);
        if (tx == null) return;

        setCharacteristicNotification(tx, true);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to serial communication.
        if (UUID_HM_RX_TX.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(HM10BleAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    // Send data to connected ble serial port device.
    public void send(byte[] data) {
        long beginMillis = System.currentTimeMillis();
        if (tx == null || data == null || data.length == 0) {
            // Do nothing if there is no connection or message to send.
            return;
        }
        // Update TX characteristic value.  Note the setValue overload that takes a byte array must be used.
        tx.setValue(data);
        writeInProgress = true; // Set the write in progress flag
        mBluetoothGatt.writeCharacteristic(tx);
        while (writeInProgress) {
            if (System.currentTimeMillis() - beginMillis > CommunicationStatus.SEND_TIME_OUT_MILLIS) {
                break;
            }
        }
        // Wait for the flag to clear in onCharacteristicWrite
    }

    // Send data to connected ble serial port device. We can only send 20 bytes per packet,
    // so break longer messages up into 20 byte payloads
    public void send(String string) {
        int len = string.length();
        int pos = 0;
        StringBuilder stringBuilder = new StringBuilder();

        while (len != 0) {
            stringBuilder.setLength(0);
            if (len >= 20) {
                stringBuilder.append(string.toCharArray(), pos, 20);
                len -= 20;
                pos += 20;
            } else {
                stringBuilder.append(string.toCharArray(), pos, len);
                len = 0;
            }
            Log.i(TAG, "T: " + stringBuilder.toString() + "   len = " + stringBuilder.toString().length());
            send(stringBuilder.toString().getBytes());
        }
    }

    public String getDeviceInfo() {
        if (tx == null) {
            // Do nothing if there is no connection.
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Manufacturer : " + disManuf.getStringValue(0) + "\n");
        sb.append("Model        : " + disModel.getStringValue(0) + "\n");
        sb.append("Firmware     : " + disSWRev.getStringValue(0) + "\n");
        return sb.toString();
    }

    public static class CommunicationStatus {
        public static final long SEND_TIME_OUT_MILLIS = TimeUnit.SECONDS.toMillis(2);
        public static final int COMMUNICATION_SUCCESS = 0;
        public static final int COMMUNICATION_TIMEOUT = -1;
    }

    public class LocalBinder extends Binder {
        BLESerialPortService getService() {
            return BLESerialPortService.this;
        }
    }
}
