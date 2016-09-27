package com.example.android.sunshine.app.sync;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * helper methods.
 */
public class SunshineWearIntentService extends IntentService implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private final String TAG = SunshineWearIntentService.class.getSimpleName();

    private static final String WEATHER_UPDATE = "com.example.android.sunshine.app.sync.action.weather_update";

    private static final String EXTRA_ID = "com.example.android.sunshine.app.sync.extra.id";
    private static final String EXTRA_HIGH = "com.example.android.sunshine.app.sync.extra.high";
    private static final String EXTRA_LOW = "com.example.android.sunshine.app.sync.extra.low";
    private static final String EXTRA_ICON_ID = "com.example.android.sunshine.app.sync.extra.icon_id";
    private static final String DATA_MAP_WEATHER = "/forecast";

    private int weatherId = 0;
    private double high = Integer.MAX_VALUE;
    private double low = Integer.MIN_VALUE;
    private int iconId = -1;
    private GoogleApiClient googleApiClient;

    public SunshineWearIntentService() {
        super("SunshineWearIntentService");
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionWearWeatherUpdate(Context context, int weatherId, double high, double low, int iconId) {
        Intent intent = new Intent(context, SunshineWearIntentService.class);
        intent.setAction(WEATHER_UPDATE);
        intent.putExtra(EXTRA_ID, weatherId);
        intent.putExtra(EXTRA_HIGH, high);
        intent.putExtra(EXTRA_LOW, low);
        intent.putExtra(EXTRA_ICON_ID, iconId);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (WEATHER_UPDATE.equals(action)) {
                final int weatherId = intent.getIntExtra(EXTRA_ID, 0);
                final double high = intent.getDoubleExtra(EXTRA_HIGH, Integer.MAX_VALUE);
                final double low = intent.getDoubleExtra(EXTRA_LOW, Integer.MIN_VALUE);
                final int iconId = intent.getIntExtra(EXTRA_ICON_ID, -1);
                handleActionWearWeatherUpdate(weatherId, high, low, iconId);
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionWearWeatherUpdate(int weatherId, double high, double low, int iconId) {
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            googleApiClient.connect();
        }

        // Check if anything has changed
        if ((this.weatherId != weatherId) || (this.high != high) || (this.low != low) || (this.iconId != iconId)) {
            PutDataMapRequest weatherDataMapRequest = PutDataMapRequest.create(DATA_MAP_WEATHER);
            DataMap weatherRequestDataMap = weatherDataMapRequest.getDataMap();
            weatherRequestDataMap.putDouble("high", high);
            weatherRequestDataMap.putDouble("low", low);
            weatherRequestDataMap.putAsset("icon", getWeatherIcon(weatherId));
            PutDataRequest weatherRequest = weatherDataMapRequest.asPutDataRequest();
            weatherRequest.setUrgent();
            Wearable.DataApi.putDataItem(googleApiClient, weatherRequest)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {
                                Log.d(TAG, "Failed to send weather data");
                            } else {
                                Log.d(TAG, "Successfully sent weather data");
                            }
                        }
                    });
            Log.d(TAG, "handleActionWearWeatherUpdate: high=" + high + " low=" + low);
        }
        this.weatherId = weatherId;
        this.high = high;
        this.low = low;
        this.iconId = iconId;
    }

    private Asset getWeatherIcon(int weatherId) {
        int iconId = Utility.getIconResourceForWeatherCondition(weatherId);
        Bitmap weatherIcon = BitmapFactory.decodeResource(getApplicationContext().getResources(), iconId);
        ByteArrayOutputStream weatherIconByteStream = new ByteArrayOutputStream();
        weatherIcon.compress(Bitmap.CompressFormat.PNG, 100, weatherIconByteStream);
        return Asset.createFromBytes(weatherIconByteStream.toByteArray());
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
