package com.customer.example;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.customer.example.R;


public class ConcertListActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_concert_list);

    }

    public void startLandsLoad(View v) {
        String audioURL = "lands image";
        String queryURL = "landsquery";

        Intent intent = new Intent(this, LoadVenueActivity.class);
        intent.putExtra("audioURL", audioURL);
        intent.putExtra("queryURL", queryURL);

        startActivity(intent);
    }

    public void startPanhandlesLoad(View v) {
        String audioURL = "panHandle image";
        String queryURL = "panHandle image";

        Intent intent = new Intent(this, LoadVenueActivity.class);
        intent.putExtra("audioURL", audioURL);
        intent.putExtra("queryURL", queryURL);

        startActivity(intent);
    }

    public void startSutroLoad(View v) {
        String audioURL = "sutro image";
        String queryURL = "sutro query";

        Intent intent = new Intent(this, LoadVenueActivity.class);
        intent.putExtra("audioURL", audioURL);
        intent.putExtra("queryURL", queryURL);

        startActivity(intent);
    }

    public void startTwinPeaksLoad(View v) {
        String audioURL = "twinpeaks image";
        String queryURL = "twinpeaks query";

        Intent intent = new Intent(this, LoadVenueActivity.class);
        intent.putExtra("audioURL", audioURL);
        intent.putExtra("queryURL", queryURL);

        startActivity(intent);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_concert_list, menu);
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
