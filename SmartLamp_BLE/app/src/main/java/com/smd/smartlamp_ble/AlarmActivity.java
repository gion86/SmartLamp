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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;

import com.smd.smartlamp_ble.data.DayAlarm;
import com.smd.smartlamp_ble.device.DeviceScanActivity;
import com.smd.smartlamp_ble.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AlarmActivity extends Activity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks, TimePickerDialog.OnTimeSetListener {

    private final static String TAG = AlarmActivity.class.getSimpleName();

    private ArrayList<DayAlarm> mDayList;
    private DayAdapter mDayAdapter;
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

        // TODO StringList for day names
        DayAlarm d1 = new DayAlarm(getString(R.string.day_1_name), 10, 7, 0, 1);
        DayAlarm d2 = new DayAlarm(getString(R.string.day_2_name), 10, 8, 0, 2);

        mDayList = new ArrayList<DayAlarm>();
        mDayList.add(d1);
        mDayList.add(d2);
        mDayAdapter = new DayAdapter(this, mDayList);

        mDayPos = -1;

        ListView daysListView = (ListView) findViewById(R.id.dayList);
        daysListView.setAdapter(mDayAdapter);
        daysListView.setOnItemClickListener(mDayClickListener);
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
        if (mDayPos >= 0 && mDayPos < mDayAdapter.getCount()) {
            mDayList.get(mDayPos).setHour(hourOfDay);
            mDayList.get(mDayPos).setMin(minute);
            mDayAdapter.notifyDataSetChanged();
        } else
            Log.e(TAG, "OnTimeSet on position " + mDayPos);
    }

    public void showTimePickerDialog(View v) {
        // Look for the clicked row on the ListView
        ListView daysListView = (ListView) findViewById(R.id.dayList);
        // Position of the item in the listview and adapter
        mDayPos = daysListView.getPositionForView(v);

        DialogFragment newFragment = new TimePickerFragment();
        newFragment.show(getFragmentManager(), "timePicker");

        Log.i(TAG, "pos = " + mDayPos);
    }

    private AdapterView.OnItemClickListener mDayClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // TODO remove AdapterView.OnItemClickListener()?
            mDayList.get(position).setEnabled(! mDayList.get(position).isEnabled());
            mDayAdapter.notifyDataSetChanged();
        }
    };

    class DayAdapter extends BaseAdapter {
        Context context;
        List<DayAlarm> days;
        LayoutInflater inflater;

        public DayAdapter(Context context, List<DayAlarm> days) {
            this.context = context;
            inflater = LayoutInflater.from(context);
            this.days = days;
        }

        @Override
        public int getCount() {
            return days.size();
        }

        @Override
        public Object getItem(int position) {
            return days.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        private String digit(int number) {
            return number <= 9 ? "0" + number : String.valueOf(number);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup vg;

            if (convertView != null) {
                vg = (ViewGroup) convertView;
            } else {
                vg = (ViewGroup) inflater.inflate(R.layout.day_item, null);
            }

            DayAlarm day = days.get(position);
            final TextView dayName = ((TextView) vg.findViewById(R.id.dayName));
            final EditText fadeTime = ((EditText) vg.findViewById(R.id.fadeTime));
            final TextView dayTime = (TextView) vg.findViewById(R.id.dayTime);
            final CheckBox dayEn = (CheckBox) vg.findViewById(R.id.dayEnabled);

            if (dayName != null)
                dayName.setText(day.getName());

            if (fadeTime != null)
                fadeTime.setText(Integer.toString(day.getFadeTime()));

            if (dayTime != null)
                dayTime.setText(digit(day.getHour()) + ":" + digit(day.getMin()));

            if (dayEn != null)
                dayEn.setChecked(day.isEnabled());

            return vg;
        }
    }
}
