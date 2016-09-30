package com.example.android.sunshine.app.sync;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Receive weather update request from watch and send update back to watch
 */
public class SunshineWearableListenerService extends WearableListenerService {
    private static final String TAG = SunshineWearableListenerService.class.getSimpleName();
    private static final String DATA_MAP_WEATHER_REQUEST = "/forecast_request";

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
                    Log.d(TAG, "onDataChanged: syncImmediately");
                    Context context = getApplicationContext();
                    SunshineWearIntentService.startActionWearCurrentWeatherUpdate(context);
//                    SunshineSyncAdapter.syncImmediately(this);
                }
            }
        }
    }
}
