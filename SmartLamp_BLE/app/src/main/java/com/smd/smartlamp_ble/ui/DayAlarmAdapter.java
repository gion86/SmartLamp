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

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.smd.smartlamp_ble.R;
import com.smd.smartlamp_ble.model.DayAlarm;

import java.util.List;

public class DayAlarmAdapter extends RecyclerView.Adapter<DayAlarmAdapter.DayAlarmViewHolder> {

    private List<DayAlarm> mDayAlarmList;
    private String [] mDayNames;

    private String digit(int number) {
        return number <= 9 ? "0" + number : String.valueOf(number);
    }

    public DayAlarmAdapter(List<DayAlarm> dayAlarmList, String [] mDayNames) {
        this.mDayAlarmList = dayAlarmList;
        this.mDayNames = mDayNames;
    }

    @Override
    public DayAlarmViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new DayAlarmViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.day_item, parent, false));
    }

    @Override
    public void onBindViewHolder(DayAlarmViewHolder holder, int position) {
        if (mDayAlarmList != null) {
            DayAlarm day = mDayAlarmList.get(position);

            // Set day name based on localized string resources.
            day.setName(mDayNames[day.getWday()]);

            holder.dayName.setText(day.getName());
            holder.fadeTime.setText(Integer.toString(day.getFadeTime()));
            holder.dayTime.setText(digit(day.getHour()) + ":" + digit(day.getMin()));
            holder.dayEn.setChecked(day.isEnabled());
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
        EditText fadeTime;
        TextView dayTime;
        CheckBox dayEn;

        public DayAlarmViewHolder(View itemView) {
            super(itemView);

            dayName = itemView.findViewById(R.id.dayName);
            fadeTime = itemView.findViewById(R.id.fadeTime);
            dayTime = itemView.findViewById(R.id.dayTime);
            dayEn = itemView.findViewById(R.id.dayEnabled);
        }
    }
}
