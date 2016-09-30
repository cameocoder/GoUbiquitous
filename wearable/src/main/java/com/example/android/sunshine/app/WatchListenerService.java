package com.example.android.sunshine.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Receive weather updates from mobile and dispatch them to watch face
 */
public class WatchListenerService extends WearableListenerService {
    private static final String TAG = WatchListenerService.class.getSimpleName();

    private static final String DATA_MAP_WEATHER = "/forecast";
    private static final String DATA_MAP_WEATHER_KEY_HIGH = "high";
    private static final String DATA_MAP_WEATHER_KEY_LOW = "low";
    private static final String DATA_MAP_WEATHER_KEY_ICON = "icon";
    private static final long TIMEOUT_MS = 100;

    private static WeatherUpdatedListener weatherUpdatedListener;

    public interface WeatherUpdatedListener {
        void onWeatherUpdateFinished(String high, String low, Bitmap bitmap);
    }

    public static void setWeatherUpdatedListener(WeatherUpdatedListener listener) {
        weatherUpdatedListener = listener;
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d(TAG, "onDataChanged: ");
        for (DataEvent dataEvent : dataEventBuffer) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                String path = dataEvent.getDataItem().getUri().getPath();
                Log.d(TAG, "onDataChanged: " + path);
                if (path.equals(DATA_MAP_WEATHER)) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataEvent.getDataItem());
                    DataMap dataMap = dataMapItem.getDataMap();
                    String highTemperature = dataMap.getString(DATA_MAP_WEATHER_KEY_HIGH, "");
                    String lowTemperature = dataMap.getString(DATA_MAP_WEATHER_KEY_LOW, "");
                    Asset iconAsset = dataMap.getAsset(DATA_MAP_WEATHER_KEY_ICON);
                    Bitmap bitmap = null;
                    if (iconAsset != null) {
                        bitmap = loadBitmapFromAsset(iconAsset);
                    }

                    weatherUpdatedListener.onWeatherUpdateFinished(highTemperature, lowTemperature, bitmap);
                    Log.d(TAG, "onDataChanged: high=" + highTemperature + " low=" + lowTemperature);
                }
            }
        }
    }

    /**
     * https://developer.android.com/training/wearables/data-layer/assets.html
     * @return bitmap
     */
    private Bitmap loadBitmapFromAsset(Asset asset) {

        final GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        googleApiClient.connect();

        ConnectionResult result =
                googleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                googleApiClient, asset).await().getInputStream();
        googleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }

}
