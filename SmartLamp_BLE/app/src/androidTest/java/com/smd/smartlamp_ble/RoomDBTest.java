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

import android.arch.persistence.room.Room;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.smd.smartlamp_ble.data.AppDatabase;
import com.smd.smartlamp_ble.data.DayAlarm;
import com.smd.smartlamp_ble.data.DayAlarmDAO;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
public class RoomDBTest {
    private DayAlarmDAO mDayAlarmDAO;
    private AppDatabase mDb;

    @Before
    public void createDb() {
        Context context = InstrumentationRegistry.getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        mDayAlarmDAO = mDb.dayAlarmDao();
    }

    @After
    public void closeDb() throws IOException {
        mDb.close();
    }

    @Test
    public void testDayCreation() throws Exception {
        DayAlarm day = new DayAlarm("Monday", 10, 7, 0, 1);;

        mDayAlarmDAO.insert(day);

        DayAlarm byWeekDay = mDayAlarmDAO.findByWeekDay(1);
        assertThat(byWeekDay.getWday(), is(1));
        assertThat(byWeekDay.getName(), is("Monday"));
    }
}


