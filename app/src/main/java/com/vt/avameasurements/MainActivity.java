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
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import net.measurementlab.ndt7.android.NdtTest;
import net.measurementlab.ndt7.android.models.ClientResponse;
import net.measurementlab.ndt7.android.models.Measurement;
import net.measurementlab.ndt7.android.utils.DataConverter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.List;
import okhttp3.OkHttpClient;

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
            if (_DEBUG_) System.out.println("Measurement download Progress: " + measurement.getBbrInfo().getElapsedTime());
            AMR = System.currentTimeMillis() + "," + measurement.getBbrInfo().getBw() + "," + measurement.getTcpInfo().getMinRtt() + "," +
                    measurement.getBbrInfo().getCwndGain() + "," + measurement.getTcpInfo().getRtt() + "," + measurement.getTcpInfo().getRttVar() + "," +
                    measurement.getTcpInfo().getTotalRetrans() + "," + measurement.getBbrInfo().getPacingGain() + "," +
                    measurement.getTcpInfo().getBusyTime() + "," + measurement.getTcpInfo().getElapsedTime();

            if (_DEBUG2_) {
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

        @Override
        public void onFinished(
                @Nullable ClientResponse clientResponse,
                @Nullable Throwable error,
                @NotNull TestType testType
        ) {
            assert clientResponse != null;
            System.out.println("Done Progress: " + DataConverter.convertToMbps(clientResponse));
            writeMeasurementOutputs(getAMR() + "," + DataConverter.convertToMbps(clientResponse), true);
            if (testType == TestType.DOWNLOAD_AND_UPLOAD && testType == TestType.DOWNLOAD) {
                return;
            }
        }

        public String getAMR() {
            return AMR;
        }
    }

    private final DecimalFormat decimalFormat = new DecimalFormat("#.00");
    private TextView downloadProgressTextView, statusTextView;
    private EditText durationTextView, intervalTextView;
    private int interval = 0, duration = 0;
    private Button downloadButton, stopButton;
    private NDTTestImpl ndtTestImpl;
    //private TelephonyManager telephonyManager;
    //private TelephonyManager.CellInfoCallback cellInfoCallback;
    private OutputStreamWriter pOSW, aOSW;
    private File passiveDataFile, activeDataFile;
    private final boolean _DEBUG_ = true, _DEBUG2_ = false;
    private String PMR, recordID;
    private double latitude, longitude;
    private LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean isDone = false;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //initializeTelephonyManager();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(30 * 1000);

        downloadProgressTextView = findViewById(R.id.download_progress_text_view);
        statusTextView = findViewById(R.id.status_text_view);
        downloadButton = findViewById(R.id.download_button);
        intervalTextView = (EditText) findViewById(R.id.samp_int_text);
        durationTextView = (EditText) findViewById(R.id.duration_text);
        stopButton = findViewById(R.id.stop_button);

        mPhoneStatelistener = new MyPhoneStateListener();
        mPhoneStatelistener.setActivity(this);
        mPhoneStatelistener.setContext(getApplicationContext());
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(mPhoneStatelistener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        downloadButton.setOnClickListener(v -> {
            long interval_start, processingTime;
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
            toggleEnabledButtons(true);

            while (currTime < endTime) {
                try {
                    if (_DEBUG_)
                        System.out.println("Initializing passive measurement collection... [" + currTime + "]");
                    collectPassiveMeasurements();

                } catch (Exception ioe) {
                    ioe.printStackTrace();
                }

                if (_DEBUG_)
                    System.out.println("Initializing NDT7 measurement collection... [" + currTime + "]");

                if (_DEBUG_) System.out.println("--> before ndt.startTest()");
                ndtTestImpl = new NDTTestImpl(null);
                ndtTestImpl.startTest(NdtTest.TestType.DOWNLOAD);

                if (_DEBUG_) System.out.println("--> after ndt.startTest()");
                processingTime = System.currentTimeMillis() - currTime;
                processWait(interval_secs * 1000 - (System.currentTimeMillis() - currTime));
                mTelephonyManager.listen(mPhoneStatelistener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

                if (_DEBUG_) System.out.println("--> after WAIT()");

                currTime = System.currentTimeMillis();
            }
        });

        stopButton.setOnClickListener(v -> {
            ndtTestImpl.stopTest();
            this.onDestroy();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        toggleEnabledButtons(false);
        closeOutputFiles();
    }

    TelephonyManager mTelephonyManager;
    MyPhoneStateListener mPhoneStatelistener;
    int mSignalStrength = 0;
    long[][] pData = null;
    int MAX_VAL = 2147483647;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    class MyPhoneStateListener extends PhoneStateListener {
        Activity thisActivity = null;
        Context thisContext = null;
        public void setActivity(Activity act) { thisActivity = act; }
        public void setContext(Context ctxt) { thisContext = ctxt; }

        @Override
        public void onSignalStrengthsChanged(android.telephony.SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);

            if (ActivityCompat.checkSelfPermission(thisContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(thisActivity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
            if (ActivityCompat.checkSelfPermission(thisContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                int s_cqi=MAX_VAL, s_rsrp=MAX_VAL, s_rsrq=MAX_VAL, s_rssnr=MAX_VAL;
                List<CellSignalStrength> cellStrengthList = signalStrength.getCellSignalStrengths();
                if (cellStrengthList.size() > 0) {
                    if (cellStrengthList.get(0).getClass().toString().equals("class android.telephony.CellSignalStrengthLte")) {
                        s_rsrp = ((CellSignalStrengthLte)cellStrengthList.get(0)).getRsrp(); // RSRP
                        s_rsrq = ((CellSignalStrengthLte)cellStrengthList.get(0)).getRsrq(); // RSRQ
                        s_rssnr = ((CellSignalStrengthLte)cellStrengthList.get(0)).getRssnr(); // RSSNR
                        s_cqi = ((CellSignalStrengthLte)cellStrengthList.get(0)).getCqi(); // CQI

                    }
                }

                long currTime = System.currentTimeMillis(), mno = MAX_VAL;
                CellIdentityLte lteCellId;
                CellSignalStrengthLte lteSigStrength;
                boolean isServingCell = false;
                int numLTE = 0;
                List<CellInfo> cellInfoList = mTelephonyManager.getAllCellInfo();
                for (int i = 0; i < cellInfoList.size(); i++) {
                    if (cellInfoList.get(i).getClass().toString().equals("class android.telephony.CellInfoLte"))
                        numLTE++;
                }

                pData = new long[numLTE][10];
                for (int i = 0; i < cellInfoList.size(); i++) {
                    // Filter out non-LTE networks
                    if (cellInfoList.get(i).getClass().toString().equals("class android.telephony.CellInfoLte")) {
                        lteCellId = ((CellInfoLte) cellInfoList.get(i)).getCellIdentity();
                        lteSigStrength = ((CellInfoLte) cellInfoList.get(i)).getCellSignalStrength();
                        isServingCell = ((CellInfoLte) cellInfoList.get(i)).isRegistered();

                        // Update pData
                        pData[i][0] = ((lteCellId.getMccString() != null) && (lteCellId.getMncString() != null)) ?
                                Integer.parseInt(lteCellId.getMccString() + lteCellId.getMncString()) : MAX_VAL; // MCC + MNC
                        pData[i][1] = lteCellId.getTac(); // TAC
                        pData[i][2] = lteCellId.getPci(); // Physical Cell ID
                        pData[i][3] = lteCellId.getEarfcn(); // EARFCN
                        pData[i][4] = (isServingCell) ? s_rsrp : lteSigStrength.getRsrp(); // RSRP
                        pData[i][5] = (isServingCell) ? s_rsrq : lteSigStrength.getRsrq(); // RSRQ
                        pData[i][6] = (isServingCell) ? s_rssnr : lteSigStrength.getRssnr(); // RSSNR
                        pData[i][7] = (isServingCell) ? s_cqi : lteSigStrength.getCqi(); // CQI
                        pData[i][8] = lteCellId.getBandwidth(); // BW_kHz
                        pData[i][9] = (isServingCell) ? 1 : 0; // BW

                        if (_DEBUG_ && isServingCell) {
                            System.out.println("SERVING CELL: (RSRP=" + s_rsrp + ", " + lteSigStrength.getRsrp() + ")");
                            System.out.println("SERVING CELL: (RSRP=" + s_rsrq + ", " + lteSigStrength.getRsrq() + ")");
                            System.out.println("SERVING CELL: (RSSNR=" + s_rssnr + ", " + lteSigStrength.getRssnr() + ")");
                            System.out.println("SERVING CELL: (RSRP=" + s_cqi + ", " + lteSigStrength.getCqi() + ")");
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void collectPassiveMeasurements() throws IOException {
        if (_DEBUG_) System.out.println("=====> inside collectPassiveMeasurements()");
        if (mTelephonyManager == null) throw new IOException("Null TelephonyManager");

        /***
         * Requests all available cell information from the current subscription for
         * observed camped/registered, serving, and neighboring cells.
         */
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    if (_DEBUG_) System.out.println("LOCATION INFO: latitude = " + latitude + ", longitude = " + longitude);
                }
            });
                    /*
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            // Got last known location. In some rare situations this can be null.
                            if (location != null) {
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();
                                if (_DEBUG_) System.out.println("LOCATION INFO: latitude = " + latitude + ", longitude = " + longitude);
                            }
                        }
                    });

                     */

            long currTime = System.currentTimeMillis();
            for (int i = 0; i < pData.length; i++) {
                PMR = currTime + "," + latitude + "," + longitude + "," + pData[i][0] + "," + pData[i][1] + "," +
                        pData[i][2] + "," + pData[i][3] + "," + pData[i][4] + "," + pData[i][5] + "," + pData[i][6] +
                        "," + pData[i][7] + "," + pData[i][8] + "," + pData[i][9];

                try {
                    writeMeasurementOutputs(PMR, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        else if (_DEBUG_) System.out.println("---------->PERMISSIONS ISSUE: TelephonyManager");
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void initializeOutputFiles(long time_msecs) {
        // Create and open output CSV files for passive and active measurement data
        String csvFileName = "Cell_Data_" + time_msecs + ".csv";
        if (_DEBUG_) System.out.println("Writing measurements out to files: " + csvFileName);
        initCSV(getApplicationContext(), csvFileName);

        if (_DEBUG_) System.out.println("writing to file " + csvFileName + " completed...");
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void initCSV(Context context, String fileName) {
        String mimeType = "text/csv";

        ContentValues p_contentValues = new ContentValues();
        p_contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "p" + fileName);
        p_contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        p_contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        ContentValues a_contentValues = new ContentValues();
        a_contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "a" + fileName);
        a_contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        a_contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, p_contentValues);
        if (uri != null) {
            try {
                pOSW = new OutputStreamWriter(resolver.openOutputStream(uri));
                pOSW.write("timestamp,lat,long,mcc-mnc,tac,pci,earfcn,rsrp,rsrq,rssnr,cqi,bw_khz,isServingCell" + "\n");
            } catch (Exception fnfe) {
                fnfe.printStackTrace();
            }
        }

        uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, a_contentValues);
        if (uri != null) {
            try {
                aOSW = new OutputStreamWriter(resolver.openOutputStream(uri));
                aOSW.write("timestamp,BW,minRTT,CwndGain,avgRTT,RTTvar,TotalRetrans,PacingGain,BusyTime,ElapsedTime,DLspeed" + "\n");
            } catch (Exception fnfe) {
                fnfe.printStackTrace();
            }
        }
    }

    public void closeOutputFiles() {
        // Close CSV files for passive and active measurement data
        if (_DEBUG_) System.out.println("Closing Output Files.");
        try {
            pOSW.flush();
            pOSW.close();

            aOSW.flush();
            aOSW.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (_DEBUG_) System.out.println("EXITING. CLOSING FILES.");
    }

    public void writeMeasurementOutputs(String measurements, boolean isActive) {
        try {
            if (isActive && aOSW != null && measurements != null) {
                if (_DEBUG_) System.out.println("Active measurements: " + measurements);
                aOSW.write(measurements + "\n");
            } else if (pOSW != null && measurements != null) {
                if (_DEBUG_) System.out.println("Passive measurements: " + measurements);
                pOSW.write(measurements + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void toggleEnabledButtons(boolean testIsRunning) {
        if (_DEBUG_) System.out.println("---TOGGLE--- (" + testIsRunning + ")");
        downloadButton.setEnabled(!testIsRunning);
        stopButton.setEnabled(testIsRunning);
        if (testIsRunning) {
            downloadProgressTextView.setText("");
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
        if (_DEBUG_) System.out.println("Download Progress: " + speed);
        downloadProgressTextView.setText(speed);

        writeMeasurementOutputs(ndtTestImpl.getAMR() + "," + speed, true);
    }

    private void processWait(long ms)
    {
        try { Thread.sleep(ms); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }

    /**
    public void sendMessage(View view) {
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(hostEdit.getWindowToken(), 0);
        sendButton.setEnabled(false);
        resultText.setText("");
        new GrpcTask(this)
                .execute(
                        hostEdit.getText().toString(),
                        messageEdit.getText().toString(),
                        portEdit.getText().toString());
    }

    private static class GrpcTask extends AsyncTask<String, Void, String> {
        private final WeakReference<Activity> activityReference;
        private ManagedChannel channel;

        private GrpcTask(Activity activity) {
            this.activityReference = new WeakReference<Activity>(activity);
        }

        @Override
        protected String doInBackground(String... params) {
            String host = params[0];
            String message = params[1];
            String portStr = params[2];
            int port = TextUtils.isEmpty(portStr) ? 0 : Integer.valueOf(portStr);
            try {
                channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
                GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
                HelloRequest request = HelloRequest.newBuilder().setName(message).build();
                HelloReply reply = stub.sayHello(request);
                return reply.getMessage();
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                pw.flush();
                return String.format("Failed... : %n%s", sw);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Activity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            TextView resultText = (TextView) activity.findViewById(R.id.grpc_response_text);
            Button sendButton = (Button) activity.findViewById(R.id.send_button);
            resultText.setText(result);
            sendButton.setEnabled(true);
        }
    }
     **/
}
