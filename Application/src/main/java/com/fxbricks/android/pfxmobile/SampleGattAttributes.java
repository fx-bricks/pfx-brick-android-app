/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fxbricks.android.pfxmobile;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String SYSTEM_ID = "00002a23-0000-1000-8000-00805f9b34fb";
    public static String MODEL_NUMBER = "00002a24-0000-1000-8000-00805f9b34fb";
    public static String SERIAL_NUMBER = "00002a25-0000-1000-8000-00805f9b34fb";
    public static String FIRMWARE_REVISION = "00002a26-0000-1000-8000-00805f9b34fb";
    public static String HARDWARE_REVISION = "00002a27-0000-1000-8000-00805f9b34fb";
    public static String SOFTWARE_REVISION = "00002a28-0000-1000-8000-00805f9b34fb";
    public static String MANUFACTURER_NAME = "00002a29-0000-1000-8000-00805f9b34fb";
    public static String IEEE_DATA = "00002a2a-0000-1000-8000-00805f9b34fb";
    public static String PNP_ID = "00002a50-0000-1000-8000-00805f9b34fb";

    public static String MLDP_DATA_PRIVATE_CHAR = "00035b03-58e6-07dd-021a-08123a000301";
    public static String TRANSPARENT_TX_PRIVATE_CHAR = "49535343-1e4d-4bd9-ba61-23c647249616";

    static {
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
