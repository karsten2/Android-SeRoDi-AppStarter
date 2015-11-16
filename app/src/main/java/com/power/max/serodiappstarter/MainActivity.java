package com.power.max.serodiappstarter;

import android.app.ActivityManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import com.power.max.serodiappstarter.Fragments.*;
import com.power.max.serodiappstarter.Services.BeaconLogger.BeaconService;
import com.power.max.serodiappstarter.Services.StepDetector.StepDetectorService;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private Context context = this;
    private ArrayList<Fragment> lstFragments = new ArrayList<>();
    private Fragment currentFragment;
    private Button btnStart, btnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        lstFragments.add(new tutPage1Fragment());
        lstFragments.add(new tutPage2Fragment());
        lstFragments.add(new tutPage3Fragment());
        lstFragments.add(new tutPage4Fragment());
        lstFragments.add(new tutPage5Fragment());
        lstFragments.add(new tutPage6Fragment());

        replaceFragment(new tutPage1Fragment());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        android.os.Process.killProcess(android.os.Process.myPid());
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

    public void onClickBack(View v) {
        replaceFragment(getPrevFragment());
    }

    public void onClickNext(View v) {
        replaceFragment(getNextFragment());
    }

    public void onClickGoTo(View v) {
        replaceFragment(lstFragments.get(lstFragments.size() - 1));
    }

    public void onClickStart(View v) { start(); }

    public void onClickEnd(View v) {
        stop();
        this.finish();
    }

    private void start() {

        btnStart = (Button) findViewById(R.id.btnStart);
        btnStop = (Button) findViewById(R.id.btnStop);
        btnStart.setVisibility(View.INVISIBLE);
        btnStop.setVisibility(View.VISIBLE);

        // Start Stepcounter Service
        startService(StepDetectorService.class);

        // Start BeaconLogger Service
        startService(BeaconService.class);

        Toast.makeText(context, "Aufzeichnung gestartet", Toast.LENGTH_SHORT).show();
    }

    private void stop() {
        // Stop Stepcounter Service
        stopService(StepDetectorService.class);

        // Stop BeaconLogger Service
        stopService(BeaconService.class);
    }

    private void startService(Class<?> cls) {

        if (!isServiceRunning(cls))
            startService(new Intent(context, cls));
    }

    private void stopService(Class<?> cls) {
        if (isServiceRunning(cls))
            stopService(new Intent(context, cls));
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void replaceFragment(Fragment newFragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.replace(R.id.contentFragment, newFragment);

        transaction.commit();

        currentFragment = newFragment;
    }

    private Fragment getNextFragment() {
        boolean found = false;
        for (Fragment f : lstFragments) {
            if (found)
                return f;
            if (f.getClass() == currentFragment.getClass())
                found = true;
        }

        return new Fragment();
    }

    private Fragment getPrevFragment() {

        Fragment tmp = new Fragment();
        for (Fragment f : lstFragments) {
            if (f.getClass() == currentFragment.getClass())
                return tmp;
            tmp = f;
        }

        return new Fragment();
    }
}
