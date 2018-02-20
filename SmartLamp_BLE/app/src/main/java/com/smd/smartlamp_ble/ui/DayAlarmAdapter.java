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

package com.smd.smartlamp_ble.ui;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.smd.smartlamp_ble.R;
import com.smd.smartlamp_ble.model.DayAlarm;

import java.util.List;

public class DayAlarmAdapter extends RecyclerView.Adapter<DayAlarmAdapter.DayAlarmViewHolder> {

    private final static String TAG = DayAlarmAdapter.class.getSimpleName();
    private final static int COLOR_ALPHA = 180;

    private List<DayAlarm> mDayAlarmList;
    private String [] mDayNames;

    // Interface instance
    private final DayAlarmAdapter.OnItemClickListener mListener;

    private String digit(int number) { return number <= 9 ? "0" + number : String.valueOf(number); }

    /**
     * Interface for click event on the Activity.
     */
    public interface OnItemClickListener {
        /**
         * Callback for click events on the time of day view.
         *
         * @param position
         */
        void onTimeClick(final int position);

        /**
         * Callback for click events on the fade time view.
         *
         * @param position
         * @param fadeTime
         */
        void onFadeTimeClick(final int position, final int fadeTime);

        /**
         * Callback for click events on the enabled checkbox.
         *
         * @param position
         * @param checked
         */
        void onEnableClick(final int position, final boolean checked);

        /**
         * Callback for click events on the color view.
         *
         * @param position
         * @param color
         */
        void onColorClick(final int position, final int color);
    }

    public DayAlarmAdapter(List<DayAlarm> dayAlarmList, String[] mDayNames, OnItemClickListener listener) {
        this.mDayAlarmList = dayAlarmList;
        this.mDayNames = mDayNames;
        this.mListener = listener;
    }

    @Override
    public DayAlarmViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new DayAlarmViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.day_item, parent, false));
    }

    @Override
    public void onBindViewHolder(final DayAlarmViewHolder holder, final int position) {
        if (mDayAlarmList != null) {
            DayAlarm day = mDayAlarmList.get(position);

            // Binds the listener to the current holder
            holder.bind(position, day, mListener);

            // Set day name based on localized string resources.
            day.setName(mDayNames[day.getWday()]);

            holder.dayName.setText(day.getName());
            holder.fadeTime.setText(Integer.toString(day.getFadeTime()));
            holder.dayTime.setText(digit(day.getHour()) + ":" + digit(day.getMin()));
            holder.dayEn.setChecked(day.isEnabled());
            holder.dayColor.getDrawable()
                    .setColorFilter(Color.argb(COLOR_ALPHA, day.getRed(), day.getGreen(), day.getBlue()),
                            PorterDuff.Mode.SRC_IN);

        } else {
            // TODO Covers the case of data not being ready yet.
        }
    }

    @Override
    public int getItemCount() {
        if (mDayAlarmList != null)
            return mDayAlarmList.size();
        else return 0;
    }

    public void setItems(List<DayAlarm> dayAlarmList) {
        this.mDayAlarmList = dayAlarmList;
        notifyDataSetChanged();
    }

    static class DayAlarmViewHolder extends RecyclerView.ViewHolder {
        TextView dayName;
        TextView fadeTime;
        TextView dayTime;
        CheckBox dayEn;
        ImageView dayColor;

        public DayAlarmViewHolder(View itemView) {
            super(itemView);

            dayName = itemView.findViewById(R.id.dayName);
            fadeTime = itemView.findViewById(R.id.fadeTime);
            dayTime = itemView.findViewById(R.id.dayTime);
            dayEn = itemView.findViewById(R.id.dayEnabled);
            dayColor = itemView.findViewById(R.id.dayColor);
        }

        public void bind(final int position, final DayAlarm day, final OnItemClickListener listener) {

            dayTime.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onTimeClick(position);
                }
            });

            dayEn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onEnableClick(position, dayEn.isChecked());
                }
            });

            fadeTime.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onFadeTimeClick(position, Integer.parseInt(fadeTime.getText().toString()));
                }
            });

            dayColor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onColorClick(position, Color.argb(COLOR_ALPHA, day.getRed(), day.getGreen(), day.getBlue()));
                }
            });;
        }
    }
}