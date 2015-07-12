package com.customer.example;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;

import com.gracenote.gnsdk.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


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

//    private List<GnMusicIdStream> streamIdObjects			= new ArrayList<GnMusicIdStream>();

    // UI Objects
    private Button listen_button;
    private AudioVisualizeDisplay		audioVisualizeDisplay;
    private LinearLayout				linearLayoutVisContainer;

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
            gnManager.systemEventHandler( new SystemEvents() );
            gnUser = new GnUser( new GnUserStore(context), gnsdkClientId, gnsdkClientTag, appString );

            // provide handler to receive system events, such as locale update needed


            // enable storage provider allowing GNSDK to use its persistent stores
            GnStorageSqlite.enable();

            // enable local MusicID-Stream recognition (GNSDK storage provider must be enabled as pre-requisite)
            GnLookupLocalStream.enable();

            // Loads data to support the requested locale, data is downloaded from Gracenote Service if not
            // found in persistent storage. Once downloaded it is stored in persistent storage (if storage
            // provider is enabled). Download and write to persistent storage can be lengthy so perform in
            // another thread
            Thread localeThread = new Thread(
                    new LocaleLoadRunnable(GnLocaleGroup.kLocaleGroupMusic,
                            GnLanguage.kLanguageEnglish,
                            GnRegion.kRegionGlobal,
                            GnDescriptor.kDescriptorDefault,
                            gnUser)
            );
            localeThread.start();

            // Ingest MusicID-Stream local bundle, perform in another thread as it can be lengthy
            Thread ingestThread = new Thread( new LocalBundleIngestRunnable(context) );
            ingestThread.start();

            gnMicrophone = new AudioVisualizeAdapter( new GnMic() );
            gnMusicIdStream = new GnMusicIdStream( gnUser, GnMusicIdStreamPreset.kPresetMicrophone, new MusicIDStreamEvents() );
            gnMusicIdStream.options().lookupData(GnLookupData.kLookupDataContent, true);
            gnMusicIdStream.options().lookupData(GnLookupData.kLookupDataSonicData, true);
            gnMusicIdStream.options().resultSingle(true);

            // Retain GnMusicIdStream object so we can cancel an active identification if requested
//            streamIdObjects.add(gnMusicIdStream);


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

        setStatus( "" , true );
        setUIState(UIState.READY);

    }

    public void createUI() {
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_listen_for_artist);

        listen_button = (Button) findViewById(R.id.listen_button);


        Log.e("DEBUG", "CREATEUI");

        listen_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.e("DEBUG", "CLICKED BUTTON");
                try {
                    Log.e("DEBUG", "IDENTIFY ALBUM ASYNC");
                    gnMusicIdStream.identifyAlbumAsync();
                    lastLookup_startTime = SystemClock.elapsedRealtime();

                } catch (GnException e) {

                    Log.e(appString, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule());
//                    showError(e.errorAPI() + ": " + e.errorDescription());

                }
            }
        });


        linearLayoutVisContainer = (LinearLayout)findViewById(R.id.linearLayoutVisContainer);
        audioVisualizeDisplay = new AudioVisualizeDisplay(this,linearLayoutVisContainer,0);

        boolean visShowing = true;
        audioVisualizeDisplay.setDisplay(visShowing, false);

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
     * Audio visualization adapter.
     * Sits between GnMic and GnMusicIdStream to receive audio data as it
     * is pulled from the microphone allowing an audio visualization to be
     * implemented.
     */
    class AudioVisualizeAdapter implements IGnAudioSource {

        private IGnAudioSource 	audioSource;
        private int				numBitsPerSample;
        private int				numChannels;

        public AudioVisualizeAdapter( IGnAudioSource audioSource ){
            this.audioSource = audioSource;
        }

        @Override
        public long sourceInit() {
            if ( audioSource == null ){
                return 1;
            }
            long retVal = audioSource.sourceInit();

            // get format information for use later
            if ( retVal == 0 ) {
                numBitsPerSample = (int)audioSource.sampleSizeInBits();
                numChannels = (int)audioSource.numberOfChannels();
            }

            return retVal;
        }

        @Override
        public long numberOfChannels() {
            return numChannels;
        }

        @Override
        public long sampleSizeInBits() {
            return numBitsPerSample;
        }

        @Override
        public long samplesPerSecond() {
            if ( audioSource == null ){
                return 0;
            }
            return audioSource.samplesPerSecond();
        }

        @Override
        public long getData(ByteBuffer buffer, long bufferSize) {
            if ( audioSource == null ){
                return 0;
            }

            long numBytes = audioSource.getData(buffer, bufferSize);

            if ( numBytes != 0 ) {
                // perform visualization effect here
                // Note: Since API level 9 Android provides android.media.audiofx.Visualizer which can be used to obtain the
                // raw waveform or FFT, and perform measurements such as peak RMS. You may wish to consider Visualizer class
                // instead of manually extracting the audio as shown here.
                // This sample does not use Visualizer so it can demonstrate how you can access the raw audio for purposes
                // not limited to visualization.
                audioVisualizeDisplay.setAmplitudePercent(rmsPercentOfMax(buffer,bufferSize,numBitsPerSample,numChannels), true);
            }

            return numBytes;
        }

        @Override
        public void sourceClose() {
            if ( audioSource != null ){
                audioSource.sourceClose();
            }
        }

        // calculate the rms as a percent of maximum
        private int rmsPercentOfMax( ByteBuffer buffer, long bufferSize, int numBitsPerSample, int numChannels) {
            double rms = 0.0;
            if ( numBitsPerSample == 8 ) {
                rms = rms8( buffer, bufferSize, numChannels );
                return (int)((rms*100)/(double)((double)(Byte.MAX_VALUE/2)));
            } else {
                rms = rms16( buffer, bufferSize, numChannels );
                return (int)((rms*100)/(double)((double)(Short.MAX_VALUE/2)));
            }
        }

        // calculate the rms of a buffer containing 8 bit audio samples
        private double rms8 ( ByteBuffer buffer, long bufferSize, int numChannels ) {

            long sum = 0;
            long numSamplesPerChannel = bufferSize/numChannels;

            for(int i = 0; i < numSamplesPerChannel; i+=numChannels)
            {
                byte sample = buffer.get();
                sum += (sample * sample);
            }

            return Math.sqrt( (double)(sum / numSamplesPerChannel) );
        }

        // calculate the rms of a buffer containing 16 bit audio samples
        private double rms16 ( ByteBuffer buffer, long bufferSize, int numChannels ) {

            long sum = 0;
            long numSamplesPerChannel = (bufferSize/2)/numChannels;	// 2 bytes per sample

            buffer.rewind();
            for(int i = 0; i < numSamplesPerChannel; i++)
            {
                short sample = Short.reverseBytes(buffer.getShort()); // reverse because raw data is little endian but Java short is big endian

                sum += (sample * sample);
                if ( numChannels == 2 ){
                    buffer.getShort();
                }
            }

            return Math.sqrt( (double)(sum / numSamplesPerChannel) );
        }
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

    class AudioVisualizeDisplay {

        private Activity					activity;
        private ViewGroup displayContainer;
        private View						view;
        ImageView bottomDisplayImageView;
        ImageView 							topDisplayImageView;
        private int							displayIndex;
        private float						zeroScaleFactor = 0.50f;
        private float						maxScaleFactor = 1.50f;
        private int							currentPercent = 50;
        private boolean						isDisplayed = false;
        private final int					zeroDelay = 150; // in milliseconds

        Timer zeroTimer;

        private FrameLayout.LayoutParams	bottomDisplayLayoutParams;
        private int							bottomDisplayImageHeight;
        private int							bottomDisplayImageWidth;
        private FrameLayout.LayoutParams 	topDisplayLayoutParams;
        private int							topDisplayImageHeight;
        private int							topDisplayImageWidth;

        AudioVisualizeDisplay( Activity activity, ViewGroup displayContainer, int displayIndex ) {
            this.activity = activity;
            this.displayContainer = displayContainer;
            this.displayIndex = displayIndex;

            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.visual_audio,null);

            // bottom layer
            bottomDisplayImageView = (ImageView)view.findViewById(R.id.imageViewAudioVisBottomLayer);
            bottomDisplayLayoutParams = (FrameLayout.LayoutParams)bottomDisplayImageView.getLayoutParams();
            BitmapDrawable bd=(BitmapDrawable) activity.getResources().getDrawable(R.drawable.colored_ring);
            bottomDisplayImageHeight=(int)((float)bd.getBitmap().getHeight() * zeroScaleFactor);
            bottomDisplayImageWidth=(int)((float)bd.getBitmap().getWidth() * zeroScaleFactor);

            // top layer
            topDisplayImageView = (ImageView)view.findViewById(R.id.imageViewAudioVisTopLayer);
            topDisplayLayoutParams = (FrameLayout.LayoutParams)topDisplayImageView.getLayoutParams();
            bd=(BitmapDrawable) activity.getResources().getDrawable(R.drawable.gracenote_logo);
            topDisplayImageHeight=(int)((float)bd.getBitmap().getHeight() * zeroScaleFactor);
            topDisplayImageWidth=(int)((float)bd.getBitmap().getWidth() * zeroScaleFactor);

            // set the size of the visualization image view container large enough to hold vis image
            TableRow tableRow = (TableRow)view.findViewById(R.id.tableRowVisImageContainer);
            tableRow.setMinimumHeight((int)(((float)bottomDisplayImageHeight * maxScaleFactor)) + 20); // room for scaling plus some padding
            tableRow.setGravity(Gravity.CENTER);
        }

        // display or hide the visualization view in the display container provided during construction
        void setDisplay( boolean display, boolean doOnMainThread ){

            // why do we set amplitude percentage here?
            // when we display the visualizer image we want it scaled, but setting the layout parameters
            // in the constructor doesn't nothing, so we call it hear to prevent it showing up full size and
            // then scaling a fraction of a second later when the first amplitude percent change
            // comes in
            if ( display ){
                SetDisplayAmplitudeRunnable setDisplayAmplitudeRunnable = new SetDisplayAmplitudeRunnable(currentPercent);
                if ( doOnMainThread ){
                    activity.runOnUiThread(setDisplayAmplitudeRunnable);
                }else{
                    setDisplayAmplitudeRunnable.run();
                }
            }

            SetDisplayRunnable setDisplayRunnable = new SetDisplayRunnable(display);
            if ( doOnMainThread ){
                activity.runOnUiThread(setDisplayRunnable);
            }else{
                setDisplayRunnable.run();
            }

//            linearLayoutHomeContainer.postInvalidate();
        }

        void setAmplitudePercent( int amplitudePercent, boolean doOnMainThread ){
            if ( isDisplayed && (currentPercent != amplitudePercent)){
                SetDisplayAmplitudeRunnable setDisplayAmplitudeRunnable = new SetDisplayAmplitudeRunnable(amplitudePercent);
                if ( doOnMainThread ){
                    activity.runOnUiThread(setDisplayAmplitudeRunnable);
                }else{
                    setDisplayAmplitudeRunnable.run();
                }
                currentPercent = amplitudePercent;

                // zeroing timer - cancel if we got a new amplitude before
                try {
                    if ( zeroTimer != null ) {
                        zeroTimer.cancel();
                        zeroTimer = null;
                    }
                    zeroTimer = new Timer();
                    zeroTimer.schedule( new ZeroTimerTask(),zeroDelay);
                } catch (IllegalStateException e){
                }
            }
        }

        int displayHeight(){
            return bottomDisplayImageHeight;
        }

        int displayWidth(){
            return bottomDisplayImageWidth;
        }

        class SetDisplayRunnable implements Runnable {
            boolean display;

            SetDisplayRunnable(boolean display){
                this.display = display;
            }

            @Override
            public void run() {
                if ( isDisplayed && (display == false) ) {
                    displayContainer.removeViewAt( displayIndex );
                    isDisplayed = false;
                } else if ( isDisplayed == false ) {
                    displayContainer.addView(view, displayIndex);
                    isDisplayed = true;
                }

            }
        }

        class SetDisplayAmplitudeRunnable implements Runnable {
            int percent;

            SetDisplayAmplitudeRunnable(int percent){
                this.percent = percent;
            }

            @Override
            public void run() {
                float scaleFactor = zeroScaleFactor + ((float)percent/100); // zero position plus audio wave amplitude percent
                if ( scaleFactor > maxScaleFactor )
                    scaleFactor = maxScaleFactor;
                bottomDisplayLayoutParams.height = (int)((float)bottomDisplayImageHeight * scaleFactor);
                bottomDisplayLayoutParams.width = (int)((float)bottomDisplayImageWidth * scaleFactor);
                bottomDisplayImageView.setLayoutParams( bottomDisplayLayoutParams );

                topDisplayLayoutParams.height = (int)((float)topDisplayImageHeight * zeroScaleFactor);
                topDisplayLayoutParams.width = (int)((float)topDisplayImageWidth * zeroScaleFactor);
                topDisplayImageView.setLayoutParams( topDisplayLayoutParams );
            }
        }

        class ZeroTimerTask extends TimerTask {

            @Override
            public void run() {
                zeroTimer = null;
                setAmplitudePercent(0,true);
            }

        }

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
                        Log.e("debug", "audioprocessing started");
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
                setUIState( UIState.READY );
            }
        }


        @Override
        public void musicIdStreamAlbumResult( GnResponseAlbums result, IGnCancellable canceller ) {
            lastLookup_matchTime = SystemClock.elapsedRealtime() - lastLookup_startTime;
            activity.runOnUiThread(new UpdateResultsRunnable( result ));
        }

        @Override
        public void musicIdStreamIdentifyCompletedWithError(GnError error) {
            if ( error.isCancelled() )
                setStatus( "Cancelled", true );
            else
                setStatus( error.errorDescription(), true );
                setUIState( UIState.READY );
        }
    }

    /**
     * Receives system events from GNSDK
     */
    class SystemEvents implements IGnSystemEvents {
        @Override
        public void localeUpdateNeeded( GnLocale locale ){

            // Locale update is detected
            try {
                locale.update( gnUser );
            } catch (GnException e) {
                Log.e( appString, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule() );
            }
        }

        @Override
        public void listUpdateNeeded( GnList list ) {
            // List update is detected
            try {
                list.update( gnUser );
            } catch (GnException e) {
                Log.e( appString, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule() );
            }
        }

        @Override
        public void systemMemoryWarning(long currentMemorySize, long warningMemorySize) {
            // only invoked if a memory warning limit is configured
        }
    }


    /**
     * Loads a locale
     */
    class LocaleLoadRunnable implements Runnable {
        GnLocaleGroup	group;
        GnLanguage		language;
        GnRegion		region;
        GnDescriptor	descriptor;
        GnUser			user;


        LocaleLoadRunnable(
                GnLocaleGroup group,
                GnLanguage		language,
                GnRegion		region,
                GnDescriptor	descriptor,
                GnUser			user) {
            this.group 		= group;
            this.language 	= language;
            this.region 	= region;
            this.descriptor = descriptor;
            this.user 		= user;
        }

        @Override
        public void run() {
            try {

                GnLocale locale = new GnLocale(group,language,region,descriptor,gnUser);
                locale.setGroupDefault();

            } catch (GnException e) {
                Log.e( appString, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule() );
            }
        }
    }

    /**
     * Helper to set the application status message
     */
    private void setStatus( String statusMessage, boolean clearStatus ){
        activity.runOnUiThread(new UpdateStatusRunnable( statusMessage, clearStatus ));
    }

    class UpdateStatusRunnable implements Runnable {

        boolean clearStatus;
        String status;

        UpdateStatusRunnable( String status, boolean clearStatus ){
            this.status = status;
            this.clearStatus = clearStatus;
        }

        @Override
        public void run() {
//            statusText.setVisibility(View.VISIBLE);
            if (clearStatus) {
//                statusText.setText(status);
            } else {
//                statusText.setText((String) statusText.getText() + "\n" + status);
            }
        }

    }

    /**
     * Helpers to enable/disable the application widgets
     */
    enum UIState{
        DISABLED,
        READY,
        INPROGRESS
    }

    private void setUIState( UIState uiState ) {
        activity.runOnUiThread(new SetUIState(uiState));
    }

    class SetUIState implements Runnable {

        UIState uiState;
        SetUIState( UIState uiState ){
            this.uiState = uiState;
        }

        @Override
        public void run() {

            boolean enabled = (uiState == UIState.READY);

//            buttonIDNow.setEnabled( enabled && audioProcessingStarted);
//            buttonTextSearch.setEnabled( enabled );
//            buttonHistory.setEnabled( enabled );
//            buttonLibraryID.setEnabled( enabled );
//            buttonCancel.setEnabled( (uiState == UIState.INPROGRESS) );
//            buttonSettings.setEnabled(enabled);
        }

    }

    /**
     * Adds album results to UI via Runnable interface
     */
    class UpdateResultsRunnable implements Runnable {

        GnResponseAlbums albumsResult;

        UpdateResultsRunnable(GnResponseAlbums albumsResult) {
            this.albumsResult = albumsResult;
        }

        @Override
        public void run() {
            try {
                if (albumsResult.resultCount() == 0) {

                    setStatus("No match", true);

                } else {

                    setStatus("Match found", true);
                    GnAlbumIterator iter = albumsResult.albums().getIterator();
                    while (iter.hasNext()) {
                        Log.e("VALUE", iter.next().gnId());

                    }

                }
            } catch (GnException e) {
                setStatus(e.errorDescription(), true);
                return;
            }

        }
    }

}
