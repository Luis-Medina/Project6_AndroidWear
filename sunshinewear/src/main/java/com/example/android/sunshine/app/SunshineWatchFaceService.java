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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Sample digital watch face with blinking colons and seconds. In ambient mode, the seconds are
 * replaced with an AM/PM indicator and the colons don't blink. On devices with low-bit ambient
 * mode, the text is drawn without anti-aliasing in ambient mode. On devices which require burn-in
 * protection, the hours are drawn in normal rather than bold. The time is drawn with less contrast
 * and without seconds in mute mode.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "SunshineWatchFace";

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for normal (not ambient and not mute) mode. We update twice
     * a second to blink the colons.
     */
    private static final long NORMAL_UPDATE_RATE_MS = 500;

    /**
     * Update rate in milliseconds for mute mode. We update every minute, like in ambient mode.
     */
    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("E, MMM d yyyy", Locale.getDefault());

    private static Engine mEngine;

    private String mMaxTemp = "";
    private String mMinTemp = "";
    private Bitmap mWeatherBitmap;

    @Override
    public Engine onCreateEngine() {
        mEngine = new Engine();
        return mEngine;
    }

    public static void setReceivedData(DataMapItem item){
        mEngine.processConfig(item);
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        static final String COLON_STRING = ":";

        /** Alpha value for drawing time when in mute mode. */
        static final int MUTE_ALPHA = 100;

        /** Alpha value for drawing time when not in mute mode. */
        static final int NORMAL_ALPHA = 255;

        static final int MSG_UPDATE_TIME = 0;

        /** How often {@link #mUpdateTimeHandler} ticks in milliseconds. */
        long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

        /** Handler to update the time periodically in interactive mode. */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "updating time");
                        }
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs =
                                    mInteractiveUpdateRateMs - (timeMs % mInteractiveUpdateRateMs);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        /**
         * Handles time zone and locale changes.
         */
        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        /**
         * Unregistering an unregistered receiver throws an exception. Keep track of the
         * registration state to prevent that.
         */
        boolean mRegisteredReceiver = false;

        Paint mBackgroundPaint;
        Paint mDatePaint;
        Paint mTimePaint;
        Paint mMaxPaint;
        Paint mMinPaint;
        boolean mMute;

        Calendar mCalendar;
        Date mDate;

        float mXOffset;
        float mYOffset;
        float mLineHeight;

        int AMBIENT_BACKGROUND;
        int AMBIENT_TIME_DIGITS;
        int AMBIENT_DATE_DIGITS;

        int INTERACTIVE_BACKGROUND;
        int INTERACTIVE_TIME_DIGITS;
        int INTERACTIVE_DATE_DIGITS;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;


        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);

            setColors(resources);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(INTERACTIVE_BACKGROUND);
            mDatePaint = createTextPaint(INTERACTIVE_DATE_DIGITS);
            mTimePaint = createTextPaint(INTERACTIVE_TIME_DIGITS, BOLD_TYPEFACE);
            mMaxPaint = createTextPaint(INTERACTIVE_TIME_DIGITS, BOLD_TYPEFACE);
            mMinPaint = createTextPaint(INTERACTIVE_DATE_DIGITS);

            mCalendar = Calendar.getInstance();
            mDate = new Date();
        }

        private void setColors(Resources resources){
            INTERACTIVE_BACKGROUND = resources.getColor(R.color.interactive_background);
            INTERACTIVE_TIME_DIGITS = resources.getColor(R.color.digital_time);
            INTERACTIVE_DATE_DIGITS = resources.getColor(R.color.digital_date);

            AMBIENT_BACKGROUND = resources.getColor(R.color.ambient_background);
            AMBIENT_TIME_DIGITS = resources.getColor(R.color.digital_time);
            AMBIENT_DATE_DIGITS = resources.getColor(R.color.digital_date);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int defaultInteractiveColor) {
            return createTextPaint(defaultInteractiveColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.e(TAG, "onVisibilityChanged: " + visible);
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
//                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
//                    Log.e(TAG, "Wearable DataApi listener removed");
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float bigTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_big_text_size_round : R.dimen.digital_big_text_size);
            float smallTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_small_text_size_round : R.dimen.digital_small_text_size);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_small_text_size));
            mDatePaint.setTextAlign(Paint.Align.CENTER);
            mTimePaint.setTextSize(bigTextSize);
            mTimePaint.setTextAlign(Paint.Align.CENTER);
            mMaxPaint.setTextSize(smallTextSize);
            mMaxPaint.setTextAlign(Paint.Align.CENTER);
            mMinPaint.setTextSize(smallTextSize);
            mMinPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mTimePaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: burn-in protection = " + burnInProtection
                        + ", low-bit ambient = " + mLowBitAmbient);
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            }
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.e(TAG, "Is in ambient mode: " + inAmbientMode);
            adjustPaintColorToCurrentMode(mBackgroundPaint, INTERACTIVE_BACKGROUND,
                    AMBIENT_BACKGROUND);
            adjustPaintColorToCurrentMode(mTimePaint, INTERACTIVE_TIME_DIGITS,
                    AMBIENT_TIME_DIGITS);
            adjustPaintColorToCurrentMode(mMaxPaint, INTERACTIVE_TIME_DIGITS,
                    AMBIENT_TIME_DIGITS);
            // Actually, the seconds are not rendered in the ambient mode, so we could pass just any
            // value as ambientColor here.
            adjustPaintColorToCurrentMode(mMinPaint, INTERACTIVE_DATE_DIGITS,
                    AMBIENT_DATE_DIGITS);

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mDatePaint.setAntiAlias(antiAlias);
                mTimePaint.setAntiAlias(antiAlias);
                mMaxPaint.setAntiAlias(antiAlias);
                mMinPaint.setAntiAlias(antiAlias);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        private void adjustPaintColorToCurrentMode(Paint paint, int interactiveColor,
                                                   int ambientColor) {
            paint.setColor(isInAmbientMode() ? ambientColor : interactiveColor);
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onInterruptionFilterChanged: " + interruptionFilter);
            }
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE;
            // We only need to update once a minute in mute mode.
            setInteractiveUpdateRateMs(inMuteMode ? MUTE_UPDATE_RATE_MS : NORMAL_UPDATE_RATE_MS);

            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                int alpha = inMuteMode ? MUTE_ALPHA : NORMAL_ALPHA;
                mDatePaint.setAlpha(alpha);
                mTimePaint.setAlpha(alpha);
                mMaxPaint.setAlpha(alpha);
                invalidate();
            }
        }

        public void setInteractiveUpdateRateMs(long updateRateMs) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return;
            }
            mInteractiveUpdateRateMs = updateRateMs;

            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerBeRunning()) {
                updateTimer();
            }
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            mTimePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(
                    timeFormat.format(mDate),
                    canvas.getWidth() / 2,
                    mYOffset,
                    mTimePaint);

            canvas.drawText(
                    dateFormat.format(mDate),
                    canvas.getWidth() / 2,
                    mYOffset + mLineHeight + 10,
                    mDatePaint);

            if(mWeatherBitmap != null){
                float left = (canvas.getWidth() / 2) - 90;
                float top = mYOffset + (mLineHeight * 2);
                canvas.drawBitmap(
                        mWeatherBitmap,
                        null,
                        new RectF(left,top,left+60, top+60),
                        null);
            }

            if(mMaxTemp == null) mMaxTemp = "";
            canvas.drawText(
                    mMaxTemp,
                    canvas.getWidth() / 2 + 10,
                    mYOffset + (mLineHeight * 3) + 10,
                    mMaxPaint);

            if(mMinTemp == null) mMinTemp = "";
            canvas.drawText(
                    mMinTemp,
                    (canvas.getWidth() / 2) + mMaxPaint.measureText(mMaxTemp) + 20,
                    mYOffset + (mLineHeight * 3) + 10,
                    mMinPaint);

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
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

        private void updateConfigDataItemAndUiOnStartup() {

            Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                @Override
                public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                    for(Node node : getConnectedNodesResult.getNodes()){
                        Log.e("NODES", node.getDisplayName());
                        String localNode = node.getId();

                        Uri uri = new Uri.Builder()
                                .scheme("wear")
                                .path(DigitalWatchFaceUtil.PATH)
                                .authority(localNode)
                                .build();
                        Wearable.DataApi.getDataItem(mGoogleApiClient, uri)
                                .setResultCallback(new DigitalWatchFaceUtil.DataItemResultCallback(new DigitalWatchFaceUtil.FetchConfigDataMapCallback() {
                                    @Override
                                    public void onConfigDataMapFetched(DataMapItem dataMapItem) {
                                        processConfig(dataMapItem);
                                    }
                                }));
                    }
                }
            }, 3, TimeUnit.SECONDS);

        }

        private void processConfig(DataMapItem dataMapItem){
            final DataMap config = dataMapItem.getDataMap();
            Log.e(TAG, "Processing config");
            if(config.containsKey("maxTemp")){
                mMaxTemp = config.getString("maxTemp");
                Log.e(TAG, "Received maxTemp: " + mMaxTemp);
            }
            if(config.containsKey("minTemp")){
                mMinTemp = config.getString("minTemp");
                Log.e(TAG, "Received minTemp: " + mMinTemp);
            }
            if(config.containsKey("weatherImage")){
                final Asset imageAsset = dataMapItem.getDataMap().getAsset("weatherImage");
                Log.e(TAG, "Received asset: " + imageAsset);
                Thread getImageThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mWeatherBitmap = loadBitmapFromAsset(imageAsset);

                        postInvalidate();
                    }
                });
                getImageThread.start();
            }else{
                invalidate();
            }
        }


        public Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                return null;
            }
//            ConnectionResult result =
//                    mGoogleApiClient.blockingConnect(30000, TimeUnit.MILLISECONDS);
//            if (!result.isSuccess()) {
//                Log.e(TAG, "No success");
//                return null;
//            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    mGoogleApiClient, asset).await().getInputStream();
            //mGoogleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.e(TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
//            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
//            Log.e(TAG, "Wearable DataApi listener added");
            updateConfigDataItemAndUiOnStartup();
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
}
