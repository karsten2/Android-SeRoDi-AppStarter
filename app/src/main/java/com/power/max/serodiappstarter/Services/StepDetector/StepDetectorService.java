package com.power.max.serodiappstarter.Services.StepDetector;

/*
 *  Pedometer - Android App
 *  Copyright (C) 2009 Levente Bagi
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Detects steps and notifies all listeners (that implement StepListener).
 * @author Levente Bagi (onSensorChanged)
 * @author Karsten Möckel (Service)
 */
public class StepDetectorService extends Service implements SensorEventListener {
    private final static String TAG = "StepDetector";
    private float   mLimit = 25;
    private float   mLimitM = 32;
    private float   mLimitH = 40;
    private float   mLastValues[] = new float[3*2];
    private float   mScale[] = new float[2];
    private float   mYOffset;

    private float   mLastDirections[] = new float[3*2];
    private float   mLastExtremes[][] = { new float[3*2], new float[3*2] };
    private float   mLastDiff[] = new float[3*2];
    private int     mLastMatch = -1;
    private int     count[] = {0, 0, 0};

    private Calendar calendar = Calendar.getInstance();
    private DateFormat dfFileName = new SimpleDateFormat("dd_MM_yyyy HH_mm");
    private DateFormat dfTimeStamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    private final String fileHeader = "TIME;STEP1;STEP2;STEP3;" + getBtDeviceName();
    private final String fileName = dfFileName.format(calendar.getTime())+ "_STEPS" + ".csv";
    private PrintStream printStream;

    Timer timer = new Timer();

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private StepListener mStepListener;

    private ArrayList<StepListener> mStepListeners = new ArrayList<StepListener>();

    public StepDetectorService() {}

    @Override
    public void onCreate() {
        super.onCreate();

        int h = 480; // TODO: remove this constant
        mYOffset = h * 0.5f;
        mScale[0] = - (h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
        mScale[1] = - (h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));

        initPrintSream();

        startService();

        try {
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {

                    Date date = new Date();

                    String print = dfTimeStamp.format(date)
                            + ";" + count[0]
                            + ";" + count[1]
                            + ";" + count[2];
                    Log.d("TIMER", print);

                    printStream.println(print);
                }
            }, 60 * 1000, 60 * 1000);
        } catch (Exception e) {
            Log.e("TIMER", e.getMessage());
        }
    }

    private void initPrintSream() {
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
                printStream = new PrintStream(new File(directory + "/" + fileName));
                printStream.println(fileHeader);
            }

        } catch (FileNotFoundException e) {
            Log.e("BeaconService", e.getMessage());
            printStream.close();
            e.printStackTrace();
        }
    }

    private void startService() {
        mStepListener = new StepListener() {
            @Override
            public void onStep() {
                passValue();
            }

            @Override
            public void passValue() {}

            @Override
            public void addStepL() {
                count[0] ++;
            }

            @Override
            public void addStepM() {
                count[1] ++;
            }

            @Override
            public void addStepH() {
                count[2] ++;
            }
        };

        this.addStepListener(mStepListener);

        try {
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception e) {
            Log.e("SENSOR", e.getMessage());
        }
    }

    public void setSensitivity(float sensitivity) {
        mLimit = sensitivity; // 1.97  2.96  4.44  6.66  10.00  15.00  22.50  33.75  50.62
    }

    public void addStepListener(StepListener sl) {
        mStepListeners.add(sl);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float orientation[] = new float[3];
        float R[] = new float[9];
        SensorManager.getOrientation(R, orientation);
        Sensor sensor = event.sensor;
        synchronized (this) {
            if (sensor.getType() == orientation[0]) {
            }
            else {
                int j = (sensor.getType() == Sensor.TYPE_ACCELEROMETER) ? 1 : 0;
                if (j == 1) {
                    float vSum = 0;
                    for (int i=0 ; i<3 ; i++) {
                        final float v = mYOffset + event.values[i] * mScale[j];
                        vSum += v;
                    }
                    int k = 0;
                    float v = vSum / 3;

                    float direction = (v > mLastValues[k] ? 1 : (v < mLastValues[k] ? -1 : 0));
                    if (direction == - mLastDirections[k]) {
                        // Direction changed
                        int extType = (direction > 0 ? 0 : 1); // minumum or maximum?
                        mLastExtremes[extType][k] = mLastValues[k];
                        float diff = Math.abs(mLastExtremes[extType][k] - mLastExtremes[1 - extType][k]);

                        if (diff > mLimit) {

                            boolean isAlmostAsLargeAsPrevious = diff > (mLastDiff[k]*2/3);
                            boolean isPreviousLargeEnough = mLastDiff[k] > (diff/3);
                            boolean isNotContra = (mLastMatch != 1 - extType);

                            if (isAlmostAsLargeAsPrevious && isPreviousLargeEnough && isNotContra) {
                                Log.i(TAG, "step");
                                for (StepListener stepListener : mStepListeners) {
                                    stepListener.addStepL();

                                    if (diff > mLimitM)
                                        stepListener.addStepM();
                                    if (diff > mLimitH)
                                        stepListener.addStepH();

                                    stepListener.onStep();
                                }
                                mLastMatch = extType;
                            }
                            else {
                                mLastMatch = -1;
                            }
                        }
                        mLastDiff[k] = diff;
                    }
                    mLastDirections[k] = direction;
                    mLastValues[k] = v;
                }
            }
        }
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        // Unregister Listener
        mSensorManager.unregisterListener(this);
        printStream.close();
    }
}
