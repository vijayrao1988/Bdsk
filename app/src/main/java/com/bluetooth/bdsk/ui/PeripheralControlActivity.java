package com.bluetooth.bdsk.ui;

/**
 * Created by blitz on 11/07/17.
 */

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.bluetooth.bdsk.Constants;
import com.bluetooth.bdsk.R;
import com.bluetooth.bdsk.bluetooth.BleAdapterService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.Timer;

import static com.bluetooth.bdsk.Constants.TAG;
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
    private static volatile int LogReadingIndex = 0;
    private File logFile;
    private FileOutputStream logOutputHandler;

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
        Log.d(TAG, msg);
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
                    boolean pebble_service_present = false;

                    for (BluetoothGattService svc : slist) {
                        Log.d(TAG, "UUID=" + svc.getUuid().toString().toUpperCase() + "INSTANCE=" + svc.getInstanceId());
                        String serviceUuid = svc.getUuid().toString().toUpperCase();
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.PEBBLE_SERVICE_UUID)) {
                            pebble_service_present = true;
                            continue;
                        }
                    }

                    if (pebble_service_present) {
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
                    Log.d(TAG, "Service=" + bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString().toUpperCase() + " Characteristic=" + bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase());
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
                        String str = "",eventData = "";
                        if(b.length > 0) {
                            if (b[0]!= 0) {

                                int volume=0;
                                int duration=0;
                                long date = ((16777216 * bluetooth_le_adapter.convertByteToInt(b[1])) + (65536 * bluetooth_le_adapter.convertByteToInt(b[2])) + (256 * bluetooth_le_adapter.convertByteToInt(b[3])) + bluetooth_le_adapter.convertByteToInt(b[4]));
                                showMsg("date = " + date);
                                //Date eventDate = new Date(date * 1000); //Arduino provides seconds since 1 Jan 1970. Android uses milliseconds since 1 Jan 1970. So, multiplying by 1000.
                                String dateString = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date(date * 1000));

                                byte[] bytes = {b[5], b[6], b[7], b[8], b[9], b[10]};


                                StringBuilder sb = new StringBuilder();
                                for (byte data : bytes) {
                                    sb.append(String.format("%02X", data));
                                }
                                    //byte[] bytes = {b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14]};

                                    /*try {
                                        str = new String(bytes, "UTF-8");  // Best way to decode using "UTF-8"
                                    } catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }*/
                                //}
                                switch(b[0])
                                {
                                    case 1 :    str = "Device connected MAC=";
                                                eventData = sb.toString();
                                                break;

                                    case 2 :    str = "Device disconnected MAC=";
                                                eventData = sb.toString();
                                                break;

                                    case 17 :   str = "Valve open";
                                                duration = (256 * bluetooth_le_adapter.convertByteToInt(b[11])) + bluetooth_le_adapter.convertByteToInt(b[12]);
                                                volume = (256 * bluetooth_le_adapter.convertByteToInt(b[13])) + bluetooth_le_adapter.convertByteToInt(b[14]);
                                                eventData = "Volume = "+volume+" "+"Duration = "+duration;
                                                break;

                                    case 18 :   str = "Valve close";
                                                volume = (256 * bluetooth_le_adapter.convertByteToInt(b[13])) + bluetooth_le_adapter.convertByteToInt(b[14]);
                                                eventData = "Counter value = "+volume;
                                                break;

                                    case 20 :   str = "Old timepoints erased";
                                                eventData = "";
                                                break;

                                    case 21 :   str = "New session time point:";
                                                String[] dayOfWeek ={"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
                                                eventData = (dayOfWeek[b[7]-1]+" "+bluetooth_le_adapter.convertByteToInt(b[8])+":"+bluetooth_le_adapter.convertByteToInt(b[9])+":"+bluetooth_le_adapter.convertByteToInt(b[10]));
                                                break;

                                    case 81 :   str = "Flush open";
                                                break;

                                    case 82 :   str = "Flush close";
                                                volume = (256 * bluetooth_le_adapter.convertByteToInt(b[13])) + bluetooth_le_adapter.convertByteToInt(b[14]);
                                                eventData = "Counter value = "+volume;
                                                break;

                                    case 97 :   str =  "Pebble Start";
                                                break;

                                    case 98 :   str = "Pebble Stop";
                                                break;

                                    case 99 :   str = "Pebble pause";
                                                break;

                                    case 113 :  str = "No. of pots";
                                                duration = (256 * bluetooth_le_adapter.convertByteToInt(b[11])) + bluetooth_le_adapter.convertByteToInt(b[12]);
                                                eventData = ": "+duration;
                                                break;
                                }
                                //showMsg("Event code=" + Integer.toHexString(b[0]) + " " + dateString + ", " + sb.toString() + ","+ volume + ","+ duration);
                                        showMsg(dateString + " " +"Event="+Integer.toHexString(b[0])+" "+str +" "+eventData);

                                if (createLogFile()) {
                                    try {

                                        //logOutputHandler.write(("Event code=" + Integer.toHexString(b[0]) + ", " + dateString +" "+ sb.toString()+ ", "+ volume + ", "+ duration+ "\n").getBytes());
                                        logOutputHandler.write((dateString + " "+"Event="+Integer.toHexString(b[0])+" "+str +" "+eventData+ "\n").getBytes());
                                        logOutputHandler.close();

                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    onReadLogDynamic();
                                }
                                //writeToFile("Event code="+Integer.toHexString(b[0])+" "+eventDate.toString());
                            }

                        } else {
                            showMsg("No log data");
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
                          /* CommListIndex++;
                            if(CommListIndex < 28) {
                                onSendTPDynamic();
                            }
                            else
                            {
                                CommListIndex = 0;

                            }*/

                        }
                    }
                    if (bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString()
                            .toUpperCase().equals(BleAdapterService.LOG_CHARACTERISTIC_UUID)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        if (b.length > 0) {
                            showMsg("Read characteristic : " + String.valueOf(LogReadingIndex));
                            if(bluetooth_le_adapter.readCharacteristic(
                                    BleAdapterService.PEBBLE_SERVICE_UUID,
                                    BleAdapterService.LOG_CHARACTERISTIC_UUID
                            ) == TRUE) {
                                showMsg("Log Event Read");
                                LogReadingIndex++;

                            } else {
                                showMsg("Log Event Read Failed");
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

        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.CURRENT_TIME_SERVICE_SERVICE_UUID,
                BleAdapterService.CURRENT_TIME_CHARACTERISTIC_UUID, currentTime
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.PEBBLE_SERVICE_UUID,
                BleAdapterService.CURRENT_TIME_CHARACTERISTIC_UUID, currentTime
        );
    }

    public void onSetPots(View view) {
        byte numberOfPots = (byte) 5;
        byte[] pots = {numberOfPots};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.POTS_SERVICE_SERVICE_UUID,
                BleAdapterService.POTS_CHARACTERISTIC_UUID, pots
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.PEBBLE_SERVICE_UUID,
                BleAdapterService.POTS_CHARACTERISTIC_UUID, pots
        );
    }

    public void onFlushOpen(View view) {
        byte[] valveCommand = {1};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.PEBBLE_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onFlushClose(View view) {
        byte[] valveCommand = {5};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.PEBBLE_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onStart(View view) {
        byte[] valveCommand = {2};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.PEBBLE_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onStop(View view) {
        byte[] valveCommand = {3};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.PEBBLE_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );
    }

    public void onPause(View view) {
        byte[] valveCommand = {4};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.VALVE_CONTROLLER_SERVICE_UUID,
                BleAdapterService.COMMAND_CHARACTERISTIC_UUID, valveCommand
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.PEBBLE_SERVICE_UUID,
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
        byte index = (byte) 9;
        byte dayOfTheWeek = (byte) calendar.get(Calendar.DAY_OF_WEEK);
        byte hours = (byte) calendar.get(Calendar.HOUR);
        if(calendar.get(Calendar.AM_PM) == 1)
        {hours = (byte) (calendar.get(Calendar.HOUR) + 12);}
        else {hours = (byte) (calendar.get(Calendar.HOUR));}
        byte minutes = (byte) calendar.get(Calendar.MINUTE);
        byte seconds = (byte) calendar.get(Calendar.SECOND);
        int duration = 2;
        int volume = 5000;
        int iDurationMSB = (duration / 256);
        int iDurationLSB = (duration % 256);
        byte bDurationMSB = (byte) iDurationMSB;
        byte bDurationLSB = (byte) iDurationLSB;
        int iVolumeMSB = (volume / 256);
        int iVolumeLSB = (volume % 256);
        byte bVolumeMSB = (byte) iVolumeMSB;
        byte bVolumeLSB = (byte) iVolumeLSB;

        byte[] timePoint = {index, dayOfTheWeek, hours, minutes, seconds, bDurationMSB, bDurationLSB, bVolumeMSB, bVolumeLSB};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.TIME_POINT_SERVICE_SERVICE_UUID,
                BleAdapterService.NEW_WATERING_TIME_POINT_CHARACTERISTIC_UUID, timePoint
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.PEBBLE_SERVICE_UUID,
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
        int duration = 2;//555 + (100 * CommListIndex);
        int volume = 5555 - (100 * CommListIndex);
        int iDurationMSB = (duration / 256);
        int iDurationLSB = (duration % 256);
        byte bDurationMSB = (byte) iDurationMSB;
        byte bDurationLSB = (byte) iDurationLSB;
        int iVolumeMSB = (volume / 256);
        int iVolumeLSB = (volume % 256);
        byte bVolumeMSB = (byte) iVolumeMSB;
        byte bVolumeLSB = (byte) iVolumeLSB;

        byte[] timePoint = {index, dayOfTheWeek, hours, minutes, seconds, bDurationMSB, bDurationLSB, bVolumeMSB, bVolumeLSB};
        /*bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.TIME_POINT_SERVICE_SERVICE_UUID,
                BleAdapterService.NEW_WATERING_TIME_POINT_CHARACTERISTIC_UUID, timePoint
        );*/
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.PEBBLE_SERVICE_UUID,
                BleAdapterService.NEW_WATERING_TIME_POINT_CHARACTERISTIC_UUID, timePoint
        );
    }

    public void onBattery(View view) {
        if(bluetooth_le_adapter.readCharacteristic(
                //BleAdapterService.BATTERY_SERVICE_SERVICE_UUID,
                BleAdapterService.PEBBLE_SERVICE_UUID,
                BleAdapterService.BATTERY_LEVEL_CHARACTERISTIC_UUID
        ) == TRUE) {
            showMsg("Battery Level Read");

        } else {
            showMsg("Reading Battery Level failed");
        }
    }

    public void onReadLog(View view)
    {
        int iLogReadingIndexMSB = LogReadingIndex / 256;
        int iLogReadingIndexLSB = LogReadingIndex % 256;
        byte readingIndex0 = (byte) iLogReadingIndexMSB;
        byte readingIndex1 = (byte) iLogReadingIndexLSB;
        byte[] readingIndex = {readingIndex0, readingIndex1};
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.PEBBLE_SERVICE_UUID,
                BleAdapterService.LOG_CHARACTERISTIC_UUID, readingIndex
        );
    }

    public void onReadLogDynamic()
    {
        int iLogReadingIndexMSB = LogReadingIndex / 256;
        int iLogReadingIndexLSB = LogReadingIndex % 256;
        byte readingIndex0 = (byte) iLogReadingIndexMSB;
        byte readingIndex1 = (byte) iLogReadingIndexLSB;
        byte[] readingIndex = {readingIndex0, readingIndex1};
        bluetooth_le_adapter.writeCharacteristic(
                BleAdapterService.PEBBLE_SERVICE_UUID,
                BleAdapterService.LOG_CHARACTERISTIC_UUID, readingIndex
        );
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
        Log.d(TAG, "onBackPressed");
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

    //******************************Create Log data text logFile for received data from Arduino**********************************
    boolean createLogFile() {
        //Toast.makeText(getBaseContext(), "inside create", Toast.LENGTH_SHORT).show();
        if(!(isExternalStorageWritable()&&isStoragePermissionGranted()))
            return false;	// Check if the external storage is available for write
        else {
            String dirPath = Environment.getExternalStorageDirectory().getPath()+"/Logger/";
        //String dirPath = getFilesDir().getAbsolutePath()+"/Logger/";
            File directory = new File(dirPath);
            logFile =new File(dirPath+"/LogData.txt");

            if(!(logFile.exists()))
            {
                try {
                    if (!(directory.exists() && directory.isDirectory())) {    //If the Directory does not exist: create a new one
                        if (directory.mkdir()) {
                            System.out.println("Directory created");
                        } else {
                            System.out.println("Directory is not created");
                        }
                    }
                    logOutputHandler = new FileOutputStream(logFile);    //// Create File for time
                    //logOutputHandler.write(("\n").getBytes());

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                try{
                    logOutputHandler = new FileOutputStream(logFile, true);
                    // logOutputHandler.write(("\n\n").getBytes());
                }
                catch(IOException e)
                {
                    e.printStackTrace();
                }
            }

       }
        return true;
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        //Toast.makeText(getBaseContext(),state, Toast.LENGTH_SHORT).show();
        //showMsg(state);
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public  boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23)
        {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }
}
