package com.example.android.sunshine.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by lmedina on 11/5/2015.
 */
public class DigitalWatchFaceListenerService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "DigitalListenerService";

    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        Log.e(TAG, "Data changed");

//        if (mGoogleApiClient == null) {
//            mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
//                    .addOnConnectionFailedListener(this).addApi(Wearable.API).build();
//        }
//        if (!mGoogleApiClient.isConnected()) {
//            ConnectionResult connectionResult =
//                    mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);
//
//            if (!connectionResult.isSuccess()) {
//                Log.e(TAG, "Failed to connect to GoogleApiClient.");
//                return;
//            }
//        }

        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                continue;
            }

            DataItem dataItem = dataEvent.getDataItem();
            if (!dataItem.getUri().getPath().equals(DigitalWatchFaceUtil.PATH)) {
                continue;
            }

            DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
            Log.e(TAG, "Max temp is " + dataMapItem.getDataMap().getString("maxTemp", "None"));
            Log.e(TAG, "Min temp is " + dataMapItem.getDataMap().getString("minTemp", "None"));

            SunshineWatchFaceService.setReceivedData(dataMapItem);
            Log.e(TAG, "Overwrite called");
        }


    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);
        Log.e(TAG, "Peer connected");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "Service Created");
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.e(TAG, "Service started");
    }

    @Override // WearableListenerService
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.e(TAG, "Message received");
        if (!messageEvent.getPath().equals(DigitalWatchFaceUtil.PATH)) {
            return;
        }

        Log.e(TAG, "Path is valid");
        byte[] rawData = messageEvent.getData();
        // It's allowed that the message carries only some of the keys used in the config DataItem
        // and skips the ones that we don't want to change.
        DataMap configKeysToOverwrite = DataMap.fromByteArray(rawData);
        Log.e(TAG, "Received watch face config message: " + configKeysToOverwrite);

//        if (mGoogleApiClient == null) {
//            mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
//                    .addOnConnectionFailedListener(this).addApi(Wearable.API).build();
//        }
//        if (!mGoogleApiClient.isConnected()) {
//            ConnectionResult connectionResult =
//                    mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);
//
//            if (!connectionResult.isSuccess()) {
//                Log.e(TAG, "Failed to connect to GoogleApiClient.");
//                return;
//            }
//        }

        //DigitalWatchFaceUtil.overwriteKeysInConfigDataMap(mGoogleApiClient, configKeysToOverwrite);
    }

    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnected: " + connectionHint);
        }
    }

    @Override  // GoogleApiClient.ConnectionCallbacks
    public void onConnectionSuspended(int cause) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnectionSuspended: " + cause);
        }
    }

    @Override  // GoogleApiClient.OnConnectionFailedListener
    public void onConnectionFailed(ConnectionResult result) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnectionFailed: " + result);
        }
    }

}
