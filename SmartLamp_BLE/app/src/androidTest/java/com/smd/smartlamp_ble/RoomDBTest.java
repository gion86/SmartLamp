package com.smd.smartlamp_ble;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.smd.smartlamp_ble.data.AppDatabase;
import com.smd.smartlamp_ble.data.DayAlarm;
import com.smd.smartlamp_ble.data.DayAlarmDAO;

import static org.hamcrest.Matchers.equalTo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

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
        assertThat(byWeekDay, equalTo(day));

//        DayAlarm byName = mDayAlarmDAO.findByName("Monday");
//        assertThat(byName, equalTo(day));


    }
}


