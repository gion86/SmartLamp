package com.smd.smartlamp_ble.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface DayAlarmDAO {
    @Query("SELECT * FROM days")
    List<DayAlarm> getAll();

    @Query("SELECT * FROM days WHERE wday IS (:wday)")
    DayAlarm findByWeekDay(int wday);

    @Query("SELECT * FROM days WHERE name LIKE :name")
    DayAlarm findByName(String name);

    @Insert
    public void insert(DayAlarm day);

    @Insert
    void insertAll(DayAlarm... users);

    @Delete
    void delete(DayAlarm user);
}