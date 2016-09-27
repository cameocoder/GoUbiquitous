/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    public final String TAG = CanvasWatchFaceService.class.getSimpleName();

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private static final String DATA_MAP_WEATHER = "/forecast";
    private static final String DATA_MAP_WEATHER_REQUEST = "/forecast_request";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WatchFace.Engine> mWeakReference;

        public EngineHandler(WatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mAmbient;

        Paint mBackgroundPaint;
        Paint timePaint;
        Paint datePaint;
        Paint dividerPaint;
        Paint highPaint;
        Paint lowPaint;

        float timeOffsetX;
        float timeOffsetY;
        float dateOffsetX;
        float dateOffsetY;
        float dividerOffsetY;

        float weatherOffsetY;

        Calendar mCalendar;
        SimpleDateFormat dateFormat;
        SimpleDateFormat timeFormat;
        SimpleDateFormat timeFormatAmbient;
        String temperatureFormat;

        int highTemperature = Integer.MAX_VALUE;
        int lowTemperature = Integer.MIN_VALUE;
        Bitmap weatherBitmap;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        GoogleApiClient mAPiClient = new GoogleApiClient.Builder(WatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

///            startService(new Intent(getApplicationContext(), SunshineWearableListenerService.class));

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = WatchFace.this.getResources();
            timeOffsetY = resources.getDimension(R.dimen.digital_time_offset_y);
            dateOffsetY = resources.getDimension(R.dimen.digital_date_offset_y);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getBaseContext(), R.color.background));

            timePaint = new Paint();
            timePaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.primary_text));

            datePaint = new Paint();
            datePaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.secondary_text));

            dividerPaint = new Paint();
            dividerPaint = createPaint(ContextCompat.getColor(getBaseContext(), R.color.divider));

            highPaint = new Paint();
            highPaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.primary_text));

            lowPaint = new Paint();
            lowPaint = createTextPaint(ContextCompat.getColor(getBaseContext(), R.color.secondary_text));


            dividerOffsetY = resources.getDimension(R.dimen.digital_divider_offset_y);
            weatherOffsetY = resources.getDimension(R.dimen.digital_temp_offset_y);

            mCalendar = Calendar.getInstance();
            dateFormat = new SimpleDateFormat(getString(R.string.date_format), Locale.getDefault());
            timeFormat = new SimpleDateFormat(
                    DateFormat.is24HourFormat(getApplicationContext()) ?
                            getString(R.string.time_format_24) : getString(R.string.time_format),
                    Locale.getDefault());
            timeFormatAmbient = new SimpleDateFormat(
                    DateFormat.is24HourFormat(getApplicationContext()) ?
                            getString(R.string.time_format_24_ambient) : getString(R.string.time_format_ambient),
                    Locale.getDefault());

            temperatureFormat = getString(R.string.temperature_format);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            removeDataApiListener();
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createPaint(int color) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mAPiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                removeDataApiListener();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WatchFace.this.getResources();
            boolean isRound = insets.isRound();

//            timeOffsetX = resources.getDimension(isRound
//                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
//
//            dateOffsetX = resources.getDimension(isRound
//                    ? R.dimen.digital_x_offset_date_round : R.dimen.digital_x_offset_date);

            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.date_text_size_round : R.dimen.date_text_size);

            float tempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);

            timePaint.setTextSize(timeTextSize);
            datePaint.setTextSize(dateTextSize);
            highPaint.setTextSize(tempTextSize);
            lowPaint.setTextSize(tempTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    timePaint.setAntiAlias(!inAmbientMode);
                    datePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            Date date = mCalendar.getTime();
            String timeString = mAmbient
                    ? timeFormatAmbient.format(date)
                    : timeFormat.format(date);

            String dateString = dateFormat.format(date);
            String highTemperatureString = String.format(temperatureFormat, highTemperature);
            String lowTemperatureString = String.format(temperatureFormat, lowTemperature);

            timeOffsetX = bounds.centerX() - timePaint.measureText(timeString) / 2f;
            dateOffsetX = bounds.centerX() - datePaint.measureText(dateString) / 2f;

            final int dividerLength = bounds.width() / 5;
            final float dividerOffsetStartX = bounds.centerX() - (dividerLength / 2f);
            final float dividerOffsetEndX = bounds.centerX() + (dividerLength / 2f);


            final float highOffsetX = bounds.centerX() - highPaint.measureText(highTemperatureString) / 2f;
            final float lowOffsetX = bounds.centerX() + bounds.width() / 5f;
            final float iconOffsetX = bounds.centerX() - bounds.width() / 2.5f;

            canvas.drawText(timeString, timeOffsetX, timeOffsetY, timePaint);
            canvas.drawText(dateString, dateOffsetX, dateOffsetY, datePaint);

            canvas.drawLine(dividerOffsetStartX, dividerOffsetY, dividerOffsetEndX,
                    dividerOffsetY, dividerPaint);

            // Check if weather info has been initialized
            if (highTemperature <= 200) {
                canvas.drawText(highTemperatureString, highOffsetX, weatherOffsetY, highPaint);
                canvas.drawText(lowTemperatureString, lowOffsetX, weatherOffsetY, lowPaint);
                if (weatherBitmap != null) {
                    canvas.drawBitmap(weatherBitmap, iconOffsetX, weatherOffsetY, highPaint);
                }
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void getWeatherData() {
            PutDataMapRequest weatherDataMapRequest = PutDataMapRequest.create(DATA_MAP_WEATHER_REQUEST);
            DataMap weatherRequestDataMap = weatherDataMapRequest.getDataMap();
            weatherRequestDataMap.putBoolean("get_weather", true);
            PutDataRequest weatherRequest = weatherDataMapRequest.asPutDataRequest();
            weatherRequest.setUrgent();
            Wearable.DataApi.putDataItem(mAPiClient, weatherRequest)
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

        }

        private void removeDataApiListener() {
            if (mAPiClient != null && mAPiClient.isConnected()) {
                Wearable.DataApi.removeListener(mAPiClient, this);
                mAPiClient.disconnect();
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mAPiClient, this);
            getWeatherData();
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    String path = dataEvent.getDataItem().getUri().getPath();
                    if (path.equals(DATA_MAP_WEATHER)) {
                        DataMapItem dataMapItem = DataMapItem.fromDataItem(dataEvent.getDataItem());
                        DataMap dataMap = dataMapItem.getDataMap();
                        highTemperature = (int) dataMap.getDouble("high",  Integer.MAX_VALUE);
                        lowTemperature = (int) dataMap.getDouble("low", Integer.MIN_VALUE);
                        Asset iconAsset = dataMap.getAsset("icon");
                        if (iconAsset != null) {
//                            weatherBitmap = loadBitmapFromAsset(iconAsset);
                        }
                        Log.d(TAG, "onDataChanged: high=" + highTemperature + " low=" + lowTemperature);
                        invalidate();
                    }
                }
            }
        }

        private Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            ConnectionResult result =
                    mAPiClient.blockingConnect(1000, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    mAPiClient, asset).await().getInputStream();
            mAPiClient.disconnect();

            if (assetInputStream == null) {
                Log.w(TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }
    }
}
