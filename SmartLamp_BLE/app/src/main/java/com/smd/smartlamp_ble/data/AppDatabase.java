package com.smd.smartlamp_ble.data;

import android.arch.persistence.room.Database;
        import android.arch.persistence.room.RoomDatabase;

@Database(entities = {DayAlarm.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract DayAlarmDAO dayAlarmDao();
}
