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

    private String digit(int number) {
        return number <= 9 ? "0" + number : String.valueOf(number);
    }

    public DayAlarmAdapter(List<DayAlarm> dayAlarmList) {
        this.mDayAlarmList = dayAlarmList;
    }

    @Override
    public DayAlarmViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new DayAlarmViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.day_item, parent, false));
    }

    @Override
    public void onBindViewHolder(DayAlarmViewHolder holder, int position) {
        DayAlarm day = mDayAlarmList.get(position);

        if (holder.mDayName != null)
            holder.mDayName.setText(day.getName());

        if (holder.mFadeTime != null)
            holder.mFadeTime.setText(Integer.toString(day.getFadeTime()));

        if (holder.mDayTime != null)
            holder.mDayTime.setText(digit(day.getHour()) + ":" + digit(day.getMin()));

        if (holder.mDayEn != null)
            holder.mDayEn.setChecked(day.isEnabled());
    }

    @Override
    public int getItemCount() {
        return mDayAlarmList.size();
    }

    public void addItems(List<DayAlarm> dayAlarmList) {
        this.mDayAlarmList = dayAlarmList;
        notifyDataSetChanged();
    }

    static class DayAlarmViewHolder extends RecyclerView.ViewHolder {
        TextView mDayName;
        EditText mFadeTime;
        TextView mDayTime;
        CheckBox mDayEn;

        public DayAlarmViewHolder(View itemView) {
            super(itemView);

            mDayName = itemView.findViewById(R.id.dayName);
            mFadeTime = itemView.findViewById(R.id.fadeTime);
            mDayTime = itemView.findViewById(R.id.dayTime);
            mDayEn = itemView.findViewById(R.id.dayEnabled);
        }
    }
}
