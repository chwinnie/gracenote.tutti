package com.customer.example;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.gracenote.gnsdk.*;

import java.io.IOException;
import java.io.InputStream;


public class ListenForArtistActivity extends Activity {
    static final String 				gnsdkClientId 			= "13401088";
    static final String 				gnsdkClientTag 			= "CE38DCE82826B79747054DABF037C8DF";
    static final String 				gnsdkLicenseFilename 	= "license.txt";	// app expects this file as an "asset"
    private static final String 		appString				= "DEBUG";

    private Activity					activity;
    private Context context;

    // Gracenote objects
    private GnManager 					gnManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listen_for_artist);

        activity = this;
        context  = this.getApplicationContext();

        // get the gnsdk license from the application assets
        String gnsdkLicense = getAssetAsString(gnsdkLicenseFilename);

        try {
            gnManager = new GnManager( context, gnsdkLicense, GnLicenseInputMode.kLicenseInputModeString );
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
}
