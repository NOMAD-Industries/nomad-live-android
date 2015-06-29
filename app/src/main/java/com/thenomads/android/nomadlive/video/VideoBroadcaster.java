package com.thenomads.android.nomadlive.video;

import android.app.AlertDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.thenomads.android.nomadlive.SECRETS;

import io.cine.android.BroadcastConfig;
import io.cine.android.CineIoClient;
import io.cine.android.CineIoConfig;
import io.swagger.client.ApiException;
import io.swagger.client.api.StreamsApi;
import io.swagger.client.model.Stream;

/**
 * A wrapper to the Cine.IO Broadcaster.
 * Wraps around a Button.
 */
public class VideoBroadcaster {

    private static final String TAG = "VideoBroadcaster";

    private BroadcastConfig mBroadcastConfig;
    private CineIoClient mCineIoClient;
    private Button mBroadcastButton;
    private Context mContext;

    private Heartbeater mHeartbeater;
    private StreamsApi mStreamsAPI;
    private Stream mCurrentStream;
    private boolean mStreamingState = false;
    private AsyncTask<Void, Void, ApiException> getNewStreamTask = null;

    public VideoBroadcaster(Button button, Context context) {
        this.mBroadcastButton = button;
        this.mContext = context;

        this.mStreamsAPI = new StreamsApi();

        startBroadcastActivityOnClick();
    }

    public static void showAPIErrorIfAny(ApiException e, Context context) {

        // Display nothing if there is no error.
        if (e == null)
            return;

        String code = "" + e.getCode();
        String message = e.getMessage();

        // TODO: Display a message to the user
        Log.e(TAG, "Code: " + code);
        Log.e(TAG, "Message: " + message);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setMessage(message).setTitle(code);

        // 3. Get the AlertDialog from create()
        AlertDialog dialog = builder.create();
        dialog.show();

    }

    public void setup() {
        CineIoConfig config = new CineIoConfig();
        config.setSecretKey(SECRETS.CINE_SECRET_KEY);
        // config.setMasterKey(SECRETS.MASTER_KEY);
        mCineIoClient = new CineIoClient(config);

        mBroadcastConfig = new BroadcastConfig();
        mBroadcastConfig.selectCamera("back");
        mBroadcastConfig.lockOrientation("landscape");

//        TODO: Make sure the quality gets actually set to 480p.
//        Trying to fix #24
//        mBroadcastConfig.setHeight(480);
//        mBroadcastConfig.setWidth(720);
    }

    private void startBroadcastActivityOnClick() {

        View.OnClickListener mStartBroadcastListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Hide the broadcast button to signal that the click has been recorded.
                // Also avoids multiple stream requests
                // Don't forget to show() it:
                // - When the activity is initiatted. DONE
                // - if there is an error dialog.
                hide();

                new AsyncTask<Void, Void, ApiException>() {
                    protected ApiException doInBackground(Void... params) {
                        try {

                            Log.v(TAG, "Asking for a new stream...");
                            mCurrentStream = mStreamsAPI.streamsPost();

                            return null;

                        } catch (ApiException e) {
                            // Pass the error onto the UI thread so it can be displayed.
                            return e;
                        }
                    }

                    @Override
                    public void onPostExecute(ApiException result) {

                        super.onPostExecute(result);

                        if (result == null) {
                            Log.v(TAG, "Got new stream: " + mCurrentStream);
                            broadcast(mCurrentStream);
                            return;
                        }

                        showAPIErrorIfAny(result, mContext);
                        Log.e(TAG, "Unable to get a new stream.");

                        // Show the broadcast button back
                        show();
                    }
                }.execute();
            }
        };

        mBroadcastButton.setOnClickListener(mStartBroadcastListener);
    }

    public void setStreamingState(boolean newStreamingState) {
        this.mStreamingState = newStreamingState;
    }

    public boolean isStreaming() {
        return this.mStreamingState;
    }

    private void broadcast(Stream stream) {

        if (stream != null && !this.isStreaming()) {

            Log.i(TAG, "Starting broadcast on stream " + stream.getId());
            mCineIoClient.broadcast(stream.getId(), mBroadcastConfig, mContext);

            this.setStreamingState(true);
            mHeartbeater = new Heartbeater(stream, mContext);
            mHeartbeater.start();
        }
    }

    public void destroyCurrentStream() {

        if (!this.isStreaming()) {
            return;
        }
        // Makes sure there is an actual stream.
        if (mCurrentStream != null) {
            new AsyncTask<Void, Void, ApiException>() {
                protected ApiException doInBackground(Void... params) {

                    try {
                        String id = mCurrentStream.getId();
                        String password = mCurrentStream.getPassword();

                        Log.i(TAG, "DELETING " + id + "?p=" + password);
                        mStreamsAPI.streamStreamIdDelete(id, password, password);

                        return null;

                    } catch (ApiException e) {

                        // Pass the error onto the UI thread so it can be displayed.
                        return e;
                    }
                }

                @Override
                public void onPostExecute(ApiException result) {
                    if (result == null) {
                        Log.i(TAG, "Stream deleted (" + mCurrentStream.getId() + ")");
                        setStreamingState(false);
                    }

                    showAPIErrorIfAny(result, mContext);

                    // Show the broadcast button back
                    show();
                }
            }.execute();
        }

        mHeartbeater.stop();
    }

    /**
     * @return false if the view was already visible.
     */
    public boolean show() {
        int old = mBroadcastButton.getVisibility();

        mBroadcastButton.setVisibility(View.VISIBLE);

        return old != View.VISIBLE;
    }

    /**
     * @return false if the view was already visible.
     */
    public boolean hide() {
        int old = mBroadcastButton.getVisibility();

        mBroadcastButton.setVisibility(View.GONE);

        return old != View.GONE;
    }
}
