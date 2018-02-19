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

import com.smd.smartlamp_ble.model.DayAlarm;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class that holds values to be used for application on first DB creation.
 */
public class DBData {

    static final DayAlarm SUNDAY = new DayAlarm("Sunday", 0, 10, 12, 0, 255, 255, 0);
    static final DayAlarm MONDAY = new DayAlarm("Monday", 1, 5, 8, 0,  255, 0, 0);
    static final DayAlarm TUEDAY = new DayAlarm("Tuesday", 2, 15, 9, 0,  0, 255, 0);
    static final DayAlarm WEDDAY = new DayAlarm("Wednesday", 3, 20, 10, 0,  125, 156, 55);
    static final DayAlarm THUDAY = new DayAlarm("Thursday", 4, 4, 11, 0,  155, 33, 200);
    static final DayAlarm FRIDAY = new DayAlarm("Friday", 5, 2, 7, 0,  225, 189, 55);
    static final DayAlarm SATDAY = new DayAlarm("Saturday", 6, 8, 18, 0,  55, 200, 200);

    static final List<DayAlarm> DAYS = Arrays.asList(SUNDAY, MONDAY, TUEDAY, WEDDAY, THUDAY, FRIDAY, SATDAY);
}


