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

package com.smd.smartlamp_ble.model;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

/**
 * TODO Doc
 */
@Entity(tableName = "days", indices = {@Index(value = "wday", unique = true)})
public class DayAlarm {

    @PrimaryKey
    private int wday;

    private String name;
    private boolean enabled;

    /**
     * Alarm fade time in minutes [1, 30]
     */
    private int fadeTime;
    private int hour;
    private int min;

    private int red;
    private int green;
    private int blue;

    public DayAlarm(int wday, int fadeTime, int hour, int min) {
        this.name = "";
        this.enabled = true;
        this.fadeTime = fadeTime;
        this.hour = hour;
        this.min = min;
        this.wday = wday;
        this.setRed(0);
        this.setGreen(0);
        this.setBlue(0);
    }

    @Ignore
    public DayAlarm(String name, int wday, int fadeTime, int hour, int min) {
        this.name = name;
        this.enabled = true;
        this.fadeTime = fadeTime;
        this.hour = hour;
        this.min = min;
        this.wday = wday;
        this.setRed(0);
        this.setGreen(0);
        this.setBlue(0);
    }

    // TODO checks for value in the constructors
    @Ignore
    public DayAlarm(String name, int wday, int fadeTime, int hour, int min, int red, int green, int blue) {
        this.name = name;
        this.enabled = true;
        this.fadeTime = fadeTime;
        this.hour = hour;
        this.min = min;
        this.wday = wday;
        this.setRed(red);
        this.setGreen(green);
        this.setBlue(blue);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean en) {
        this.enabled = en;
    }

    public int getFadeTime() { return fadeTime; }

    public void setFadeTime(int fadeTime) {
        this.fadeTime = fadeTime;
    }

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public int getMin() { return min; }

    public void setMin(int min) { this.min = min;  }

    public int getWday() {
        return wday;
    }

    public void setWday(int wday) {
        this.wday = wday;
    }

    public int getRed() { return red; }

    public int getGreen() { return green; }

    public int getBlue() { return blue; }

    public void setRed(int red) { this.red = red; }

    public void setGreen(int green) { this.green = green; }

    public void setBlue(int blue) { this.blue = blue; }
}
