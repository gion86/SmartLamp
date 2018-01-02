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
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

/**
 * TODO
 */
@Entity(tableName = "days", indices = {@Index(value = "wday", unique = true)})
public class DayAlarm {

    @PrimaryKey
    private int wday;

    private String name;
    private boolean enabled;
    private int fadeTime;
    private int hour;
    private int min;

    public DayAlarm(String name, int fadeTime, int hour, int min, int wday) {
        this.name = name;
        this.enabled = true;
        this.fadeTime = fadeTime;
        this.hour = hour;
        this.min = min;
        this.wday = wday;
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

    public int getMin() {
        return min;
    }

    public void setMin(int min) { this.min = min;  }

    public int getWday() {
        return wday;
    }

    public void setWday(int wday) {
        this.wday = wday;
    }
}
