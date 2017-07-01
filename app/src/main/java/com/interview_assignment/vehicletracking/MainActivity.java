package com.interview_assignment.vehicletracking;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Assumption done:
 * - Considering vehicle will be starting, initial speed is taken as 30 km/h and initial location update interval is set as 5 minutes;
 */


public class MainActivity extends AppCompatActivity implements View.OnClickListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener{

    TextView tvLocationUpdateStatus;
    Button btnStartLocationUpdate;
    Button btnStopLocationUpdate;

    long locationUpdateInterval = 5 * 60 * 1000; //Initial interval set to 5 minutes

    float previousSpeed = 30; //Initial speed considered as 30 km/h

    private static final String LOCATION_UPDATE_FILE_NAME = "location_updates.txt";

    private static final String DATE_TIME_FORMAT = "dd/MM/yy-HH:mm:ss:SSS";

    DateFormat dateFormat;

    private static final int REQUEST_CODE_RECOVER_PLAY_SERVICES = 1000;

    private static final int REQUEST_RESOLVE_ERROR = 1001;

    private static final int REQUEST_RESOLVE_LOCATION_SETTING = 1002;

    private static final int PERMISSION_CALLBACK_CONSTANT = 1003;

    private static final String DIALOG_ERROR = "dialog_error";

    private GoogleApiClient mGoogleApiClient;

    private boolean mInProgress, mResolvingError;

    LocationRequest mLocationRequest;

    PendingResult<LocationSettingsResult> result;

    DatabaseHelper databaseHelper;

    String[] permissionsRequired = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION};

    @SuppressLint("SimpleDateFormat")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT);

        mInProgress = false;

        tvLocationUpdateStatus = (TextView) findViewById(R.id.tv_location_update_status);

        btnStartLocationUpdate = (Button) findViewById(R.id.btn_start_location_update);

        btnStartLocationUpdate.setOnClickListener(this);

        btnStopLocationUpdate = (Button) findViewById(R.id.btn_stop_location_update);

        btnStopLocationUpdate.setOnClickListener(this);

        buildGoogleApiClient();

        createLocationRequest();

        if (checkGooglePlayServices()){

            setUpLocationClientIfNeeded();

        }

    }

    private void setUpLocationClientIfNeeded(){
        if(mGoogleApiClient == null)
            buildGoogleApiClient();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

    }

    private void initiateGooglePlayApiConnection(){
        setUpLocationClientIfNeeded();

        if(!mGoogleApiClient.isConnected() || !mGoogleApiClient.isConnecting() && !mInProgress){
            mInProgress = true;
            mGoogleApiClient.connect();
        }
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(locationUpdateInterval);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private boolean checkGooglePlayServices(){
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(this);
        if(result != ConnectionResult.SUCCESS) {
            if(googleAPI.isUserResolvableError(result)) {
                googleAPI.getErrorDialog(this, result, REQUEST_CODE_RECOVER_PLAY_SERVICES).show();
            }

            return false;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESOLVE_ERROR){

            mResolvingError = false;

        }else if (requestCode == REQUEST_CODE_RECOVER_PLAY_SERVICES) {
            if (resultCode == RESULT_OK) {

            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.google_play_service_must, Toast.LENGTH_SHORT).show();

                finish();
            }
        }else if (requestCode == REQUEST_RESOLVE_LOCATION_SETTING){
            if (resultCode == RESULT_OK){
                initiateSettingCheckAndLocationUpdate();
            }else if(resultCode == RESULT_CANCELED){
                Toast.makeText(this, R.string.location_setting_on_must, Toast.LENGTH_SHORT).show();

                btnStartLocationUpdate.setEnabled(true);

                mGoogleApiClient.disconnect();

                mGoogleApiClient = null;
            }
        }
    }

    private void createTimeFileForStoringLocationUpdate(){
        String currentTime = "Time";
        String latitude = "Latitude";
        String longitude = "Longitude";
        String currentTimeInterval = "CurrentTimeInterval(seconds)";
        String nextTimeInterval = "NextTimeInterval(seconds)";

        String outputString = currentTime + " " + latitude + " " + longitude + " " + currentTimeInterval + " " + nextTimeInterval + "\n";

        rightToFile(outputString, true);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == btnStartLocationUpdate.getId()) {
            //setUpLocationFetching();
            btnStartLocationUpdate.setEnabled(false);

            initiateGooglePlayApiConnection();

            tvLocationUpdateStatus.setText(R.string.location_update_status_running_lbl);
        }else if(view.getId() == btnStopLocationUpdate.getId()){

            stopLocationUpdates();

            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()){
                mGoogleApiClient.disconnect();

                mGoogleApiClient = null;
            }

            tvLocationUpdateStatus.setText(R.string.location_update_status_stopped_lbl);

            btnStartLocationUpdate.setEnabled(true);
        }
    }

    private void generateStringToWrite(Location location, long previousLocationTimeInterval){
        String currentTime = dateFormat.format(Calendar.getInstance().getTime());
        String latitude = String.valueOf(location.getLatitude());
        String longitude = String.valueOf(location.getLongitude());
        String currentTimeInterval = String.valueOf(previousLocationTimeInterval / 1000); //(previousLocationTimeInterval > 30 * 1000 ?  previousLocationTimeInterval / 60 : previousLocationTimeInterval / 1000)
        String nextTimeInterval = String.valueOf(locationUpdateInterval / 1000);

        String outputString = currentTime + " " + latitude + " " + longitude + " " + currentTimeInterval + " " + nextTimeInterval + "\n";

        rightToFile(outputString, false);

        databaseHelper = DatabaseHelper.getInstance(this);

        databaseHelper.addLocationRecord(currentTime, latitude, longitude, currentTimeInterval, nextTimeInterval);
    }

    /**
     * As the speed received in location update is in m/sec, we have to convert it to km/h
     *
     * @param speed in float received in location instance
     *
     * @return float speed in km/h
     */
    private float convertSpeedToKmph(float speed){
        //1 meter/sec = 3.6 km/h
        return (float) (speed * 3.6);
    }

    /**
     * Method used to check whether we require to recalculate location update time interval or not.
     *
     * @param speed in float received in location instance
     *
     * @return boolean if change in location update is done.
     *
     * locationUpdateInterval being global variable can be accessed throught the class. So no need to retrun that.
     */
    private boolean checkIsLocationUpdateIntervalChangeRequired(float speed){
        /*
        We are only concerned if current vehicle speed is lower or higher than current speed, if it is same no change in interval is required
         */
        if (previousSpeed < speed){
           /*
           Vehicle is speeding, and for speeding it is not mentioned to gradually manage time increase
           so we will consider the time we found based on vehicle speed
           */
            previousSpeed = speed;

            long modifiedInterval = -1;

            if (speed >= 80){
                modifiedInterval = 30 * 1000;
            }else if(speed >= 60 && speed < 80){
                modifiedInterval = 60 * 1000;
            }else if(speed >= 30 && speed < 60){
                modifiedInterval = 2 * 60 * 1000;
            }else if (speed < 30){
                modifiedInterval = 5 * 60 * 1000;
            }

            if (locationUpdateInterval == modifiedInterval){
                return false;
            }else {
                locationUpdateInterval = modifiedInterval;

                return true;
            }
        }else if (previousSpeed > speed){
            /*
            Vehicle is slowing down
            */
            if (speed >= 80){
                /*
                No change is interval as though vehicle is slowing down but still its speed is above 80 km/h
                */
                return false;
            }else if(speed >= 60 && speed < 80){
                //60 * 1000;
                if (locationUpdateInterval == 30 * 1000){
                    locationUpdateInterval = 60 * 1000;

                    return true;
                }
                return false;
            }else if(speed >= 30 && speed < 60){
                //2 * 60 * 1000;
                if (locationUpdateInterval == 30 * 1000){
                    locationUpdateInterval = 60 * 1000;

                    return true;
                }else if (locationUpdateInterval == 60 * 1000){
                    locationUpdateInterval = 2 * 60 * 1000;

                    return true;
                }
                return false;
            }else if (speed < 30){
                //5 * 60 * 1000;
                if (locationUpdateInterval == 30 * 1000){
                    locationUpdateInterval = 60 * 1000;

                    return true;
                }else if (locationUpdateInterval == 60 * 1000){
                    locationUpdateInterval = 2 * 60 * 1000;

                    return true;
                }else if (locationUpdateInterval == 2 * 60 * 1000){
                    locationUpdateInterval = 5 * 60 * 1000;

                    return true;
                }
                return false;
            }
        }

        return false;

    }

    private void rightToFile(String strToWrite, boolean isHeader){
        File locationUpdateFile = new File(Environment.getExternalStorageDirectory(), LOCATION_UPDATE_FILE_NAME);

        if (locationUpdateFile.exists() && isHeader){
            //Don't want to write header label twice
            return;
        }

        try {
            FileOutputStream fos = new FileOutputStream(locationUpdateFile, true);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fos);
            outputStreamWriter.append(strToWrite);

            outputStreamWriter.close();

            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, permissionsRequired[0]) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, permissionsRequired[1]) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, permissionsRequired[2]) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, permissionsRequired[3]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissionsRequired, PERMISSION_CALLBACK_CONSTANT);

        }else{
            initiateSettingCheckAndLocationUpdate();
        }
    }
    private void initiateSettingCheckAndLocationUpdate(){
        createTimeFileForStoringLocationUpdate();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        builder.setAlwaysShow(true);

        result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        startLocationUpdates();

                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(MainActivity.this, REQUEST_RESOLVE_LOCATION_SETTING);
                        } catch (IntentSender.SendIntentException e) {

                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Toast.makeText(MainActivity.this, R.string.location_setting_unknown_error, Toast.LENGTH_SHORT).show();

                        btnStartLocationUpdate.setEnabled(true);

                        mGoogleApiClient.disconnect();

                        mGoogleApiClient = null;

                        break;
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Turn off the request flag
        mInProgress = false;
        // Destroy the current location client
        mGoogleApiClient = null;
        //Enable the start button
        btnStartLocationUpdate.setEnabled(true);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        mInProgress = false;
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (connectionResult.hasResolution()) {
            mResolvingError = true;

            try {
                connectionResult.startResolutionForResult(MainActivity.this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            showErrorDialog(connectionResult.getErrorCode());
            mResolvingError  = true;
        }
    }

    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    protected void stopLocationUpdates() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {

            btnStopLocationUpdate.performClick();

        }
    }

    @Override
    public void onLocationChanged(Location location) {
        long previousLocationTimeInterval = locationUpdateInterval;

        if (location.hasSpeed()){

            float speed = convertSpeedToKmph(location.getSpeed());

            if (checkIsLocationUpdateIntervalChangeRequired(speed)){
                //Stop location update
                stopLocationUpdates();

                //Create location request again with newly calculated location update time interval based on current speed.
                createLocationRequest();

                //Start location update again with new location request.
                startLocationUpdates();
            }

        }

        generateStringToWrite(location, previousLocationTimeInterval);
    }

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), "errordialog");
    }

    public void onDialogDismissed() {
        mResolvingError = false;
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() { }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GoogleApiAvailability.getInstance().getErrorDialog(
                    this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((MainActivity)getActivity()).onDialogDismissed();
        }
    }

    @Override
    public void onBackPressed() {
        if (btnStartLocationUpdate.isEnabled()){
            super.onBackPressed();
        }else {
            showCloseConfirmationDialog();
        }
    }

    private void showCloseConfirmationDialog(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        alertDialog.setTitle(null);

        alertDialog.setMessage(R.string.alert_location_service_running);

        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                btnStopLocationUpdate.performClick();

                onBackPressed();
            }
        });

        alertDialog.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });

        alertDialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == PERMISSION_CALLBACK_CONSTANT){
            if (grantResults.length > 0) {
                boolean storageAccepted = (grantResults[0] == PackageManager.PERMISSION_GRANTED) || (grantResults[1] == PackageManager.PERMISSION_GRANTED);
                boolean locationAccepted = (grantResults[2] == PackageManager.PERMISSION_GRANTED) || (grantResults[3] == PackageManager.PERMISSION_GRANTED);

                if (storageAccepted && locationAccepted){
                    initiateSettingCheckAndLocationUpdate();
                }else{
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissionsRequired[0])
                            || ActivityCompat.shouldShowRequestPermissionRationale(this, permissionsRequired[1])
                            || ActivityCompat.shouldShowRequestPermissionRationale(this, permissionsRequired[2])
                            || ActivityCompat.shouldShowRequestPermissionRationale(this, permissionsRequired[3])) {
                        //Show Information about why you need the permission
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle(R.string.permission_rationale_dialog_title);
                        builder.setMessage(R.string.permission_rationale_explanation);
                        builder.setPositiveButton(R.string.permission_dialog_grant_lbl, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                                ActivityCompat.requestPermissions(MainActivity.this, permissionsRequired, PERMISSION_CALLBACK_CONSTANT);
                            }
                        });
                        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();

                                btnStartLocationUpdate.setEnabled(true);

                                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()){
                                    mGoogleApiClient.disconnect();
                                }
                            }
                        });
                        builder.show();
                    }else{
                        //Case of User said never ask again. So better stop the application.
                        btnStartLocationUpdate.setEnabled(true);

                        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()){
                            mGoogleApiClient.disconnect();
                        }

                        Toast.makeText(MainActivity.this, R.string.permission_missing, Toast.LENGTH_LONG).show();
                        //finish();
                    }
                }
            }
        }

    }
}
