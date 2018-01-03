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

    public LiveData<List<DayAlarm>> getmDayAlarmList() {
        return mDayAlarmList;
    }

    public void addItem(final DayAlarm day) { new addAsyncTask(mAppDatabase).execute(day);}

    public void deleteItem(int wday) {
        new deleteWdayAsyncTask(mAppDatabase).execute(new Integer(wday));
    }

    public void deleteItem(DayAlarm day) {
        new deleteAsyncTask(mAppDatabase).execute(day);
    }

    private static class addAsyncTask extends AsyncTask<DayAlarm, Void, Void> {

        private AppDatabase db;

        addAsyncTask(AppDatabase appDatabase) {
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
