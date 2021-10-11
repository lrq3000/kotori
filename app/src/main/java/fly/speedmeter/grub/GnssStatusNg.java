package fly.speedmeter.grub;

import android.content.Context;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.Manifest;

import androidx.annotation.RequiresApi;
import androidx.core.content.PermissionChecker;

@RequiresApi(24)
public class GnssStatusNg extends GnssStatus.Callback implements PositioningStatus {
    
    int satellitesUsed;
    int satelliteCount;
    
    Listener mListener;
    
    private Context mContext;
    private LocationManager mLocationManager;
    
    public GnssStatusNg(Context context, Listener listener) {
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
        
        mLocationManager.registerGnssStatusCallback(this, null);
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
        mLocationManager.unregisterGnssStatusCallback(this);
    }
    
    @Override
    public void setListener(Listener listener) {
        mListener = listener;
    }
    
    @Override
    public void onSatelliteStatusChanged(GnssStatus status) {
        satelliteCount = status.getSatelliteCount();
        satellitesUsed = 0;
        
        for (int i = 0; i < satelliteCount; i++) {
            if (status.usedInFix(i)) {
                satellitesUsed++;
            }
        }
        
        if (mListener != null) {
            mListener.onStatusChanged(2);
        }
    }
    
    @Override
    public void onStarted() {
        if (mListener != null) {
            mListener.onStatusChanged(1);
        }
    }
    
    @Override
    public void onStopped() {      
        if (mListener != null) {
            mListener.onStatusChanged(0);
        }
    }
}