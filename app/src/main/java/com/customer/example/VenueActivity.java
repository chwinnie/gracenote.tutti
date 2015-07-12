package com.customer.example;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.TextView;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class VenueActivity extends Activity implements OnTaskCompleted {
    TextView textview;
    String SESSION_URL_BASE = "http://symphonic2-1003.appspot.com/sessions-list";
    String VENUE_URL_BASE = "http://symphonic2-1003.appspot.com/match/";
    String VENUE_URL_FORMAT = "/json";
    String artist;

    private OnTaskCompleted listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_venue);

        textview = (TextView) findViewById(R.id.TextView01);

        Bundle bundle = getIntent().getExtras();
        artist = bundle.getString("artist");

//        final Animation animation = new AlphaAnimation(1, 0); // Change alpha from fully visible to invisible
//        animation.setDuration(500); // duration - half a second
//        animation.setInterpolator(new LinearInterpolator()); // do not alter animation rate
//        animation.setRepeatCount(Animation.INFINITE); // Repeat animation infinitely
//        animation.setRepeatMode(Animation.REVERSE); // Reverse animation at the end so the button will fade back in
//        final Button btn = (Button) findViewById(R.id.btn);
//        btn.startAnimation(animation);

//        TextView tv = (TextView) findViewById(R.id.textView);
//        tv.setText(artist);

        displaySessions(textview);

//            btn.setText("Success!");

    }

    public void onTaskCompleted(boolean isReady) {
        if (!isReady) {

        }
    }

    public void displaySessions(View view) {
        PingCloudServerTask task = new PingCloudServerTask(listener);
        task.setView(textview);

        String venueURL = VENUE_URL_BASE + artist + VENUE_URL_FORMAT;
        task.execute(new String[]{venueURL});



    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_venue, menu);
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
