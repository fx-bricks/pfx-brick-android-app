# pfx-brick-android-app
This is the source code for the reference Android mobile app.

It shows you how to connect to the PFx Brick and communicate with it. It implements a simple interface which replicates the functionality of the Power Functions remote controls (the speed remote and the joystick remote).

Communication with the PFx Brick is done using the Bluetooth Low Energy protocol, the code for which is in the BluetoothScanner and BluetoothLeService classes.

The initial interface, responsible for scanning for devices, is in the DeviceScanActivity class.

Once a PFx Brick is found and selected, all interaction with the device is done in the DeviceControlActivity class. There are three fragments loaded by this activity, responsible for controlling the PFx Brick.

The DeviceInfoFragment class displays all the relevant information about the PFx Brick.

The JoystickRemoteFragment replicates the functionality of the Power Functions Joystick Remote.

The SpeedRemoteFragment replicates the functionality of the Power Functions Speed Remote.
