package com.example.android.sunshine.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class LoadBitmapTask extends AsyncTask<Asset,Void,Bitmap> {

    private final String LOG_TAG = LoadBitmapTask.class.getSimpleName();
    private static final long TIMEOUT_MS = 100;
    private GoogleApiClient googleApiClient;
    private LoadBitmapListener listener;

    public interface LoadBitmapListener {
        void onLoadBitmapFinished(Bitmap bitmap);
    }

    public LoadBitmapTask(GoogleApiClient googleApiClient, LoadBitmapListener callback){
        this.googleApiClient = googleApiClient;
        this.listener = callback;
    }

    @Override
    protected Bitmap doInBackground(Asset... assets) {
        Asset asset = assets[0];

        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        // decode the stream into a bitmap
        return loadBitmapFromAsset(asset);
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        super.onPostExecute(bitmap);
        listener.onLoadBitmapFinished(bitmap);
    }

    /**
     * https://developer.android.com/training/wearables/data-layer/assets.html
     * @return bitmap
     */
    private Bitmap loadBitmapFromAsset(Asset asset) {
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
            Log.w(LOG_TAG, "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }
}
