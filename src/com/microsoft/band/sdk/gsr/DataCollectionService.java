package com.microsoft.band.sdk.gsr;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandIOException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.sensors.BandGsrEvent;
import com.microsoft.band.sensors.BandGsrEventListener;
import com.microsoft.band.sensors.BandSensorManager;
import com.microsoft.band.sensors.BandAccelerometerEvent;
import com.microsoft.band.sensors.BandAccelerometerEventListener;
import com.microsoft.band.sensors.BandSkinTemperatureEvent;
import com.microsoft.band.sensors.BandSkinTemperatureEventListener;
import com.microsoft.band.sensors.GsrSampleRate;
import com.microsoft.band.sensors.SampleRate;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
//import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.sensors.BandRRIntervalEvent;
import com.microsoft.band.sensors.BandRRIntervalEventListener;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Created by wenbing on 9/10/17.
 * Edited by Joe Komperda 3/25/18 - Added class for Skin temperature data and conversion from Celsius to Farenheit
 */
public class DataCollectionService extends Service {

    private GSRData gsrdata = new GSRData();
    private AccData accdata = new AccData();
    private HRData hrdata = new HRData();
    private RRData rrdata = new RRData();
    private SkinTempData tempdata = new SkinTempData();
    private BandClient client = null;

    private BandGsrEventListener mGsrEventListener = new BandGsrEventListener() {
        @Override
        public void onBandGsrChanged(final BandGsrEvent event) {
            if (event != null) {
                gsrdata.gsrValue = event.getResistance();
                gsrdata.ts = event.getTimestamp();
                appendGsrLog("" + gsrdata.ts + "," + gsrdata.gsrValue);

            }
        }
    };

    private BandSkinTemperatureEventListener mSkinTempEventListener = new BandSkinTemperatureEventListener() {
        @Override
        public void onBandSkinTemperatureChanged(final BandSkinTemperatureEvent event) {
            if (event != null) {
                tempdata.TempDataC = event.getTemperature();
                tempdata.TempDataF = ((event.getTemperature()*9)/5)+32;
                tempdata.ts = event.getTimestamp();
            }
        }
    };



    private BandAccelerometerEventListener mAccelerometerEventListener = new BandAccelerometerEventListener() {
        @Override
        public void onBandAccelerometerChanged(final BandAccelerometerEvent event) {
            if (event != null) {
                accdata.x = event.getAccelerationX();
                accdata.y = event.getAccelerationY();
                accdata.z = event.getAccelerationZ();
                accdata.ts = event.getTimestamp();

                String accentry = String.format("%.5f,%.5f,%.5f", accdata.x, accdata.y, accdata.z);
                appendAccLog(accdata.ts+","+accentry);
            }
        }
    };

    private BandHeartRateEventListener mHeartRateEventListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(final BandHeartRateEvent event) {
            if (event != null) {
                hrdata.ts = event.getTimestamp();
                hrdata.hr = event.getHeartRate();
                hrdata.quality = event.getQuality().toString();

                String hrentry = hrdata.ts+","+hrdata.hr+","+hrdata.quality;
                System.out.println("heart rate update: "+hrentry);
                appendHrLog(hrentry);
            }
        }
    };

    private BandRRIntervalEventListener mRRIntervalEventListener = new BandRRIntervalEventListener() {
        @Override
        public void onBandRRIntervalChanged(final BandRRIntervalEvent event) {
            if (event != null) {
                rrdata.ts = event.getTimestamp();
                rrdata.rr = event.getInterval();

                String rrentry = rrdata.ts+","+rrdata.rr;
                System.out.println("RR update: "+rrentry);
                appendRRLog(rrentry);
            }
        }
    };

    //public

    public class LocalBinder extends Binder {
        com.microsoft.band.sdk.gsr.DataCollectionService getService() {
            System.out.println("localBinder getService");

            return com.microsoft.band.sdk.gsr.DataCollectionService.this;
        }
    }

    public GSRData getGSRData() {
        return this.gsrdata;
    }

    public HRData getHRData() {
        return this.hrdata;
    }

    public AccData getAccData() {
        return this.accdata;
    }

    public RRData getRRData() {return this.rrdata; }

    public SkinTempData getTempData() {
        return this.tempdata;
    }

    @Override
    public void onCreate() {
        System.out.println("service onCreate");



        new DataCollectionService.SensorsSubscriptionTask().execute();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        System.out.println("service onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (client != null) {
            try {
                client.getSensorManager().unregisterGsrEventListener(mGsrEventListener);
            } catch (BandIOException e) {
                //appendToUI(e.getMessage());
            }
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private boolean getConnectedBandClient() throws InterruptedException, BandException {
        if (client == null) {
            BandInfo[] devices = BandClientManager.getInstance().getPairedBands();
            if (devices.length == 0) {
                //appendToUI("Band isn't paired with your phone.\n");
                return false;
            }
            client = BandClientManager.getInstance().create(getBaseContext(), devices[0]);
        } else if (ConnectionState.CONNECTED == client.getConnectionState()) {
            return true;
        }

        System.out.println("Band is connecting...\n");
        return ConnectionState.CONNECTED == client.connect().await();
    }


    private class SensorsSubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    int hardwareVersion = Integer.parseInt(client.getHardwareVersion().await());
                    if (hardwareVersion >= 20) {
                        //appendToUI("Band is connected.\n");
                        BandSensorManager mgr = client.getSensorManager();
                        mgr.registerGsrEventListener(mGsrEventListener, GsrSampleRate.MS200);
                        mgr.registerAccelerometerEventListener(mAccelerometerEventListener, SampleRate.MS128);
                        mgr.registerHeartRateEventListener(mHeartRateEventListener);
                        mgr.registerRRIntervalEventListener(mRRIntervalEventListener);
                        mgr.registerSkinTemperatureEventListener(mSkinTempEventListener);
                    } else {
                        //appendToUI("The Gsr sensor is not supported with your Band version. Microsoft Band 2 is required.\n");
                    }
                } else {
                    System.out.println("Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occurred: " + e.getMessage() + "\n";
                        break;
                }
                System.out.println(exceptionMessage);

            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
            return null;
        }
    }

    public void appendGsrLog(String text)
    {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "msbanddata");
        if(!dir.exists()) {
            if(!dir.mkdirs()) {
                System.out.println("cannot create directory");
            }
        }

        String filePath = dir.getPath().toString()+"/gsrlog.csv";

        File logFile = new File(filePath);
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.flush();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void appendAccLog(String text)
    {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "msbanddata");
        if(!dir.exists()) {
            if(!dir.mkdirs()) {
                System.out.println("cannot create directory");
            }
        }

        String filePath = dir.getPath().toString()+"/acclog.csv";

        File logFile = new File(filePath);
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.flush();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void appendHrLog(String text)
    {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "msbanddata");
        if(!dir.exists()) {
            if(!dir.mkdirs()) {
                System.out.println("cannot create directory");
            }
        }

        String filePath = dir.getPath().toString()+"/hrlog.csv";

        File logFile = new File(filePath);
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.flush();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void appendRRLog(String text)
    {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "msbanddata");
        if(!dir.exists()) {
            if(!dir.mkdirs()) {
                System.out.println("cannot create directory");
            }
        }

        String filePath = dir.getPath().toString()+"/rrlog.csv";

        File logFile = new File(filePath);
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.flush();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}


