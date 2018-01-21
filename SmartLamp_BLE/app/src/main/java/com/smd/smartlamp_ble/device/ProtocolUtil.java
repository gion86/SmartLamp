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

package com.smd.smartlamp_ble.device;


import android.support.annotation.NonNull;

import com.smd.smartlamp_ble.model.DayAlarm;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class ProtocolUtil {
    private static final String DATE_FORMAT = "yyyyMMdd_HHmmss";
    private static final String TIME_ZONE = "GMT";

    private static String digit(int number) { return number <= 9 ? "0" + number : String.valueOf(number); }

    private static String digit2(int number) {
        if (number <= 9) {
            return "00" + number;
        } else if (number <= 99) {
            return "0" + number;
        } else {
            return String.valueOf(number);
        }
    }

    @NonNull
    public static String cmdSendTime(Date date) {
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat(DATE_FORMAT);
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone(TIME_ZONE));

        return "ST_" + dateFormatGmt.format(date);
    }

    @NonNull
    public static String cmdDisableAlarm(DayAlarm day) {
        return "AL_DIS_" + digit(day.getWday());
    }

    @NonNull
    public static String cmdSetAlarmFull(DayAlarm day) {
        if (day.isEnabled()) {
            return "AL_" + digit(day.getWday()) + "_" + digit(day.getHour()) + digit(day.getMin()) +
                    "_" +  digit(day.getFadeTime());
        } else {
            return cmdDisableAlarm(day);
        }
    }

    @NonNull
    public static String cmdSetAlarm(DayAlarm day) {
        if (day.isEnabled()) {
            return "AL_" + digit(day.getWday()) + "_" + digit(day.getHour()) + digit(day.getMin());
        } else {
            return cmdDisableAlarm(day);
        }
    }

    @NonNull
    public static String toCmdSetFadeTime(DayAlarm day) {
        return "FT_" + digit(day.getWday()) + "_" + day.getFadeTime();
    }

    @NonNull
    public static String cmdSendRGB(short r, short g, short b) {
        return "RGB_" + digit2(r) + "_" + digit2(g) + "_" + digit2(b);
    }
}
