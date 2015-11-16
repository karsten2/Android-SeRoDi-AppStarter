package com.power.max.serodiappstarter.Services.BeaconLogger;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.service.RangedBeacon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The BeaconService that listens for available Bluetooth beacons.
 */
public class BeaconService extends Service implements BeaconConsumer {

    public static final String TAG = "BeaconLog";
    //public static final String beaconUuid = "F0018B9B-7509-4C31-A905-1A27D39C003C";
    public static final String beaconUuid = "20CAE8A0-A9CF-11E3-A5E2-0800200C9A66";
    public static final String beaconLayout = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";

    private String phoneUUID;

    private BeaconManager beaconManager;

    private Calendar calendar = Calendar.getInstance();
    private DateFormat dfDate = DateFormat.getDateInstance();
    private DateFormat dfTime = new SimpleDateFormat("HH:mm:ss.SSS");
    private DateFormat dfFileName = new SimpleDateFormat("dd_MM_yyyy HH_mm");

    private static final String fileHeader = "DATE;TIME;PHONE;BEACON1;DISTANCE;REGION;BEACON2;DISTANCE;REGION;BEACON3;DISTANCE;REGION;BEACON4;DISTANCE;REGION";
    private final String filename = dfFileName.format(calendar.getTime()) + ".csv";
    private PrintStream printStream;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        phoneUUID = getBtDeviceName();

        initBeaconManager();
        initPrintStream();

        if (!isBluetoothEnabled())
            activateBluetooth();
    }

    /**
     * Method to initiate the beaconmanager.
     */
    private void initBeaconManager() {
        beaconManager = BeaconManager.getInstanceForApplication(this);

        beaconManager.getBeaconParsers().add(new BeaconParser()
                .setBeaconLayout(beaconLayout));

        beaconManager.setForegroundScanPeriod(1000);
        beaconManager.setForegroundBetweenScanPeriod(0);
        RangedBeacon.setSampleExpirationMilliseconds(2000);
        beaconManager.bind(this);
    }

    /**
     * Method to initiate the PrintStream to create the directory and create, append the logfile.
     */
    private void initPrintStream() {
        /* Create File, open OutputStream for logging. */
        try {
            File directory = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS).getPath() + "/beacon");
            boolean success = true;
            if (!directory.exists()) {
                success = directory.mkdir();
            }

            if (success) {
                // directory successfully created; directory already existed.
                printStream = new PrintStream(new File(directory + "/" + filename));
                printStream.println(fileHeader);
            }

        } catch (FileNotFoundException e) {
            Log.e("BeaconService", e.getMessage());
            printStream.close();
            e.printStackTrace();
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        final Region region = new Region("myBeacon", Identifier.parse(beaconUuid), null, null);
        beaconManager.setMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                try {
                    Log.d(TAG, "Did enter Region!");
                    beaconManager.startRangingBeaconsInRegion(region);
                } catch (RemoteException e) {
                    Log.e("BeaconService", e.getMessage());
                    e.printStackTrace();
                }
            }

            @Override
            public void didExitRegion(Region region) {
                try {
                    Log.d(TAG, "Did exit Region!");
                    beaconManager.stopRangingBeaconsInRegion(region);
                } catch (RemoteException e) {
                    Log.e("BeaconService", e.getMessage());
                    e.printStackTrace();
                }
            }

            @Override
            public void didDetermineStateForRegion(int i, Region region) {
            }
        });

        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> collection, Region region) {
                if (collection.size() > 0) try {
                    List<Beacon> selectedBeacons;

                    // Select 4 closest beacons to log.
                    if (collection.size() <= 4) {
                        selectedBeacons = new ArrayList(collection);
                        Collections.sort(selectedBeacons, new BeaconDistanceComparator());
                    } else {
                        if (collection instanceof List)
                            selectedBeacons = (List) collection;
                        else
                            selectedBeacons = new ArrayList(collection);
                        Collections.sort(selectedBeacons, new BeaconDistanceComparator());
                        selectedBeacons = selectedBeacons.subList(0, 4);
                    }

                    calendar = Calendar.getInstance();

                    StringBuilder sbLog = new StringBuilder();
                    sbLog.append(dfDate.format(calendar.getTime())
                            + ";" + dfTime.format(calendar.getTime())
                            + ";" + phoneUUID);

                    /* Create String for the logfile. If less then three beacons are
                       available, the space is filled up with empty separators. */

                    for (Beacon beacon : selectedBeacons) {
                        sbLog.append(";"
                                + beacon.getId2().toString() + "/" + beacon.getId3().toString() + ";"
                                + trimDouble(beacon.getDistance(), "#.####") + ";"
                                + getDistanceRegion(beacon.getDistance()));
                    }

                    printStream.println(sbLog.toString());
                    Log.d(TAG, sbLog.toString());

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            beaconManager.startMonitoringBeaconsInRegion(region);
        } catch (RemoteException e) {
            Log.e("BeaconService", e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        beaconManager.unbind(this);
        printStream.close();
    }

    /**
     * Function to rate the distance between the beacon and the device.
     *
     * @param distance the distance between beacon and device.
     * @return Region of the Device in relation to the connected beacon.
     */
    private String getDistanceRegion(double distance) {
        if (distance >= 0 && distance <= 0.5) {
            return "nah";
        } else if(distance > 0.5 && distance <= 2) {
            return "mittel";
        }
        return "fern";
    }

    /**
     * Function to return the devices bluetooth name.
     * @return String with bluetooth name.
     */
    private String getBtDeviceName() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.getName() == null)
            return "null";
        else
            return mBluetoothAdapter.getName();
    }

    /**
     * Function to trim a double value after a specific pattern.
     * e.g.:    value:      0.123456
     *          pattern:    #.##
     *          returns:    0.12
     * The dot in the return value is replaced by a comma (Customer requirement).
     *
     * @param value the value to trim.
     * @param pattern the trim pattern.
     * @return the trimmed value as String.
     */
    private String trimDouble(double value, String pattern) {
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(value).replace(".",",");
    }


    /**
     * Function checks, if Bluetooth is enabled.
     * @return false if not enabled, else true.
     */
    private boolean isBluetoothEnabled() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    /**
     * Method to activate Bluetooth.
     */
    private void activateBluetooth() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothAdapter.enable();
    }

}