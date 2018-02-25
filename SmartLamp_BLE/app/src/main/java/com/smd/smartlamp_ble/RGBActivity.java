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
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.jaredrummler.android.colorpicker.ColorPanelView;
import com.jaredrummler.android.colorpicker.ColorPickerView;
import com.smd.smartlamp_ble.device.BLESerialPortService;
import com.smd.smartlamp_ble.device.ProtocolUtil;

public class RGBActivity extends AppCompatActivity implements ColorPickerView.OnColorChangedListener {

    private final static String TAG = RGBActivity.class.getSimpleName();
    public static final int INITIAL_COLOR = 0xFF0000;

    private ColorPickerView mColorPickerView;
    private ColorPanelView mColorPanelView;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rgb);

        getWindow().setFormat(PixelFormat.RGBA_8888);

        mColorPickerView = findViewById(R.id.cpv_color_picker_view);
        ColorPanelView colorPanelView = findViewById(R.id.cpv_color_panel_old);
        mColorPanelView = findViewById(R.id.cpv_color_panel_new);

        Button btnOK = findViewById(R.id.okRGBButton);

        ((LinearLayout) colorPanelView.getParent())
                .setPadding(mColorPickerView.getPaddingLeft(), 0, mColorPickerView.getPaddingRight(), 0);

        mColorPickerView.setOnColorChangedListener(this);
        mColorPickerView.setColor(INITIAL_COLOR, true);
        colorPanelView.setColor(INITIAL_COLOR);

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mBLESerialPortService != null) {
                    mBLESerialPortService.addCommand(ProtocolUtil.cmdExit());
                    mBLESerialPortService.sendAll();
                }
                finish();
            }
        });

        // Bind and start the bluetooth service
        Intent gattServiceIntent = new Intent(this, BLESerialPortService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        Log.d(TAG, "Bind service");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unbindService(mServiceConnection);
        mBLESerialPortService = null;
    }

    /**
     *
     * @param newColor
     */
    @Override public void onColorChanged(int newColor) {
        mColorPanelView.setColor(mColorPickerView.getColor());

        Log.d(TAG, "Color = " + newColor);
        int red = Color.red(newColor);
        int green = Color.green(newColor);
        int blue = Color.blue(newColor);

        Log.d(TAG, "RGB = " + red + ", " + green + ", " + blue);

        if (mBLESerialPortService != null) {
            mBLESerialPortService.addCommand(ProtocolUtil.cmdSendRGB(red, green, blue));
            mBLESerialPortService.sendAll();
        }
    }
}
