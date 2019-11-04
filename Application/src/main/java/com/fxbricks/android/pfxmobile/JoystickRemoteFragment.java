package com.fxbricks.android.pfxmobile;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;

public class JoystickRemoteFragment extends Fragment implements View.OnClickListener, View.OnTouchListener {
    public static JoystickRemoteFragment newInstance() {
        JoystickRemoteFragment fragment = new JoystickRemoteFragment();
        return fragment;
    }

    private static int EVT_8885_LEFT_FWD = 0x1C;
    private static int EVT_8885_LEFT_REV = 0x20;
    private static int EVT_8885_RIGHT_FWD = 0x24;
    private static int EVT_8885_RIGHT_REV = 0x28;
    private static int EVT_8885_LEFT_CTROFF = 0x2C;
    private static int EVT_8885_RIGHT_CTROFF = 0x30;
    private static int EVT_EV3_BEACON = 0x34;

    private static int REPEAT_DELAY = 200;

    private DeviceControlActivity myActivity;
    private View myView;

    private Handler leftHandler = new Handler();
    private byte[] leftCommand;
    private int leftChannel = 0;
    private Runnable leftRunnable = new Runnable() {
        @Override
        public void run() {
            myActivity.sendPFxCommand(leftCommand);
            leftHandler.postDelayed(this, REPEAT_DELAY);
        }
    };

    private Handler rightHandler = new Handler();
    private byte[] rightCommand;
    private int rightChannel = 0;
    private Runnable rightRunnable = new Runnable() {
        @Override
        public void run() {
            myActivity.sendPFxCommand(rightCommand);
            rightHandler.postDelayed(this, REPEAT_DELAY);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        myView = inflater.inflate(R.layout.joystick_remote, container, false);

        Button button = (Button) myView.findViewById(R.id.joystick_left_up_button);
        button.setOnTouchListener(this);
        button = (Button) myView.findViewById(R.id.joystick_left_down_button);
        button.setOnTouchListener(this);
        button = (Button) myView.findViewById(R.id.joystick_right_up_button);
        button.setOnTouchListener(this);
        button = (Button) myView.findViewById(R.id.joystick_right_down_button);
        button.setOnTouchListener(this);

        button = (Button) myView.findViewById(R.id.joystick_beacon_button);
        button.setOnClickListener(this);

        myActivity = (DeviceControlActivity) getActivity();

        return myView;
    }

    @Override
    public void onDestroyView() {
        leftHandler.removeCallbacks(leftRunnable);
        rightHandler.removeCallbacks(rightRunnable);
        super.onDestroyView();
    }

    @Override
    public void onClick(View v) {
        DeviceControlActivity activity = (DeviceControlActivity) getActivity();

        activity.sendPFxCommand(DeviceControlActivity.pfxRemoteCommand(EVT_EV3_BEACON, currentChannel() ));
    }

    @Override
    public boolean onTouch(View v, MotionEvent motion) {
        switch (motion.getAction()) {
            case MotionEvent.ACTION_DOWN:
                switch (v.getId()) {
                    case R.id.joystick_left_up_button:
                        leftChannel = currentChannel();
                        leftCommand = DeviceControlActivity.pfxRemoteCommand(EVT_8885_LEFT_FWD, leftChannel );
                        leftHandler.post(leftRunnable);
                        break;
                    case R.id.joystick_left_down_button:
                        leftChannel = currentChannel();
                        leftCommand = DeviceControlActivity.pfxRemoteCommand(EVT_8885_LEFT_REV, leftChannel );
                        leftHandler.post(leftRunnable);
                        break;
                    case R.id.joystick_right_up_button:
                        rightChannel = currentChannel();
                        rightCommand = DeviceControlActivity.pfxRemoteCommand(EVT_8885_RIGHT_FWD, rightChannel );
                        rightHandler.post(rightRunnable);
                        break;
                    case R.id.joystick_right_down_button:
                        rightChannel = currentChannel();
                        rightCommand = DeviceControlActivity.pfxRemoteCommand(EVT_8885_RIGHT_REV, rightChannel );
                        rightHandler.post(rightRunnable);
                        break;
                }
                break;
            case MotionEvent.ACTION_UP:
                switch (v.getId()) {
                    case R.id.joystick_left_up_button:
                    case R.id.joystick_left_down_button: {
                        leftHandler.removeCallbacks(leftRunnable);
                        myActivity.sendPFxCommand(DeviceControlActivity.pfxRemoteCommand(EVT_8885_LEFT_CTROFF, leftChannel));
                    }
                    break;
                    case R.id.joystick_right_up_button:
                    case R.id.joystick_right_down_button: {
                        rightHandler.removeCallbacks(rightRunnable);
                        myActivity.sendPFxCommand(DeviceControlActivity.pfxRemoteCommand(EVT_8885_RIGHT_CTROFF, rightChannel));
                    }
                    break;
                }
                break;
        }
        return true;
    }

    private int currentChannel() {
        int currentChannel = 0;

        RadioGroup radioGroup = (RadioGroup)myView.findViewById(R.id.channel_buttons);
        switch( radioGroup.getCheckedRadioButtonId() ) {
            case R.id.channel_1_button:
                currentChannel = 0;
                break;
            case R.id.channel_2_button:
                currentChannel = 1;
                break;
            case R.id.channel_3_button:
                currentChannel = 2;
                break;
            case R.id.channel_4_button:
                currentChannel = 3;
                break;
        }

        return currentChannel;
    }
}