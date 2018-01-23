package com.bluetooth.bdsk.ui;

/**
 * Created by blitz on 11/07/17.
 */

import android.app.Activity;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.bluetooth.bdsk.Constants;
import com.bluetooth.bdsk.R;
import com.bluetooth.bdsk.bluetooth.BleAdapterService;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.Timer;

import static java.lang.Boolean.TRUE;

public class PeripheralControlActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_ID = "id";

    private BleAdapterService bluetooth_le_adapter;

    private String device_name;
    private String device_address;
    private Timer mTimer;
    private boolean sound_alarm_on_disconnect = false;
    private int alert_level;
    private boolean back_requested = false;
    private boolean share_with_server = false;
    private Switch share_switch;
    private static volatile int CommListIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peripheral_control);

        //read intent data
        final Intent intent = getIntent();
        device_name = intent.getStringExtra(EXTRA_NAME);
        device_address = intent.getStringExtra(EXTRA_ID);

        //show the device name
        ((TextView) this.findViewById(R.id.nameTextView)).setText("Device : "+device_name+"["+device_address+"]"); //hid the coloured rectangle used to show green/amber/red rssi distance
        ((LinearLayout) this.findViewById(R.id.rectangle)).setVisibility(View.INVISIBLE);

        //disable the noise button
        ((Button) PeripheralControlActivity.this.findViewById(R.id.pauseButton)).setEnabled(false);

        //disable the LOW/MID/HIGH alert level selection buttons
        ((Button) this.findViewById(R.id.lowButton)).setEnabled(false);
        ((Button) this.findViewById(R.id.midButton)).setEnabled(false);
        ((Button) this.findViewById(R.id.highButton)).setEnabled(false);

        share_switch = (Switch) this.findViewById(R.id.switch1);
        share_switch.setEnabled(false);
        share_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // we'll complete this later
            }
        });

        // connect to the Bluetooth adapter service
        Intent gattServiceIntent = new Intent(this, BleAdapterService.class);
        bindService(gattServiceIntent, service_connection, BIND_AUTO_CREATE);
        showMsg("READY");
    }

    private void showMsg(final String msg) {
        Log.d(Constants.TAG, msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.msgTextView)).setText(msg);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(service_connection);
        bluetooth_le_adapter = null;
    }

    private final ServiceConnection service_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetooth_le_adapter = ((BleAdapterService.LocalBinder) service).getService();
            bluetooth_le_adapter.setActivityHandler(message_handler);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetooth_le_adapter = null;
        }
    };

    private Handler message_handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle;
            String service_uuid = "";
            String characteristic_uuid = "";
            byte[] b = null;
            //message handling logic
            switch (msg.what) {
                case BleAdapterService.MESSAGE:
                    bundle = msg.getData();
                    String text = bundle.getString(BleAdapterService.PARCEL_TEXT);
                    showMsg(text);
                    break;

                case BleAdapterService.GATT_CONNECTED:
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.connectButton)).setEnabled(false);
                    //we're connected
                    showMsg("CONNECTED");
                    // enable the LOW/MID/HIGH alert level selection buttons
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.lowButton)).setEnabled(true);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.midButton)).setEnabled(true);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.highButton)).setEnabled(true);
                    bluetooth_le_adapter.discoverServices();
                    break;

                case BleAdapterService.GATT_DISCONNECT:
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.connectButton)).setEnabled(true);
                    //we're disconnected
                    showMsg("DISCONNECTED");
                    // hide the rssi distance colored rectangle
                    ((LinearLayout) PeripheralControlActivity.this.findViewById(R.id.rectangle)).setVisibility(View.INVISIBLE);
                    // disable the LOW/MID/HIGH alert level selection buttons
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.lowButton)).setEnabled(false);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.midButton)).setEnabled(false);
                    ((Button) PeripheralControlActivity.this.findViewById(R.id.highButton)).setEnabled(false);
                    if (back_requested) {
                        PeripheralControlActivity.this.finish();
                    }
                    break;

                case BleAdapterService.GATT_SERVICES_DISCOVERED:
                    //validate services and if ok...
                    List<BluetoothGattService> slist = bluetooth_le_adapter.getSupportedGattServices();
                    boolean time_point_service_present = false;
                    boolean current_time_service_present = false;
                    boolean pots_service_present = false;
                    boolean battery_service_present = false;
                    boolean valve_controller_service_present = false;

                    for (BluetoothGattService svc : slist) {
                        Log.d(Constants.TAG, "UUID=" + svc.getUuid().toString().toUpperCase() + "INSTANCE=" + svc.getInstanceId());
                        String serviceUuid = svc.getUuid().toString().toUpperCase();
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.TIME_POINT_SERVICE_SERVICE_UUID)) {
                            time_point_service_present = true;
                            continue;
                        }
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.CURRENT_TIME_SERVICE_SERVICE_UUID)) {
                            current_time_service_present = true;
                            continue;
                        }
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.POTS_SERVICE_SERVICE_UUID)) {
                            pots_service_present = true;
                            continue;
                        }
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.BATTERY_SERVICE_SERVICE_UUID)) {
                            battery_service_present = true;
                            continue;
                        }
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID)) {
                            valve_controller_service_present = true;
                            continue;
                        }
                    }

                    if (time_point_service_present && current_time_service_present && pots_service_present && battery_service_present) {
                        showMsg("Device has expected services");


                        //enable the LOW/MID/HIGH alert level selection buttons
                        //((Button) PeripheralControlActivity.this.findViewById(R.id.lowButton)).setEnabled(true);
                        //((Button) PeripheralControlActivity.this.findViewById(R.id.midButton)).setEnabled(true);
                        //((Button) PeripheralControlActivity.this.findViewById(R.id.highButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.potsButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.timeButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.timepointButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.flushOpen)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.flushClose)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.startButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.stopButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.batteryButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.pauseButton)).setEnabled(true);
                        ((Button) PeripheralControlActivity.this.findViewById(R.id.logButton)).setEnabled(true);

                    } else {
                        showMsg("Device does not have expected GATT services");
                    }
                    break;

                case BleAdapterService.GATT_CHARACTERISTIC_READ:
                    bundle = msg.getData();
                    Log.d(Constants.TAG, "Service=" + bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString().toUpperCase() + " Characteristic=" + bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase());
                    if(bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase().equals(BleAdapterService.BATTERY_LEVEL_CHARACTERISTIC_UUID)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        if(b.length > 0) {
                            PeripheralControlActivity.this.setAlertLevel((int) b[0]);
                            showMsg("Received " + b.toString() + "from Pebble.");
                            showMsg("Battery characteristic non-empty = " + (int)b[0]);
                        } else {
                            showMsg("Battery characteristic empty");
                        }
                    }
                    if(bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase().equals(BleAdapterService.LOG_CHARACTERISTIC_UUID)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        if(b.length > 0) {
                            PeripheralControlActivity.this.setAlertLevel((int) b[0]);
                            showMsg("Received " + b.toString() + "from Pebble.");
                            showMsg("Log data = " + (int)b[0]);
                        } else {
                            showMsg("Battery characteristic empty");
                        }
                    }

                    break;

                case BleAdapterService.GATT_CHARACTERISTIC_WRITTEN:
                    bundle = msg.getData();
                    if (bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString()
                            .toUpperCase().equals(BleAdapterService.ALERT_LEVEL_CHARACTERISTIC)
                            && bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString().toUpperCase().equals(BleAdapterService.LINK_LOSS_SERVICE_UUID)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        if (b.length > 0) {
                            PeripheralControlActivity.this.setAlertLevel((int) b[0]);
                        }
                    }
                    if (bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString()
                            .toUpperCase().equals(BleAdapterService.NEW_WATERING_TIME_POINT_CHARACTERISTIC_UUID)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        if (b.length > 0) {
                            showMsg("Received ack");
                            CommListIndex++;
                            if(CommListIndex < 28) {
                                onSendTPDynamic();
                            }
                            else
                            {
                                CommListIndex = 0;

                            }
                        }
                    }
                    break;
            }
        }
    };

    public void onLow(View view) {
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.LINK_LOSS_SERVICE_UUID,
                BleAdapterService.ALERT_LEVEL_CHARACTERISTIC, Constants.ALERT_LEVEL_LOW
        );
    }

    public void onMid(View view) {
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.LINK_LOSS_SERVICE_UUID,
                BleAdapterService.ALERT_LEVEL_CHARACTERISTIC, Constants.ALERT_LEVEL_MID
        );
    }

    public void onHigh(View view) {
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.LINK_LOSS_SERVICE_UUID,
                BleAdapterService.ALERT_LEVEL_CHARACTERISTIC, Constants.ALERT_LEVEL_HIGH
        );
    }

    public void onSetTime(View view) {
        Calendar calendar = Calendar.getInstance();

        //Set present time as data packet
        byte hours = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        byte minutes = (byte) calendar.get(Calendar.MINUTE);
        byte seconds = (byte) calendar.get(Calendar.SECOND);
        byte DATE = (byte) calendar.get(Calendar.DAY_OF_MONTH);
        byte MONTH = (byte) ((calendar.get(Calendar.MONTH)) + 1);
        int iYEARMSB = (calendar.get(Calendar.YEAR) / 256);
        int iYEARLSB = (calendar.get(Calendar.YEAR) % 256);
        byte bYEARMSB = (byte) iYEARMSB;
        byte bYEARLSB = (byte) iYEARLSB;

        //Set 1,2,3,4,5,6,7 as data packet
        /*byte hours = (byte) 1;
        byte minutes = (byte) 2;
        byte seconds = (byte) 3;
        byte DATE = (byte) 4;
        byte MONTH = (byte) 5;
        //int iYEARMSB = (calendar.get(Calendar.YEAR) / 256);
        //int iYEARLSB = (calendar.get(Calendar.YEAR) % 256);
        //byte bYEARMSB = (byte) iYEARMSB;
        //byte bYEARLSB = (byte) iYEARLSB;
        byte bYEARMSB = (byte) 6;
        byte bYEARLSB = (byte) 7;*/

        byte[] currentTime = {hours, minutes, seconds, DATE, MONTH, bYEARMSB, bYEARLSB};

        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.CURRENT_TIME_SERVICE_SERVICE_UUID,
                BleAdapterService.CURRENT_TIME_CHARACTERISTIC_UUID, currentTime
        );
    }

    public void onSetPots(View view) {
        byte numberOfPots = (byte) 5;
        byte[] pots = {numberOfPots};
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.POTS_SERVICE_SERVICE_UUID,
                BleAdapterService.POTS_CHARACTERISTIC_UUID, pots
        );
    }

    public void onFlushOpen(View view) {
        byte[] valveCommand = {1};
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onFlushClose(View view) {
        byte[] valveCommand = {5};
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onStart(View view) {
        byte[] valveCommand = {2};
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onStop(View view) {
        byte[] valveCommand = {3};
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onPause(View view) {
        byte[] valveCommand = {4};
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onSendTP(View view) {

        /*byte hours = (byte) 1;
        byte minutes = (byte) 2;
        byte seconds = (byte) 3;
        byte dayOfTheWeek = (byte) 4;
        byte durationMsb = (byte) 5;
        byte durationLsb = (byte) 6;
        byte volumeMsb = (byte) 7;
        byte volumeLsb = (byte) 8;*/

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 2);

        //Set present time as data packet
        byte index = (byte) CommListIndex;
        byte dayOfTheWeek = (byte) calendar.get(Calendar.DAY_OF_WEEK);
        byte hours = (byte) calendar.get(Calendar.HOUR);
        if(calendar.get(Calendar.AM_PM) == 1)
        {hours = (byte) (calendar.get(Calendar.HOUR) + 12);}
        else {hours = (byte) (calendar.get(Calendar.HOUR));}
        byte minutes = (byte) calendar.get(Calendar.MINUTE);
        byte seconds = (byte) calendar.get(Calendar.SECOND);
        int duration = 555;
        int volume = 555;
        int iDurationMSB = (duration / 256);
        int iDurationLSB = (duration % 256);
        byte bDurationMSB = (byte) iDurationMSB;
        byte bDurationLSB = (byte) iDurationLSB;
        int iVolumeMSB = (volume / 256);
        int iVolumeLSB = (volume % 256);
        byte bVolumeMSB = (byte) iVolumeMSB;
        byte bVolumeLSB = (byte) iVolumeLSB;

        byte[] timePoint = {index, dayOfTheWeek, hours, minutes, seconds, bDurationMSB, bDurationLSB, bVolumeMSB, bVolumeLSB};
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.TIME_POINT_SERVICE_SERVICE_UUID,
                BleAdapterService.NEW_WATERING_TIME_POINT_CHARACTERISTIC_UUID, timePoint
        );
    }

    public void onSendTPDynamic() {

        /*byte hours = (byte) 1;
        byte minutes = (byte) 2;
        byte seconds = (byte) 3;
        byte dayOfTheWeek = (byte) 4;
        byte durationMsb = (byte) 5;
        byte durationLsb = (byte) 6;
        byte volumeMsb = (byte) 7;
        byte volumeLsb = (byte) 8;*/

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 2);

        //Set present time as data packet
        byte index = (byte) CommListIndex;
        byte dayOfTheWeek = (byte) calendar.get(Calendar.DAY_OF_WEEK);
        byte hours = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        byte minutes = (byte) calendar.get(Calendar.MINUTE);
        byte seconds = (byte) calendar.get(Calendar.SECOND);
        int duration = 555 + CommListIndex;
        int volume = 555 - CommListIndex;
        int iDurationMSB = (duration / 256);
        int iDurationLSB = (duration % 256);
        byte bDurationMSB = (byte) iDurationMSB;
        byte bDurationLSB = (byte) iDurationLSB;
        int iVolumeMSB = (volume / 256);
        int iVolumeLSB = (volume % 256);
        byte bVolumeMSB = (byte) iVolumeMSB;
        byte bVolumeLSB = (byte) iVolumeLSB;

        byte[] timePoint = {index, dayOfTheWeek, hours, minutes, seconds, bDurationMSB, bDurationLSB, bVolumeMSB, bVolumeLSB};
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.TIME_POINT_SERVICE_SERVICE_UUID,
                BleAdapterService.NEW_WATERING_TIME_POINT_CHARACTERISTIC_UUID, timePoint
        );
    }

    public void onBattery(View view) {
        if(bluetooth_le_adapter.readCharacteristic(
                BleAdapterService.BATTERY_SERVICE_SERVICE_UUID,
                BleAdapterService.BATTERY_LEVEL_CHARACTERISTIC_UUID
        ) == TRUE) {
            showMsg("Battery Level Read");

        } else {
            showMsg("Reading Battery Level failed");
        }
        ;
    }

    public void onReadLog(View view) {
        if(bluetooth_le_adapter.readCharacteristic(
                BleAdapterService.BATTERY_SERVICE_SERVICE_UUID,
                BleAdapterService.BATTERY_LEVEL_CHARACTERISTIC_UUID
        ) == TRUE) {
            showMsg("Battery Level Read");

        } else {
            showMsg("Reading Battery Level failed");
        }
        ;
    }

    public void onNoise(View view) {

    }

    public void onConnect(View view) {
        showMsg("onConnect");
        if (bluetooth_le_adapter != null) {
            if (bluetooth_le_adapter.connect(device_address)) {
                ((Button) PeripheralControlActivity.this
                .findViewById(R.id.connectButton)).setEnabled(false);
            } else {
                showMsg("onConnect: failed to connect");
            }
        } else {
            showMsg("onConnect: bluetooth_le_adapter=null");
        }
    }

    public void onBackPressed() {
        Log.d(Constants.TAG, "onBackPressed");
        back_requested = true;
        if (bluetooth_le_adapter.isConnected()) {
            try {
                bluetooth_le_adapter.disconnect();
            } catch (Exception e) {

            }
        } else {
            finish();
        }
    }

    private void setAlertLevel(int alert_level) {
        this.alert_level = alert_level;
        ((Button) this.findViewById(R.id.lowButton)).setTextColor(Color.parseColor("#000000"));
        ;
        ((Button) this.findViewById(R.id.midButton)).setTextColor(Color.parseColor("#000000"));
        ;
        ((Button) this.findViewById(R.id.highButton)).setTextColor(Color.parseColor("#000000"));
        ;

        switch (alert_level) {
            case 0:
                ((Button) this.findViewById(R.id.lowButton)).setTextColor(Color.parseColor("#FF0000"));;
                break;

            case 1:
                ((Button) this.findViewById(R.id.midButton)).setTextColor(Color.parseColor("#FF0000"));
                break;

            case 2:
                ((Button) this.findViewById(R.id.highButton)).setTextColor(Color.parseColor("#FF0000"));
                break;
        }
    }

}
