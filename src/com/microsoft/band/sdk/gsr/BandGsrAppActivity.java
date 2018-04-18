//Copyright (c) Microsoft Corporation All rights reserved.  
// 
//MIT License: 
// 
//Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
//documentation files (the  "Software"), to deal in the Software without restriction, including without limitation
//the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
//to permit persons to whom the Software is furnished to do so, subject to the following conditions: 
// 
//The above copyright notice and this permission notice shall be included in all copies or substantial portions of
//the Software. 
// 
//THE SOFTWARE IS PROVIDED ""AS IS"", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
//TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
//THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
//CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
//IN THE SOFTWARE.
package com.microsoft.band.sdk.gsr;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandIOException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandGsrEvent;
import com.microsoft.band.sensors.BandGsrEventListener;
import com.microsoft.band.sensors.BandSkinTemperatureEvent;
import com.microsoft.band.sensors.HeartRateConsentListener;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.Context;
import android.app.Activity;
import android.app.Service;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.renderscript.ScriptGroup;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.concurrent.Delayed;


public class BandGsrAppActivity extends Activity {

    private BandClient client = null;
    private Button btnStart;
    private Button btnUpdate;
    private ToggleButton Data;
    private TextView txtStatus;
    private int gsrValue = 0;

    private DataCollectionService mBoundService;
    boolean mIsBound;
    Object dataset[] = {5};

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((DataCollectionService.LocalBinder) service).getService();
            System.out.println("onServiceConnected is called");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(this,
                DataCollectionService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        System.out.println("doBindService");
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    /*instant update of data, pulls data and stores in variable for each instance the button is pushed*/
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WeakReference<Activity> reference = new WeakReference<Activity>(this);

        txtStatus = (TextView) findViewById(R.id.txtStatus);
        btnStart = (Button) findViewById(R.id.btnStart);
        btnStart.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                txtStatus.setText("");

                //if (client.getSensorManager().getCurrentHeartRateConsent() != UserConsent.GRANTED) {
                new HeartRateConsentTask().execute(reference);
                //}

                //if(mBoundService == null)
                doBindService();
            }
        });

        btnUpdate = (Button) findViewById(R.id.btnUpdate);
        btnUpdate.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mIsBound && mBoundService != null) {
                    GSRData data = mBoundService.getGSRData();
                    AccData accdata = mBoundService.getAccData();
                    HRData hrdata = mBoundService.getHRData();
                    RRData rrdata = mBoundService.getRRData();
                    SkinTempData tempdata = mBoundService.getTempData();
                    if(data != null)
                        appendToUI(String.format("Resistance = %d kOhms", data.gsrValue)+", heart rate="+hrdata.hr+", RR="+rrdata.rr +", " + accdata.x + "Temp F: " + tempdata.TempDataF + "Temp C: "+ tempdata.TempDataC);
                }
            }
        });

        /*Toggle button for data, continously runs, but not on a timer*/
        Data = (ToggleButton) findViewById(R.id.Data);
        Data.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                         if(isChecked){
                         double test[] = new double[100];
                        for(int i =0; i< 99;i++) {
                               // testing[i] = test[i];
                        if (mIsBound && mBoundService != null ) {
                            GSRData data = mBoundService.getGSRData();//Galvanic Skin Response Variable
                            AccData accdata = mBoundService.getAccData();//Accelorometer data
                            HRData hrdata = mBoundService.getHRData();//Heart rate data
                            RRData rrdata = mBoundService.getRRData();//Heart Rate Variability
                            SkinTempData tempdata = mBoundService.getTempData();//Skin Temperature Data
                            test[i]= data.ts;
                            if (data != null)
                                appendToUI(String.format("Resistance = %d kOhms", data.gsrValue) + ", heart rate=" + hrdata.hr + ", RR=" + rrdata.rr + ", " + accdata.x + "Temp F: " + tempdata.TempDataF + "Temp C: " + tempdata.TempDataC);
                        }
                            appendToUI(Double.toString(test[1])+"\n"+ Double.toString(test[2])
                            + "\n" + Double.toString(test[50])+ "\n"+Double.toString(test[99]));
                        }


               }else{
                    appendToUI(String.format("Data Collection has stopped"));
               }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        doBindService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mIsBound) {
            doUnbindService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //txtStatus.setText("");
        //appendToUI(String.format("Resistance = %d kOhms\n", gsrValue));
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {

	       super.onDestroy();
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

    private class HeartRateConsentTask extends AsyncTask<WeakReference<Activity>, Void, Void> {
        @Override
        protected Void doInBackground(WeakReference<Activity>... params) {
            try {
                if (getConnectedBandClient()) {

                    if (params[0].get() != null) {
                        client.getSensorManager().requestHeartRateConsent(params[0].get(), new HeartRateConsentListener() {
                            @Override
                            public void userAccepted(boolean consentGiven) {
                            }
                        });
                    }
                } else {
                    appendToUI("Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage = "";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                appendToUI(exceptionMessage);

            } catch (Exception e) {
                appendToUI(e.getMessage());
            }
            return null;
        }

    }

    private void appendToUI(final String string) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtStatus.setText(string);
            }
        });
    }



}

