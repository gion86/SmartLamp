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
import java.util.Locale;
import java.util.TimeZone;

public class ProtocolUtil {
    public static final String DATE_FORMAT = "yyyyMMdd_HHmmss";
    public static final String TIME_ZONE = "GMT";

    public final static String LINE_SEP = "\r\n";

    public static String digit(int number) {
        return number <= 9 ? "0" + number : String.valueOf(number);
    }

    public static String digit2(int number) {
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
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone(TIME_ZONE));

        return "ST_" + dateFormatGmt.format(date) + LINE_SEP;
    }

    @NonNull
    public static String cmdDisableAlarm(DayAlarm day) {
        return "AL_DIS_" + digit(day.getWday()) + LINE_SEP;
    }

    @NonNull
    public static String cmdSetAlarmFull(DayAlarm day) {
        if (day.isEnabled()) {
            return "AL_" + digit(day.getWday()) + "_" + digit(day.getHour()) + digit(day.getMin()) +
                    "_" +  digit(day.getFadeTime()) + "_" + digit2(day.getRed()) +
                    "_" + digit2(day.getGreen()) + "_" + digit2(day.getBlue())+ LINE_SEP;
        } else {
            return cmdDisableAlarm(day);
        }
    }

    @NonNull
    public static String cmdSetAlarm(DayAlarm day) {
        if (day.isEnabled()) {
            return "AL_" + digit(day.getWday()) + "_" + digit(day.getHour()) + digit(day.getMin()) + LINE_SEP;
        } else {
            return cmdDisableAlarm(day);
        }
    }

    @NonNull
    public static String cmdSetFadeTime(DayAlarm day) {
        return "FT_" + digit(day.getWday()) + "_" + day.getFadeTime() + LINE_SEP;
    }

    @NonNull
    public static String cmdSendRGB(int r, int g, int b) {
        return "RGB_" + digit2(r) + "_" + digit2(g) + "_" + digit2(b) + LINE_SEP;
    }

    @NonNull
    public static String cmdSendOPT(int onTime, int ledBright) {
        return "OPT_" + digit(onTime) + "_" + digit2(ledBright) + LINE_SEP;
    }

    @NonNull
    public static String cmdTest() {
        return "TEST" + LINE_SEP;
    }

    @NonNull
    public static String cmdPrint() {
        return "PRINT" + LINE_SEP;
    }

    @NonNull
    public static String cmdExit() {
        return "EXIT" + LINE_SEP;
    }
}
