package fly.speedmeter.grub;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.SystemClock;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.os.Bundle;
//import androidx.appcompat.widget.Toolbar;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

public class MainActivity extends AppCompatActivity implements LocationListener,
                                  PositioningStatus.Listener {

    private SharedPreferences sharedPreferences;
    private LocationManager mLocationManager;
    private static Data data;

    private MaterialToolbar toolbar;
    private FloatingActionButton fab;
    private Menu optionsMenu;
    //private ProgressBarCircularIndeterminate progressBarCircularIndeterminate;
    private TextView satellite;
    private TextView accuracy;
    private TextView currentSpeed;
    private TextView maxSpeed;
    private TextView averageSpeed;
    private TextView distance;
    private Chronometer time;
    private Data.OnGpsServiceUpdate onGpsServiceUpdate;
    private PositioningStatus mPositioningStatus;

    private boolean firstfix;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        data = new Data(onGpsServiceUpdate);

        sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        toolbar = (MaterialToolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setVisibility(View.INVISIBLE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 1337);
        }

        onGpsServiceUpdate = new Data.OnGpsServiceUpdate() {
            @Override
            public void update() {
                boolean autoAverage = sharedPreferences.getBoolean("auto_average", false);
                boolean imperial = sharedPreferences.getBoolean("miles_per_hour", false);
                
                double maxSpeedTemp = data.getMaxSpeed();
                double distanceTemp = data.getDistance();
                                
                double averageTemp = (autoAverage ? data.getAverageSpeedMotion() :
                                                   data.getAverageSpeed());

                String speedUnits = (imperial ? "mi/h" : "km/h");
                String distanceUnits = (imperial ? "mi" :
                                                   (distanceTemp <= 1000.0 ? "m" : "km"));
                if (imperial) {
                    maxSpeedTemp *= 0.62137119;
                    distanceTemp = distanceTemp / 1000.0 * 0.62137119;
                    averageTemp *= 0.62137119;
                } else {
                    if (distanceTemp >= 1000.0) {
                        distanceTemp /= 1000.0;
                    }
                }

                SpannableString s = new SpannableString(String.format("%.0f %s", maxSpeedTemp, speedUnits));
                s.setSpan(new RelativeSizeSpan(0.5f), s.length() - speedUnits.length() - 1, s.length(), 0);
                maxSpeed.setText(s);

                s = new SpannableString(String.format("%.0f %s", averageTemp, speedUnits));
                s.setSpan(new RelativeSizeSpan(0.5f), s.length() - speedUnits.length() - 1, s.length(), 0);
                averageSpeed.setText(s);

                s = new SpannableString(String.format("%.02f %s", distanceTemp, distanceUnits));
                s.setSpan(new RelativeSizeSpan(0.5f), s.length() - distanceUnits.length() - 1, s.length(), 0);
                distance.setText(s);
            }
        };

        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        boolean imperial = sharedPreferences.getBoolean("miles_per_hour", false);

        String speedUnits = (imperial ? "mi/h" : "km/h");
        String lengthUnits = (imperial ? "mi" : "m");

        satellite = (TextView) findViewById(R.id.satelliteData);
        accuracy = (TextView) findViewById(R.id.accuracyData);

        maxSpeed = (TextView) findViewById(R.id.maxSpeedData);

        averageSpeed = (TextView) findViewById(R.id.averageSpeedData);

        distance = (TextView) findViewById(R.id.distance);
        distance.setText("---");

        time = (Chronometer) findViewById(R.id.time);
        time.setBase(SystemClock.elapsedRealtime());

        SpannableString s = new SpannableString(String.format("%.0f %s", 0.0f, speedUnits));
        s.setSpan(new RelativeSizeSpan(0.25f), s.length() - speedUnits.length() - 1, s.length(), 0);

        currentSpeed = (TextView) findViewById(R.id.currentSpeed);
        currentSpeed.setText(s);
        //progressBarCircularIndeterminate = (ProgressBarCircularIndeterminate) findViewById(R.id.progressBarCircularIndeterminate);
        
        createPositioningStatus();
    }

    public void onFabClick(View v) {
        if (!data.isRunning()) {
            fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_pause));
            data.setRunning(true);
            time.setBase(SystemClock.elapsedRealtime() - data.getTime());
            time.start();
            data.setFirstTime(true);
            startService(new Intent(getBaseContext(), GpsServices.class));
            optionsMenu.findItem(R.id.action_refresh).setVisible(false);

        } else {
            fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_play));
            data.setRunning(false);
            data.setTime(SystemClock.elapsedRealtime() - time.getBase());
            time.stop();
            toolbar.setTitle(R.string.app_name);
            stopService(new Intent(getBaseContext(), GpsServices.class));
            optionsMenu.findItem(R.id.action_refresh).setVisible(true);
        }
    }

    public void onRefreshClick() {
        resetData();
        stopService(new Intent(getBaseContext(), GpsServices.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        firstfix = true;
        if (!data.isRunning()) {
            Gson gson = new Gson();
            String json = sharedPreferences.getString("data", "");
            data = gson.fromJson(json, Data.class);
        }
        if (data == null) {
            data = new Data(onGpsServiceUpdate);
        } else {
            data.setOnGpsServiceUpdate(onGpsServiceUpdate);
        }


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        if (mLocationManager == null) {
            return;
        }

        if (mLocationManager.getAllProviders().indexOf(LocationManager.GPS_PROVIDER) >= 0) {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this);
        } else {
            Log.w("MainActivity", "No GPS location provider found. GPS data display will not be available.");
        }

        /*if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            showGpsDisabledDialog();
        }

        mLocationManager.addGpsStatusListener(this);*/
        
        createPositioningStatus();
        
        if (!mPositioningStatus.isProviderEnabled()) {
            showGpsDisabledDialog();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mLocationManager != null) {
            mLocationManager.removeUpdates(this);
            //mLocationManager.removeGpsStatusListener(this);
        }
        mPositioningStatus.unregister();

        SharedPreferences.Editor prefsEditor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(data);
        prefsEditor.putString("data", json);
        prefsEditor.commit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService(new Intent(getBaseContext(), GpsServices.class));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        optionsMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                onRefreshClick();
                return true;
            case R.id.action_settings:
                startActivity(new Intent(this, Settings.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location.hasAccuracy()) {
            double acc = location.getAccuracy();
            String units;
            if (sharedPreferences.getBoolean("miles_per_hour", false)) {
                units = "ft";
                acc *= 3.28084;
            } else {
                units = "m";
            }

            SpannableString s = new SpannableString(String.format("%.0f %s", acc, units));
            s.setSpan(new RelativeSizeSpan(0.75f), s.length() - units.length() - 1, s.length(), 0);
            accuracy.setText(s);

            if (firstfix) {
                toolbar.setTitle(R.string.app_name);
                fab.show();
                if (!data.isRunning() && data.getMaxSpeed() != 0) {
                    optionsMenu.findItem(R.id.action_refresh).setVisible(true);
                }
                firstfix = false;
            }
        } else {
            firstfix = true;
        }

        if (location.hasSpeed()) {
            //progressBarCircularIndeterminate.setVisibility(View.GONE);
            double speed = location.getSpeed() * 3.6;
            String units;
            if (sharedPreferences.getBoolean("miles_per_hour", false)) { // Convert to MPH
                speed *= 0.62137119;
                units = "mi/h";
            } else {
                units = "km/h";
            }
            SpannableString s = new SpannableString(String.format("%.0f %s", speed, units));
            s.setSpan(new RelativeSizeSpan(0.25f), s.length() - units.length() - 1, s.length(), 0);
            currentSpeed.setText(s);
        }

    }

    /*@Override
    public void onGpsStatusChanged(int event) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        switch (event) {
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:

                GpsStatus gpsStatus = mLocationManager.getGpsStatus(null);
                int satsInView = 0;
                int satsUsed = 0;
                Iterable<GpsSatellite> sats = gpsStatus.getSatellites();
                for (GpsSatellite sat : sats) {
                    satsInView++;
                    if (sat.usedInFix()) {
                        satsUsed++;
                    }
                }

                satellite.setText(String.format("%d/%d", satsUsed, satsInView));
                if (satsUsed == 0) {
                    fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_play));
                    data.setRunning(false);
                    toolbar.setTitle(R.string.app_name);
                    stopService(new Intent(getBaseContext(), GpsServices.class));
                    fab.hide();
                    optionsMenu.findItem(R.id.action_refresh).setVisible(false);

                    accuracy.setText(String.format("%.0f %s", 0.0f,
                                    sharedPreferences.getBoolean("miles_per_hour", false) ? "ft" : "m"));

                    toolbar.setTitle(R.string.waiting_for_fix);
                    firstfix = true;
                }
                break;

            case GpsStatus.GPS_EVENT_STOPPED:
                if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    showGpsDisabledDialog();
                }
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                break;
        }
    }*/
    
    @Override
    public void onStatusChanged(int event) {
        
        switch (event) {
            case 0: {
                if (!mPositioningStatus.isProviderEnabled()) {
                   fab.hide();
                   data.setRunning(false);
                   Toast.makeText(getApplicationContext(), getResources().getString(R.string.gps_disabled), Toast.LENGTH_SHORT)
                   .show();
                }
                break;
            }
            case 1:
                break;
            case 2: {
                satellite.setText(String.format("%d/%d", mPositioningStatus.getSatellitesUsed(),
                                        mPositioningStatus.getSatelliteCount()));
        
                if (mPositioningStatus.getSatellitesUsed() == 0) {
                    fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_play));
                    fab.hide();
                    data.setRunning(false);
                    stopService(new Intent(getBaseContext(), GpsServices.class));
                    optionsMenu.findItem(R.id.action_refresh).setVisible(false);

                    accuracy.setText(String.format("%.0f %s", 0.0f,
                                    sharedPreferences.getBoolean("miles_per_hour", false) ? "ft" : "m"));

                    toolbar.setTitle(R.string.waiting_for_fix);
                    firstfix = true;
                }
                break;
            }
        }
    }

    public void showGpsDisabledDialog(){
        new MaterialAlertDialogBuilder(this)
                .setTitle(getResources().getString(R.string.gps_disabled))
                .setMessage(getResources().getString(R.string.please_enable_gps))
                .setPositiveButton(getResources().getString(R.string.accept), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent("android.settings.LOCATION_SOURCE_SETTINGS"));
                    }
                })
                .show();
    }

    public void resetData(){
        fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_play));
        optionsMenu.findItem(R.id.action_refresh).setVisible(false);

        maxSpeed.setText("");

        averageSpeed.setText("");

        distance.setText("---");

        time.stop();
        time.setBase(SystemClock.elapsedRealtime());

        data = new Data(onGpsServiceUpdate);
    }

    public static Data getData() {
        return data;
    }

    @Override
    public void onBackPressed(){
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {}

    @Override
    public void onProviderEnabled(String s) {}

    @Override
    public void onProviderDisabled(String s) {}
    
    private void createPositioningStatus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            mPositioningStatus = new GnssStatusNg(this, this);
        }
        else {
            mPositioningStatus = new GpsStatusLegacy(this, this);
        }
    }
}