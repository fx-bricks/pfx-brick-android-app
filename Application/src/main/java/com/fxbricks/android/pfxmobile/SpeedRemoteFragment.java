package com.fxbricks.android.pfxmobile;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class SpeedRemoteFragment extends Fragment implements View.OnClickListener, View.OnTouchListener {
    public static SpeedRemoteFragment newInstance() {
        SpeedRemoteFragment fragment = new SpeedRemoteFragment();
        return fragment;
    }

    private static int EVT_8879_TWO_BUTTONS = 0x00;
    private static int EVT_8879_LEFT_BUTTON = 0x04;
    private static int EVT_8879_RIGHT_BUTTON = 0x08;
    private static int EVT_8879_LEFT_INC = 0x0C;
    private static int EVT_8879_LEFT_DEC = 0x10;
    private static int EVT_8879_RIGHT_INC = 0x14;
    private static int EVT_8879_RIGHT_DEC = 0x18;

    private static int REPEAT_DELAY = 200;

    private DeviceControlActivity myActivity;
    private View myView;
    private int myCurrentChannel = 0;

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
        myView = inflater.inflate(R.layout.speed_remote, container, false);

        Button button = (Button) myView.findViewById(R.id.speed_left_inc_button);
        button.setOnTouchListener(this);
        button = (Button) myView.findViewById(R.id.speed_left_dec_button);
        button.setOnTouchListener(this);
        button = (Button) myView.findViewById(R.id.speed_right_inc_button);
        button.setOnTouchListener(this);
        button = (Button) myView.findViewById(R.id.speed_right_dec_button);
        button.setOnTouchListener(this);

        button = (Button) myView.findViewById(R.id.channel_1_button);
        button.setOnClickListener(this);
        button = (Button) myView.findViewById(R.id.channel_2_button);
        button.setOnClickListener(this);
        button = (Button) myView.findViewById(R.id.channel_3_button);
        button.setOnClickListener(this);
        button = (Button) myView.findViewById(R.id.channel_4_button);
        button.setOnClickListener(this);

        button = (Button) myView.findViewById(R.id.speed_left_button);
        button.setOnClickListener(this);
        button = (Button) myView.findViewById(R.id.speed_right_button);
        button.setOnClickListener(this);
        button = (Button) myView.findViewById(R.id.speed_both_button);
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

        switch(v.getId() ) {
            case R.id.channel_1_button:
                myCurrentChannel = 0;
                break;
            case R.id.channel_2_button:
                myCurrentChannel = 1;
                break;
            case R.id.channel_3_button:
                myCurrentChannel = 2;
                break;
            case R.id.channel_4_button:
                myCurrentChannel = 3;
                break;
            case R.id.speed_left_button:
                activity.sendPFxCommand(DeviceControlActivity.pfxRemoteCommand(EVT_8879_LEFT_BUTTON, myCurrentChannel ));
                break;
            case R.id.speed_right_button:
                activity.sendPFxCommand(DeviceControlActivity.pfxRemoteCommand(EVT_8879_RIGHT_BUTTON, myCurrentChannel ));
                break;
            case R.id.speed_both_button:
                activity.sendPFxCommand(DeviceControlActivity.pfxRemoteCommand(EVT_8879_TWO_BUTTONS, myCurrentChannel ));
                break;
        }
    }
    @Override
    public boolean onTouch(View v, MotionEvent motion) {
        switch (motion.getAction()) {
            case MotionEvent.ACTION_DOWN:
                switch (v.getId()) {
                    case R.id.speed_left_inc_button:
                        leftChannel = myCurrentChannel;
                        leftCommand = DeviceControlActivity.pfxRemoteCommand(EVT_8879_LEFT_INC, leftChannel );
                        leftHandler.post(leftRunnable);
                        break;
                    case R.id.speed_left_dec_button:
                        leftChannel = myCurrentChannel;
                        leftCommand = DeviceControlActivity.pfxRemoteCommand(EVT_8879_LEFT_DEC, leftChannel );
                        leftHandler.post(leftRunnable);
                        break;
                    case R.id.speed_right_inc_button:
                        rightChannel = myCurrentChannel;
                        rightCommand = DeviceControlActivity.pfxRemoteCommand(EVT_8879_RIGHT_INC, rightChannel );
                        rightHandler.post(rightRunnable);
                        break;
                    case R.id.speed_right_dec_button:
                        rightChannel = myCurrentChannel;
                        rightCommand = DeviceControlActivity.pfxRemoteCommand(EVT_8879_RIGHT_DEC, rightChannel );
                        rightHandler.post(rightRunnable);
                        break;
                }
                break;
            case MotionEvent.ACTION_UP:
                switch (v.getId()) {
                    case R.id.speed_left_inc_button:
                    case R.id.speed_left_dec_button: {
                        leftHandler.removeCallbacks(leftRunnable);
                    }
                    break;
                    case R.id.speed_right_inc_button:
                    case R.id.speed_right_dec_button: {
                        rightHandler.removeCallbacks(rightRunnable);
                    }
                    break;
                }
                break;
        }
        return true;
    }
}