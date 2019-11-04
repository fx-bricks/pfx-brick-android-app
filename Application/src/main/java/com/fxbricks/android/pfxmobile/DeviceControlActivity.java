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

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends AppCompatActivity {
    private static byte PFX_CMD_GET_STATUS = 0x01;
    private static byte PFX_CMD_GET_NAME = 0x07;

    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private byte currentPFxResponse = 0;
    List<Byte> pfxResponseData = new ArrayList<Byte>();

    public static final byte[] pfxRemoteCommand(int event, int channel) {
        byte[] command = {0x5b, 0x5b, 0x5b, 0x15, 0, 0x5d, 0x5d, 0x5d};
        command[4] = (byte) (event | channel);
        return command;
    }

    public void sendPFxCommand(byte[] data) {
        mBluetoothLeService.writeMLDP(data);
    }
    public void pushPFxCommand( byte[] data ){
        pfxCommandStack.add( data );
    }

    private boolean processPFxStatusResponse() {
        boolean result = false;
        if( pfxResponseData.size() > 40 )
        {
            mHardwareVersion = String.format( "%X", ( ( pfxResponseData.get(7) & 0xFF ) << 0x08 ) + ( pfxResponseData.get(8) & 0xFF ) );
            mFirmwareVersion = String.format("%X.%02X", pfxResponseData.get(37) & 0xFF, pfxResponseData.get(38) & 0xFF );
            result = true;
        }

        return result;
    }

    private boolean processPFxNameResponse() {
        boolean result = false;
        if( pfxResponseData.size() > 24 )
        {
            mBrickName = "";
            for (int i = 1; (i < pfxResponseData.size()) && pfxResponseData.get(i) != 0; i++) {
//                    mBrickName += new String( inData[i], "UTF-8" );
                mBrickName += String.format("%c", pfxResponseData.get(i));
            }
            getSupportActionBar().setTitle(mBrickName);
            SharedPreferences sharedPref = getSharedPreferences("com.fxbricks.pfxmobile.CACHED_BRICK_INFO", Context.MODE_PRIVATE );
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString( mDeviceAddress, mBrickName );
            editor.commit();
            Fragment deviceFragment = getSupportFragmentManager().findFragmentByTag("DEVICE_FRAGMENT");
            if (null != deviceFragment && deviceFragment.isVisible()) {
                ((DeviceInfoFragment) deviceFragment).refreshData();
            }
            result = true;
        }

        return result;
    }

    private void processPFxResponse(byte[] inData) {
        if( 0 == currentPFxResponse ) {
            currentPFxResponse = inData[0];
            pfxResponseData.clear();
        }

        for (byte value : inData) {
            pfxResponseData.add(value);
        }

        boolean result = true;
        if( currentPFxResponse == ( byte )( PFX_CMD_GET_STATUS | 0x80 ) ) {
            result = processPFxStatusResponse();
        }
        else if( ( byte )( PFX_CMD_GET_NAME | 0x80 ) == currentPFxResponse ) {
            result = processPFxNameResponse();
        }

        if( result ) {
            currentPFxResponse = 0;
            processPendingCommands();
        }
    }

    //    private TextView mDataField;
    private String mDeviceAddress = "...";
    private String mDeviceName = "...";
    private String mBrickName = "...";
    private String mFirmwareVersion = "...";
    private String mHardwareVersion = "...";

    public String getDeviceAddress() {
        return mDeviceAddress;
    }
    public String getDeviceName() {
        return mDeviceName;
    }
    public String getBrickName() {
        return mBrickName;
    }
    public String getFirmwareVersion() { return mFirmwareVersion; }
    public String getHardwareVersion() { return mHardwareVersion; }

    private static HashMap<String, String> gattValues = new HashMap();

    public String getGATTValue(String inUUID) {
        return gattValues.get(inUUID);
    }

    //    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    //    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
//            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private int mConnectionStatus = BluetoothLeService.STATE_DISCONNECTED;

    public int getConnectedState() {
        return mConnectionStatus;
    }
//    private BluetoothGattCharacteristic mNotifyCharacteristic;

//    private final String LIST_NAME = "NAME";
//    private final String LIST_UUID = "UUID";

    private List<BluetoothGattCharacteristic> gattCharsToRead = new ArrayList<>();
    private List<byte[]> pfxCommandStack = new ArrayList<>();

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            connectToDevice();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnectionStatus = BluetoothLeService.STATE_CONNECTED;
                updateConnectionStatus();
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnectionStatus = BluetoothLeService.STATE_DISCONNECTED;

                Fragment deviceFragment = DeviceInfoFragment.newInstance();
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.frame_layout, deviceFragment, "DEVICE_FRAGMENT");
                transaction.commit();

                gattValues.put(SampleGattAttributes.SERIAL_NUMBER, "...");

                updateConnectionStatus();
                invalidateOptionsMenu();
//                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                getDeviceInformation();
//                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String extraUUID = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                if (extraUUID.equals(SampleGattAttributes.MLDP_DATA_PRIVATE_CHAR) || extraUUID.equals(SampleGattAttributes.TRANSPARENT_TX_PRIVATE_CHAR)) {
                    processPFxResponse(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA));
                } else {
                    displayData(extraUUID, intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                }
            }
        }
    };

//    // If a given GATT characteristic is selected, check for supported features.  This sample
//    // demonstrates 'Read' and 'Notify' features.  See
//    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
//    // list of supported characteristic features.
//    private final ExpandableListView.OnChildClickListener servicesListClickListner =
//            new ExpandableListView.OnChildClickListener() {
//                @Override
//                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
//                                            int childPosition, long id) {
//                    if (mGattCharacteristics != null) {
//                        final BluetoothGattCharacteristic characteristic =
//                                mGattCharacteristics.get(groupPosition).get(childPosition);
//                        final int charaProp = characteristic.getProperties();
//                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
//                            // If there is an active notification on a characteristic, clear
//                            // it first so it doesn't update the data field on the user interface.
//                            if (mNotifyCharacteristic != null) {
//                                mBluetoothLeService.setCharacteristicNotification(
//                                        mNotifyCharacteristic, false);
//                                mNotifyCharacteristic = null;
//                            }
//                            mBluetoothLeService.readCharacteristic(characteristic);
//                        }
//                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
//                            mNotifyCharacteristic = characteristic;
//                            mBluetoothLeService.setCharacteristicNotification(
//                                    characteristic, true);
//                        }
//                        return true;
//                    }
//                    return false;
//                }
//    };
//
//    private void clearUI() {
////        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
////        mDataField.setText(R.string.no_data);
//    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.gatt_services_characteristics);
        setContentView(R.layout.device_control_activity);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        gattValues.put(SampleGattAttributes.SERIAL_NUMBER, "...");

        BottomNavigationView bottomNavigationView = (BottomNavigationView) findViewById(R.id.navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener
                (new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        Fragment selectedFragment = null;
                        String fragmentTag = null;
                        switch (item.getItemId()) {
                            case R.id.action_item1:
                                selectedFragment = DeviceInfoFragment.newInstance();
                                fragmentTag = "DEVICE_FRAGMENT";
                                break;
                            case R.id.action_item2:
                                selectedFragment = SpeedRemoteFragment.newInstance();
                                fragmentTag = "SPEED_FRAGMENT";
                                break;
                            case R.id.action_item3:
                                selectedFragment = JoystickRemoteFragment.newInstance();
                                fragmentTag = "JOYSTICK_FRAGMENT";
                                break;
                        }
                        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                        transaction.replace(R.id.frame_layout, selectedFragment, fragmentTag);
                        transaction.commit();
                        return true;
                    }
                });

        //Manually displaying the first fragment - one time only
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frame_layout, DeviceInfoFragment.newInstance(), "DEVICE_FRAGMENT");
        transaction.commit();

        // Sets up UI references.
//        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
//        mGattServicesList.setOnChildClickListener(servicesListClickListner);
//        mDataField = (TextView) findViewById(R.id.data_value);

        getSupportActionBar().setTitle(mDeviceName);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            connectToDevice();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnectionStatus == BluetoothLeService.STATE_CONNECTED) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                connectToDevice();
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionStatus() {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
        // Get the active fragment, and tell it and update is available.
        Fragment deviceFragment = getSupportFragmentManager().findFragmentByTag("DEVICE_FRAGMENT");
        if (null != deviceFragment && deviceFragment.isVisible()) {
            ((DeviceInfoFragment) deviceFragment).refreshData();
        }
//            }
//        });
    }

    private void displayData(String inUUID, String inData) {
        gattValues.put(inUUID, inData);

//        final GattInfoChar infoChar = new GattInfoChar(inUUID, inData);
//        mCharAdapter.addChar( infoChar );
//        mCharAdapter.notifyDataSetChanged();

        processPendingCommands();

        Fragment deviceFragment = getSupportFragmentManager().findFragmentByTag("DEVICE_FRAGMENT");
        if (null != deviceFragment && deviceFragment.isVisible()) {
            ((DeviceInfoFragment) deviceFragment).refreshData();
        }
    }
//    private void displayData(String data) {
//        if (data != null) {
//            mDataField.setText(data);
//        }
//    }

    private void processPendingCommands() {
        if( pfxCommandStack.size() > 0 ) {
            sendPFxCommand(pfxCommandStack.get(0));
            pfxCommandStack.remove(0);
        }
        else if (gattCharsToRead.size() > 0) {
            gattCharsToRead.remove(gattCharsToRead.size() - 1);
            if (gattCharsToRead.size() > 0) {
                mBluetoothLeService.readCharacteristic(gattCharsToRead.get(gattCharsToRead.size() - 1));
            }
        }
    }

    private void getDeviceInformation() {
        // Queue up device information characteristics to read, but don't read them until after
        // we get the PFx Brick name.
        BluetoothGattService informationService = mBluetoothLeService.getInformationService();
        if (informationService != null) {
            List<BluetoothGattCharacteristic> gattCharacteristics = informationService.getCharacteristics();
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                gattCharsToRead.add(gattCharacteristic);
            }
        }

        byte[] command = {0x5b, 0x5b, 0x5b, PFX_CMD_GET_NAME, 0x5d, 0x5d, 0x5d};
        pushPFxCommand( command );

        byte[] getStatus = {0x5b, 0x5b, 0x5b, PFX_CMD_GET_STATUS, (byte)0xa5, 0x5a, 0x6e, 0x40, 0x54, (byte)0xa4, (byte)0xe5, 0x5d, 0x5d, 0x5d };
        pushPFxCommand( getStatus );

        processPendingCommands();
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
//    private void displayGattServices(List<BluetoothGattService> gattServices) {
//        if (gattServices == null) return;
//        String uuid = null;
//        String unknownServiceString = getResources().getString(R.string.unknown_service);
//        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
//        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
//        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
//                = new ArrayList<ArrayList<HashMap<String, String>>>();
//        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
//
//        // Loops through available GATT Services.
//        for (BluetoothGattService gattService : gattServices) {
//            HashMap<String, String> currentServiceData = new HashMap<String, String>();
//            uuid = gattService.getUuid().toString();
//            currentServiceData.put(
//                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
//            currentServiceData.put(LIST_UUID, uuid);
//            gattServiceData.add(currentServiceData);
//
//            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
//                    new ArrayList<HashMap<String, String>>();
//            List<BluetoothGattCharacteristic> gattCharacteristics =
//                    gattService.getCharacteristics();
//            ArrayList<BluetoothGattCharacteristic> charas =
//                    new ArrayList<BluetoothGattCharacteristic>();
//
//            // Loops through available Characteristics.
//            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
//                charas.add(gattCharacteristic);
//                HashMap<String, String> currentCharaData = new HashMap<String, String>();
//                uuid = gattCharacteristic.getUuid().toString();
//                currentCharaData.put(
//                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
//                currentCharaData.put(LIST_UUID, uuid);
//                gattCharacteristicGroupData.add(currentCharaData);
//            }
//            mGattCharacteristics.add(charas);
//            gattCharacteristicData.add(gattCharacteristicGroupData);
//        }
//
//        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
//                this,
//                gattServiceData,
//                android.R.layout.simple_expandable_list_item_2,
//                new String[] {LIST_NAME, LIST_UUID},
//                new int[] { android.R.id.text1, android.R.id.text2 },
//                gattCharacteristicData,
//                android.R.layout.simple_expandable_list_item_2,
//                new String[] {LIST_NAME, LIST_UUID},
//                new int[] { android.R.id.text1, android.R.id.text2 }
//        );
//        mGattServicesList.setAdapter(gattServiceAdapter);
//    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public interface DataInterface {
        void refreshData();
    }

    private void connectToDevice() {
        if( null != mBluetoothLeService )
        {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);

            mConnectionStatus = BluetoothLeService.STATE_CONNECTING;
            updateConnectionStatus();
        }
    }
}
