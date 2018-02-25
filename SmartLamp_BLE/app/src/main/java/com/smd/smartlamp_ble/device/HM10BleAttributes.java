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

import java.util.HashMap;

/**
 * This class includes the GATT attributes exposed by the HM10 BLE module.
 */
public class HM10BleAttributes {
    public static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static final String HM_10_CONF = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static final String HM_RX_TX = "0000ffe1-0000-1000-8000-00805f9b34fb";
    @SuppressWarnings("unchecked")
    private static final HashMap<String, String> attributes = new HashMap();

    static {
        // Services.
        attributes.put("0000ffe0-0000-1000-8000-00805f9b34fb", "HM 10 Serial");
        attributes.put("00001800-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Characteristics.
        attributes.put(HM_RX_TX, "RX/TX data");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
