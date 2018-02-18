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

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.smd.smartlamp_ble.device.BLESerialPortService;
import com.smd.smartlamp_ble.device.ProtocolUtil;

public class RGBActivity extends AppCompatActivity {

    private final static String TAG = RGBActivity.class.getSimpleName();

    private ColorPicker mPicker;

    private BLESerialPortService mBLESerialPortService;
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


    /**
     *
     * @param color
     */
    private void colorChanged(int color) {
        Log.d(TAG, "Color = " + color);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);

        Log.d(TAG, "RGB = " + red + ", " + green + ", " + blue);

        if (mBLESerialPortService != null) {
            mBLESerialPortService.addCommand(ProtocolUtil.cmdSendRGB(red, green, blue));
            mBLESerialPortService.sendAll();
        }
    }

    public void onCancelClick(View view) {
        finish();
    }

    public void onOKClick(View view) {
        // TODO set color preference
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rgb);

        mPicker = findViewById(R.id.picker);

        // Set the old selected color
        mPicker.setOldCenterColor(mPicker.getColor());

        // Turn of showing the old color
        mPicker.setShowOldCenterColor(false);

        mPicker.setOnColorChangedListener(new ColorPicker.OnColorChangedListener() {
            @Override
            public void onColorChanged(int color) {
                colorChanged(color);
            }
        });

        SaturationBar satBar = findViewById(R.id.saturationbar);

        satBar.setOnSaturationChangedListener(new SaturationBar.OnSaturationChangedListener() {
            public void onSaturationChanged(int color) {
                colorChanged(color);
            }
        });

        mPicker.addSaturationBar(satBar);

        // Bind and start the bluetooth service
        Intent gattServiceIntent = new Intent(this, BLESerialPortService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        Log.d(TAG, "Bind service");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
            mBLESerialPortService = null;
        }
    }
}
