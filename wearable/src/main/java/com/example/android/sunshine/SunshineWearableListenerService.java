package com.example.android.sunshine;

import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineWearableListenerService extends WearableListenerService {
    public final String TAG = SunshineWearableListenerService.class.getSimpleName();
    private static final String DATA_MAP_WEATHER = "/wear/weather";

    public SunshineWearableListenerService() {
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        super.onDataChanged(dataEventBuffer);

        for (DataEvent dataEvent : dataEventBuffer) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                String path = dataEvent.getDataItem().getUri().getPath();
                if (path.equals(DATA_MAP_WEATHER)) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataEvent.getDataItem());
                    DataMap dataMap = dataMapItem.getDataMap();
                    Double high = dataMap.getDouble("high");
                    Double low = dataMap.getDouble("low");
                    Log.d(TAG, "onDataChanged: high=" + high + " low=" + low);
                }

            }
        }
    }
}
