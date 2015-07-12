package com.customer.example;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.gracenote.gnsdk.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;


public class ListenForArtistActivity extends Activity {
    static final String 				gnsdkClientId 			= "13401088";
    static final String 				gnsdkClientTag 			= "CE38DCE82826B79747054DABF037C8DF";
    static final String 				gnsdkLicenseFilename 	= "license.txt";	// app expects this file as an "asset"
    private static final String 		appString				= "DEBUG";

    private Activity					activity;
    private Context context;

    // Gracenote objects
    private GnManager 					gnManager;
    private GnUser 						gnUser;
    private GnMusicIdStream 			gnMusicIdStream;
    private IGnAudioSource				gnMicrophone;

    // store some tracking info about the most recent MusicID-Stream lookup
    protected volatile boolean 			lastLookup_local		 = false;	// indicates whether the match came from local storage
    protected volatile long				lastLookup_matchTime 	 = 0;  		// total lookup time for query
    protected volatile long				lastLookup_startTime;  				// start time of query
    private volatile boolean			audioProcessingStarted   = false;
    private volatile boolean			analyzingCollection 	 = false;
    private volatile boolean			analyzeCancelled 	 	 = false;

    // UI Objects
    Button listen_button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listen_for_artist);

        activity = this;
        context  = this.getApplicationContext();

        createUI();

        // get the gnsdk license from the application assets
        String gnsdkLicense = getAssetAsString(gnsdkLicenseFilename);

        try {
            gnManager = new GnManager( context, gnsdkLicense, GnLicenseInputMode.kLicenseInputModeString );
            gnUser = new GnUser( new GnUserStore(context), gnsdkClientId, gnsdkClientTag, appString );

            // enable local MusicID-Stream recognition (GNSDK storage provider must be enabled as pre-requisite)
            GnLookupLocalStream.enable();

            // Ingest MusicID-Stream local bundle, perform in another thread as it can be lengthy
            Thread ingestThread = new Thread( new LocalBundleIngestRunnable(context) );
            ingestThread.start();

            gnMicrophone = new GnMic();
            gnMusicIdStream = new GnMusicIdStream( gnUser, GnMusicIdStreamPreset.kPresetMicrophone, new MusicIDStreamEvents() );
            gnMusicIdStream.options().lookupData(GnLookupData.kLookupDataContent, true);
            gnMusicIdStream.options().lookupData(GnLookupData.kLookupDataSonicData, true);
            gnMusicIdStream.options().resultSingle(true);


        } catch ( GnException e ) {
            Log.e(appString, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule() );
            return;
        } catch ( Exception e ) {
            if(e.getMessage() != null){
                Log.e(appString, e.getMessage() );
            }
            else{
                e.printStackTrace();
            }
            return;

        }
    }

    public void createUI() {
        listen_button = (Button) findViewById(R.id.listen_button);
        listen_button.setEnabled( false );
    }

    public void initializeGraceNote(View v) {
        Button btn = (Button) findViewById(R.id.listen_button);
        btn.setText("Listening...");
    }

    /**
     * Helpers to read license file from assets as string
     */
    private String getAssetAsString( String assetName ){

        String 		assetString = null;
        InputStream assetStream;

        try {

            assetStream = this.getApplicationContext().getAssets().open(assetName);
            if(assetStream != null){

                java.util.Scanner s = new java.util.Scanner(assetStream).useDelimiter("\\A");

                assetString = s.hasNext() ? s.next() : "";
                assetStream.close();

            }else{
                Log.e(appString, "Asset not found:" + assetName);
            }

        } catch (IOException e) {

            Log.e( appString, "Error getting asset as string: " + e.getMessage() );

        }

        return assetString;
    }


    /**
     * GNSDK bundle ingest status event delegate
     */
    private class BundleIngestEvents implements IGnLookupLocalStreamIngestEvents{

        @Override
        public void statusEvent(GnLookupLocalStreamIngestStatus status, String bundleId, IGnCancellable canceller) {
//            setStatus("Bundle ingest progress: " + status.toString() , true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Loads a local bundle for MusicID-Stream lookups
     */
    class LocalBundleIngestRunnable implements Runnable {
        Context context;

        LocalBundleIngestRunnable(Context context) {
            this.context = context;
        }

        public void run() {
            try {

                // our bundle is delivered as a package asset
                // to ingest the bundle access it as a stream and write the bytes to
                // the bundle ingester
                // bundles should not be delivered with the package as this, rather they
                // should be downloaded from your own online service

                InputStream 	bundleInputStream 	= null;
                int				ingestBufferSize	= 1024;
                byte[] 			ingestBuffer 		= new byte[ingestBufferSize];
                int				bytesRead			= 0;

                GnLookupLocalStreamIngest ingester = new GnLookupLocalStreamIngest(new BundleIngestEvents());

                try {

                    bundleInputStream = context.getAssets().open("1557.b");

                    do {

                        bytesRead = bundleInputStream.read(ingestBuffer, 0, ingestBufferSize);
                        if ( bytesRead == -1 )
                            bytesRead = 0;

                        ingester.write( ingestBuffer, bytesRead );

                    } while( bytesRead != 0 );

                } catch (IOException e) {
                    e.printStackTrace();
                }

                ingester.flush();

            } catch (GnException e) {
                Log.e( appString, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule() );
            }

        }
    }

    /**
     * GNSDK MusicID-Stream event delegate
     */
    private class MusicIDStreamEvents implements IGnMusicIdStreamEvents {

        HashMap<String, String> gnStatus_to_displayStatus;

        public MusicIDStreamEvents(){
            gnStatus_to_displayStatus = new HashMap<String,String>();
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingStarted.toString(), "Identification started");
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingFpGenerated.toString(), "Fingerprinting complete");
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingLocalQueryStarted.toString(), "Lookup started");
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingOnlineQueryStarted.toString(), "Lookup started");
//			gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingEnded.toString(), "Identification complete");
        }

        @Override
        public void statusEvent( GnStatus status, long percentComplete, long bytesTotalSent, long bytesTotalReceived, IGnCancellable cancellable ) {

        }

        @Override
        public void musicIdStreamProcessingStatusEvent( GnMusicIdStreamProcessingStatus status, IGnCancellable canceller ) {

            if(GnMusicIdStreamProcessingStatus.kStatusProcessingAudioStarted.compareTo(status) == 0)
            {
                audioProcessingStarted = true;
                activity.runOnUiThread(new Runnable (){
                    public void run(){
                        listen_button.setEnabled(true);
                    }
                });

            }

        }

        @Override
        public void musicIdStreamIdentifyingStatusEvent( GnMusicIdStreamIdentifyingStatus status, IGnCancellable canceller ) {
            if(gnStatus_to_displayStatus.containsKey(status.toString())){
//                setStatus( String.format("%s", gnStatus_to_displayStatus.get(status.toString())), true );
            }

            if(status.compareTo( GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingLocalQueryStarted ) == 0 ){
                lastLookup_local = true;
            }
            else if(status.compareTo( GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingOnlineQueryStarted ) == 0){
                lastLookup_local = false;
            }

            if ( status == GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingEnded )
            {
//                setUIState( UIState.READY );
            }
        }


        @Override
        public void musicIdStreamAlbumResult( GnResponseAlbums result, IGnCancellable canceller ) {
            lastLookup_matchTime = SystemClock.elapsedRealtime() - lastLookup_startTime;
//            activity.runOnUiThread(new UpdateResultsRunnable( result ));
        }

        @Override
        public void musicIdStreamIdentifyCompletedWithError(GnError error) {
//            if ( error.isCancelled() )
//                setStatus( "Cancelled", true );
//            else
//                setStatus( error.errorDescription(), true );
//                setUIState( UIState.READY );
        }
    }
}
