package com.bluetooth.bdsk.ui;

import android.Manifest;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bluetooth.bdsk.Constants;
import com.bluetooth.bdsk.R;
import com.bluetooth.bdsk.bluetooth.BleScanner;
import com.bluetooth.bdsk.bluetooth.ScanResultsConsumer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;

import static java.lang.Boolean.TRUE;
import static java.lang.Boolean.valueOf;


public class MainActivity extends AppCompatActivity implements ScanResultsConsumer {

    private boolean ble_scanning = false;
    private Handler handler = new Handler();
    private ListAdapter ble_device_list_adapter;
    private BleScanner ble_scanner;

    //This constant is the length of the scan (scan duration)
    private static final long SCAN_TIMEOUT = 600000;

    private static final int REQUEST_LOCATION = 0;
    private static String[] PERMISSIONS_LOCATION = {Manifest.permission.ACCESS_COARSE_LOCATION};
    private boolean permissions_granted=false;
    private int device_count=0;
    private Toast toast;
    private int TotalSensorDataProduced = 0;

    static class ViewHolder {
        public TextView text;
        public TextView bdaddr;
        public TextView serialNumber;
        public TextView rssi;
        public TextView temperature;
        public TextView battery;
        public TextView time;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setButtonText();
        ble_device_list_adapter = new ListAdapter();
        ListView listView = (ListView) this.findViewById(R.id.deviceList);
        listView.setAdapter(ble_device_list_adapter);
        ble_scanner = new BleScanner(this.getApplicationContext());
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(ble_scanning) {
                    setScanState(false);
                    ble_scanner.stopScanning();
                }
                BluetoothDevice device = ble_device_list_adapter.getDevice(position);
                if(toast != null) {
                    toast.cancel();
                }
                Intent intent = new Intent(MainActivity.this, PeripheralControlActivity.class);
                intent.putExtra(PeripheralControlActivity.EXTRA_NAME, device.getName());
                intent.putExtra(PeripheralControlActivity.EXTRA_ID, device.getAddress());
                startActivity(intent);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void candidateBleDevice(final ScanResult scanResult, int rssi) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //Check for ManufacturerID in ManufacturerSpecificData. 249 corresponds to StickNFind / BluVision in BluetoothSIG
                if(scanResult.getScanRecord().getManufacturerSpecificData().keyAt(0)==249) {
                    //Log.i(Constants.TAG,"Heard from a BluVision Device");
                    ble_device_list_adapter.addDevice(scanResult);
                    ble_device_list_adapter.notifyDataSetChanged();
                    device_count++;
                }

            }
        });
    }

    private void setButtonText() {
        String text="";
        text = Constants.FIND;
        final String button_text = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) MainActivity.this.findViewById(R.id.scanButton)).setText(button_text);
            }
        });
    }

    private void setScanState (boolean value) {
        ble_scanning = value;
        if(ble_scanning == true) {
            ((Button) this.findViewById(R.id.scanButton)).setText(Constants.STOP_SCANNING);
        } else {
            ((Button) this.findViewById(R.id.scanButton)).setText(Constants.FIND);
        }
    }


    private class ListAdapter extends BaseAdapter {
        private ArrayList<ScanResult> ble_devices;

        public ListAdapter() {
            super();
            ble_devices = new ArrayList<ScanResult>();
        }

        /**
         * Search the adapter for an existing device address and return it, otherwise return -1.
         */
        private int getPosition(String address) {
            int position = -1;
            for (int i = 0; i < ble_devices.size(); i++) {
                if (ble_devices.get(i).getDevice().getAddress().equals(address)) {
                    position = i;
                    break;
                }
            }
            return position;
        }


        public void addDevice(ScanResult scanResult) {

            int existingPosition = getPosition(scanResult.getDevice().getAddress());

            /*if (!ble_devices.contains(device)) {
                ble_devices.add(device);
                //Log.i("NewBleDevice",scanRecord.getDeviceName() + " - " + scanRecord.getManufacturerSpecificData().size()  + " - " + scanRecord.getManufacturerSpecificData() + " - " + scanRecord.getManufacturerSpecificData().keyAt(0) + " - " + scanRecord.getManufacturerSpecificData().valueAt(0).toString());
            }*/

            if (existingPosition >= 0) {
                //Device is already in the list, update its record
                ble_devices.set(existingPosition, scanResult);
            } else {
                //Add new device's scanResult to the list
                ble_devices.add(scanResult);
            }
        }

        public boolean contains(BluetoothDevice device) {
            return ble_devices.contains(device);
        }

        public BluetoothDevice getDevice(int position) {
            return ble_devices.get(position).getDevice();
        }

        public void clear() {
            ble_devices.clear();
        }

        @Override
        public int getCount() {
            return ble_devices.size();
        }

        @Override
        public Object getItem(int i) {
            return ble_devices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if (view == null) {
                view = MainActivity.this.getLayoutInflater().inflate(R.layout.list_row, null);
                viewHolder = new ViewHolder();
                viewHolder.text = (TextView) view.findViewById(R.id.textView);
                viewHolder.bdaddr = (TextView) view.findViewById(R.id.bdaddr);
                viewHolder.serialNumber = (TextView) view.findViewById((R.id.bluvisionSerialNumber));
                viewHolder.battery = (TextView) view.findViewById(R.id.bluvisionBattery);
                viewHolder.rssi = (TextView) view.findViewById(R.id.bluvisionRssi);
                viewHolder.temperature = (TextView) view.findViewById(R.id.bluvisionTemperature);
                viewHolder.time = (TextView) view.findViewById(R.id.bluvisionDeviceTime);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            ScanResult scanResult = ble_devices.get(i);
            BluetoothDevice device = scanResult.getDevice();
            String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.text.setText(deviceName); } else {
                viewHolder.text.setText("unknown device"); }


            viewHolder.bdaddr.setText("GID : " + "01:" + device.getAddress());


            //ScanRecordByte = SRB
            byte bSRB[] = scanResult.getScanRecord().getBytes();

            //byte bSerialNumber[] = Arrays.copyOfRange(bSRB,19,11);
            byte bSerialNumber[] = new byte[8];
            byte bDeviceTime[] = new byte[4];

            double iSRB[] = new double[bSRB.length];

            //Normalize bytes to unsigned
            int indexNormalize = 0;
            for(indexNormalize=0; indexNormalize<bSRB.length; indexNormalize++) {
                //if condition to extract serial number bytes from the scan record bytes
                if((indexNormalize>=12)&&(indexNormalize<=19)) {
                    bSerialNumber[19 - indexNormalize] = bSRB[indexNormalize];
                }

                //if condition to extract device time from the scan record bytes
                if((indexNormalize>=20)&&(indexNormalize<=23)) {
                    bDeviceTime[23 - indexNormalize] = bSRB[indexNormalize];
                }

                //moving scan record bytes to unsigned integers
                if(bSRB[indexNormalize] < 0) {
                    iSRB[indexNormalize] = 256 + bSRB[indexNormalize];


                } else {
                    iSRB[indexNormalize] = bSRB[indexNormalize];
                }
            }


            //Converting bSerialNumber bytes into a BigInteger for conversion from Binary Number to a Integer Representation
            BigInteger biSerialNumber = new BigInteger(1, bSerialNumber);

            //Converting bDeviceTime bytes into a BigInteger for conversion from Binary Number to a Integer Respresentation
            BigInteger biDeviceTime = new BigInteger(1, bDeviceTime);

            //If ScanRecord is needed, use the following line
            NumberFormat serialNumberString = DecimalFormat.getInstance();
            serialNumberString.setMaximumFractionDigits(0);
            serialNumberString.setGroupingUsed(false);
            viewHolder.serialNumber.setText("Serial Number : " + String.valueOf(biSerialNumber));


            //If ManufacturerSpecificData is needed, use the following line
            //viewHolder.battery.setText("Battery : " + " "  + bSRB[24] + " "  + bSRB[25] + " " + bSRB[26] + " " + bSRB[27] + " "  + bSRB[28] + " "  + bSRB[29] + " "  + bSRB[30] + " "  + bSRB[31]);
            viewHolder.battery.setText("Scan Record (Length=" + bSRB.length + "): " + bSRB[0] +" "+bSRB[1] +" "+bSRB[2] +" "+bSRB[3] +" "+bSRB[4]
                    +" "+bSRB[5]+" "+bSRB[6] +" "+bSRB[7] +" "+bSRB[8] +" "+bSRB[9]
                    +" "+bSRB[10] +" "+bSRB[11] +" "+bSRB[12] +" "+bSRB[13] +" "+bSRB[14]
                    +" "+bSRB[15] +" "+bSRB[16] +" "+bSRB[17] +" "+bSRB[18] +" "+bSRB[19]
                    +" "+bSRB[20] +" "+bSRB[21] +" "+bSRB[22] +" "+bSRB[23] +" "+bSRB[24]
                    +" "+bSRB[25] +" "+bSRB[26] +" "+bSRB[27] +" "+bSRB[28] +" "+bSRB[29]
                    +" "+bSRB[30] +" "+bSRB[31] +" "+bSRB[32] +" "+bSRB[33] +" "+bSRB[34]
                    +" "+bSRB[35] +" "+bSRB[36] +" "+bSRB[37] +" "+bSRB[38] +" "+bSRB[39]
                    +" "+bSRB[40] +" "+bSRB[41] +" "+bSRB[42] +" "+bSRB[43] +" "+bSRB[44]
                    +" "+bSRB[45] +" "+bSRB[46] +" "+bSRB[47] +" "+bSRB[48] +" "+bSRB[49]
                    +" "+bSRB[50] +" "+bSRB[51] +" "+bSRB[52] +" "+bSRB[53] +" "+bSRB[54]
                    +" "+bSRB[55] +" "+bSRB[56] +" "+bSRB[57] +" "+bSRB[58] +" "+bSRB[59]
                    +" "+bSRB[60] +" "+bSRB[61]);

            //viewHolder.battery.setText("Battery : -");

            viewHolder.rssi.setText("RSSI : " + scanResult.getRssi());

            viewHolder.temperature.setText("Temperature : " + bSRB[26]);

            viewHolder.time.setText("Time : " + String.valueOf(biDeviceTime));


            return view;
        }
    }

    public void onScan(View view) {
        if(!ble_scanner.isScanning()) {
            device_count = 0;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissions_granted = false;
                    requestLocationPermission();
                } else {
                    Log.i(Constants.TAG, "Location permission has already been granted. Starting scan.");
                    permissions_granted = true;
                }
            } else {
                // the ACCESS_COARSE_LOCATION permission did not exist before M so....
                permissions_granted = true;
            }
            startScanning();
        } else {
            ble_scanner.stopScanning();
        }
    }

    private void requestLocationPermission() {
        Log.i(Constants.TAG, "Location permission has NOT yet been granted. Requesting permission.");
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            Log.i(Constants.TAG, "Displaying location permission rationale to provide additional context.");
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permision Required");
            builder.setMessage("Please grant Location access so this application can perform Bluetooth scanning");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    Log.d(Constants.TAG, "Requesting permissions after explanation");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
                }
            });
            builder.show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            Log.i(Constants.TAG, "Received response for location permission request.");
            //Check if the only required permission has been granted
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                //Location permission has been granted
                Log.i(Constants.TAG, "Location permission has now been granted. Scanning.....");
                permissions_granted = true;
                if (ble_scanner.isScanning()) {
                    startScanning();
                }
            } else {
                Log.i(Constants.TAG, "Location permission was NOT granted");
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void simpleToast (String message, int duration) {
        toast = Toast.makeText(this, message, duration);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    private void startScanning() {
        if (permissions_granted) {
            runOnUiThread(new Runnable () {
                @Override
                public void run() {
                    ble_device_list_adapter.clear();
                    ble_device_list_adapter.notifyDataSetChanged();
                }
            });
            simpleToast(Constants.SCANNING + " : " + String.valueOf(SCAN_TIMEOUT/1000) + "seconds", 2000);
            ble_scanner.startScanning(this, SCAN_TIMEOUT);
        } else {
            Log.i(Constants.TAG, "Permission to perform Bluetooth scanning is not granted yet");
        }
    }

    @Override
    public void scanningStarted() {
        setScanState(true);
    }

    @Override
    public void scanningStopped() {
        if (toast != null) {
            toast.cancel();
        }
        setScanState(false);
        simpleToast("Scanning finished", 2000);
    }

}
