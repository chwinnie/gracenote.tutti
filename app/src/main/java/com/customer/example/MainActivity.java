package com.customer.example;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;


public class MainActivity extends Activity {

    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView) findViewById(R.id.TextView01);
    }

    public void showVenueList(View view) {
        Intent intent = new Intent(this, VenueListActivity.class);
        startActivity(intent);
    }

    public void showListenView(View view) {
        Intent intent = new Intent(this, GracenoteMusicID.class);
        startActivity(intent);
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

//    private class DownloadWebPageTask extends AsyncTask<String, Void, String> {
//        @Override
//        protected String doInBackground(String... urls) {
//            String response = "";
//            for (String url : urls) {
//                DefaultHttpClient client = new DefaultHttpClient();
//                HttpGet httpGet = new HttpGet(url);
//                try {
//                    HttpResponse execute = client.execute(httpGet);
//                    InputStream content = execute.getEntity().getContent();
//
//                    BufferedReader buffer = new BufferedReader(new InputStreamReader(content));
//                    String s = "";
//                    while ((s = buffer.readLine()) != null) {
//                        response += s;
//                    }
//
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//            return response;
//        }
//
//        @Override
//        protected void onPostExecute(String result) {
//            textView.setText(result);
//        }
//    }

//    public void onClick(View view) {
//        PingCloudServerTask task = new PingCloudServerTask();
//        task.setView(textView);
//        task.execute(new String[] { "http://symphonic2-1003.appspot.com/sessions-list" });
//    }

}
