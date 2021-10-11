package fly.speedmeter.grub;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.LocationManager;
import android.Manifest;

import androidx.core.content.PermissionChecker;

public class GpsStatusLegacy implements GpsStatus.Listener, PositioningStatus {
    
    boolean mEnabled;
    int satellitesUsed;
    int satelliteCount;
    
    Listener mListener;
    
    private Context mContext;
    private LocationManager mLocationManager;
    
    public GpsStatusLegacy(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
        setupObjects();
    }
    
    private void setupObjects() {
        if (PermissionChecker.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PermissionChecker.PERMISSION_GRANTED) {
            return;
        }
        
        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.addGpsStatusListener(this);
    }
    
    @Override
    public boolean isProviderEnabled() {
        return mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
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
    public void setListener(Listener listener) {
        mListener = listener;
    }
    
    @Override
    public void onGpsStatusChanged(int event) {
        if (PermissionChecker.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PermissionChecker.PERMISSION_GRANTED) {
            return;
        }
        
        switch (event) {
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS: {
                GpsStatus status = mLocationManager.getGpsStatus(null);
                
                satellitesUsed = 0;
                satelliteCount = 0;
                
                for (GpsSatellite satellite : status.getSatellites()) {
                    satelliteCount++;
                    
                    if (satellite.usedInFix()) {
                        satellitesUsed++;
                    }
                }
                
                if (mListener != null) {
                    mListener.onStatusChanged(2);
                }
                
                break;
            }
            case GpsStatus.GPS_EVENT_STOPPED: {
                if (mListener != null) {
                    mListener.onStatusChanged(0);
                }
                break;
            }
            case GpsStatus.GPS_EVENT_STARTED: {
                if (mListener != null) {
                    mListener.onStatusChanged(1);
                }
                break;
            }
        }        
    }
}