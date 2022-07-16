/**
 * Creates separate threads to run various measurement tests.
 *
 * @author Demetrius Davis (VT)
 */

package com.vt.avameasurements;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.lang.Long;
import java.io.OutputStreamWriter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Interface to enforce standard implementation for each measurement test.
 */
interface MeasurementTest {
    void initCSVFile();
    void writeToCSV(String measurementReport);
    void closeCSVFile();
}

public class ThreadManager extends Application {
    /**
     * Enum data type for each measurement test
     */
    public enum MeasurementTestType {
        PASSIVE,
        NDT7
    }

    ExecutorService threadpool;
    Activity activity;
    Context context;
    TelephonyManager mTelephonyManager;
    private final String _TAG_ = "ThreadManager";
    PassiveMeasurementTest pmt;
    NDT7Test ndt7;

    public ThreadManager(Activity appActivity, Context appContext, TelephonyManager telephonyManager) {
        this.threadpool = Executors.newFixedThreadPool(MeasurementTestType.values().length);
        this.activity = appActivity;
        this.context = appContext;
        this.mTelephonyManager = telephonyManager;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void runAllTests() {
        // creates separate Runnable tasks
        pmt = new PassiveMeasurementTest(activity, context, mTelephonyManager);
        ndt7 = new NDT7Test(context, this);
        //Runnable r3 = new Task("task 3");

        // passes the Runnable tasks to the thread pool to execute
        threadpool.execute(pmt);
        threadpool.execute(ndt7);
        //threadpool.execute(r5); // Placeholder for additional measurements tests
    }

    /**
     * Format numbers into a 8-character text string
     *
     * @param testID number to convert into a formatted text string
     * @return formatted 8-character text string (with padded zeros)
     */
    public static String formatTestID(long testID) {
        final int paddingLength = 8;
        return String.format(Locale.US, "%0" + paddingLength + "d", testID);
    }

    public void endTests() {
        this.activity.runOnUiThread(() -> ((MainActivity)this.activity).onDestroy());

        // pool shutdown ( Step 4)
        threadpool.shutdown();
        pmt = null;
        ndt7 = null;
    }
}