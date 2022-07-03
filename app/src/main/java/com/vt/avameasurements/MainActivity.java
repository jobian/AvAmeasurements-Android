/**
 * AvA mobile measurements mobile app GUI.
 *
 * @author Demetrius Davis (VT)
 */

package com.vt.avameasurements;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import androidx.annotation.RequiresApi;

public class MainActivity extends Activity {
    private TextView downloadProgressTextView;
    private EditText durationTextView, intervalTextView;
    private Button downloadButton, stopButton;
    public static int duration_secs = 60;
    public static int interval_secs = 30;
    public static final long start_time = System.currentTimeMillis();
    public static final boolean _DEBUG_ = false;
    public static final boolean _DEBUG_NDT_ = false;

    /**
     * Creates the mobile app GUI and initiates measurement tests when download button is clicked.
     * Also creates a thread pool manager to run measurement tests simultaneously.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        downloadProgressTextView = findViewById(R.id.download_progress_text_view);
        //TextView statusTextView = findViewById(R.id.status_text_view);
        downloadButton = findViewById(R.id.download_button);
        intervalTextView = findViewById(R.id.samp_int_text);
        durationTextView = findViewById(R.id.duration_text);
        stopButton = findViewById(R.id.stop_button);
        toggleEnabledButtons(false);

        downloadButton.setOnClickListener(v -> {
            try {
                duration_secs = Integer.parseInt(durationTextView.getText().toString());
                if (duration_secs < 60) duration_secs = 60;
            } catch (NumberFormatException nfe) {
                duration_secs = 60; // Default/minimum: 1-min run
            }

            try {
                interval_secs = Integer.parseInt(intervalTextView.getText().toString());
                if (interval_secs < 30) interval_secs = 30;
            } catch (NumberFormatException nfe) {
                interval_secs = 30; // Default/minimum: 30-sec interval
            }

            toggleEnabledButtons(true);

            if (_DEBUG_) System.out.println("Initiating ThreadManager...");
            ThreadManager tm = new ThreadManager(this, getApplicationContext(),
                    (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE));
            tm.runAllTests();

            toggleEnabledButtons(false);
        });

        stopButton.setOnClickListener(v -> this.onDestroy());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        toggleEnabledButtons(false);
    }

    private void toggleEnabledButtons(boolean testIsRunning) {
        if (_DEBUG_) System.out.println("---TOGGLE--- (" + testIsRunning + ")");
        downloadButton.setEnabled(!testIsRunning);
        stopButton.setEnabled(testIsRunning);

        if (testIsRunning) downloadProgressTextView.setText("");
    }
}
