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

package com.smd.smartlamp_ble.viewmodel;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.graphics.Color;
import android.os.AsyncTask;

import com.smd.smartlamp_ble.db.AppDatabase;
import com.smd.smartlamp_ble.model.DayAlarm;

import java.util.List;

public class DayAlarmViewModel extends AndroidViewModel {

    private final LiveData<List<DayAlarm>> mDayAlarmList;

    private AppDatabase mAppDatabase;

    public DayAlarmViewModel(Application application) {
        super(application);
        mAppDatabase = AppDatabase.getDatabase(this.getApplication());
        mDayAlarmList = mAppDatabase.dayAlarmDAO().getAll();
    }

    public LiveData<List<DayAlarm>> getDayAlarmList() {
        return mDayAlarmList;
    }

    public void addItem(final DayAlarm day) {
        new addUpdateAsyncTask(mAppDatabase).execute(day);
    }

    public void updateItemTime(int position, int hour, int min) {
        DayAlarm day = mDayAlarmList.getValue(). get(position);

        if (day != null) {
            day.setHour(hour);
            day.setMin(min);
            new addUpdateAsyncTask(mAppDatabase).execute(day);
        }
    }

    public void updateItemFade(int position, int fadeTime) {
        DayAlarm day = mDayAlarmList.getValue().get(position);

        if (day != null) {
            day.setFadeTime(fadeTime);
            new addUpdateAsyncTask(mAppDatabase).execute(day);
        }
    }

    public void updateItemEn(int position, boolean enabled) {
        DayAlarm day = mDayAlarmList.getValue().get(position);

        if (day != null) {
            day.setEnabled(enabled);
            new addUpdateAsyncTask(mAppDatabase).execute(day);
        }
    }

    public void updateItemColor(int position, int color) {
        DayAlarm day = mDayAlarmList.getValue().get(position);

        if (day != null) {
            day.setRed(Color.red(color));
            day.setGreen(Color.green(color));
            day.setBlue(Color.blue(color));
            new addUpdateAsyncTask(mAppDatabase).execute(day);
        }
    }

    public void updateItem(int position) {
        DayAlarm day = mDayAlarmList.getValue().get(position);

        if (day != null) {
            day.setEnabled(!day.isEnabled());
            new addUpdateAsyncTask(mAppDatabase).execute(day);
        }
    }

    public void deleteItem(int wday) {
        new deleteWdayAsyncTask(mAppDatabase).execute(wday);
    }

    public void deleteItem(DayAlarm day) {
        new deleteAsyncTask(mAppDatabase).execute(day);
    }

    private static class addUpdateAsyncTask extends AsyncTask<DayAlarm, Void, Void> {

        private AppDatabase db;

        addUpdateAsyncTask(AppDatabase appDatabase) {
            db = appDatabase;
        }

        @Override
        protected Void doInBackground(final DayAlarm... params) {
            db.dayAlarmDAO().insert(params[0]);
            return null;
        }
    }

    private static class deleteWdayAsyncTask extends AsyncTask<Integer, Void, Void> {

        private AppDatabase db;

        deleteWdayAsyncTask(AppDatabase appDatabase) {
            db = appDatabase;
        }

        @Override
        protected Void doInBackground(final Integer... params) {
            db.dayAlarmDAO().delete(params[0]);
            return null;
        }
    }

    private static class deleteAsyncTask extends AsyncTask<DayAlarm, Void, Void> {

        private AppDatabase db;

        deleteAsyncTask(AppDatabase appDatabase) {
            db = appDatabase;
        }

        @Override
        protected Void doInBackground(final DayAlarm... params) {
            db.dayAlarmDAO().delete(params[0]);
            return null;
        }
    }
}
