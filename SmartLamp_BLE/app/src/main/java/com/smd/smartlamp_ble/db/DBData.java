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

import android.support.annotation.NonNull;

import com.smd.smartlamp_ble.BuildConfig;
import com.smd.smartlamp_ble.model.DayAlarm;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class that holds values to be used for application on first DB creation.
 */
public class DBData {

    // Debug data
    public static final DayAlarm SUNDAY_D = new DayAlarm("Sunday", 0, 10, 12, 0, 255, 255, 0);
    public static final DayAlarm MONDAY_D = new DayAlarm("Monday", 1, 5, 8, 0,  255, 0, 0);
    public static final DayAlarm TUEDAY_D = new DayAlarm("Tuesday", 2, 15, 9, 0,  0, 255, 0);
    public static final DayAlarm WEDDAY_D = new DayAlarm("Wednesday", 3, 20, 10, 0,  125, 156, 55);
    public static final DayAlarm THUDAY_D = new DayAlarm("Thursday", 4, 4, 11, 0,  155, 33, 200);
    public static final DayAlarm FRIDAY_D = new DayAlarm("Friday", 5, 2, 7, 0,  225, 189, 55);
    public static final DayAlarm SATDAY_D = new DayAlarm("Saturday", 6, 8, 18, 0,  55, 200, 200);

    // Release data: to populate DB on first install
    public static final DayAlarm SUNDAY = new DayAlarm("Sunday", 0, 20, 10, 0, 215, 11, 0);
    public static final DayAlarm MONDAY = new DayAlarm("Monday", 1, 10, 7, 0,  238, 218, 0);
    public static final DayAlarm TUEDAY = new DayAlarm("Tuesday", 2, 10, 7, 0,  238, 218, 0);
    public static final DayAlarm WEDDAY = new DayAlarm("Wednesday", 3, 10, 7, 0,  238, 218, 0);
    public static final DayAlarm THUDAY = new DayAlarm("Thursday", 4, 10, 7, 0,  238, 218, 0);
    public static final DayAlarm FRIDAY = new DayAlarm("Friday", 5, 10, 7, 0,  238, 218, 0);
    public static final DayAlarm SATDAY = new DayAlarm("Saturday", 6, 20, 9, 0,  215, 11, 0);

    /**
     * Creates an array of {@link DayAlarm} objects to populate DB for debug and release build.
     *
     * @return a List of {@link DayAlarm} objects
     */
    @NonNull
    public static List<DayAlarm> createDaysData() {
       if (BuildConfig.DEBUG) {
           return Arrays.asList(SUNDAY_D, MONDAY_D, TUEDAY_D, WEDDAY_D, THUDAY_D, FRIDAY_D, SATDAY_D);
       } else {
           return Arrays.asList(SUNDAY, MONDAY, TUEDAY, WEDDAY, THUDAY, FRIDAY, SATDAY);
       }
    }
}
