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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.smd.smartlamp_ble.R;
import com.smd.smartlamp_ble.model.DayAlarm;

import java.util.Date;

import static com.smd.smartlamp_ble.device.BLESerialPortService.EXTRA_DATA;
import static com.smd.smartlamp_ble.device.ProtocolUtil.LINE_SEP;

/**
 * For a given BLE device, this Activity provides the user interface to send and receive command,
 * and display data from BLE device.
 * The Activity communicates with {@code BLESerialPortService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceDebugActivity extends AppCompatActivity {
    private final static String TAG = DeviceDebugActivity.class.getSimpleName();

    private TextView mReadTextView;
    private EditText mWriteText;

    private BLESerialPortService mBLESerialPortService;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "New service connection");
            mBLESerialPortService = ((BLESerialPortService.LocalBinder) service).getService();
            if (!mBLESerialPortService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBLESerialPortService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_DATA_AVAILABLE: received data from the device. This can be a result of read
    //                        or notification operations.
    // ACTION_CMD_ACK_TIMEOUT: timeout on a sent command.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BLESerialPortService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BLESerialPortService.EXTRA_DATA));
            } else if (BLESerialPortService.ACTION_CMD_ACK_TIMEOUT.equals(action)) {
                Log.e(TAG, "Ack time out on cmd: " + intent.getStringExtra(EXTRA_DATA));
                Toast.makeText(getApplicationContext(),
                        getResources().getString(R.string.error_ack_timeout) + " " + intent.getStringExtra(EXTRA_DATA),
                        Toast.LENGTH_LONG).show();
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLESerialPortService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BLESerialPortService.ACTION_CMD_ACK_TIMEOUT);
        return intentFilter;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.serial_read_write);

        // Sets up UI references.
        mReadTextView = findViewById(R.id.readTextView);
        mReadTextView.setMovementMethod(new ScrollingMovementMethod());

        mWriteText = findViewById(R.id.writeText);

        final Button sendButton = findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String cmd = mWriteText.getText().toString();

                if (!cmd.contains(LINE_SEP))
                    mBLESerialPortService.addCommand(cmd + LINE_SEP);
                else
                    mBLESerialPortService.addCommand(cmd);

                mBLESerialPortService.sendAll();
            }
        });

        final Button timeButton = findViewById(R.id.timeButton);
        timeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send date in command as the UTC actual time.
                mWriteText.setText(ProtocolUtil.cmdSendTime(new Date()));
            }
        });

        final Button alarmButton = findViewById(R.id.alarmButton);
        alarmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                DayAlarm day = new DayAlarm(0, 10, 17, 35);
                mWriteText.setText(ProtocolUtil.cmdSetAlarmFull(day));
            }
        });

        final Button colorButton = findViewById(R.id.colorButton);
        colorButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mWriteText.setText(ProtocolUtil.cmdSendRGB(55, 129, 255));
            }
        });

        final Button testButton = findViewById(R.id.testButton);
        testButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mWriteText.setText(ProtocolUtil.cmdTest());
            }
        });

        final Button exitButton = findViewById(R.id.exitButton);
        exitButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mWriteText.setText(ProtocolUtil.cmdExit());
            }
        });

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Bind and start the bluetooth service
        Intent gattServiceIntent = new Intent(this, BLESerialPortService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        Log.d(TAG, "Bind service");
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mServiceConnection);
        mBLESerialPortService = null;
    }

    private void displayData(String data) {
        if (data != null) {
            mReadTextView.append(data);
        }
    }
}
