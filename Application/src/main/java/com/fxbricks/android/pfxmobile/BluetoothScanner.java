package com.fxbricks.android.pfxmobile;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.List;

public class BluetoothScanner {

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    private DeviceScanActivity mScanActivity;

    public BluetoothScanner(DeviceScanActivity inScanActivity) {
        mScanActivity = inScanActivity;
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        @TargetApi(21)
        public void onScanResult(int callbackType, ScanResult result) {
            mScanActivity.addDevice(result.getDevice());
            super.onScanResult(callbackType, result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            mScanActivity.addDevice(device);
                }
    };

    public final boolean startScan()
    {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) mScanActivity.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }

        if(Build.VERSION.SDK_INT < 23 ) {
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
        else
        {
            final BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            bluetoothLeScanner.startScan( mScanCallback);
        }

        return true;
    }

    public boolean stopScan()
    {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) mScanActivity.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }

        if(android.os.Build.VERSION.SDK_INT<23) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        else {
            final BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            bluetoothLeScanner.stopScan(mScanCallback);
        }

        return true;
    }
}
