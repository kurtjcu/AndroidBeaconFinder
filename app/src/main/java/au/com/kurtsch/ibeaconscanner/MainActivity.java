package au.com.kurtsch.ibeaconscanner;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    //Constants
    private static final String LOG_TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int MY_PERMISSIONS_ACCESS_COARSE_LOCATION = 2;
    public static final String SCAN_INTERVAL = "scanInterval";

    // BLE
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner btScanner;
    private Handler scanHandler = new Handler();
    private int scanIntervalInMs;
    private boolean isScanning = false;

    SharedPreferences sharedPref; // Stores the Scan Interval

    //Expandable list
    ExpandableListAdapter listAdapter;
    ExpandableListView expListView;
    List<String> listDataHeader;
    HashMap<String, List<String>> listDataChild;

    //gui
    TextView status;
    Button startScan, stopScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPref = this.getSharedPreferences(SCAN_INTERVAL, Context.MODE_PRIVATE);

        try {
            scanIntervalInMs = sharedPref.getInt(SCAN_INTERVAL, 5000);
        } catch (Exception e) {
            Log.d("cantgetstored", e.toString());
            e.printStackTrace();
        }

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

        //setup Gui Elements
        status = (TextView) findViewById(R.id.status);
        startScan = (Button) findViewById(R.id.startScan);
        stopScan = (Button) findViewById(R.id.stopScan);

        // get the listview
        expListView = (ExpandableListView) findViewById(R.id.lvExp);

        // preparing list data
        prepareListData();

        listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);

        // setting list adapter
        expListView.setAdapter(listAdapter);

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

        scanHandler.post(scanRunnable);
    }


    //runs the scan in intervals to help preserve battery life as per android recommendations
    private Runnable scanRunnable = new Runnable()
    {
        @Override
        public void run() {

            if (isScanning)
            {
                if (btAdapter != null)
                {
                    btScanner.stopScan(bleScanCallback);
                    status.setText("Scan is waiting");
                }
            }
            else
            {
                if (btAdapter != null)
                {
                    btScanner.startScan(bleScanCallback);
                    status.setText("Scan is running");
                }
            }

            isScanning = !isScanning;
            scanHandler.postDelayed(this, scanIntervalInMs);
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


                //determine if it is an ibeacon
                int startByte = 2;
                boolean isBeacon = false;
                while (startByte <= 5)
                {
                    if (    ((int) scanRecord[startByte + 2] & 0xff) == 0x02 &&
                            ((int) scanRecord[startByte + 3] & 0xff) == 0x15){
                        isBeacon = true;
                        break;
                    }
                    startByte++;
                }

                hexString = bytesToHex(scanRecord);
                Log.i(LOG_TAG, "is it a beacon? " + isBeacon);

                if(isBeacon){
                    //TODO: put the data in the list?
                    handleIBeacon(scanRecord, rssi, startByte);
                }

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
                    // permission was granted, yay!

                } else {
                    // permission not granted - BOO!
                    Toast.makeText(this, R.string.ble_not_allowed, Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    // Button Callbacks

    public void scanPress(View v) {
        switch (v.getId()) {

            case (R.id.startScan):
                scanHandler.post(scanRunnable);
                status.setText("Scan started");

                break;
            case (R.id.stopScan):
                scanHandler.removeCallbacks(scanRunnable);
                btScanner.stopScan(bleScanCallback);
                status.setText("Scan has been stopped");
                break;
            case (R.id.clearScan):
                listDataHeader.clear();
                listAdapter.notifyDataSetChanged();

        }
    }


    private void handleIBeacon(byte[] scanRecord, Integer rssi, Integer startByte){
        HashMap<String,String> iBeaconHashMap;
        List<String> details;

        Log.i(LOG_TAG, "handling the IBeacon " + bytesToHex(scanRecord));

        iBeaconHashMap = breakdownIBeacon(scanRecord, startByte);
        String UUID = iBeaconHashMap.get("UUID");


        if (listDataHeader.contains("None Found Yet")){
            listDataHeader.clear();
        }

        if (!listDataHeader.contains(UUID)) {
            listDataHeader.add(UUID);
        }

        details = new ArrayList<String>();
        details.add("Major: " + iBeaconHashMap.get("Major"));
        details.add("Minor: " + iBeaconHashMap.get("Minor"));
        details.add("RSSI: " + String.valueOf(rssi));

        listDataChild.put(listDataHeader.get(listDataHeader.indexOf(UUID)), details); // Header, Child data

        listAdapter.notifyDataSetChanged();

    }

    /*
     * Preparing the list data
     */
    private void prepareListData() {
        listDataHeader = new ArrayList<String>();
        listDataChild = new HashMap<String, List<String>>();


        // Adding header data
        listDataHeader.add("None Found Yet");

        // Adding child data
        List<String> noneFound = new ArrayList<String>();
        noneFound.add("Do You Have Any Beacon In Range?");
        listDataChild.put(listDataHeader.get(0), noneFound); // Header, Child data


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

    public HashMap<String,String> breakdownIBeacon (byte[] scanRecord, Integer startByte){

        HashMap<String,String> iBeaconDetails = new HashMap<>();

        //UUID detection
        byte[] uuidBytes = new byte[16];
        System.arraycopy(scanRecord, startByte + 4, uuidBytes, 0, 16);
        String hexString = bytesToHex(uuidBytes);
        String uuid =  hexString.substring(0,8) + "-" +
                hexString.substring(8,12) + "-" +
                hexString.substring(12,16) + "-" +
                hexString.substring(16,20) + "-" +
                hexString.substring(20,32);
        iBeaconDetails.put("UUID", uuid);

        // major
        String major = String.valueOf((scanRecord[startByte + 20] & 0xff) * 0x100 +
                (scanRecord[startByte + 21] & 0xff));
        iBeaconDetails.put("Major", major);

        // minor
        String minor = String.valueOf((scanRecord[startByte + 22] & 0xff) * 0x100 +
                (scanRecord[startByte + 23] & 0xff));
        iBeaconDetails.put("Minor", minor);

        Log.i(LOG_TAG,"UUID: " +uuid + "\nmajor: " +major +"\nminor: " +minor);

        return iBeaconDetails;
    }
}
