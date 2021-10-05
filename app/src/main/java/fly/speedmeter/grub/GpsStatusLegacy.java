package fly.speedmeter.grub;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.LocationManager;

public class GpsStatusLegacy implements GpsStatus.Listener, PositioningStatus {
    
    boolean mEnabled;
    int satellitesUsed;
    int satelliteCount;
    
    private Context mContext;
    private LocationManager mLocationManager;
    
    public GpsStatusLegacy(Context context) {
        mContext = context;
        setupObjects();
    }
    
    private void setupObjects() {
        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        
        mLocationManager.addGpsStatusListener(this);
    }
    
    @Override
    public boolean enabled() {
        return mEnabled;
    }
    
    @Override
    public int getSatelliteCount() {
        return satelliteCount;
    }
    
    @Override
    public int getSatellitesUsed() {
        return satellitesUsed;
    }
    
    @Override
    public void unregister() {
        mLocationManager.removeGpsStatusListener(this);
    }
    
    @Override
    public void update() { }
    
    @Override
    public void onGpsStatusChanged(int event) {
        switch (event) {
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS: {
                GpsStatus status = mLocationManager.getGpsStatus(null);
                
                for (GpsSatellite satellite : status.getSatellites()) {
                    satelliteCount++;
                    
                    if (satellite.usedInFix()) {
                        satellitesUsed++;
                    }
                }
                break;
            }
            case GpsStatus.GPS_EVENT_STOPPED:
                mEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                break;
        }
        update();
    }
}