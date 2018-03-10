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

package com.smd.smartlamp_ble.device;

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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.smd.smartlamp_ble.R;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static com.smd.smartlamp_ble.device.ProtocolUtil.LINE_SEP;

/**
 * Service for managing connection and serial data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BLESerialPortService extends Service {
    private final static String TAG = BLESerialPortService.class.getSimpleName();

    // UUID for the ble serial port client characteristic which is necessary for notifications.
    public static final UUID SERIAL_SERVICE_UUID = UUID.fromString(HM10BleAttributes.HM_10_CONF);
    public final static UUID UUID_HM_RX_TX = UUID.fromString(HM10BleAttributes.HM_RX_TX);

    public final static String ACTION_GATT_CONNECTED =
            "com.smd.smartlamp_ble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.smd.smartlamp_ble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_GATT_DEVICE_DOES_NOT_SUPPORT_UART =
            "com.example.bluetooth.le.ACTION_GATT_DEVICE_DOES_NOT_SUPPORT_UART";
    public final static String ACTION_DATA_AVAILABLE =
            "com.smd.smartlamp_ble.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.smd.smartlamp_ble.EXTRA_DATA";

    public final static String ACTION_CMD_ACK_RECV =
            "com.smd.smartlamp_ble.ACTION_CMD_ACK_RECV";

    public final static String ACTION_CMD_ACK_TIMEOUT =
            "com.smd.smartlamp_ble.ACTION_CMD_ACK_TIMEOUT";

    // Communication status
    public static final long SEND_TIME_OUT_MILLIS = TimeUnit.SECONDS.toMillis(1);
    public static final long RECV_TIME_OUT_MILLIS = TimeUnit.SECONDS.toMillis(1);

    public final static int ERR_CODE_TIMEOUT_WRITE = -1;
    public final static int ERR_CODE_NO_CONNECTION = -2;

    public class LocalBinder extends Binder {
        public BLESerialPortService getService() {
            return BLESerialPortService.this;
        }
    }

    private final static String ACK_DATA = "OK";

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // Binder for service
    private final IBinder mBinder = new LocalBinder();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mTx;

    // Flags to indicate a write is currently in progress
    private boolean mWriteInProgress;

    private int mConnectionState = STATE_DISCONNECTED;

    private String mBluetoothDeviceAddress;
    private String mBLEData;

    private Queue<String> mWriteQueue;
    private Handler mCmdTimeOutHandler;
    private long mCmdSendMillis;
    private Handler mCmdSendHandler;

    /**
     * Implements callback methods for GATT events that the app cares about.  For example,
     * connection change and services discovered.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String intentAction;

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Connected to GATT server.");

                    intentAction = ACTION_GATT_CONNECTED;
                    broadcastUpdate(intentAction);

                    // Connected to device, start discovering services.
                    mConnectionState = STATE_CONNECTED;

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
                Log.i(TAG, "Disconnected from GATT server.");

                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                broadcastUpdate(intentAction);

                mTx = null;
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
            //Log.d(TAG, "onCharacteristicChanged");

            String data = characteristic.getStringValue(0);

            broadcastUpdate(ACTION_DATA_AVAILABLE, data);

            mBLEData += data;

            int idx = mBLEData.lastIndexOf(LINE_SEP);

            if (idx >= 0) {
                Log.d(TAG, mBLEData.substring(0, idx));

                if (mBLEData.substring(0, idx).contains(ACK_DATA)) {
                    Log.d(TAG, "DATA_OK: RTT " + (System.currentTimeMillis() - mCmdSendMillis) + " ms");

                    // Send broadcast information: a command has been acknowledged.
                    broadcastUpdate(ACTION_CMD_ACK_RECV);

                    // Remove all handlers for command timeout.
                    mCmdTimeOutHandler.removeCallbacksAndMessages(null);

                    // Remove head of the message queue.
                    mWriteQueue.poll();

                    // Send next command.
                    if (!mWriteQueue.isEmpty()) {
                        sendCmd();
                    }
                }
                mBLEData = mBLEData.substring(idx + 1);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicRead");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic.getStringValue(0));
                Log.d(TAG, characteristic.getStringValue(0));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            //Log.d(TAG, "onCharacteristicWrite: " + characteristic.getStringValue(0));
            mWriteInProgress = false;
        }
    };

    /**
     * Default public constructor
     */
    public BLESerialPortService() {
        super();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothGatt = null;
        mTx = null;
        mWriteInProgress = false;
        mBLEData = "";
        mWriteQueue = new ConcurrentLinkedQueue<>();
        mCmdTimeOutHandler = new Handler();

        // Create a new background thread for processing messages.
        HandlerThread handlerThread = new HandlerThread("HandlerThreadName");

        // Starts the background thread
        handlerThread.start();

        // Create a handler attached to the HandlerThread's Looper
        mCmdSendHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                mCmdSendMillis = System.currentTimeMillis();

                int errCode = sendCmdStr((String) msg.obj);

                switch (errCode) {
                    case ERR_CODE_TIMEOUT_WRITE:
                        Log.e(TAG, "Write time out on cmd: " + msg.obj);
                        Toast.makeText(getApplicationContext(),
                                getResources().getString(R.string.error_write_timeout) + " " + msg.obj,
                                Toast.LENGTH_LONG).show();
                        break;

                    case ERR_CODE_NO_CONNECTION:
                        Log.e(TAG, "No connection: tx characteristic == null");
                        Toast.makeText(getApplicationContext(), R.string.error_no_conn,
                                Toast.LENGTH_LONG).show();
                        break;
                }

                if (errCode != 0) {
                    mWriteQueue.clear();
                }
            }
        };
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final String data) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, data);
        sendBroadcast(intent);
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

        // Previously connected device. Try to reconnect.
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
        mTx = null;
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

        Log.w(TAG, characteristic.getStringValue(0));
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    private void enableRXNotification() {
        if (mBluetoothGatt == null) return;

        BluetoothGattService serialService = mBluetoothGatt.getService(SERIAL_SERVICE_UUID);

        if (serialService == null) {
            Log.e(TAG, "Rx serial service not found!");
            broadcastUpdate(ACTION_GATT_DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }

        mTx = serialService.getCharacteristic(UUID_HM_RX_TX);
        if (mTx == null) {
            Log.e(TAG, "Tx characteristic not found!");
            broadcastUpdate(ACTION_GATT_DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }

        setCharacteristicNotification(mTx);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, true);

        // This is specific to serial communication.
        if (UUID_HM_RX_TX.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(HM10BleAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    public void addCommand(String command) {
        mWriteQueue.add(command);
    }

    public int getCommandCount() {
        return mWriteQueue.size();
    }

    // Send data to connected ble serial port device.
    private Integer sendData(byte[] data) {
        long beginMillis = System.currentTimeMillis();

        // mTx could be null if anything had happened to the BLE connection.
        if (mTx == null)
            return ERR_CODE_NO_CONNECTION;

        mWriteInProgress = true;
        mTx.setValue(data);
        writeCharacteristic(mTx);

        while (mWriteInProgress) {           // Wait for the flag to clear in onCharacteristicWrite
            if (System.currentTimeMillis() - beginMillis > SEND_TIME_OUT_MILLIS) {
                Log.e(TAG, "current - begin = " + (System.currentTimeMillis() - beginMillis));
                return ERR_CODE_TIMEOUT_WRITE;
            }
        }

        return 0;
    }

    /**
     * Send data to connected ble serial port device. We can only send 20 bytes per packet,
     * so break longer messages up into 20 byte payloads
     *
     * @param string
     * @return
     */
    private Integer sendCmdStr(String string) {
        int len = string.length();
        int pos = 0;
        StringBuilder stringBuilder = new StringBuilder();

        while (len > 0) {
            stringBuilder.setLength(0);
            if (len >= 20) {
                stringBuilder.append(string.toCharArray(), pos, 20);
                len -= 20;
                pos += 20;
            } else {
                stringBuilder.append(string.toCharArray(), pos, len);
                len = 0;
            }
            int errCode = sendData(stringBuilder.toString().getBytes());

            if (errCode != 0)
                return errCode;
        }
        return 0;
    }

    /**
     * Send one command at a time, in an separate thread with an message handles.
     * Sets also the handler for the command timeout.
     */
    private void sendCmd() {
        // Sets handler for timeout check
        mCmdTimeOutHandler.postDelayed(new Runnable() {
            public void run() {
                // Timeout on a sent command.
                if (!mWriteQueue.isEmpty()) {
                    broadcastUpdate(ACTION_CMD_ACK_TIMEOUT, mWriteQueue.element());
                    mWriteQueue.clear();

                    // Remove all handlers for command timeout.
                    mCmdTimeOutHandler.removeCallbacksAndMessages(null);
                }
            }
        }, RECV_TIME_OUT_MILLIS);

        // Send message to the handler
        mCmdSendHandler.obtainMessage(0, mWriteQueue.element()).sendToTarget();
    }

    public void sendAll() {
        if (mTx == null) {
            // Do nothing if there is no connection.
            Log.e(TAG, "No connection: tx characteristic == null");
            Toast.makeText(getApplicationContext(), R.string.error_no_conn, Toast.LENGTH_LONG).show();
            mWriteQueue.clear();     // Clear queue in any case
            return;
        }

        // Send first command.
        sendCmd();
    }
}
