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

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.TimePickerDialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.smd.smartlamp_ble.device.BLESerialPortService;
import com.smd.smartlamp_ble.device.DeviceScanActivity;
import com.smd.smartlamp_ble.device.ProtocolUtil;
import com.smd.smartlamp_ble.model.DayAlarm;
import com.smd.smartlamp_ble.settings.SettingsActivity;
import com.smd.smartlamp_ble.ui.DayAlarmAdapter;
import com.smd.smartlamp_ble.viewmodel.DayAlarmViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AlarmActivity extends AppCompatActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks, TimePickerDialog.OnTimeSetListener {

    private final static String TAG = AlarmActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 2;

    private final static int MENU_POS_SETTING = 0;
    private final static int MENU_POS_DEV_SCAN = 1;

    private DayAlarmAdapter mDayAdapter;
    private DayAlarmViewModel mViewModel;
    private RecyclerView mRecyclerView;

    /**
     *
     */
    private BluetoothAdapter mBtAdapter = null;

    /**
     *
     */
    private boolean mConnected;     // True if connected to a BLE device
    private String mDeviceName;
    private String mDeviceAddress;

    // Code to manage Service lifecycle.
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

    private DayAlarmAdapter.OnItemClickListener mAdpaterListerner = new DayAlarmAdapter.OnItemClickListener() {
        @Override
        public void onTimeClick(int position) {
            // Current list position for the TimePickerDialog.
            mDayPos = position;

            DialogFragment newFragment = new TimePickerFragment();
            newFragment.show(getFragmentManager(), "timePicker");
        }

        @Override
        public void onFadeTimeClick(final int position, final int fadeTime) {

            AlertDialog.Builder alert = new AlertDialog.Builder(AlarmActivity.this);
            alert.setTitle(R.string.fade_time_choose);

            LayoutInflater inflater = getLayoutInflater();
            View alertLayout = inflater.inflate(R.layout.fragment_fade_time, null);

            // This is set the view from XML inside AlertDialog
            alert.setView(alertLayout);

            final TextView currentFadeTime = alertLayout.findViewById(R.id.currentFadeTime);
            currentFadeTime.setText(getString(R.string.actual_fade_time) + " " + fadeTime);

            final SeekBar fadeTimeBar = alertLayout.findViewById(R.id.fadeTimeBar);

            fadeTimeBar.setProgress(fadeTime);

            fadeTimeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    currentFadeTime.setText(getString(R.string.actual_fade_time) + " " + i);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            alert.setPositiveButton("Done", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (position >= 0 && position < mDayAdapter.getItemCount()) {
                        mViewModel.updateItem(position, fadeTimeBar.getProgress());
                    }
                    Log.i(TAG, "onFadeTimeClick on position " + position + " = " + fadeTimeBar.getProgress());
                }
            });
            AlertDialog dialog = alert.create();
            dialog.show();
        }

        @Override
        public void onEnableClick(int position, boolean checked) {
            if (position >= 0 && position < mDayAdapter.getItemCount()) {
                mViewModel.updateItem(position, checked);
            }
            Log.i(TAG, "onEnableClick on position " + position + " = " + checked);
        }
    };

    private Observer mDayListObserver = new Observer<List<DayAlarm>>() {
        @Override
        public void onChanged(@Nullable List<DayAlarm> mDayList) {
            mDayAdapter.setItems(mDayList);
        }
    };

    private int mDayPos;

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        Button sendAllButton = findViewById(R.id.sendAllButton);
        sendAllButton.setEnabled(mConnected);

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // TODO preference for mDeviceAddress
        mDeviceName = "HM10";
        mDeviceAddress = "34:15:13:1A:E1:AD";

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout));

        // The listener is passed to the adapter
        mDayAdapter = new DayAlarmAdapter(new ArrayList<DayAlarm>(),
                getResources().getStringArray(R.array.day_names), mAdpaterListerner);

        mRecyclerView = findViewById(R.id.dayList);
        mRecyclerView.setAdapter(mDayAdapter);

        mViewModel = ViewModelProviders.of(this).get(DayAlarmViewModel.class);
        mViewModel.getmDayAlarmList().observe(AlarmActivity.this, mDayListObserver);

        mDayPos = -1;
        mConnected = false;

        // Bind and start the bluetooth service
        Intent gattServiceIntent = new Intent(this, BLESerialPortService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        Log.d(TAG, "Bind service");
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreActionBar();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBLESerialPortService != null) {
            final boolean result = mBLESerialPortService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
            mBLESerialPortService = null;
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
                .commit();

        switch (position) {
            case MENU_POS_SETTING:
                startActivity(new Intent(this, SettingsActivity.class));
                break;

            case MENU_POS_DEV_SCAN:
                startActivity(new Intent(this, DeviceScanActivity.class));
                break;
        }
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = getString(R.string.title_section1);
                break;
            case 2:
                mTitle = getString(R.string.title_section2);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();

        // Enable ActionBar app icon to behave as action to toggle nav drawer
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_alarm_menu, menu);

        if (mConnected) {
            menu.findItem(R.id.menu_connect).setEnabled(false);
            menu.findItem(R.id.menu_disconnect).setEnabled(true);
        } else {
            menu.findItem(R.id.menu_connect).setEnabled(true);
            menu.findItem(R.id.menu_disconnect).setEnabled(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else {
                    Log.i(TAG, "Device address: " + mDeviceAddress);
                    mBLESerialPortService.connect(mDeviceAddress);
                }
                return true;

            case R.id.menu_disconnect:
                mBLESerialPortService.disconnect();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BLESerialPortService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                invalidateOptionsMenu();
            } else if (BLESerialPortService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
            }

            Button sendAllButton = findViewById(R.id.sendAllButton);
            sendAllButton.setEnabled(mConnected);
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLESerialPortService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLESerialPortService.ACTION_GATT_DISCONNECTED);
        //intentFilter.addAction(BLESerialPortService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    /**
     * A placeholder fragment containing a simple view for the activity with navigation drawer.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_alarm, container, false);
            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((AlarmActivity) activity).onSectionAttached(getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

    /**
     * Time picker fragment for the alarm time of day.
     */
    public static class TimePickerFragment extends DialogFragment {

        private Activity mActivity;
        private TimePickerDialog.OnTimeSetListener mListener;

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mActivity = activity;

            try {
                mListener = (TimePickerDialog.OnTimeSetListener) activity;
            } catch (ClassCastException e) {
                throw new ClassCastException(activity.toString() + " must implement OnTimeSetListener");
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current time as the default values for the picker
            final Calendar c = Calendar.getInstance();
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);

            // Create a new instance of TimePickerDialog and return it
            return new TimePickerDialog(mActivity, mListener, hour, minute,
                    DateFormat.is24HourFormat(mActivity));
        }
    }

    /**
     * Gets the time of day from the {@link TimePickerDialog} and update the database.
     *
     * @param view
     * @param hourOfDay
     * @param minute
     */
    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        if (mDayPos >= 0 && mDayPos < mDayAdapter.getItemCount()) {
            mViewModel.updateItem(mDayPos, hourOfDay, minute);
        }
        Log.i(TAG, "OnTimeSet on position " + mDayPos);
    }

    public void onSendDayClick(View view) {
        for (DayAlarm day : mViewModel.getmDayAlarmList().getValue()) {
            mBLESerialPortService.addCommand(ProtocolUtil.cmdSetAlarmFull(day));
            Log.i(TAG, "SEND: " + ProtocolUtil.cmdSetAlarmFull(day));
        }

//        mBLESerialPortService.addCommand("RGB_111_222_333" + "\r\n");
//        mBLESerialPortService.addCommand("RGB_101_202_303" + "\r\n");
//        mBLESerialPortService.addCommand("RGB_121_122_233" + "\r\n");
        mBLESerialPortService.sendAll();
    }
}
