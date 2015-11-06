package com.example.android.sunshine.app;

/**
 * Created by lmedina on 11/5/2015.
 */
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

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMapItem;

public final class DigitalWatchFaceUtil {
    private static final String TAG = "DigitalWatchFaceUtil";

    public static final String PATH = "/sunshine_data_update";


    public interface FetchConfigDataMapCallback {
        void onConfigDataMapFetched(DataMapItem dataMapItem);
    }

//    private static int parseColor(String colorName) {
//        return Color.parseColor(colorName.toLowerCase());
//    }
//
//    public static void fetchConfigDataMap(final GoogleApiClient client,
//                                          final FetchConfigDataMapCallback callback) {
////        Wearable.NodeApi.getConnectedNodes(client);
////        Wearable.NodeApi.getLocalNode(client).setResultCallback(
////                new ResultCallback<NodeApi.GetLocalNodeResult>() {
////                    @Override
////                    public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
////                        String localNode = getLocalNodeResult.getNode().getId();
////                        Uri uri = new Uri.Builder()
////                                .scheme("wear")
////                                .path(DigitalWatchFaceUtil.PATH)
////                                .authority(localNode)
////                                .build();
////                        Wearable.DataApi.getDataItem(client, uri)
////                                .setResultCallback(new DataItemResultCallback(callback));
////                    }
////                }
////        );
//    }
//
//    /**
//     * Overwrites (or sets, if not present) the keys in the current config {@link DataItem} with
//     * the ones appearing in the given {@link DataMap}. If the config DataItem doesn't exist,
//     * it's created.
//     * <p>
//     * It is allowed that only some of the keys used in the config DataItem appear in
//     * {@code configKeysToOverwrite}. The rest of the keys remains unmodified in this case.
//     */
////    public static void overwriteKeysInConfigDataMap(final GoogleApiClient googleApiClient,
////                                                    final DataMap configKeysToOverwrite) {
////
////        DigitalWatchFaceUtil.fetchConfigDataMap(googleApiClient,
////                new FetchConfigDataMapCallback() {
////                    @Override
////                    public void onConfigDataMapFetched(DataMapItem dataMapItem) {
//////                        DataMap overwrittenConfig = new DataMap();
//////                        overwrittenConfig.putAll(dataMapItem.getDataMap());
//////                        overwrittenConfig.putAll(configKeysToOverwrite);
////
////                        Log.e(TAG, "Max temp is " + configKeysToOverwrite.getString("maxTemp", "None"));
////                        Log.e(TAG, "Min temp is " + configKeysToOverwrite.getString("minTemp", "None"));
////                        Log.e(TAG, "Asset is " + configKeysToOverwrite.getAsset("weatherImage"));
////
////                        Log.e(TAG, "DataMapFetched");
////                        DigitalWatchFaceUtil.putConfigDataItem(googleApiClient, configKeysToOverwrite);
////                    }
////                }
////        );
////    }
//
//    /**
//     * Overwrites the current config {@link DataItem}'s {@link DataMap} with {@code newConfig}.
//     * If the config DataItem doesn't exist, it's created.
//     */
//    public static void putConfigDataItem(GoogleApiClient googleApiClient, DataMap newConfig) {
//        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH);
//        DataMap configToPut = putDataMapRequest.getDataMap();
//        configToPut.putAll(newConfig);
//        Wearable.DataApi.putDataItem(googleApiClient, putDataMapRequest.asPutDataRequest())
//                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
//                    @Override
//                    public void onResult(DataApi.DataItemResult dataItemResult) {
//                        Log.e(TAG, "putDataItem result status: " + dataItemResult.getStatus());
//                    }
//                });
//    }

    public static class DataItemResultCallback implements ResultCallback<DataApi.DataItemResult> {

        private final FetchConfigDataMapCallback mCallback;

        public DataItemResultCallback(FetchConfigDataMapCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onResult(DataApi.DataItemResult dataItemResult) {
            if (dataItemResult.getStatus().isSuccess()) {
                if (dataItemResult.getDataItem() != null) {
                    DataItem configDataItem = dataItemResult.getDataItem();
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(configDataItem);
                    mCallback.onConfigDataMapFetched(dataMapItem);
                } else {
                    mCallback.onConfigDataMapFetched(null);
                }
            }
        }
    }

    private DigitalWatchFaceUtil() { }
}
