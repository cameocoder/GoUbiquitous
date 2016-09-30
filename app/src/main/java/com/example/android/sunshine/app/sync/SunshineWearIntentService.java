package com.example.android.sunshine.app.sync;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
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
import java.util.Date;

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

    private static final String WEATHER_UPDATE_CURRENT = "com.example.android.sunshine.app.sync.action.current_weather_update";
    private static final String WEATHER_UPDATE = "com.example.android.sunshine.app.sync.action.weather_update";

    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC
    };

    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;

    private static final String EXTRA_ID = "com.example.android.sunshine.app.sync.extra.id";
    private static final String EXTRA_HIGH = "com.example.android.sunshine.app.sync.extra.high";
    private static final String EXTRA_LOW = "com.example.android.sunshine.app.sync.extra.low";
    private static final String EXTRA_ICON_ID = "com.example.android.sunshine.app.sync.extra.icon_id";
    private static final String DATA_MAP_WEATHER = "/forecast";
    private static final String DATA_MAP_WEATHER_KEY_HIGH = "high";
    private static final String DATA_MAP_WEATHER_KEY_LOW = "low";
    private static final String DATA_MAP_WEATHER_KEY_ICON = "icon";

    private GoogleApiClient googleApiClient;

    public SunshineWearIntentService() {
        super("SunshineWearIntentService");
    }

    /**
     * Starts this service to perform action WEATHER_UPDATE_CURRENT with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionWearCurrentWeatherUpdate(Context context) {
        Intent intent = new Intent(context, SunshineWearIntentService.class);
        intent.setAction(WEATHER_UPDATE_CURRENT);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action WEATHER_UPDATE with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionWearWeatherUpdate(Context context, double high, double low, int iconId) {
        Intent intent = new Intent(context, SunshineWearIntentService.class);
        intent.setAction(WEATHER_UPDATE);
        intent.putExtra(EXTRA_HIGH, high);
        intent.putExtra(EXTRA_LOW, low);
        intent.putExtra(EXTRA_ICON_ID, iconId);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (WEATHER_UPDATE_CURRENT.equals(action)) {
                getCurrentWeather();
            }
             else if (WEATHER_UPDATE.equals(action)) {
                final double high = intent.getDoubleExtra(EXTRA_HIGH, Integer.MAX_VALUE);
                final double low = intent.getDoubleExtra(EXTRA_LOW, Integer.MIN_VALUE);
                final int iconId = intent.getIntExtra(EXTRA_ICON_ID, -1);
                handleActionWearWeatherUpdate(high, low, iconId);
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionWearWeatherUpdate(double high, double low, int iconId) {
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }
        googleApiClient.connect();

        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(DATA_MAP_WEATHER);
        DataMap dataMap = dataMapRequest.getDataMap();
        // time only used to make the request unique
        dataMap.putLong("time", new Date().getTime());
        dataMap.putString(DATA_MAP_WEATHER_KEY_HIGH, Utility.formatTemperature(getApplicationContext(), high));
        dataMap.putString(DATA_MAP_WEATHER_KEY_LOW, Utility.formatTemperature(getApplicationContext(), low));
        dataMap.putAsset(DATA_MAP_WEATHER_KEY_ICON, getWeatherIcon(iconId));
        PutDataRequest weatherRequest = dataMapRequest.asPutDataRequest();
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

    private void getCurrentWeather() {
        Context context = getApplicationContext();

        String locationQuery = Utility.getPreferredLocation(context);

        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

        // we'll query our contentProvider, as always
        Cursor cursor = context.getContentResolver().query(weatherUri, NOTIFY_WEATHER_PROJECTION, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            int weatherId = cursor.getInt(INDEX_WEATHER_ID);
            double high = cursor.getDouble(INDEX_MAX_TEMP);
            double low = cursor.getDouble(INDEX_MIN_TEMP);

            handleActionWearWeatherUpdate(high, low, weatherId);
        }
        cursor.close();
    }

}
