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