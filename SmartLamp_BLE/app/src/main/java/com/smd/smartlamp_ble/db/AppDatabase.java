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

package com.smd.smartlamp_ble.db;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.smd.smartlamp_ble.model.DayAlarm;
import static com.smd.smartlamp_ble.db.DBData.FRIDAY;
import static com.smd.smartlamp_ble.db.DBData.MONDAY;
import static com.smd.smartlamp_ble.db.DBData.SUNDAY;

@Database(entities = {DayAlarm.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "day_alarm_db")
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract DayAlarmDAO dayAlarmDAO();

    private static RoomDatabase.Callback sRoomDatabaseCallback =
            new RoomDatabase.Callback(){

                public void onCreate (SupportSQLiteDatabase db) {
                    super.onCreate(db);
                    new PopulateDbAsync(INSTANCE).execute();
                }

                @Override
                public void onOpen (@NonNull SupportSQLiteDatabase db) {
                    super.onOpen(db);
                }
            };

    private static class PopulateDbAsync extends AsyncTask<Void, Void, Void> {

        private final DayAlarmDAO mDao;

        PopulateDbAsync(AppDatabase db) {
            mDao = db.dayAlarmDAO();
        }

        @Override
        protected Void doInBackground(final Void... params) {
            mDao.deleteAll();

            // Populate the database with default data
            mDao.insert(SUNDAY);     // Sunday (wday = 0)
            mDao.insert(MONDAY);     // Monday (wday = 1)
            mDao.insert(FRIDAY);     // Friday (wday = 5)

            return null;
        }
    }
}
