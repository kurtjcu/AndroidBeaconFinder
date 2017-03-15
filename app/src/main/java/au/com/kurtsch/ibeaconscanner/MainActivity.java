package au.com.kurtsch.ibeaconscanner;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;

    private static final int MY_PERMISSIONS_ACCESS_COARSE_LOCATION = 2;

    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner btScanner;
    private Handler scanHandler = new Handler();
    private int scan_interval_ms = 5000;
    private boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // init BLE
        btManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (btAdapter == null || !btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Request permission to use bluetooth
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {

                // TODO: Show an explanation to the user *asynchronously* -- don't block
                // TODO: this thread waiting for the user's response! After the user
                // TODO: sees the explanation, try again to request the permission.

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        MY_PERMISSIONS_ACCESS_COARSE_LOCATION);

            }
        }




        // TODO: launch and cancel this from a button
        scanHandler.post(scanRunnable);
    }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    //run the scan
    private Runnable scanRunnable = new Runnable()
    {
        @Override
        public void run() {

            if (isScanning)
            {
                if (btAdapter != null)
                {
                    btScanner.stopScan(bleScanCallback);
                }
            }
            else
            {
                if (btAdapter != null)
                {
                    btScanner.startScan(bleScanCallback);
                }
            }

            isScanning = !isScanning;

            scanHandler.postDelayed(this, scan_interval_ms);
        }
    };

    // ------------------------------------------------------------------------
    // Callbacks
    // ------------------------------------------------------------------------

    // handles returned data from scans
    //TODO write a test for this...
    private ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            int rssi = 0;
            String hexString = null;
            try {
                rssi = result.getRssi();
                byte[] scanRecord = result.getScanRecord().getBytes();

                hexString = bytesToHex(scanRecord);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.i(LOG_TAG,"Received packet:  " + hexString);
            Log.i(LOG_TAG,"RSSI:  " + ((Integer)rssi).toString());
            // TODO: properly filter results to be just ibeacons.
            
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_ACCESS_COARSE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {
                    // permission not granted - BOO!
                    Toast.makeText(this, R.string.ble_not_allowed, Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }




    //Helper Functions

    //------------------------------------------------------------------------
    //taken from
    // http://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
    //------------------------------------------------------------------------
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
