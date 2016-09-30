package com.example.android.sunshine.app.sync;

import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineWearableListenerService extends WearableListenerService {
    private static final String TAG = SunshineWearableListenerService.class.getSimpleName();
    private static final String DATA_MAP_WEATHER_REQUEST = "/forecast_request";
    private static final String DATA_MAP_WEATHER_REQUEST_KEY_GET_WEATHER = "get_weather";

    private GoogleApiClient mGoogleApiClient;

    public SunshineWearableListenerService() {
        Log.d(TAG, "SunshineWearableListenerService: ");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d(TAG, "onDataChanged: ");
        for (DataEvent dataEvent : dataEventBuffer) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem item = dataEvent.getDataItem();
                if (item.getUri().getPath().compareTo(DATA_MAP_WEATHER_REQUEST) == 0) {
//                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataEvent.getDataItem());
//                    DataMap dataMap = dataMapItem.getDataMap();
//                    boolean getWeather = dataMap.getBoolean(DATA_MAP_WEATHER_REQUEST_KEY_GET_WEATHER, false);
                    Log.d(TAG, "onDataChanged: syncImmediately");
                    SunshineSyncAdapter.syncImmediately(this);
                }
            }
        }
    }
}
