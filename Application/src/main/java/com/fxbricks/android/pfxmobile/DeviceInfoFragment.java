package com.fxbricks.android.pfxmobile;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class DeviceInfoFragment extends Fragment implements DeviceControlActivity.DataInterface {
    public static DeviceInfoFragment newInstance() {
        DeviceInfoFragment fragment = new DeviceInfoFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.device_info, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        refreshData();
    }

    public void refreshData() {
        DeviceControlActivity activity = (DeviceControlActivity) getActivity();
        if (null != activity) {
//            TextView deviceAddressView = (TextView) getView().findViewById(R.id.bluetooth_address);
//            if (null != deviceAddressView) {
//                deviceAddressView.setText(activity.getDeviceAddress());
//            }

            switch( activity.getConnectedState() ) {
                case BluetoothLeService.STATE_CONNECTED:
                    ((TextView) getView().findViewById(R.id.connection_state)).setText( R.string.connected );
                    break;
                case BluetoothLeService.STATE_CONNECTING:
                    ((TextView) getView().findViewById(R.id.connection_state)).setText( R.string.connecting );
                    break;
                case BluetoothLeService.STATE_DISCONNECTED:
                    ((TextView) getView().findViewById(R.id.connection_state)).setText( R.string.disconnected );
                    break;
            }

            ((TextView) getView().findViewById(R.id.name_value)).setText( activity.getBrickName() );
            ((TextView) getView().findViewById(R.id.model_value)).setText( activity.getDeviceName() );
            ((TextView) getView().findViewById(R.id.serial_number_value)).setText( activity.getGATTValue(SampleGattAttributes.SERIAL_NUMBER) );
            ((TextView) getView().findViewById(R.id.hardware_version_value)).setText( activity.getHardwareVersion() );
            ((TextView) getView().findViewById(R.id.firmware_version_value)).setText( activity.getFirmwareVersion() );
        }
    }
}