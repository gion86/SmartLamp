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

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.TimePickerDialog;
import android.arch.lifecycle.LifecycleActivity;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TimePicker;

import com.smd.smartlamp_ble.device.DeviceScanActivity;
import com.smd.smartlamp_ble.model.DayAlarm;
import com.smd.smartlamp_ble.settings.SettingsActivity;
import com.smd.smartlamp_ble.ui.DayAlarmAdapter;
import com.smd.smartlamp_ble.viewmodel.DayAlarmViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

// TODO AppCompatActivity
// TODO update fadeTime on user change
public class AlarmActivity extends LifecycleActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks, TimePickerDialog.OnTimeSetListener {

    private final static String TAG = AlarmActivity.class.getSimpleName();

    private DayAlarmAdapter mDayAdapter;
    private DayAlarmViewModel mViewModel;
    private RecyclerView mRecyclerView;

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

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        mDayAdapter = new DayAlarmAdapter(new ArrayList<DayAlarm>(),
                getResources().getStringArray(R.array.day_names));

        mRecyclerView = findViewById(R.id.dayList);
        mRecyclerView.setAdapter(mDayAdapter);

        // OnItemTouchListener to get the current list position for the TimePickerDialog.
        mRecyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            GestureDetector gestureDetector = new GestureDetector(getApplicationContext(),
                    new GestureDetector.SimpleOnGestureListener() {

                @Override public boolean onSingleTapUp(MotionEvent e) {
                    return true;
                }
            });

            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                View child = rv.findChildViewUnder(e.getX(), e.getY());
                if (child != null && gestureDetector.onTouchEvent(e)) {
                    mDayPos = rv.getChildAdapterPosition(child);
                    // TODO mViewModel.updateItem(mDayPos); change enable only on tap on the checkbox
                }
                return false;
            }

            @Override
            public void onTouchEvent(RecyclerView rv, MotionEvent e) {
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        });

        mViewModel = ViewModelProviders.of(this).get(DayAlarmViewModel.class);

        mViewModel.getmDayAlarmList().observe(AlarmActivity.this, new Observer<List<DayAlarm>>() {
            @Override
            public void onChanged(@Nullable List<DayAlarm> mDayList) {
                mDayAdapter.setItems(mDayList);
            }
        });

        mDayPos = -1;
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
                .commit();

        switch (position) {
            case 0:
                startActivity(new Intent(this, SettingsActivity.class));
                break;

            case 1: // TODO constants?
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
            case 3:
                mTitle = getString(R.string.title_section3);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    /**
     * A placeholder fragment containing a simple view.
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
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_alarm, container, false);
            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((AlarmActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

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

    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        if (mDayPos >= 0 && mDayPos < mDayAdapter.getItemCount()) {
            mViewModel.updateItem(mDayPos, hourOfDay, minute);

        } else Log.e(TAG, "OnTimeSet on position " + mDayPos);
    }

    public void showTimePickerDialog(View v) {
        DialogFragment newFragment = new TimePickerFragment();
        newFragment.show(getFragmentManager(), "timePicker");
    }

    public void onFadeTimeChange(View v) {
        if (mDayPos >= 0 && mDayPos < mDayAdapter.getItemCount()) {
            EditText e = findViewById(R.id.fadeTime);
            mViewModel.updateItem(mDayPos, Integer.parseInt(e.getText().toString()));   // FIXME database not updated...

        } else Log.e(TAG, "onFadeTimeChange on position " + mDayPos);
    }
}
