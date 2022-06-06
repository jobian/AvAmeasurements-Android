package com.vt.avameasurements;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;

import android.net.Uri;
import android.content.Intent;
import android.app.PendingIntent;
import android.app.Notification;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import net.measurementlab.ndt7.android.NdtTest;
import net.measurementlab.ndt7.android.models.ClientResponse;
import net.measurementlab.ndt7.android.models.Measurement;
import net.measurementlab.ndt7.android.utils.DataConverter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.List;

import okhttp3.OkHttpClient;
//import com.google.api.client.http.FileContent;

public class MainActivity extends Activity {
    private static final String TAG = "AvA";

    class NDTTestImpl extends NdtTest {
        private TestType testType;
        String AMR;

        public NDTTestImpl(@Nullable OkHttpClient httpClient) {
            super(httpClient);
        }

        @RequiresApi(api = Build.VERSION_CODES.Q)
        @Override
        public void startTest(@NonNull TestType testType) {
            super.startTest(testType);
            this.testType = testType;
        }

        @Override
        public void onDownloadProgress(@NotNull ClientResponse clientResponse) {
            super.onDownloadProgress(clientResponse);
            runOnUiThread(() -> showDownloadProgress(clientResponse));
        }

        @Override
        public void onMeasurementDownloadProgress(@NotNull Measurement measurement) {
            super.onMeasurementDownloadProgress(measurement);
            System.out.println("Measurement download Progress: " + measurement.getBbrInfo().getElapsedTime());
            AMR = System.currentTimeMillis() + "," + measurement.getBbrInfo().getBw() + "," + measurement.getTcpInfo().getMinRtt() + "," +
                    measurement.getBbrInfo().getCwndGain() + "," + measurement.getTcpInfo().getRtt() + "," + measurement.getTcpInfo().getRttVar() + "," +
                    measurement.getTcpInfo().getTotalRetrans() + "," + measurement.getBbrInfo().getPacingGain() + "," +
                    measurement.getTcpInfo().getBusyTime() + "," + measurement.getTcpInfo().getElapsedTime();

            if (_DEBUG_) {
                System.out.println("\t-->Measurement download progress: BW = " + measurement.getBbrInfo().getBw());
                System.out.println("\t-->Measurement download progress: CwndGain = " + measurement.getBbrInfo().getCwndGain());
                System.out.println("\t-->Measurement download progress: ElaspedTime(BBR) = " + measurement.getBbrInfo().getElapsedTime());
                System.out.println("\t-->Measurement download progress: MinRTT(BBR) = " + measurement.getBbrInfo().getMinRtt());
                System.out.println("\t-->Measurement download progress: PacingGain = " + measurement.getBbrInfo().getPacingGain());
                System.out.println("\t-->Measurement download progress: AdvMss = " + measurement.getTcpInfo().getAdvMss());
                System.out.println("\t-->Measurement download progress: ATO = " + measurement.getTcpInfo().getAto());
                System.out.println("\t-->Measurement download progress: BusyTime = " + measurement.getTcpInfo().getBusyTime());
                System.out.println("\t-->Measurement download progress: CaState = " + measurement.getTcpInfo().getCaState());
                System.out.println("\t-->Measurement download progress: ElapsedTime(TCP) = " + measurement.getTcpInfo().getElapsedTime());
                System.out.println("\t-->Measurement download progress: Fackets = " + measurement.getTcpInfo().getFackets());
                System.out.println("\t-->Measurement download progress: MaxPacingRate = " + measurement.getTcpInfo().getMaxPacingRate());
                System.out.println("\t-->Measurement download progress: MinRTT(TCP) = " + measurement.getTcpInfo().getMinRtt());
                System.out.println("\t-->Measurement download progress: Probes = " + measurement.getTcpInfo().getProbes());
                System.out.println("\t-->Measurement download progress: RcvMss = " + measurement.getTcpInfo().getRcvMss());
                System.out.println("\t-->Measurement download progress: PMTU = " + measurement.getTcpInfo().getPmtu());
                System.out.println("\t-->Measurement download progress: RTO = " + measurement.getTcpInfo().getRto());
                System.out.println("\t-->Measurement download progress: RTT = " + measurement.getTcpInfo().getRtt());
                System.out.println("\t-->Measurement download progress: RTTvar = " + measurement.getTcpInfo().getRttVar());
                System.out.println("\t-->Measurement download progress: State = " + measurement.getTcpInfo().getState());
                System.out.println("\t-->Measurement download progress: TotalRetrans = " + measurement.getTcpInfo().getTotalRetrans());
            }
        }
        /*
        @Override
        public void onUploadProgress(@NotNull ClientResponse clientResponse) {
            super.onUploadProgress(clientResponse);
            runOnUiThread(() -> showUploadProgress(clientResponse));
        }

        @Override
        public void onMeasurementUploadProgress(@NotNull Measurement measurement) {
            super.onMeasurementUploadProgress(measurement);
            System.out.println("Measurement upload Progress: " + measurement);
        }
        */
        @Override
        public void onFinished(
                @Nullable ClientResponse clientResponse,
                @Nullable Throwable error,
                @NotNull TestType testType
        ) {
            assert clientResponse != null;
            System.out.println("Done Progress: " + DataConverter.convertToMbps(clientResponse));
            if (testType == TestType.DOWNLOAD_AND_UPLOAD && testType == TestType.DOWNLOAD) {
                return;
            }
            runOnUiThread(() -> toggleEnabledButtons(false));
        }

        public String getAMR() { return AMR; }
    }

    private final DecimalFormat decimalFormat = new DecimalFormat("#.00");
    private TextView downloadProgressTextView, uploadProgressTextView;
    private EditText durationTextView, intervalTextView;
    private int interval=0, duration=0;
    private Button downloadButton, stopButton;
    private NDTTestImpl ndtTestImpl;
    private TelephonyManager telephonyManager;
    private TelephonyManager.CellInfoCallback cellInfoCallback;
    private OutputStreamWriter pOSW, aOSW;
    private File passiveDataFile, activeDataFile;
    private final boolean _DEBUG_ = true;
    private String PMR;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
/*
        // If the notification supports a direct reply action, use
        // PendingIntent.FLAG_MUTABLE instead.
        Intent notificationIntent = new Intent(this, ExampleActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE);

        Notification notification =
                new Notification.Builder(this, CHANNEL_DEFAULT_IMPORTANCE)
                        .setContentTitle("AvA mobile measurements")
                        .setContentText("AvA is collecting signal measurements...")
                        .setContentIntent(pendingIntent)
                        .setSmallIcon(null)
                        .build();
        //.setTicker(getText(R.string.ticker_text))

        // Notification ID cannot be 0.
        Service.startForeground(notification, FOREGROUND_SERVICE_TYPE_LOCATION);
*/
        downloadProgressTextView = findViewById(R.id.download_progress_text_view);
        uploadProgressTextView = findViewById(R.id.upload_progress_text_view);
        downloadButton = findViewById(R.id.download_button);
        intervalTextView = (EditText)findViewById(R.id.samp_int_text);
        durationTextView = (EditText)findViewById(R.id.duration_text);
        stopButton = findViewById(R.id.stop_button);

        ndtTestImpl = new NDTTestImpl(null);
        initializeTelephonyManager();

        downloadButton.setOnClickListener(v -> {
            toggleEnabledButtons(false);

            long interval_start;
            long currTime = interval_start = System.currentTimeMillis();

            int duration_secs, interval_secs;
            try {
                duration_secs = Integer.parseInt(durationTextView.getText().toString());
            } catch (NumberFormatException nfe) {
                duration_secs = 60; // Default: 1-min run
            }
            try {
                interval_secs = Integer.parseInt(intervalTextView.getText().toString());
            } catch (NumberFormatException nfe) {
                interval_secs = 30; // Default: 30-sec interval
            }
            long endTime = currTime + (duration_secs * 1000);

            initializeOutputFiles(currTime); // initialize CSV output files for passive and active measurements

            while (currTime < endTime) {
                while (currTime < interval_start) { // advance timer until start of next interval
                    try {
                        Thread.sleep(100);
                    } // sleep for 100 ms
                    catch (Exception e) {
                        e.printStackTrace();
                    }

                    currTime = System.currentTimeMillis();
                }

                try {
                    collectPassiveMeasurements();
                } catch (Exception ioe) {
                    ioe.printStackTrace();
                }

                ndtTestImpl.startTest(NdtTest.TestType.DOWNLOAD);

                interval_start += (interval_secs*1000);
                currTime = System.currentTimeMillis();
            }
            closeOutputFiles();
            toggleEnabledButtons(true);

        });

        stopButton.setOnClickListener(v -> {
            ndtTestImpl.stopTest();
            toggleEnabledButtons(false);
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void collectPassiveMeasurements() throws IOException {
        if (telephonyManager == null) throw new IOException("Null TelephonyManager");
        else if (cellInfoCallback == null) throw new IOException("Null TelephonyManager.CellInfoCallback");

        /***
         * Requests all available cell information from the current subscription for
         * observed camped/registered, serving, and neighboring cells.
         */
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 1);
        }
        telephonyManager.requestCellInfoUpdate(getMainExecutor(), cellInfoCallback);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void initializeTelephonyManager() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            if (_DEBUG_) System.out.println("ERROR: TelephonyManager is NULL.");
            return;
        }

        cellInfoCallback = new TelephonyManager.CellInfoCallback() {
            @Override
            public void onCellInfo(List<CellInfo> cellInfoList) {
                if (cellInfoList == null) {
                    System.err.println("NO CELL INFO FOUND.");
                    return;
                }

                CellSignalStrengthLte lteSigStrength;
                CellIdentityLte ltecellID;
                CellInfoLte lteInfo;
                long currTime = System.currentTimeMillis();
                boolean isServingCell, _DEBUG_ = true;

                for (int i = 0; i < cellInfoList.size(); i++) {
                    // Filter out non-LTE networks
                    if (cellInfoList.get(i).getClass().toString().equals("class android.telephony.CellInfoLte")) {
                        lteInfo = (CellInfoLte) cellInfoList.get(i);
                        ltecellID = lteInfo.getCellIdentity();
                        lteSigStrength = lteInfo.getCellSignalStrength();
                        isServingCell = lteInfo.isRegistered(); //(lteInfo.getCellConnectionStatus() == CellInfo.CONNECTION_PRIMARY_SERVING);

                        if (_DEBUG_) {
                            System.out.println("Evaluating cell #" + (i + 1) + ", MNO: " + ltecellID.getMobileNetworkOperator());

                            System.out.println("Cell ID: " + ltecellID.getMobileNetworkOperator() + ", CI: " + ltecellID.getCi() +
                                    ", Earfcn: " + ltecellID.getEarfcn() + ", Pci: " + ltecellID.getPci() + ", TAC: " + ltecellID.getTac());
                            System.out.println("Cell ID: mcc = " + ltecellID.getMccString() + ", mnc = " + ltecellID.getMncString() + ", BW = " +
                                    ltecellID.getBandwidth());
                            System.out.println("Cell info: connectionStatus = " + lteInfo.getCellConnectionStatus());
                            //System.out.println("Cell signal strength: Dbu: " + lteInfo.getCellSignalStrength().getDbm() + ", ASU = " + lteInfo.getCellSignalStrength().getAsuLevel());
                        }

                        // Compose the passive measurement report
                        // Format: timestamp,rsrp,rsrq,rssnr,cqi,mcc,mnc,tac,pci,earfcn,enbID,bw_khz,isServingCell
                        PMR = currTime + "," + lteSigStrength.getRsrp() + "," + lteSigStrength.getRsrq() + "," +
                                lteSigStrength.getRssnr() + "," + lteSigStrength.getCqi() + "," + ltecellID.getMccString() +
                                "," + ltecellID.getMncString() + "," + ltecellID.getTac() + "," + ltecellID.getPci() +
                                "," + ltecellID.getEarfcn() + "," + ltecellID.getCi() + "," + ltecellID.getBandwidth() +
                                "," + isServingCell;

                        try {
                            writeMeasurementOutputs(PMR, false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        if (_DEBUG_) System.out.println("--> NON-LTE CELL FOUND: " + cellInfoList.get(i).getClass());
                    }
                }
            }
        };
    }

    public void initializeOutputFiles2(long time_msecs) {
        // Create and open output CSV files for passive and active measurement data
        String csvFileName = "Cell_Data_" + time_msecs + ".csv";
        System.out.println("Writing measurements out to files: " + csvFileName);
        try {
            passiveDataFile = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "p" + csvFileName);
            activeDataFile = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "a" + csvFileName);

            pOSW = new OutputStreamWriter(new FileOutputStream(passiveDataFile));
            pOSW.write("timestamp,rsrp,rsrq,rssnr,cqi,mcc,mnc,tac,pci,earfcn,enbID,bw_khz,isServingCell" + "\n");

            aOSW = new OutputStreamWriter(new FileOutputStream(activeDataFile));
            aOSW.write("timestamp,BW,minRTT,CwndGain,avgRTT,RTTvar,TotalRetrans,PacingGain,BusyTime,ElapsedTime,DLspeed" + "\n");

        } catch (IOException e) {
            e.printStackTrace();
        }
        /*
        FileContent passiveContent = new FileContent("application/octet-stream", passiveDataFile);
        FileContent activeContent = new FileContent("application/octet-stream", activeDataFile);
        File file = driveService.files().create(passiveDataFile, passiveContent)
                .setFields("id")
                .execute();
        System.out.println("File ID: " + file.getId());
        */
    }

    private void initializeOutputFiles(long time_msecs)
    {
        // Create and open output CSV files for passive and active measurement data
        String csvFileName = "Cell_Data_" + time_msecs + ".csv";
        System.out.println("Writing measurements out to files: " + csvFileName);

        try
        {
            pOSW = new OutputStreamWriter(openFileOutput("p" + csvFileName, Context.MODE_PRIVATE));
            pOSW.write("timestamp,rsrp,rsrq,rssnr,cqi,mcc,mnc,tac,pci,earfcn,enbID,bw_khz,isServingCell" + "\n");

            aOSW = new OutputStreamWriter(openFileOutput("a" + csvFileName, Context.MODE_PRIVATE));
            aOSW.write("timestamp,BW,minRTT,CwndGain,avgRTT,RTTvar,TotalRetrans,PacingGain,BusyTime,ElapsedTime,DLspeed" + "\n");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        System.out.println("writing to file " + csvFileName + "completed...");

    }

    public void closeOutputFiles() {
        // Close CSV files for passive and active measurement data
        System.out.println("Closing Output Files.");
        try {
            pOSW.flush();
            pOSW.close();

            aOSW.flush();
            aOSW.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("EXITING. CLOSING FILES.");
    }

    public void writeMeasurementOutputs(String measurements, boolean isActive) {
        try {
            if (isActive && aOSW != null && measurements != null) {
                System.out.println("Active measurements: " + measurements);
                aOSW.write(measurements + "\n");
            }
            else if (pOSW != null && measurements != null) {
                System.out.println("Passive measurements: " + measurements);
                pOSW.write(measurements + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void toggleEnabledButtons(boolean testIsRunning) {
        downloadButton.setEnabled(!testIsRunning);
        stopButton.setEnabled(testIsRunning);
        if (testIsRunning) {
            downloadProgressTextView.setText("");
            //uploadProgressTextView.setText("");
        }
    }

    private String formatProgress(ClientResponse clientResponse) {
        return String.format(
                "%s Mbps",
                decimalFormat.format(DataConverter.convertToMbps(clientResponse))
        );
    }

    private void showDownloadProgress(ClientResponse clientResponse) {
        String speed = formatProgress(clientResponse);
        System.out.println("Download Progress: " + speed);
        downloadProgressTextView.setText(speed);

        writeMeasurementOutputs(ndtTestImpl.getAMR()+","+speed, true);
    }

    private void showUploadProgress(ClientResponse clientResponse) {
        String speed = formatProgress(clientResponse);
        System.out.println("Upload Progress: " + speed);
        uploadProgressTextView.setText(speed);
    }

    /** Authorizes the installed application to access user's protected data. *
    private static Credential authorize() throws Exception {
        // load client secrets
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(CalendarSample.class.getResourceAsStream("/client_secrets.json")));
        // set up authorization code flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets,
                Collections.singleton(CalendarScopes.CALENDAR)).setDataStoreFactory(dataStoreFactory)
                .build();
        // authorize
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }
    */
}