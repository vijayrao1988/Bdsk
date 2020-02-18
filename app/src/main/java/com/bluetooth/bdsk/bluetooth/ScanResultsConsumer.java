package com.bluetooth.bdsk.bluetooth;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;

public interface ScanResultsConsumer {
    public void candidateBleDevice(ScanResult scanResult, int rssi);
    public void scanningStarted();
    public void scanningStopped();
}