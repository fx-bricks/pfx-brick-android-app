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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private BluetoothGattService mGattInformationService;
    private BluetoothGattService mMLPDService;
    private BluetoothGattCharacteristic mldpDataCharacteristic, transparentTxDataCharacteristic, transparentRxDataCharacteristic;

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED = "com.fxbricks.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.fxbricks.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.fxbricks.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.fxbricks.bluetooth.le.ACTION_DATA_AVAILABLE";

    public final static String EXTRA_UUID = "com.fxbricks.bluetooth.le.EXTRA_UUID";
    public final static String EXTRA_DATA = "com.fxbricks.bluetooth.le.EXTRA_DATA";

    private final static UUID UUID_DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    private final static UUID UUID_MLDP_PRIVATE_SERVICE = UUID.fromString("00035b03-58e6-07dd-021a-08123a000300"); //Private service for Microchip MLDP
    private final static UUID UUID_MLDP_DATA_PRIVATE_CHAR = UUID.fromString( SampleGattAttributes.MLDP_DATA_PRIVATE_CHAR); //Characteristic for MLDP Data, properties - notify, write
    private final static UUID UUID_MLDP_CONTROL_PRIVATE_CHAR = UUID.fromString("00035b03-58e6-07dd-021a-08123a0003ff"); //Characteristic for MLDP Control, properties - read, write

    private final static UUID UUID_TANSPARENT_PRIVATE_SERVICE = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455"); //Private service for Microchip Transparent
    private final static UUID UUID_TRANSPARENT_TX_PRIVATE_CHAR = UUID.fromString( SampleGattAttributes.TRANSPARENT_TX_PRIVATE_CHAR ); //Characteristic for Transparent Data from BM module, properties - notify, write, write no response
    private final static UUID UUID_TRANSPARENT_RX_PRIVATE_CHAR = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3"); //Characteristic for Transparent Data to BM module, properties - write, write no response

    private final static UUID UUID_CHAR_NOTIFICATION_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //Special descriptor needed to enable notifications
    public static UUID[] uuidScanList = {UUID_MLDP_PRIVATE_SERVICE, UUID_TANSPARENT_PRIVATE_SERVICE};
    private final Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
    private final Queue<BluetoothGattCharacteristic> characteristicWriteQueue = new LinkedList<BluetoothGattCharacteristic>();

    public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                descriptorWriteQueue.clear();                                                   //Clear write queues in case there was something left in the queue from the previous connection
                characteristicWriteQueue.clear();
                Log.i(TAG, "Attempting to start service discovery:" +
                    mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            try {
                mldpDataCharacteristic = transparentTxDataCharacteristic = transparentRxDataCharacteristic = null;
                mGattInformationService = null;
                mMLPDService = null;
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    List<BluetoothGattService> gattServices = gatt.getServices();                       //Get the list of services discovered
                    if (gattServices == null) {
                        Log.d(TAG, "No BLE services found");
                        return;
                    }

                    UUID uuid;
                    for (BluetoothGattService gattService : gattServices) {                             //Loops through available GATT services
                        uuid = gattService.getUuid();                                                   //Get the UUID of the service
                        if( uuid.equals( UUID_DEVICE_INFORMATION_SERVICE )) {
                            mGattInformationService = gattService;
                        }
                        else if (uuid.equals(UUID_MLDP_PRIVATE_SERVICE) || uuid.equals(UUID_TANSPARENT_PRIVATE_SERVICE)) { //See if it is the MLDP or Transparent private service UUID
                            if( uuid.equals(UUID_TANSPARENT_PRIVATE_SERVICE) ) {
                                mMLPDService = gattService;
                            }
                            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) { //Loops through available characteristics
                                uuid = gattCharacteristic.getUuid();                                    //Get the UUID of the characteristic
                                if (uuid.equals(UUID_TRANSPARENT_TX_PRIVATE_CHAR)) {                    //See if it is the Transparent Tx data private characteristic UUID
                                    transparentTxDataCharacteristic = gattCharacteristic;
                                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) { //See if the characteristic has the Notify property
                                        mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification in the BluetoothGatt
                                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID_CHAR_NOTIFICATION_DESCRIPTOR); //Get the descriptor that enables notification on the server
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); //Set the value of the descriptor to enable notification
                                        descriptorWriteQueue.add(descriptor);                           //put the descriptor into the write queue
                                        if(descriptorWriteQueue.size() == 1) {                          //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                                            mBluetoothGatt.writeDescriptor(descriptor);                 //Write the descriptor
                                        }
                                    }
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                    }
                                    Log.d(TAG, "Found Transparent service Tx characteristics");
                                }
                                if (uuid.equals(UUID_TRANSPARENT_RX_PRIVATE_CHAR)) {                    //See if it is the Transparent Rx data private characteristic UUID
                                    transparentRxDataCharacteristic = gattCharacteristic;
                                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                    }
                                    Log.d(TAG, "Found Transparent service Rx characteristics");
                                }

                                if (uuid.equals(UUID_MLDP_DATA_PRIVATE_CHAR)) {                         //See if it is the MLDP data private characteristic UUID
                                    mldpDataCharacteristic = gattCharacteristic;
                                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) { //See if the characteristic has the Notify property
                                        mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification in the BluetoothGatt
                                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID_CHAR_NOTIFICATION_DESCRIPTOR); //Get the descriptor that enables notification on the server
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); //Set the value of the descriptor to enable notification
                                        descriptorWriteQueue.add(descriptor);                           //put the descriptor into the write queue
                                        if(descriptorWriteQueue.size() == 1) {                          //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                                            mBluetoothGatt.writeDescriptor(descriptor);                 //Write the descriptor
                                        }
                                    }
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                    }
                                    Log.d(TAG, "Found MLDP service and characteristics");
                                }
                            }
                            break;
                        }
                    }
                    if(mldpDataCharacteristic == null && (transparentTxDataCharacteristic == null || transparentRxDataCharacteristic == null)) {
                        Log.d(TAG, "Did not find MLDP or Transparent service");
                    }

                    broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                } else {
                    Log.w(TAG, "onServicesDiscovered received: " + status);
                }
            }
            catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        //Write completed
        //Use write queue because BluetoothGatt can only do one write at a time
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) {                                             //See if the write was successful
                    Log.w(TAG, "Error writing GATT characteristic with status: " + status);
                }
                characteristicWriteQueue.remove();                                                      //Pop the item that we just finishing writing
                if(characteristicWriteQueue.size() > 0) {                                               //See if there is more to write
                    mBluetoothGatt.writeCharacteristic(characteristicWriteQueue.element());              //Write characteristic
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        //Write descriptor completed
        //Use write queue because BluetoothGatt can only do one write at a time
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Error writing GATT descriptor with status: " + status);
                }
                descriptorWriteQueue.remove();                                                          //Pop the item that we just finishing writing
                if(descriptorWriteQueue.size() > 0) {                                                   //See if there is more to write
                    mBluetoothGatt.writeDescriptor(descriptorWriteQueue.element());                      //Write descriptor
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else if (UUID_MLDP_DATA_PRIVATE_CHAR.equals(characteristic.getUuid()) || UUID_TRANSPARENT_TX_PRIVATE_CHAR.equals(characteristic.getUuid())) {
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                intent.putExtra(EXTRA_DATA, data);
                intent.putExtra(EXTRA_UUID, characteristic.getUuid().toString());
            }
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
//                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
                intent.putExtra(EXTRA_DATA, new String(data));
                intent.putExtra(EXTRA_UUID, characteristic.getUuid().toString() );
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
//        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
//                && mBluetoothGatt != null) {
//            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
//            if (mBluetoothGatt.connect()) {
//                mConnectionState = STATE_CONNECTING;
//                return true;
//            } else {
//                return false;
//            }
//        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        if (mBluetoothGatt!= null) {                                                                //See if an existing connection needs to be closed
            mBluetoothGatt.close();                                                                  //Faster to create new connection than reconnect with existing BluetoothGatt
        }

        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Write to the MLDP data characteristic
    public void writeMLDP(String string) {                                                          //Write string (may need to add code to limit write to 20 bytes)
        try {
            BluetoothGattCharacteristic writeDataCharacteristic;
//            if (mldpDataCharacteristic != null) {
//                writeDataCharacteristic = mldpDataCharacteristic;
//            }
//            else {
//                writeDataCharacteristic = transparentRxDataCharacteristic;
//            }

            writeDataCharacteristic = mMLPDService.getCharacteristic(UUID_TRANSPARENT_RX_PRIVATE_CHAR);

            if (mBluetoothAdapter == null || mBluetoothGatt == null || writeDataCharacteristic == null) {
                Log.w(TAG, "Write attempted with Bluetooth uninitialized or not connected");
                return;
            }
            writeDataCharacteristic.setValue(string);
            characteristicWriteQueue.add(writeDataCharacteristic);                                       //Put the characteristic into the write queue
            if(characteristicWriteQueue.size() == 1){                                                   //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                if (!mBluetoothGatt.writeCharacteristic(writeDataCharacteristic)) {                       //Request the BluetoothGatt to do the Write
                    Thread.sleep( 200 );
                    if( !mBluetoothGatt.writeCharacteristic(writeDataCharacteristic) ) {
                    Log.d(TAG, "Failed to write characteristic");                                       //Write request was not accepted by the BluetoothGatt
                    }
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    public void writeMLDP(byte[] byteValues) {                                                      //Write bytes (may need to add code to limit write to 20 bytes)
        try {
            BluetoothGattCharacteristic writeDataCharacteristic;
            if (mldpDataCharacteristic != null) {
                writeDataCharacteristic = mldpDataCharacteristic;
            }
            else {
                writeDataCharacteristic = transparentRxDataCharacteristic;
            }

//            writeDataCharacteristic = mMLPDService.getCharacteristic(UUID_TRANSPARENT_RX_PRIVATE_CHAR);

            if (mBluetoothAdapter == null || mBluetoothGatt == null || writeDataCharacteristic == null) {
                Log.w(TAG, "Write attempted with Bluetooth uninitialized or not connected");
                return;
            }
            writeDataCharacteristic.setValue(byteValues);
            characteristicWriteQueue.add(writeDataCharacteristic);                                       //Put the characteristic into the write queue
            if(characteristicWriteQueue.size() == 1){                                                   //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                if (!mBluetoothGatt.writeCharacteristic(writeDataCharacteristic)) {                       //Request the BluetoothGatt to do the Write
                    Thread.sleep( 200 );
                    if( !mBluetoothGatt.writeCharacteristic(writeDataCharacteristic) ) {
                        Log.d(TAG, "Failed to write characteristic");                                       //Write request was not accepted by the BluetoothGatt
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public BluetoothGattService getInformationService(){
        return mGattInformationService;
    }
}
